package com.gabrielsalem.openroutercredits

import retrofit2.http.GET
import retrofit2.http.Header

data class CreditResponse(
    val data: CreditData
)

data class CreditData(
    val total_credits: Double = 0.0,
    val total_usage: Double = 0.0,
    val remaining_credits: Double? = null
)

data class ActivityResponse(
    val data: List<ActivityItem>
)

data class ActivityItem(
    val date: String = "",            // YYYY-MM-DD
    val model: String = "",
    val model_permaslug: String = "",
    val endpoint_id: String = "",
    val provider_name: String = "",
    val usage: Double = 0.0,          // OpenRouter credits spent (USD)
    val byok_usage_inference: Double = 0.0,
    val requests: Int = 0,
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val reasoning_tokens: Int = 0
)

interface OpenRouterApi {
    @GET("credits")
    suspend fun getCredits(@Header("Authorization") auth: String): CreditResponse

    @GET("activity")
    suspend fun getActivity(@Header("Authorization") auth: String): ActivityResponse
}
