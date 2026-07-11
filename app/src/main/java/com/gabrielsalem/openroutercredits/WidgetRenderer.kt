package com.gabrielsalem.openroutercredits

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetRenderer {

    enum class Tier { COMPACT, MEDIUM, FULL }

    /** Decide o nível de detalhe conforme o tamanho do widget (dp). */
    fun layoutTier(opts: Bundle?): Tier {
        val w = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val h = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        return when {
            w >= 250 && h >= 180 -> Tier.FULL   // 4x4+
            w >= 200 && h >= 140 -> Tier.MEDIUM // 3x3+
            else -> Tier.COMPACT                // 2x2 / pequeno
        }
    }

    /** Renderiza um Drawable (fundo com alpha) em Bitmap para o RemoteViews. */
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        val w = 600
        val h = 400
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    fun refreshIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, CreditsWidgetProvider::class.java).apply {
            action = CreditsWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, id, intent, flags)
    }

    fun configIntent(context: Context, id: Int): PendingIntent =
        ConfigActivity.pendingIntent(context, id)

    fun renderLoading(
        context: Context,
        theme: WidgetTheme,
        bgAlpha: Int,
        cachedCredits: String?
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_credits)
        rv.setImageViewBitmap(R.id.bg, drawableToBitmap(backgroundDrawable(theme, bgAlpha)))
        rv.setTextColor(R.id.credits, Color.parseColor(theme.text))
        rv.setTextColor(R.id.title, Color.parseColor(theme.title))
        rv.setTextColor(R.id.updated, Color.parseColor(theme.subText))
        rv.setTextViewText(R.id.credits, cachedCredits ?: context.getString(R.string.credits_loading))
        return rv
    }

    fun renderNoKey(
        context: Context,
        theme: WidgetTheme,
        bgAlpha: Int,
        appWidgetId: Int
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_credits)
        rv.setImageViewBitmap(R.id.bg, drawableToBitmap(backgroundDrawable(theme, bgAlpha)))
        rv.setTextColor(R.id.credits, Color.parseColor(theme.text))
        rv.setTextColor(R.id.title, Color.parseColor(theme.title))
        rv.setTextColor(R.id.updated, Color.parseColor(theme.subText))
        rv.setTextViewText(R.id.credits, context.getString(R.string.credits_no_key))
        rv.setTextViewText(R.id.updated, context.getString(R.string.credits_no_key_hint))
        rv.setViewVisibility(R.id.sparkline, View.GONE)
        rv.setViewVisibility(R.id.bars7d, View.GONE)
        rv.setViewVisibility(R.id.top_models, View.GONE)
        rv.setViewVisibility(R.id.spent_today, View.GONE)
        rv.setOnClickPendingIntent(R.id.root, configIntent(context, appWidgetId))
        return rv
    }

    fun renderContent(
        context: Context,
        data: WidgetData,
        tier: Tier,
        theme: WidgetTheme,
        bgAlpha: Int
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_credits)
        val accent = Color.parseColor(theme.accent)
        val accentDim = Color.parseColor(theme.accentDim)
        val textCol = Color.parseColor(theme.text)
        val subTextCol = Color.parseColor(theme.subText)
        val titleCol = Color.parseColor(theme.title)
        val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        rv.setImageViewBitmap(R.id.bg, drawableToBitmap(backgroundDrawable(theme, bgAlpha)))
        rv.setTextColor(R.id.credits, textCol)
        rv.setTextColor(R.id.title, titleCol)
        rv.setTextColor(R.id.updated, subTextCol)
        rv.setTextColor(R.id.spent_today, accent)
        rv.setTextViewText(R.id.credits, data.creditsText)

        // sparkline
        if (tier != Tier.COMPACT && data.sparklineSeries.size >= 2) {
            rv.setImageViewBitmap(
                R.id.sparkline,
                WidgetCharts.sparkline(data.sparklineSeries, 600, 96, accent)
            )
            rv.setViewVisibility(R.id.sparkline, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.sparkline, View.GONE)
        }

        // barras 7 dias
        if (tier == Tier.FULL && data.last7Days.any { it.second > 0.0 }) {
            rv.setImageViewBitmap(
                R.id.bars7d,
                WidgetCharts.bars(data.last7Days, 600, 160, accent, accentDim)
            )
            rv.setViewVisibility(R.id.bars7d, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.bars7d, View.GONE)
        }

        // top modelos (só FULL)
        if (tier == Tier.FULL) {
            if (data.topModels.isNotEmpty()) {
                val s = data.topModels.joinToString("  ") {
                    "${it.first.split('/').last()}:$${"%.3f".format(it.second)}"
                }
                rv.setTextViewText(R.id.top_models, s)
                rv.setTextColor(R.id.top_models, subTextCol)
                rv.setViewVisibility(R.id.top_models, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.top_models, View.GONE)
            }
        } else {
            rv.setViewVisibility(R.id.top_models, View.GONE)
        }

        // último modelo
        if (tier != Tier.COMPACT) {
            val last = data.lastModel
            if (last != null) {
                val name = last.first.split('/').last()
                val spent = "%.3f".format(last.second)
                rv.setTextViewText(
                    R.id.last_model,
                    String.format(context.getString(R.string.last_model_prefix), name, spent)
                )
                rv.setTextColor(R.id.last_model, accentDim)
                rv.setViewVisibility(R.id.last_model, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.last_model, View.GONE)
            }
        } else {
            rv.setViewVisibility(R.id.last_model, View.GONE)
        }

        // status line
        val status = when (tier) {
            Tier.FULL -> String.format(
                context.getString(R.string.status_full), data.spentToday, data.total24h, now
            )
            Tier.MEDIUM -> String.format(
                context.getString(R.string.status_medium), data.total24h, now
            )
            Tier.COMPACT -> now
        }
        rv.setTextViewText(R.id.updated, status)

        // spent today
        if (data.spentToday > 0 && data.activity.isNotEmpty()) {
            rv.setTextViewText(
                R.id.spent_today,
                String.format(context.getString(R.string.spent_today), data.spentToday)
            )
            rv.setViewVisibility(R.id.spent_today, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.spent_today, View.GONE)
        }

        return rv
    }

    fun renderError(
        context: Context,
        theme: WidgetTheme,
        bgAlpha: Int,
        message: String
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_credits)
        rv.setImageViewBitmap(R.id.bg, drawableToBitmap(backgroundDrawable(theme, bgAlpha)))
        rv.setTextColor(R.id.credits, Color.parseColor(theme.text))
        rv.setTextColor(R.id.title, Color.parseColor(theme.title))
        rv.setTextColor(R.id.updated, Color.parseColor(theme.subText))
        rv.setTextViewText(R.id.credits, context.getString(R.string.credits_error))
        rv.setTextViewText(R.id.updated, message.take(24))
        rv.setViewVisibility(R.id.sparkline, View.GONE)
        rv.setViewVisibility(R.id.bars7d, View.GONE)
        rv.setViewVisibility(R.id.top_models, View.GONE)
        rv.setViewVisibility(R.id.spent_today, View.GONE)
        return rv
    }
}