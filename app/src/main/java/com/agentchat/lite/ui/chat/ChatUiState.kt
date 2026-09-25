package com.agentchat.lite.ui.chat

import com.agentchat.lite.data.local.entity.AgentEntity
import com.agentchat.lite.data.local.entity.ConversationEntity

/**
 * 单条工具调用的 UI 模型（仅在流式过程中存在，不持久化）。
 */
data class UiToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val result: String? = null,
)

/**
 * 单条消息的 UI 模型。
 *
 * @property id 消息唯一 ID
 * @property role "user" 或 "assistant"
 * @property content 文本内容
 * @property status "sending" | "success" | "failed" | "stopped"
 * @property createdAt 创建时间戳
 * @property toolCalls 流式过程中的工具调用卡片（assistant 消息）
 * @property isStreaming 是否正在接收增量
 * @property errorMessage 失败时的错误原因
 */
data class UiMessage(
    val id: String,
    val role: String,
    val content: String,
    val status: String,
    val createdAt: Long,
    val toolCalls: List<UiToolCall> = emptyList(),
    val isStreaming: Boolean = false,
    val errorMessage: String? = null,
) {
    val isUser: Boolean get() = role == "user"
    val isFailed: Boolean get() = status == "failed"
    val isStopped: Boolean get() = status == "stopped"
    val isSending: Boolean get() = status == "sending"
}

/**
 * 聊天主界面的完整 UI 状态。
 */
data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val currentAgent: AgentEntity? = null,
    val agents: List<AgentEntity> = emptyList(),
    val conversations: List<ConversationEntity> = emptyList(),
    val activeConversationId: String? = null,
    val isOnline: Boolean = true,
    val showConversationDrawer: Boolean = false,
    val apiKeyMissing: Boolean = false,
    val autoScrollEnabled: Boolean = true,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
) {
    /** 是否可以发送消息（输入非空、未在生成、在线、有 API Key）。 */
    val canSend: Boolean
        get() = inputText.isNotBlank() && !isGenerating && isOnline && !apiKeyMissing

    sealed class ConnectionStatus {
        /** 空闲，无请求。 */
        data object Idle : ConnectionStatus()

        /** 已发送请求，等待首个 delta（显示"连接中…"）。 */
        data object Connecting : ConnectionStatus()

        /** 正在接收流式增量。 */
        data object Streaming : ConnectionStatus()
    }
}
