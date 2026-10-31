package com.buddy.assistant

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    @SerializedName("message") val message: String,
    @SerializedName("sessionId") val sessionId: String = "android-device-session"
)

data class ActionItem(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("parameters") val parameters: Map<String, Any>,
    @SerializedName("requiresConfirmation") val requiresConfirmation: Boolean = false
)

data class ChatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("message") val message: String,
    @SerializedName("mode") val mode: String,
    @SerializedName("intent") val intent: String,
    @SerializedName("actions") val actions: List<ActionItem>? = emptyList()
)
