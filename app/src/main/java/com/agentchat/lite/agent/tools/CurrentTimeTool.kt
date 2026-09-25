package com.agentchat.lite.agent.tools

import com.agentchat.lite.agent.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 获取当前日期、时间和星期。
 *
 * 对应 v1.0.0 app.js 中的 get_current_time 工具。
 */
class CurrentTimeTool : Tool {

    override val name: String = "get_current_time"

    override val description: String = "获取当前的日期、时间和星期"

    override val jsonSchema: String = """
        {"type":"object","properties":{},"required":[]}
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(arguments: String): String {
        val now = Date()
        val cal = Calendar.getInstance()
        val weekDays = listOf("日", "一", "二", "三", "四", "五", "六")
        val weekday = "周" + weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val local = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now)
        val result = JsonObject(
            mapOf(
                "iso" to kotlinx.serialization.json.JsonPrimitive(now.toInstant().toString()),
                "local" to kotlinx.serialization.json.JsonPrimitive(local),
                "weekday" to kotlinx.serialization.json.JsonPrimitive(weekday),
            ),
        )
        return json.encodeToString(JsonObject.serializer(), result)
    }
}
