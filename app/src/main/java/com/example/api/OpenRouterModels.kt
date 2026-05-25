package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenRouterModelsResponse(
    @Json(name = "data") val data: List<OpenRouterModel>
)

@JsonClass(generateAdapter = true)
data class OpenRouterModel(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "pricing") val pricing: OpenRouterPricing? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterPricing(
    @Json(name = "prompt") val prompt: String? = null,
    @Json(name = "completion") val completion: String? = null
) {
    fun isFree(): Boolean {
        return (prompt == "0" || prompt == "0.0" || prompt?.startsWith("0.000000") == true) &&
                (completion == "0" || completion == "0.0" || completion?.startsWith("0.000000") == true)
    }
}

@JsonClass(generateAdapter = true)
data class OpenRouterChatRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<OpenRouterChatMessage>,
    @Json(name = "temperature") val temperature: Float = 0.8f,
    @Json(name = "max_tokens") val maxTokens: Int = 150
)

@JsonClass(generateAdapter = true)
data class OpenRouterChatMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class OpenRouterChatResponse(
    @Json(name = "choices") val choices: List<OpenRouterChoice>? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterChoice(
    @Json(name = "message") val message: OpenRouterChatMessage? = null
)
