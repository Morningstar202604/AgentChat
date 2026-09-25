package com.agentchat.lite.agent.tools

import com.agentchat.lite.agent.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.random.Random

/**
 * 生成随机数或从候选项中随机选择。
 */
class RandomTool : Tool {

    override val name: String = "random"

    override val description: String = "生成指定范围的随机数，或从候选项列表中随机选择一项"

    override val jsonSchema: String = """
        {
          "type": "object",
          "properties": {
            "min": {"type": "number", "description": "随机数下限（含）"},
            "max": {"type": "number", "description": "随机数上限（含）"},
            "count": {"type": "integer", "description": "生成个数，默认 1"},
            "choices": {"type": "array", "items": {"type": "string"}, "description": "候选项列表，给出时随机选一个"}
          }
        }
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(arguments: String): String {
        val args = runCatching {
            json.parseToJsonElement(arguments) as? JsonObject
        }.getOrNull() ?: return "参数解析失败"

        // 模式一：从 choices 中随机选
        (args["choices"] as? JsonArray)?.let { arr ->
            val items = arr.mapNotNull { (it as? JsonPrimitive)?.content }.filter { it.isNotBlank() }
            if (items.isEmpty()) return "choices 为空"
            val picked = items[Random.nextInt(items.size)]
            return "从 ${items.size} 个候选项中随机选中: $picked"
        }

        // 模式二：范围随机数
        val min = (args["min"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        val max = (args["max"] as? JsonPrimitive)?.doubleOrNull ?: 100.0
        val count = (args["count"] as? JsonPrimitive)?.intOrNull ?: 1
        if (max < min) return "max 不能小于 min"

        val results = (1..count).map {
            if (min == min.toLong().toDouble() && max == max.toLong().toLong().toDouble()) {
                Random.nextLong(min.toLong(), max.toLong() + 1).toString()
            } else {
                (Math.round(Random.nextDouble(min, max) * 10000.0) / 10000.0).toString()
            }
        }
        return "生成 $count 个随机数 [$min, $max]: ${results.joinToString(", ")}"
    }
}
