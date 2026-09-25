package com.agentchat.lite.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 对话请求体（对应 OpenAI /chat/completions）。
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    val stream: Boolean,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
)

/**
 * 单条消息。当 role="assistant" 且发起工具调用时，[toolCalls] 非空；
 * 当 role="tool" 时，[toolCallId] 与 [name] 对应被调用的工具。
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
)

/**
 * 助手消息中发起的一次工具调用。
 */
@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction,
)

@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: String,
)

/**
 * 发送给 API 的工具定义（tools 数组元素）。
 */
@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction,
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonElement,
)
