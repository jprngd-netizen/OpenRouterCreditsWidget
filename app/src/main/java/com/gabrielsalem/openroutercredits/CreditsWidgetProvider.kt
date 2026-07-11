package com.gabrielsalem.openroutercredits

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CreditsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?
    ) {
        // widget foi redimensionado -> re-renderiza com o novo layout
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateScheduler.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                updateWidget(context, AppWidgetManager.getInstance(context), id)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.gabrielsalem.openroutercredits.ACTION_REFRESH"

        /** Decide o nível de detalhe conforme o tamanho do widget (dp). */
        private fun layoutTier(opts: android.os.Bundle?): Tier {
            val w = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val h = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            return when {
                w >= 250 && h >= 180 -> Tier.FULL   // 4x4+
                w >= 200 && h >= 140 -> Tier.MEDIUM // 3x3+
                else -> Tier.COMPACT               // 2x2 / pequeno
            }
        }

        private enum class Tier { COMPACT, MEDIUM, FULL }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val opts = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val tier = layoutTier(opts)
            val theme = WidgetTheme.fromId(Prefs.getTheme(context, appWidgetId))
            val bgAlpha = Prefs.getBgAlpha(context, appWidgetId)

            val loading = RemoteViews(context.packageName, R.layout.widget_credits)
            loading.setImageViewBitmap(R.id.bg, drawableToBitmap(backgroundDrawable(theme, bgAlpha)))
            loading.setTextColor(R.id.credits, Color.parseColor(theme.text))
            loading.setTextColor(R.id.title, Color.parseColor(theme.title))
            loading.setTextColor(R.id.updated, Color.parseColor(theme.subText))
            loading.setTextViewText(R.id.credits, context.getString(R.string.credits_loading))
            appWidgetManager.updateAppWidget(appWidgetId, loading)

            val key = Prefs.getKey(context, appWidgetId)
            val accent = Color.parseColor(theme.accent)
            val accentDim = Color.parseColor(theme.accentDim)
            val textCol = Color.parseColor(theme.text)
            val subTextCol = Color.parseColor(theme.subText)
            val titleCol = Color.parseColor(theme.title)

            if (key.isNullOrBlank()) {
                val rv = RemoteViews(context.packageName, R.layout.widget_credits)
                rv.setImageViewBitmap(R.id.bg, drawableToBitmap(backgroundDrawable(theme, bgAlpha)))
                rv.setTextColor(R.id.credits, textCol)
                rv.setTextColor(R.id.title, titleCol)
                rv.setTextColor(R.id.updated, subTextCol)
                rv.setTextViewText(R.id.credits, context.getString(R.string.credits_no_key))
                rv.setTextViewText(R.id.updated, context.getString(R.string.credits_no_key_hint))
                rv.setViewVisibility(R.id.sparkline, View.GONE)
                rv.setViewVisibility(R.id.bars7d, View.GONE)
                rv.setViewVisibility(R.id.top_models, View.GONE)
                rv.setViewVisibility(R.id.spent_today, View.GONE)
                rv.setOnClickPendingIntent(R.id.root, ConfigActivity.pendingIntent(context, appWidgetId))
                appWidgetManager.updateAppWidget(appWidgetId, rv)
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val data = WidgetDataFetcher.fetch(context, key)
                    val accent = Color.parseColor(theme.accent)
                    val accentDim = Color.parseColor(theme.accentDim)
                    val textCol = Color.parseColor(theme.text)
                    val subTextCol = Color.parseColor(theme.subText)
                    val titleCol = Color.parseColor(theme.title)
                    val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                    val rv = RemoteViews(context.packageName, R.layout.widget_credits)
                    rv.setImageViewBitmap(R.id.bg, drawableToBitmap(backgroundDrawable(theme, bgAlpha)))
                    rv.setTextColor(R.id.credits, textCol)
                    rv.setTextColor(R.id.title, titleCol)
                    rv.setTextColor(R.id.updated, subTextCol)
                    rv.setTextColor(R.id.spent_today, accent)
                    rv.setTextViewText(R.id.credits, data.creditsText)

                    // sparkline
                    if (tier != Tier.COMPACT && data.sparklineSeries.size >= 2) {
                        rv.setImageViewBitmap(R.id.sparkline, WidgetCharts.sparkline(data.sparklineSeries, 600, 96, accent))
                        rv.setViewVisibility(R.id.sparkline, View.VISIBLE)
                    } else {
                        rv.setViewVisibility(R.id.sparkline, View.GONE)
                    }

                    // barras 7 dias
                    if (tier == Tier.FULL && data.last7Days.any { it.second > 0.0 }) {
                        rv.setImageViewBitmap(R.id.bars7d, WidgetCharts.bars(data.last7Days, 600, 160, accent, accentDim))
                        rv.setViewVisibility(R.id.bars7d, View.VISIBLE)
                    } else {
                        rv.setViewVisibility(R.id.bars7d, View.GONE)
                    }

                    // top modelos (só FULL)
                    if (tier == Tier.FULL) {
                        if (data.topModels.isNotEmpty()) {
                            val s = data.topModels.joinToString("  ") { "${it.first.split('/').last()}:$${"%.3f".format(it.second)}" }
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
                            rv.setTextViewText(R.id.last_model, String.format(context.getString(R.string.last_model_prefix), name, spent))
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
                        Tier.FULL -> String.format(context.getString(R.string.status_full), data.spentToday, data.total24h, now)
                        Tier.MEDIUM -> String.format(context.getString(R.string.status_medium), data.total24h, now)
                        Tier.COMPACT -> now
                    }
                    rv.setTextViewText(R.id.updated, status)

                    if (data.spentToday > 0 && data.activity.isNotEmpty()) {
                        rv.setTextViewText(R.id.spent_today, String.format(context.getString(R.string.spent_today), data.spentToday))
                        rv.setViewVisibility(R.id.spent_today, View.VISIBLE)
                    } else {
                        rv.setViewVisibility(R.id.spent_today, View.GONE)
                    }

                    rv.setOnClickPendingIntent(R.id.root, ConfigActivity.pendingIntent(context, appWidgetId))
                    rv.setOnClickPendingIntent(R.id.refresh, refreshIntent(context, appWidgetId))
                    appWidgetManager.updateAppWidget(appWidgetId, rv)
                } catch (e: Exception) {
                    val rv = RemoteViews(context.packageName, R.layout.widget_credits)
                    rv.setImageViewBitmap(R.id.bg, drawableToBitmap(backgroundDrawable(theme, bgAlpha)))
                    rv.setTextColor(R.id.credits, textCol)
                    rv.setTextColor(R.id.title, titleCol)
                    rv.setTextColor(R.id.updated, subTextCol)
                    rv.setTextViewText(R.id.credits, context.getString(R.string.credits_error))
                    rv.setTextViewText(R.id.updated, (e.message ?: "falha").take(24))
                    rv.setViewVisibility(R.id.sparkline, View.GONE)
                    rv.setViewVisibility(R.id.bars7d, View.GONE)
                    rv.setViewVisibility(R.id.top_models, View.GONE)
                    rv.setViewVisibility(R.id.spent_today, View.GONE)
                    rv.setOnClickPendingIntent(R.id.refresh, refreshIntent(context, appWidgetId))
                    appWidgetManager.updateAppWidget(appWidgetId, rv)
                }
            }
        }

        /** Renderiza um Drawable (fundo com alpha) em Bitmap para o RemoteViews. */
        private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
            val w = 600
            val h = 400
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            return bmp
        }

        private fun refreshIntent(context: Context, id: Int): android.app.PendingIntent {
            val intent = Intent(context, CreditsWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    android.app.PendingIntent.FLAG_IMMUTABLE else 0
            return android.app.PendingIntent.getBroadcast(context, id, intent, flags)
        }
    }
}
