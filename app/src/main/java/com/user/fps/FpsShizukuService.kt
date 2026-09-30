package com.user.fps

import android.content.Context
import android.os.Binder
import android.util.Log
import java.io.InputStream
import java.util.concurrent.TimeUnit

class FpsShizukuService(context: Context) : IFpsService.Stub() {

    private val TAG = "FpsShizukuService"

    private val allowedCmd = "dumpsys SurfaceFlinger --latency"
    private val candidates = listOf("/system/bin/dumpsys", "dumpsys")

    private val expectedUid: Int = try {
        context.packageManager.getPackageUid(context.packageName, 0)
    } catch (e: Exception) {
        -1
    }

    private fun drain(stream: InputStream, into: StringBuilder): Thread = Thread {
        try {
            stream.bufferedReader().use { into.append(it.readText()) }
        } catch (_: Exception) {
        }
    }.apply { isDaemon = true }

    private fun kill(p: Process) {
        p.destroy()
        if (p.isAlive) p.destroyForcibly()
    }

    override fun runCommand(cmd: String): String {
        if (cmd.trim() != allowedCmd) {
            Log.w(TAG, "reject non-whitelisted command: $cmd")
            return ""
        }
        val caller = Binder.getCallingUid()
        if (expectedUid != -1 && caller != expectedUid)
            Log.i(TAG, "callerUid=$caller != appUid=$expectedUid (forwarded by framework, allow)")

        for (bin in candidates) {
            val p: Process = try {
                ProcessBuilder(bin, "SurfaceFlinger", "--latency").start()
            } catch (e: Exception) {
                Log.w(TAG, "$bin start failed: ${e.message}")
                continue
            }

            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = drain(p.inputStream, out).apply { start() }
            val tErr = drain(p.errorStream, err).apply { start() }

            val exited = try {
                p.waitFor(1, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                kill(p)
                tOut.join(200)
                tErr.join(200)
                return ""
            }

            if (!exited) {
                Log.w(TAG, "$bin timed out")
                kill(p)
                tOut.join(200)
                tErr.join(200)
                continue
            }

            tOut.join(500)
            tErr.join(500)
            if (out.isBlank() && err.isNotBlank())
                Log.w(TAG, "stderr: ${err.take(200)}")
            kill(p)
            return out.toString()
        }
        return ""
    }
}
