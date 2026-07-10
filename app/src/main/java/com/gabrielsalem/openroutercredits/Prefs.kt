package com.gabrielsalem.openroutercredits

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Prefs {
    private const val NAME = "or_widget_prefs"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getKey(context: Context, appWidgetId: Int): String? {
        return prefs(context).getString("key_$appWidgetId", null)
    }

    fun setKey(context: Context, appWidgetId: Int, key: String) {
        prefs(context).edit().putString("key_$appWidgetId", key).apply()
    }

    fun getTheme(context: Context, appWidgetId: Int): String {
        return prefs(context).getString("theme_$appWidgetId", WidgetTheme.DARK.id) ?: WidgetTheme.DARK.id
    }

    fun setTheme(context: Context, appWidgetId: Int, themeId: String) {
        prefs(context).edit().putString("theme_$appWidgetId", themeId).apply()
    }

    fun getBgAlpha(context: Context, appWidgetId: Int): Int {
        // default 85 (~transparência leve)
        return prefs(context).getInt("bgalpha_$appWidgetId", 85)
    }

    fun setBgAlpha(context: Context, appWidgetId: Int, alpha: Int) {
        prefs(context).edit().putInt("bgalpha_$appWidgetId", alpha.coerceIn(0, 100)).apply()
    }
}
