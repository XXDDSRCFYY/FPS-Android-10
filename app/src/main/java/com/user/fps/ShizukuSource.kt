package com.user.fps

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import rikka.shizuku.Shizuku

object ShizukuSource {

    private const val TAG = "ShizukuSource"

    private val bindRequested = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)

    @Volatile
    private var svc: IFpsService? = null

    private fun args(ctx: Context) = Shizuku.UserServiceArgs(
        ComponentName(ctx, FpsShizukuService::class.java)
    ).processNameSuffix("fps").version(1).debuggable(false)

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (bindRequested.get()) {
                svc = IFpsService.Stub.asInterface(binder)
                connected.set(true)
                Log.i(TAG, "Shizuku user service connected")
            } else {
                Log.w(TAG, "ignored late onServiceConnected after unbind")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connected.set(false)
            svc = null
            Log.w(TAG, "Shizuku user service disconnected")
        }
    }

    fun isReady(): Boolean = try {
    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
} catch (e: Exception) { false }

    val isBound: Boolean get() = connected.get()

    fun bind(ctx: Context) {
        if (!isReady()) return
        if (!bindRequested.compareAndSet(false, true)) return
        try {
            Shizuku.bindUserService(args(ctx.applicationContext), conn)
        } catch (e: Exception) {
            bindRequested.set(false)
            Log.e(TAG, "bindUserService failed", e)
        }
    }

    fun unbind(ctx: Context) {
        if (!bindRequested.compareAndSet(true, false)) return
        connected.set(false)
        try {
            Shizuku.unbindUserService(args(ctx.applicationContext), conn, false)
        } catch (e: Exception) {
            Log.w(TAG, "unbindUserService: ${e.message}")
        }
    }

    fun readLatency(): String? {
        if (!connected.get()) return null
        val s = svc ?: return null
        return try {
            s.runCommand("dumpsys SurfaceFlinger --latency")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
