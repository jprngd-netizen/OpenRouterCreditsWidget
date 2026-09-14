package com.gabrielsalem.openroutercredits

import retrofit2.http.GET
import retrofit2.http.Header

// --- /api/v1/key (funciona com API key normal, sem Management Key) ---
data class KeyResponse(
    val data: KeyData
)

data class KeyData(
    val usage: Double = 0.0,
    val usage_daily: Double = 0.0,
    val limit: Double? = null,          // null = sem limite configurado
    val limit_remaining: Double? = null // null = sem limite configurado
)

// --- /api/v1/credits (requer Management Key — mantido para compatibilidade futura) ---
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
    // Endpoint principal: funciona com qualquer API key
    @GET("key")
    suspend fun getKey(@Header("Authorization") auth: String): KeyResponse

    // Endpoint de atividade (pode falhar silenciosamente — usado apenas para dados extras)
    @GET("activity")
    suspend fun getActivity(@Header("Authorization") auth: String): ActivityResponse
}
