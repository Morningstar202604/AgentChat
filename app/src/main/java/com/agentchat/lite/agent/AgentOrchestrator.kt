package com.agentchat.lite.agent

import com.agentchat.lite.data.local.entity.AgentEntity
import com.agentchat.lite.network.OpenAiClient
import com.agentchat.lite.network.model.ChatEvent
import com.agentchat.lite.network.model.ChatMessage
import com.agentchat.lite.network.model.ToolCall
import com.agentchat.lite.network.model.ToolCallFunction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Function Calling 编排状态机。
 *
 * 流程：
 * 1. 若 Agent 启用工具，以 tools 参数非流式请求，进入工具调用循环（最多 [maxRounds] 轮）。
 * 2. 响应含 tool_calls → emit ToolCall → 执行工具 → emit ToolResult → 追加 tool 消息 → 回到 1。
 * 3. 响应无 tool_calls → 流式输出 content（Delta）→ Done。
 * 4. 服务商返回 400（不支持 tools）→ 自动降级为普通流式对话。
 * 5. 超过 maxRounds → emit Error。
 *
 * Flow 被取消时会中止底层 OkHttp 请求（结构化并发）。
 */
class AgentOrchestrator(
    private val openAiClient: OpenAiClient,
    private val toolRegistry: ToolRegistry,
    private val maxRounds: Int = 6,
) {

    /**
     * 运行一轮 Agent 对话。
     *
     * @param messages 会话历史消息（不含 system prompt）
     * @param agent    当前 Agent 人格（提供 systemPrompt 与 toolsEnabled）
     * @param model    使用的模型名
     * @param temperature 采样温度
     * @param maxTokens 最大生成 token 数
     */
    fun run(
        messages: List<ChatMessage>,
        agent: AgentEntity,
        model: String,
        temperature: Double,
        maxTokens: Int,
    ): Flow<ChatEvent> = flow {
        val history = mutableListOf<ChatMessage>()
        val renderedPrompt = PromptTemplate.render(agent.systemPrompt)
        if (renderedPrompt.isNotBlank()) {
            history.add(ChatMessage(role = "system", content = renderedPrompt.trim()))
        }
        history.addAll(messages)

        // 解析 agent.toolsEnabled JSON 数组
        val enabledToolNames = runCatching {
            kotlinx.serialization.json.Json.decodeFromString<List<String>>(agent.toolsEnabled)
        }.getOrDefault(emptyList())

        var toolsEnabled = enabledToolNames.isNotEmpty()
        var round = 0

        while (true) {
            val tools = if (toolsEnabled && enabledToolNames.isNotEmpty()) {
                toolRegistry.toToolDefinitions(enabledToolNames)
            } else {
                null
            }

            val toolCallsCollected = mutableListOf<ParsedToolCall>()
            var finalContent = ""
            var gotError: ChatEvent.Error? = null

            openAiClient.streamChat(history, model, temperature, maxTokens, tools).collect { event ->
                when (event) {
                    is ChatEvent.Delta -> emit(event)
                    is ChatEvent.ToolCall -> {
                        toolCallsCollected.add(ParsedToolCall(event.id, event.name, event.arguments))
                        emit(event)
                    }
                    is ChatEvent.Done -> finalContent = event.fullContent
                    is ChatEvent.Error -> gotError = event
                    is ChatEvent.ToolResult -> Unit // 客户端不会发出，忽略
                }
            }

            // 错误处理
            if (gotError != null) {
                // 400 且当前在用工具 → 服务商不支持 tools，降级为普通流式
                if (gotError.statusCode == 400 && toolsEnabled) {
                    emit(ChatEvent.Delta("（该服务商不支持工具调用，已切换为普通对话）\n\n"))
                    toolsEnabled = false
                    continue
                }
                emit(gotError)
                return@flow
            }

            // 工具调用轮
            if (toolCallsCollected.isNotEmpty()) {
                // 追加 assistant 的 tool_calls 消息
                history.add(
                    ChatMessage(
                        role = "assistant",
                        content = "",
                        toolCalls = toolCallsCollected.map {
                            ToolCall(
                                id = it.id,
                                type = "function",
                                function = ToolCallFunction(name = it.name, arguments = it.arguments),
                            )
                        },
                    ),
                )
                // 逐个执行工具
                for (tc in toolCallsCollected) {
                    val tool = toolRegistry.get(tc.name)
                    val result = tool?.execute(tc.arguments) ?: "未知工具: ${tc.name}"
                    emit(ChatEvent.ToolResult(tc.name, result))
                    history.add(
                        ChatMessage(
                            role = "tool",
                            toolCallId = tc.id,
                            name = tc.name,
                            content = result,
                        ),
                    )
                }
                round++
                if (round >= maxRounds) {
                    emit(ChatEvent.Error("工具调用已达 $maxRounds 轮上限，请精简问题或关闭该 Agent 的工具调用"))
                    return@flow
                }
                continue
            }

            // 最终答案：Delta 已透传，补 Done
            emit(ChatEvent.Done(finalContent))
            return@flow
        }
    }

    private data class ParsedToolCall(
        val id: String,
        val name: String,
        val arguments: String,
    )
}
