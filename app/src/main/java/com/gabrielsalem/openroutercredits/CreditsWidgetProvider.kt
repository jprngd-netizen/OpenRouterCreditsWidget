package com.gabrielsalem.openroutercredits

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val opts = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val tier = WidgetRenderer.layoutTier(opts)
            val theme = WidgetTheme.fromId(Prefs.getTheme(context, appWidgetId))
            val bgAlpha = Prefs.getBgAlpha(context, appWidgetId)

            val key = Prefs.getKey(context, appWidgetId)
            if (key.isNullOrBlank()) {
                val rv = WidgetRenderer.renderNoKey(context, theme, bgAlpha, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, rv)
                return
            }

            val cachedCredits = WidgetStateManager.getDisplayCredits(context)
            val loadingRv = WidgetRenderer.renderLoading(context, theme, bgAlpha, cachedCredits)
            appWidgetManager.updateAppWidget(appWidgetId, loadingRv)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val data = WidgetDataFetcher.fetch(context, key)
                    WidgetStateManager.saveCredits(context, data.remainingCredits)
                    val rv = WidgetRenderer.renderContent(context, data, tier, theme, bgAlpha)
                    rv.setOnClickPendingIntent(R.id.root, WidgetRenderer.configIntent(context, appWidgetId))
                    rv.setOnClickPendingIntent(R.id.refresh, WidgetRenderer.refreshIntent(context, appWidgetId))
                    appWidgetManager.updateAppWidget(appWidgetId, rv)
                } catch (e: Exception) {
                    val rv = WidgetRenderer.renderError(context, theme, bgAlpha, e.message ?: "falha")
                    rv.setOnClickPendingIntent(R.id.refresh, WidgetRenderer.refreshIntent(context, appWidgetId))
                    appWidgetManager.updateAppWidget(appWidgetId, rv)
                }
            }
        }
    }
}
