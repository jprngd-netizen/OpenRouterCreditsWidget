package com.gabrielsalem.openroutercredits

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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

            val loading = RemoteViews(context.packageName, R.layout.widget_credits)
            loading.setTextViewText(R.id.credits, "…")
            appWidgetManager.updateAppWidget(appWidgetId, loading)

            val key = Prefs.getKey(context, appWidgetId)
            if (key.isNullOrBlank()) {
                val rv = RemoteViews(context.packageName, R.layout.widget_credits)
                rv.setTextViewText(R.id.credits, "set key")
                rv.setTextViewText(R.id.updated, "toque p/ configurar")
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
                    val credits = ApiClient.api.getCredits("Bearer $key")
                    val activity = runCatching { ApiClient.api.getActivity("Bearer $key") }
                        .getOrNull()?.data ?: emptyList()

                    val remaining = credits.data.remaining_credits
                        ?: (credits.data.total_credits - credits.data.total_usage)
                    val text = "$%.4f".format(remaining)
                    val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                    val rv = RemoteViews(context.packageName, R.layout.widget_credits)
                    rv.setTextViewText(R.id.credits, text)

                    // sparkline de tempo real (derivação local entre polls)
                    UsageStore.record(context, credits.data.total_usage)
                    val series = UsageStore.series(context)
                    val total24 = UsageStore.total24h(context)
                    if (tier != Tier.COMPACT && series.size >= 2) {
                        rv.setImageViewBitmap(R.id.sparkline, WidgetCharts.sparkline(series, 600, 96))
                        rv.setViewVisibility(R.id.sparkline, View.VISIBLE)
                    } else {
                        rv.setViewVisibility(R.id.sparkline, View.GONE)
                    }

                    // barras 7 dias + gasto hoje (via /activity)
                    val spentToday = if (activity.isNotEmpty()) ActivityStore.spentToday(activity) else null
                    val last7 = ActivityStore.last7Days(activity)
                    if (tier == Tier.FULL && last7.any { it.second > 0.0 }) {
                        rv.setImageViewBitmap(R.id.bars7d, WidgetCharts.bars(last7, 600, 160))
                        rv.setViewVisibility(R.id.bars7d, View.VISIBLE)
                    } else {
                        rv.setViewVisibility(R.id.bars7d, View.GONE)
                    }

                    // top modelos (só FULL)
                    if (tier == Tier.FULL) {
                        val top = ActivityStore.topModels(activity, 3)
                        if (top.isNotEmpty()) {
                            val s = top.joinToString("  ") { "${it.first.split('/').last()}:$${"%.3f".format(it.second)}" }
                            rv.setTextViewText(R.id.top_models, s)
                            rv.setViewVisibility(R.id.top_models, View.VISIBLE)
                        } else {
                            rv.setViewVisibility(R.id.top_models, View.GONE)
                        }
                    } else {
                        rv.setViewVisibility(R.id.top_models, View.GONE)
                    }

                    // linha de status adaptada ao tier
                    val status = when (tier) {
                        Tier.FULL -> "hoje $${"%.4f".format(spentToday ?: 0.0)} · 24h $${"%.4f".format(total24)} · $now"
                        Tier.MEDIUM -> "24h $${"%.4f".format(total24)} · $now"
                        Tier.COMPACT -> now
                    }
                    rv.setTextViewText(R.id.updated, status)

                    if (spentToday != null) {
                        rv.setTextViewText(R.id.spent_today, "hoje $${"%.2f".format(spentToday)}")
                        rv.setViewVisibility(R.id.spent_today, View.VISIBLE)
                    } else {
                        rv.setViewVisibility(R.id.spent_today, View.GONE)
                    }

                    rv.setOnClickPendingIntent(R.id.root, ConfigActivity.pendingIntent(context, appWidgetId))
                    rv.setOnClickPendingIntent(R.id.refresh, refreshIntent(context, appWidgetId))
                    appWidgetManager.updateAppWidget(appWidgetId, rv)
                } catch (e: Exception) {
                    val rv = RemoteViews(context.packageName, R.layout.widget_credits)
                    rv.setTextViewText(R.id.credits, "erro")
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
