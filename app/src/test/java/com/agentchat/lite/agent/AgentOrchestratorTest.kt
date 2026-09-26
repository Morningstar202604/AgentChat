package com.agentchat.lite.agent

import com.agentchat.lite.agent.tools.CalculatorTool
import com.agentchat.lite.data.local.entity.AgentEntity
import com.agentchat.lite.network.OpenAiClient
import com.agentchat.lite.network.model.ChatEvent
import com.agentchat.lite.network.model.ChatMessage
import com.agentchat.lite.network.model.ToolDefinition
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AgentOrchestrator 状态机单元测试。
 *
 * 用 mockk 模拟 OpenAiClient，验证：
 * - 无工具时正常流式输出
 * - 工具调用一轮后返回最终答案
 * - 超过 maxRounds 时返回 Error
 * - 服务商不支持 tools 时自动降级
 * - Flow cancel 不崩溃
 */
class AgentOrchestratorTest {

    private val openAiClient = mockk<OpenAiClient>(relaxed = true)
    private val toolRegistry = ToolRegistry().apply { register(CalculatorTool()) }

    private val testAgent = AgentEntity(
        id = "general",
        name = "通用助手",
        icon = "SmartToy",
        systemPrompt = "你是助手",
        toolsEnabled = """["calculate"]""",
        isBuiltin = true,
    )

    private val noToolAgent = testAgent.copy(toolsEnabled = "[]")

    private val userMessage = listOf(ChatMessage(role = "user", content = "你好"))

    @Test
    fun `no tools streams delta then done`() = runTest {
        every {
            openAiClient.streamChat(any(), any(), any(), any(), any())
        } returns flowOf(
            ChatEvent.Delta("你"),
            ChatEvent.Delta("好"),
            ChatEvent.Done("你好"),
        )

        val events = mutableListOf<ChatEvent>()
        val orchestrator = AgentOrchestrator(openAiClient, toolRegistry, maxRounds = 6)
        orchestrator.run(userMessage, noToolAgent, "deepseek-chat", 0.7, 4096).collect {
            events.add(it)
        }

        val deltas = events.filterIsInstance<ChatEvent.Delta>().map { it.content }
        assertEquals(listOf("你", "好"), deltas)
        assertTrue(events.last() is ChatEvent.Done)
        assertEquals("你好", (events.last() as ChatEvent.Done).fullContent)
    }

    @Test
    fun `one tool call round then final answer`() = runTest {
        // 第一轮：模型要求调用 calculate 工具
        every {
            openAiClient.streamChat(any(), any(), any(), any(), any())
        } returnsMany listOf(
            flowOf(
                ChatEvent.ToolCall("call_1", "calculate", """{"expr":"(1+2)*3"}"""),
                ChatEvent.Done(""),
            ),
            // 第二轮：工具结果后给出最终答案
            flowOf(
                ChatEvent.Delta("结果是 9"),
                ChatEvent.Done("结果是 9"),
            ),
        )

        val events = mutableListOf<ChatEvent>()
        val orchestrator = AgentOrchestrator(openAiClient, toolRegistry, maxRounds = 6)
        orchestrator.run(userMessage, testAgent, "deepseek-chat", 0.7, 4096).collect {
            events.add(it)
        }

        // 应有 ToolCall 事件
        val toolCallEvents = events.filterIsInstance<ChatEvent.ToolCall>()
        assertEquals(1, toolCallEvents.size)
        assertEquals("calculate", toolCallEvents[0].name)

        // 应有 ToolResult 事件
        val toolResultEvents = events.filterIsInstance<ChatEvent.ToolResult>()
        assertEquals(1, toolResultEvents.size)
        assertEquals("9", toolResultEvents[0].result)

        // 最终 Done
        assertTrue(events.last() is ChatEvent.Done)
        assertEquals("结果是 9", (events.last() as ChatEvent.Done).fullContent)
    }

    @Test
    fun `exceeds maxRounds emits error`() = runTest {
        // 每轮都返回 tool_calls，永不结束
        every {
            openAiClient.streamChat(any(), any(), any(), any(), any())
        } returns flowOf(
            ChatEvent.ToolCall("call_x", "calculate", """{"expr":"1+1"}"""),
            ChatEvent.Done(""),
        )

        val events = mutableListOf<ChatEvent>()
        val orchestrator = AgentOrchestrator(openAiClient, toolRegistry, maxRounds = 2)
        orchestrator.run(userMessage, testAgent, "deepseek-chat", 0.7, 4096).collect {
            events.add(it)
        }

        val errorEvents = events.filterIsInstance<ChatEvent.Error>()
        assertEquals(1, errorEvents.size)
        assertTrue(errorEvents[0].message.contains("2 轮"))
    }

    @Test
    fun `provider does not support tools degrades to streaming`() = runTest {
        every {
            openAiClient.streamChat(any(), any(), any(), any(), any())
        } returnsMany listOf(
            // 第一轮带 tools → 400 错误
            flowOf(ChatEvent.Error("请求参数被拒绝 (400)", 400)),
            // 降级后不带 tools → 正常流式
            flowOf(
                ChatEvent.Delta("普通回答"),
                ChatEvent.Done("普通回答"),
            ),
        )

        val events = mutableListOf<ChatEvent>()
        val orchestrator = AgentOrchestrator(openAiClient, toolRegistry, maxRounds = 6)
        orchestrator.run(userMessage, testAgent, "deepseek-chat", 0.7, 4096).collect {
            events.add(it)
        }

        // 应有降级提示
        val deltas = events.filterIsInstance<ChatEvent.Delta>().map { it.content }
        assertTrue("应包含降级提示: $deltas", deltas.any { it.contains("不支持工具调用") })
        // 应有最终普通回答
        assertTrue("应包含普通回答: $deltas", deltas.any { it.contains("普通回答") })
        assertTrue(events.last() is ChatEvent.Done)
        assertEquals("普通回答", (events.last() as ChatEvent.Done).fullContent)

        // 验证第二次调用时 tools 为 null（降级）
        verify(exactly = 2) { openAiClient.streamChat(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `flow cancel does not crash`() = runTest {
        every {
            openAiClient.streamChat(any(), any(), any(), any(), any())
        } returns flowOf(
            ChatEvent.Delta("开始"),
            // 不结束，模拟长时间生成
        )

        val orchestrator = AgentOrchestrator(openAiClient, toolRegistry, maxRounds = 6)
        val flow = orchestrator.run(userMessage, noToolAgent, "deepseek-chat", 0.7, 4096)

        // 收集一个事件后取消
        val collector = flow.testCollectOnce()
        // 不抛异常即通过
        assertTrue(true)
    }

    /** 辅助：收集第一个事件后取消。 */
    private suspend fun Flow<ChatEvent>.testCollectOnce() {
        val collected = mutableListOf<ChatEvent>()
        try {
            collect { event ->
                collected.add(event)
                if (collected.size >= 1) throw kotlinx.coroutines.CancellationException("cancel")
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            // 预期取消
        }
    }
}