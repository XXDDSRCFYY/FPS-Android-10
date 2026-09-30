package com.user.fps

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

object FpsStore {

    const val MIN_INTERVAL_MS = 500L

    private const val NAME = "fps_cfg"
    private const val KEY_INTERVAL = "interval"

    private val lock = Any()

    @Volatile
    private var sp: SharedPreferences? = null

    private val listeners: MutableSet<SharedPreferences.OnSharedPreferenceChangeListener> =
        ConcurrentHashMap.newKeySet()

    private fun prefs(): SharedPreferences =
        sp ?: throw IllegalStateException(
            "FpsStore not initialized: call FpsStore.init(context) first")

    fun init(ctx: Context) {
        synchronized(lock) {
            if (sp == null) {
                sp = ctx.applicationContext
                    .getSharedPreferences(NAME, Context.MODE_PRIVATE)
                    .also { s ->
                        val stale = s.getLong(KEY_INTERVAL, MIN_INTERVAL_MS)
                        if (stale < MIN_INTERVAL_MS)
                            s.edit().putLong(KEY_INTERVAL, MIN_INTERVAL_MS).apply()
                    }
            }
            sp?.let { current ->
                listeners.forEach { current.registerOnSharedPreferenceChangeListener(it) }
            }
        }
    }

    fun register(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(lock) {
            if (listeners.add(l)) sp?.registerOnSharedPreferenceChangeListener(l)
        }
    }

    fun unregister(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(lock) {
            if (listeners.remove(l)) sp?.unregisterOnSharedPreferenceChangeListener(l)
        }
    }

    var textSize: Float
        get() = prefs().getFloat("text", 16f)
        set(v) = prefs().edit().putFloat("text", v).apply()

    var textColor: Int
        get() = prefs().getInt("tcolor", 0xFFFFFFFF.toInt())
        set(v) = prefs().edit().putInt("tcolor", v).apply()

    var bgColor: Int
        get() = prefs().getInt("bcolor", 0x99000000.toInt())
        set(v) = prefs().edit().putInt("bcolor", v).apply()

    var cornerRadius: Float
        get() = prefs().getFloat("radius", 36f)
        set(v) = prefs().edit().putFloat("radius", v).apply()

    var intervalMs: Long
        get() = prefs().getLong(KEY_INTERVAL, MIN_INTERVAL_MS)
        set(v) = prefs().edit().putLong(KEY_INTERVAL, v.coerceAtLeast(MIN_INTERVAL_MS)).apply()

    var posX: Int
        get() = prefs().getInt("posX", 60)
        set(v) = prefs().edit().putInt("posX", v).apply()

    var posY: Int
        get() = prefs().getInt("posY", 120)
        set(v) = prefs().edit().putInt("posY", v).apply()

    var showJank: Boolean
        get() = prefs().getBoolean("jank", true)
        set(v) = prefs().edit().putBoolean("jank", v).apply()

    var showHz: Boolean
        get() = prefs().getBoolean("hz", false)
        set(v) = prefs().edit().putBoolean("hz", v).apply()

    var demo: Boolean
        get() = prefs().getBoolean("demo", false)
        set(v) = prefs().edit().putBoolean("demo", v).apply()

    var hasReqShizuku: Boolean
        get() = prefs().getBoolean("reqShizuku", false)
        set(v) = prefs().edit().putBoolean("reqShizuku", v).apply()
}
