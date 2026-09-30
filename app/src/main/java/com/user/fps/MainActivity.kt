package com.user.fps

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val shizukuReq = 1002
    private val uiHandler = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var btnStart: Button
    private var pendingOverlayGrant = false

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { req, _ ->
        if (req == shizukuReq) {
            FpsStore.hasReqShizuku = true
            refreshStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FpsStore.init(this)
        Shizuku.addRequestPermissionResultListener(shizukuListener)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        setContentView(ScrollView(this).apply { addView(box) })

        box.addView(TextView(this).apply {
            text = "FPS Monitor"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
        })

        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, 24, 0, 24)
        }
        box.addView(status)

        box.addView(Button(this).apply {
            text = "授权 Shizuku（免 root）"
            setOnClickListener {
                try {
                    when {
    ShizukuSource.isReady() -> toast("Shizuku 已授权 ✅")
    !FpsStore.hasReqShizuku -> requestShizukuPermission()
    Shizuku.shouldShowRequestPermissionRationale() ->
        toast("Shizuku 权限未授予，请前往 Shizuku 应用手动允许本应用")
    else -> requestShizukuPermission()
                    }
                } catch (e: Exception) {
                    toast("Shizuku 未就绪，请先激活")
                }
                refreshStatus()
            }
        })
            btnStart = Button(this).apply {
        text = "启动悬浮帧率"
        setOnClickListener {
            when {
                OverlayService.isRunning -> {
                    val ok = stopService(
                        Intent(this@MainActivity, OverlayService::class.java))
                    if (!ok) {
                        Log.w(TAG, "stopService=false but isRunning=true, force reset")
                        OverlayService.isRunning = false
                        refreshStatus()
                    } else {
                        isEnabled = false
                        pollStopped()
                    }
                }
                !Settings.canDrawOverlays(this@MainActivity) -> {
                    pendingOverlayGrant = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
                else -> {
                    toast("正在启动悬浮帧率…")
                    startOverlay()
                }
            }
        }
    }
    box.addView(btnStart)

    header(box, "──── 悬浮窗自定义 ────")
    slider(box, "字体大小", 10, 32, FpsStore.textSize.toInt(), { "$it sp" })
        { FpsStore.textSize = it.toFloat() }
    slider(box, "背景不透明度", 0, 255, FpsStore.bgColor ushr 24, { "$it / 255" })
        { FpsStore.bgColor = (FpsStore.bgColor and 0xFFFFFF) or (it shl 24) }
    slider(box, "背景圆角", 0, 80, FpsStore.cornerRadius.toInt(), { "$it px" })
        { FpsStore.cornerRadius = it.toFloat() }
    slider(box, "刷新间隔", 500, 2000,
        FpsStore.intervalMs.toInt().coerceIn(500, 2000), { "$it ms" })
        { FpsStore.intervalMs = it.toLong() }

    switchRow(box, "显示掉帧率", FpsStore.showJank) { FpsStore.showJank = it }
    switchRow(box, "显示屏幕刷新率", FpsStore.showHz) { FpsStore.showHz = it }
    switchRow(box, "演示模式（模拟数据，无需 Shizuku）", FpsStore.demo) { FpsStore.demo = it }

    header(box, "文字颜色")
    colorRow(box, listOf(
        "白" to 0xFFFFFFFFL, "黑" to 0xFF111111L, "黄" to 0xFFFFD600L,
        "青" to 0xFF00E5FFL, "粉" to 0xFFFF4081L, "绿" to 0xFF76FF03L
    )) { FpsStore.textColor = it.toInt() }
    header(box, "背景颜色")
    colorRow(box, listOf(
        "黑90%" to 0xE6000000L, "黑60%" to 0x99000000L, "黑30%" to 0x4D000000L,
        "白60%" to 0x99FFFFFFL, "蓝80%" to 0xCC1565C0L, "无" to 0x00000000L
    )) { FpsStore.bgColor = it.toInt() }

    refreshStatus()
}

override fun onResume() {
    super.onResume()
    maybeAutoStart()
    refreshStatus()
}
    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun requestShizukuPermission() {
        FpsStore.hasReqShizuku = true
        try {
            Shizuku.requestPermission(shizukuReq)
        } catch (e: Exception) {
            FpsStore.hasReqShizuku = false
            toast("Shizuku 未就绪，请先激活")
        }
    }

    private fun startOverlay() {
        if (OverlayService.isRunning) return
        try {
            ShizukuSource.bind(applicationContext)
            startForegroundService(Intent(this, OverlayService::class.java))
            pollStatus { toast("悬浮帧率已运行 ✅") }
        } catch (e: Exception) {
            if (e.javaClass.name.contains("ForegroundServiceStartNotAllowed"))
                toast("应用不在前台，请回到 App 后再试")
            else
                toast("启动失败：${e.message}")
        }
    }

    private fun maybeAutoStart() {
        if (!pendingOverlayGrant) return
        pendingOverlayGrant = false
        if (Settings.canDrawOverlays(this) && !OverlayService.isRunning) {
            toast("悬浮窗权限已授予，正在启动悬浮帧率…")
            startOverlay()
        } else if (!Settings.canDrawOverlays(this)) {
            toast("尚未授予悬浮窗权限，可再次点击「启动悬浮帧率」")
        }
    }

    private fun pollStatus(retries: Int = 5, onRunning: (() -> Unit)? = null) {
        if (OverlayService.isRunning) {
            onRunning?.invoke()
            refreshStatus()
            return
        }
        if (retries <= 0) {
            refreshStatus()
            return
        }
        uiHandler.postDelayed({ pollStatus(retries - 1, onRunning) }, 300)
    }

    private fun pollStopped(retries: Int = 8) {
        if (!OverlayService.isRunning) {
            btnStart.isEnabled = true
            refreshStatus()
            return
        }
        if (retries <= 0) {
            Log.w(TAG, "pollStopped timeout, onDestroy not called, force reset isRunning")
            OverlayService.isRunning = false
            btnStart.isEnabled = true
            refreshStatus()
            return
        }
        uiHandler.postDelayed({ pollStopped(retries - 1) }, 300)
    }

    private fun refreshStatus() {
        status.text = "悬浮窗权限：" + (if (Settings.canDrawOverlays(this)) "✅" else "❌") +
            "\nShizuku：" + (if (ShizukuSource.isReady()) "✅ 已授权" else "❌ 未授权") +
            "\n服务状态：" + (if (OverlayService.isRunning) "🟢 运行中" else "⚪ 已停止") +
            "\n架构支持：" + Build.SUPPORTED_ABIS.take(2).joinToString(" / ")
        btnStart.text = if (OverlayService.isRunning) "停止悬浮帧率" else "启动悬浮帧率"
    }

    private fun header(b: LinearLayout, t: String) =
        b.addView(TextView(this).apply {
            text = t
            textSize = 15f
            setPadding(0, 40, 0, 16)
        })

    private fun slider(b: LinearLayout, label: String, min: Int, max: Int,
                       init: Int, fmt: (Int) -> String, on: (Int) -> Unit) {
        val tv = TextView(this).apply { text = "$label：${fmt(init)}"; textSize = 14f }
        b.addView(tv)
        b.addView(SeekBar(this).apply {
            this.max = max - min
            progress = init - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val v = p + min
                    tv.text = "$label：${fmt(v)}"
                    on(v)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
    }

    private fun switchRow(b: LinearLayout, label: String, init: Boolean,
                          on: (Boolean) -> Unit) =
        b.addView(Switch(this).apply {
            text = label
            isChecked = init
            setOnCheckedChangeListener { _, c -> on(c) }
        })

    private fun colorRow(b: LinearLayout, colors: List<Pair<String, Long>>,
                         on: (Long) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colors.forEach { (name, argb) ->
            val r = ((argb shr 16) and 0xFF).toInt()
            val g = ((argb shr 8) and 0xFF).toInt()
            val b = (argb and 0xFF).toInt()
            val yiq = (r * 299 + g * 587 + b * 114) / 1000
            row.addView(TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (yiq >= 128) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(argb.toInt())
                    cornerRadius = 24f
                }
                setOnClickListener { on(argb) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = 8 })
        }
        b.addView(row)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
