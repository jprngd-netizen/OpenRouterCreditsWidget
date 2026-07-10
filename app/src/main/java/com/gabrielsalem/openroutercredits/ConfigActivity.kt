package com.gabrielsalem.openroutercredits

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class ConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED) // segurança: se fechar sem salvar, o widget não é adicionado

        setContentView(R.layout.activity_config)

        val edit = findViewById<EditText>(R.id.keyInput)
        val btn = findViewById<Button>(R.id.saveBtn)

        val existing = Prefs.getKey(this, appWidgetId)
        if (!existing.isNullOrBlank()) edit.setText(existing)

        btn.setOnClickListener {
            val key = edit.text.toString().trim()
            if (key.isEmpty()) {
                edit.error = "Informe a API key"
                return@setOnClickListener
            }
            Prefs.setKey(this, appWidgetId, key)

            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, result)

            CreditsWidgetProvider.updateWidget(
                this,
                AppWidgetManager.getInstance(this),
                appWidgetId
            )
            finish()
        }
    }

    companion object {
        fun pendingIntent(context: android.content.Context, appWidgetId: Int): android.app.PendingIntent {
            val intent = Intent(context, ConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    android.app.PendingIntent.FLAG_IMMUTABLE else 0
            return android.app.PendingIntent.getActivity(
                context,
                appWidgetId + 1000,
                intent,
                flags
            )
        }
    }
}
