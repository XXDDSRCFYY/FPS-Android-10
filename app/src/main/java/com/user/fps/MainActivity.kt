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
                        !Shizuku.pingBinder() -> toast("请先安装并激活 Shizuku")
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
            "黑90%" to 0xE6000000L, "黑60%" to 0x99000000L, "黑30%" to 0x6
