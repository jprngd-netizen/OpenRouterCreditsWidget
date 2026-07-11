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
        val credits = ApiClient.api.getCredits("Bearer $key")
        val activity = runCatching { ApiClient.api.getActivity("Bearer $key") }
            .getOrNull()?.data ?: emptyList()

        val remaining = credits.data.remaining_credits
            ?: (credits.data.total_credits - credits.data.total_usage)

        UsageStore.record(context, credits.data.total_usage)
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
