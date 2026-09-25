package com.agentchat.lite.network.model

/**
 * 对话过程中产出的事件流，供 UI 层消费。
 */
sealed class ChatEvent {

    /** 流式输出的一段文本增量。 */
    data class Delta(val content: String) : ChatEvent()

    /** 模型发起了一次工具调用。 */
    data class ToolCall(val id: String, val name: String, val arguments: String) : ChatEvent()

    /** 工具执行完成，附带结果文本。 */
    data class ToolResult(val name: String, val result: String) : ChatEvent()

    /** 本轮对话结束，[fullContent] 为完整文本。 */
    data class Done(val fullContent: String) : ChatEvent()

    /** 发生错误，[message] 为面向用户的中文提示。 */
    data class Error(val message: String, val statusCode: Int? = null) : ChatEvent()
}
