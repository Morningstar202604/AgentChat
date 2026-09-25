package com.agentchat.lite.agent.tools

import com.agentchat.lite.agent.Tool
import kotlinx.serialization.json.Json
import net.objecthunter.exp4j.ExpressionBuilder

/**
 * 安全地计算一个数学表达式。
 *
 * 使用 exp4j 库，支持 + - * / % ^ () 以及 sqrt/abs/round/floor/ceil/
 * sin/cos/tan/log/ln/exp 等常用函数，不手写解析器。
 */
class CalculatorTool : Tool {

    override val name: String = "calculate"

    override val description: String =
        "安全地计算一个数学表达式，支持 + - * / % ^ () 和常用函数（sqrt/abs/round/sin/cos/log 等）"

    override val jsonSchema: String = """
        {
          "type": "object",
          "properties": {
            "expr": {
              "type": "string",
              "description": "数学表达式，例如 (1+2)*3 或 sqrt(16)+2^10"
            }
          },
          "required": ["expr"]
        }
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(arguments: String): String {
        val args = runCatching {
            json.parseToJsonElement(arguments) as? kotlinx.serialization.json.JsonObject
        }.getOrNull()
        val expr = args?.get("expr")?.let {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
        }?.trim().orEmpty()

        if (expr.isEmpty()) return "缺少表达式参数 expr"
        if (expr.length > 200) return "表达式过长（最多 200 字符）"

        return runCatching {
            val value = ExpressionBuilder(expr).build().evaluate()
            if (!value.isFinite()) return "结果不是有限数值"
            // 保留 10 位小数精度，去除浮点尾差
            val rounded = Math.round(value * 1e10) / 1e10
            if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
            else rounded.toString()
        }.getOrElse { e ->
            when {
                e.message?.contains("divide by zero", ignoreCase = true) == true ||
                    e.message?.contains("/ by zero", ignoreCase = true) == true -> "除以 0"
                else -> "表达式错误: ${e.message ?: "无法解析"}"
            }
        }
    }
}
