package com.agentchat.lite.agent

import com.agentchat.lite.agent.tools.CalculatorTool
import com.agentchat.lite.agent.tools.CurrentTimeTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具单元测试——纯 JVM，不依赖 Android。
 */
class ToolTest {

    @Test
    fun `CurrentTimeTool returns JSON with current date`() = runTest {
        val tool = CurrentTimeTool()
        val result = tool.execute("{}")
        // 应包含 local / iso / weekday 字段
        assertTrue("应包含 local 字段: $result", result.contains("local"))
        assertTrue("应包含 weekday 字段: $result", result.contains("weekday"))
        assertTrue("应包含 iso 字段: $result", result.contains("iso"))
    }

    @Test
    fun `CalculatorTool computes basic expression`() = runTest {
        val tool = CalculatorTool()
        val result = tool.execute("""{"expr":"(1+2)*3"}""")
        assertEquals("9", result)
    }

    @Test
    fun `CalculatorTool computes sqrt`() = runTest {
        val tool = CalculatorTool()
        val result = tool.execute("""{"expr":"sqrt(16)"}""")
        assertTrue("期望 4.0，实际: $result", result == "4" || result == "4.0")
    }

    @Test
    fun `CalculatorTool computes power`() = runTest {
        val tool = CalculatorTool()
        val result = tool.execute("""{"expr":"2^10"}""")
        assertEquals("1024", result)
    }

    @Test
    fun `CalculatorTool handles divide by zero`() = runTest {
        val tool = CalculatorTool()
        val result = tool.execute("""{"expr":"1/0"}""")
        assertTrue("应返回除零错误，实际: $result", result.contains("除以 0") || result.contains("错误"))
    }

    @Test
    fun `CalculatorTool handles invalid expression`() = runTest {
        val tool = CalculatorTool()
        val result = tool.execute("""{"expr":"1 + +"}""")
        assertTrue("应返回错误消息，实际: $result", result.contains("错误"))
    }

    @Test
    fun `CalculatorTool handles missing expr`() = runTest {
        val tool = CalculatorTool()
        val result = tool.execute("{}")
        assertTrue("应提示缺少参数，实际: $result", result.contains("缺少表达式"))
    }
}
