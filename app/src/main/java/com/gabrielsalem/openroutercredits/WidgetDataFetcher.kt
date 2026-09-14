package com.gabrielsalem.openroutercredits

import android.content.Context

data class WidgetData(
    val remainingCredits: Double,
    val creditsText: String,
    val spentToday: Double,
    val last7Days: List<Pair<String, Double>>,
    val topModels: List<Pair<String, Double>>,
    val lastModel: LastModelInfo?,
    val sparklineSeries: List<Pair<Long, Double>>,
    val total24h: Double,
    val activity: List<ActivityItem>
)

object WidgetDataFetcher {
    suspend fun fetch(context: Context, key: String): WidgetData {
        // GET /api/v1/key funciona com API key normal (sem Management Key)
        val keyInfo = ApiClient.api.getKey("Bearer $key")

        // remaining = limit_remaining se houver limite configurado,
        // senão usa (limit - usage) ou fallback para 0
        val remaining = keyInfo.data.limit_remaining
            ?: keyInfo.data.limit?.let { it - keyInfo.data.usage }
            ?: 0.0

        val currentTotal = keyInfo.data.usage

        // Atividade é opcional — falha silenciosa para não travar o widget
        val activity = runCatching { ApiClient.api.getActivity("Bearer $key") }
            .getOrNull()?.data ?: emptyList()

        UsageStore.seedFromActivity(context, activity, currentTotal)
        UsageStore.record(context, currentTotal)
        val series = UsageStore.series(context)
        val total24h = UsageStore.total24h(context)

        return WidgetData(
            remainingCredits = remaining,
            creditsText = "$%.4f".format(remaining),
            spentToday = if (activity.isNotEmpty()) ActivityStore.spentToday(activity) else 0.0,
            last7Days = ActivityStore.last7Days(activity),
            topModels = ActivityStore.topModels(activity, 3),
            lastModel = ActivityStore.lastModel(activity),
            sparklineSeries = series,
            total24h = total24h,
            activity = activity
        )
    }
}
