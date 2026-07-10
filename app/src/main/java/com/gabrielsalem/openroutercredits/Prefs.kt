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
}
