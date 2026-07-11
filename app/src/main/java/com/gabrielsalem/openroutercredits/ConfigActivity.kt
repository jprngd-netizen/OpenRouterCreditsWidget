package com.gabrielsalem.openroutercredits

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
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
        val themeSpinner = findViewById<Spinner>(R.id.themeSpinner)
        val alphaSeek = findViewById<SeekBar>(R.id.alphaSeek)
        val alphaLabel = findViewById<TextView>(R.id.alphaLabel)
        val btn = findViewById<Button>(R.id.saveBtn)

        // temas
        val themes = WidgetTheme.ALL
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            themes.map { it.label }
        )
        themeSpinner.adapter = adapter

        val existingKey = Prefs.getKey(this, appWidgetId)
        if (!existingKey.isNullOrBlank()) edit.setText(existingKey)

        val existingTheme = Prefs.getTheme(this, appWidgetId)
        val themeIndex = themes.indexOfFirst { it.id == existingTheme }.coerceAtLeast(0)
        themeSpinner.setSelection(themeIndex)

        val existingAlpha = Prefs.getBgAlpha(this, appWidgetId)
        alphaSeek.progress = existingAlpha
        alphaLabel.text = String.format(getString(R.string.config_alpha_label), existingAlpha)

        alphaSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                alphaLabel.text = String.format(getString(R.string.config_alpha_label), p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btn.setOnClickListener {
            val key = edit.text.toString().trim()
            if (key.isEmpty()) {
                edit.error = getString(R.string.config_key_empty_error)
                return@setOnClickListener
            }
            Prefs.setKey(this, appWidgetId, key)
            Prefs.setTheme(this, appWidgetId, themes[themeSpinner.selectedItemPosition].id)
            Prefs.setBgAlpha(this, appWidgetId, alphaSeek.progress)

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
