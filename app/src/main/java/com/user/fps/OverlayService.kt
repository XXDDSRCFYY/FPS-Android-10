package com.user.fps

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class OverlayService : Service(), DisplayManager.DisplayListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        var isRunning = false
    }

    private enum class Src { DEMO, OK, EMPTY, DENIED, BINDING, FAIL }

    private data class Cfg(
        val demo: Boolean,
        val showJank: Boolean,
        val showHz: Boolean,
        val intervalMs: Long
    )

    @Volatile
    private var cfg = Cfg(false, true, false, FpsStore.MIN_INTERVAL_MS)

    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val sampling = AtomicBoolean(false)
    private lateinit var wm: WindowManager
    private var tv: TextView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var hz = 60f
    private var tx = 0f
    private var ty = 0f
    private var nextTickAt = 0L

    private fun reloadCfg() {
        cfg = Cfg(
            FpsStore.demo, FpsStore.showJank, FpsStore.showHz,
            maxOf(FpsStore.intervalMs, FpsStore.MIN_INTERVAL_MS)
        )
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (cfg.demo) {
                val t = System.nanoTime() / 3e9
                render(
                    buildText(
                        (60f + 12f * sin(t).toFloat()).roundToInt(),
                        (8f * (1f - cos(t)) / 2f).toInt(),
                        Src.DEMO
                    )
                )
            } else if (sampling.compareAndSet(false, true)) {
                exec.execute {
                    var fps = -1
                    var jank = -1
                    var src = Src.FAIL
                    try {
                        when {
                            !ShizukuSource.isReady() -> src = Src.DENIED
                            !ShizukuSource.isBound() -> src = Src.BINDING
                            else -> {
                                val raw = ShizukuSource.readLatency()
                                when {
                                    raw.isNullOrBlank() -> src = Src.FAIL
                                    else -> {
                                        val st = FpsEngine.parseLatency(raw)
                                        if (st.isEmpty()) {
                                            src = Src.EMPTY
                                        } else {
                                            fps = FpsEngine.fpsOf(st).roundToInt()
                                            jank = FpsEngine.jankOf(st, hz)
                                            src = Src.OK
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        sampling.set(false)
                    }
                    main.post { render(buildText(fps, jank, src)) }
                }
            }
            main.removeCallbacks(this)
            val interval = cfg.intervalMs
            val now = SystemClock.uptimeMillis()
            var next = if (nextTickAt == 0L) now + interval else nextTickAt + interval
            if (next < now) next = now + interval
            nextTickAt = next
            main.postAtTime(this, next)
        }
    }

    private fun buildText(fps: Int, jank: Int, src: Src): String {
        val lines = mutableListOf<String>()
        lines += when {
            src == Src.DENIED -> "未授权 Shizuku"
            fps >= 0 -> "$fps FPS"
            else -> "-- FPS"
        }
        if (cfg.showJank && jank >= 0) lines += "掉帧 $jank%"
        if (cfg.showHz) lines += "${hz.roundToInt()} Hz"
        when (src) {
            Src.BINDING -> lines += "Shizuku 连接中…"
            Src.EMPTY -> lines += "SurfaceFlinger 无数据"
            Src.FAIL -> lines += "读取失败，请检查 Shizuku"
            else -> {}
        }
        return lines.joinToString("\n")
    }

    private fun render(text: String) {
        tv?.text = text
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startFgs()
        FpsStore.init(applicationContext)
        FpsStore.register(this)
        reloadCfg()
        val dm = getSystemService
