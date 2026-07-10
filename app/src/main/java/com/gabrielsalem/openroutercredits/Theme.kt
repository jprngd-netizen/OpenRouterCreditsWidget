package com.gabrielsalem.openroutercredits

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * Esquemas de cor predefinidos para o widget. Cada tema define:
 * - bg: cor base do fundo (será aplicado com alpha editável)
 * - accent: cor principal (saldo + sparkline + barra do dia atual)
 * - accentDim: cor das barras anteriores
 * - text: cor do texto principal (saldo/hoje)
 * - subText: cor do texto secundário (hora, labels)
 * - title: cor do título "OpenRouter"
 */
enum class WidgetTheme(
    val id: String,
    val label: String,
    val bg: String,
    val accent: String,
    val accentDim: String,
    val text: String,
    val subText: String,
    val title: String
) {
    DARK("dark", "Dark",
        "#1E1E1E", "#4CC2FF", "#2E7DA8", "#FFFFFF", "#AAAAAA", "#4CC2FF"),
    LIGHT("light", "Light",
        "#F2F2F2", "#0B66C2", "#7FA8D0", "#111111", "#666666", "#0B66C2"),
    AMOLED("amoled", "AMOLED",
        "#000000", "#33D17A", "#1A6E3E", "#FFFFFF", "#888888", "#33D17A"),
    OCEAN("ocean", "Ocean",
        "#0A1A2F", "#00D4FF", "#0A6E8C", "#E6F7FF", "#7FB3C9", "#00D4FF"),
    SUNSET("sunset", "Sunset",
        "#2A1421", "#FF8C42", "#A14A2A", "#FFE8D6", "#C99A8A", "#FF8C42"),
    MINT("mint", "Mint",
        "#10241C", "#3DDC97", "#1E7A57", "#E8FFF5", "#7FBFA8", "#3DDC97"),
    GRAPE("grape", "Grape",
        "#1C1228", "#B388FF", "#6A4CA8", "#F3E8FF", "#A98FC9", "#B388FF");

    companion object {
        val ALL = entries.toList()
        fun fromId(id: String?): WidgetTheme =
            ALL.firstOrNull { it.id == id } ?: DARK
    }
}

/** Gera o drawable de fundo com a transparência (alpha 0-100) aplicada. */
fun backgroundDrawable(theme: WidgetTheme, bgAlpha: Int): GradientDrawable {
    val alpha = (bgAlpha.coerceIn(0, 100) * 255 / 100).coerceIn(0, 255)
    val base = Color.parseColor(theme.bg)
    val argb = Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 24f
        setColor(argb)
    }
}
