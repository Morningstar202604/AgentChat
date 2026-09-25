package com.agentchat.lite.agent

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * System Prompt 模板渲染。
 *
 * 支持变量：{{date}} {{time}} {{weekday}} {{username}}
 */
object PromptTemplate {

    /** 渲染上下文。 */
    data class PromptContext(
        val date: String,
        val time: String,
        val weekday: String,
        val username: String = "用户",
    )

    /** 用当前系统时间构造一个默认上下文。 */
    fun now(): PromptContext {
        val now = Date()
        val cal = Calendar.getInstance()
        val weekDays = listOf("日", "一", "二", "三", "四", "五", "六")
        return PromptContext(
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
            weekday = "周" + weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1],
        )
    }

    /** 把模板中的 {{var}} 替换为上下文里的值。 */
    fun render(systemPrompt: String, context: PromptContext = now()): String {
        return systemPrompt
            .replace("{{date}}", context.date)
            .replace("{{time}}", context.time)
            .replace("{{weekday}}", context.weekday)
            .replace("{{username}}", context.username)
    }
}
