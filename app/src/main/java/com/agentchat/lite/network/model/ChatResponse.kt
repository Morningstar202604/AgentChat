package com.agentchat.lite.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 非流式响应体。
 */
@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val error: ResponseError? = null,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
)

@Serializable
data class ResponseError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

/**
 * SSE 流式响应的单个 chunk。
 */
@Serializable
data class StreamChunk(
    val choices: List<StreamChoice> = emptyList(),
)

@Serializable
data class StreamChoice(
    val delta: StreamDelta = StreamDelta(),
)

@Serializable
data class StreamDelta(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
)
