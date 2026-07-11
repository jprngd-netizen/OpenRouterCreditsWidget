package com.gabrielsalem.openroutercredits

import android.content.Context
import android.content.SharedPreferences

object WidgetStateManager {
    private const val PREFS_NAME = "or_widget_cache"
    private const val KEY_LAST_CREDITS = "last_credits"
    private const val KEY_LAST_UPDATE = "last_update_ms"
    private const val OFFLINE_THRESHOLD_MS = 30L * 60 * 1000 // 30 min

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCredits(context: Context, remaining: Double) {
        prefs(context).edit()
            .putFloat(KEY_LAST_CREDITS, remaining.toFloat())
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    fun getCachedCredits(context: Context): Double? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_LAST_CREDITS)) return null
        return prefs.getFloat(KEY_LAST_CREDITS, 0f).toDouble()
    }

    fun isOffline(context: Context): Boolean {
        val lastUpdate = prefs(context).getLong(KEY_LAST_UPDATE, 0L)
        return lastUpdate == 0L || (System.currentTimeMillis() - lastUpdate) > OFFLINE_THRESHOLD_MS
    }

    fun getDisplayCredits(context: Context): String? {
        val cached = getCachedCredits(context) ?: return null
        return "$%.4f".format(cached)
    }
}
