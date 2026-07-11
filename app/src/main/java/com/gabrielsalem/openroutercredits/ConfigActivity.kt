package com.gabrielsalem.openroutercredits

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText

class ConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedThemeIndex = 0
    private val themeCards = mutableListOf<MaterialCardView>()
    private val density by lazy { resources.displayMetrics.density }

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

        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_config)

        // --- API Key ---
        val keyInput = findViewById<TextInputEditText>(R.id.keyInput)
        val existingKey = Prefs.getKey(this, appWidgetId)
        if (!existingKey.isNullOrBlank()) keyInput.setText(existingKey)

        // --- Theme preview cards ---
        val container = findViewById<LinearLayout>(R.id.themePreviewContainer)
        val themes = WidgetTheme.ALL

        val existingTheme = Prefs.getTheme(this, appWidgetId)
        selectedThemeIndex = themes.indexOfFirst { it.id == existingTheme }.coerceAtLeast(0)

        themes.forEachIndexed { index, theme ->
            val card = buildThemeCard(theme, index == selectedThemeIndex)
            card.setOnClickListener { selectTheme(index) }
            container.addView(card)
            themeCards.add(card)
        }

        // --- Alpha slider ---
        val alphaSlider = findViewById<Slider>(R.id.alphaSlider)
        val alphaLabel = findViewById<TextView>(R.id.alphaLabel)

        val existingAlpha = Prefs.getBgAlpha(this, appWidgetId)
        alphaSlider.value = existingAlpha.toFloat()
        alphaLabel.text = getString(R.string.config_alpha_percent, existingAlpha)

        alphaSlider.addOnChangeListener { _, value, _ ->
            alphaLabel.text = getString(R.string.config_alpha_percent, value.toInt())
        }

        // --- Footer: GitHub ---
        findViewById<View>(R.id.githubLink).setOnClickListener {
            openUrl("https://github.com/jprngd-netizen/OpenRouterCreditsWidget")
        }

        // --- Footer: About ---
        findViewById<View>(R.id.aboutLink).setOnClickListener {
            showAboutDialog()
        }

        // --- Footer: Rate ---
        findViewById<View>(R.id.rateLink).setOnClickListener {
            openUrl("https://play.google.com/store/apps/details?id=$packageName")
        }

        // --- Save ---
        findViewById<com.google.android.material.button.MaterialButton>(R.id.saveBtn)
            .setOnClickListener {
                val key = keyInput.text.toString().trim()
                if (key.isEmpty()) {
                    keyInput.error = getString(R.string.config_key_empty_error)
                    return@setOnClickListener
                }
                Prefs.setKey(this, appWidgetId, key)
                Prefs.setTheme(this, appWidgetId, themes[selectedThemeIndex].id)
                Prefs.setBgAlpha(this, appWidgetId, alphaSlider.value.toInt())

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

    // ---------- helpers ----------

    private fun dp(value: Int): Int = (value * density).toInt()
    private fun dpF(value: Float): Float = value * density

    private fun selectTheme(index: Int) {
        selectedThemeIndex = index
        themeCards.forEachIndexed { i, card ->
            val h = Color.parseColor(WidgetTheme.ALL[i].accent)
            card.setStrokeColor(
                if (i == index) ColorStateList.valueOf(h)
                else ColorStateList.valueOf(Color.parseColor("#3A3A3E"))
            )
            card.strokeWidth = if (i == index) dp(3) else dp(1)
        }
    }

    private fun buildThemeCard(theme: WidgetTheme, isSelected: Boolean): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(110)).apply {
                marginEnd = dp(10)
            }
            cardElevation = dpF(2f)
            radius = dpF(12f)
            setCardBackgroundColor(Color.parseColor("#1C1C1E"))
            setStrokeColor(
                if (isSelected) ColorStateList.valueOf(Color.parseColor(theme.accent))
                else ColorStateList.valueOf(Color.parseColor("#3A3A3E"))
            )
            strokeWidth = if (isSelected) dp(3) else dp(1)
        }

        // Inner container
        val inner = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(8))
        }

        // BG color preview
        val bgPreview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)
            ).apply { bottomMargin = dp(6) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(8f)
                setColor(Color.parseColor(theme.bg))
            }
        }
        inner.addView(bgPreview)

        // Color dot row
        val dotRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        listOf(theme.accent, theme.text).forEach { colorHex ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                    marginEnd = dp(4)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(colorHex))
                }
            }
            dotRow.addView(dot)
        }
        inner.addView(dotRow)

        // Theme label
        val label = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = theme.label
            textSize = 10f
            setTextColor(Color.parseColor(theme.text))
            gravity = Gravity.CENTER
        }
        inner.addView(label)

        card.addView(inner)
        return card
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // no browser available — silently ignore
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sobre o OR Credits")
            .setMessage(
                """
                OR Credits Widget v1.6
                
                Um widget Android que exibe o saldo de créditos e uso recente da API OpenRouter diretamente na tela inicial.
                
                Código-fonte: github.com/jprngd-netizen/OpenRouterCreditsWidget
                
                ⚠️ Projeto pessoal — sem garantias.
                O autor não se responsabiliza por danos decorrentes do uso deste aplicativo.
                
                🔒 Privacidade
                Este app NÃO coleta, armazena ou transmite dados pessoais.
                A chave de API fornecida pelo usuário é armazenada localmente no dispositivo (criptografada) e usada exclusivamente para consultar a API da OpenRouter.
                Nenhum dado é enviado a terceiros.
                """.trimIndent()
            )
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        fun pendingIntent(context: android.content.Context, appWidgetId: Int): android.app.PendingIntent {
            val intent = Intent(context, ConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            return android.app.PendingIntent.getActivity(
                context,
                appWidgetId + 1000,
                intent,
                flags
            )
        }
    }
}
