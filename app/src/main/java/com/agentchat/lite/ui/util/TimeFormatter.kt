package com.agentchat.lite.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 消息时间格式化工具。
 *
 * 规则：
 * - 今日：HH:mm
 * - 昨天：昨天 HH:mm
 * - 更早：MM/dd
 * - 跨年：yyyy/MM/dd
 */
object TimeFormatter {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    /** 格式化单条消息的时间戳。 */
    fun format(ts: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { time = Date(ts) }

        return when {
            isSameDay(now, then) -> timeFormat.format(ts)
            isYesterday(now, then) -> "昨天 " + timeFormat.format(ts)
            isSameYear(now, then) -> dateTimeFormat.format(ts)
            else -> yearFormat.format(ts)
        }
    }

    /** 判断两条消息是否需要显示日期分隔线（跨天）。 */
    fun shouldShowDateSeparator(prevTs: Long?, currentTs: Long): Boolean {
        if (prevTs == null) return true
        val prev = Calendar.getInstance().apply { time = Date(prevTs) }
        val curr = Calendar.getInstance().apply { time = Date(currentTs) }
        return !isSameDay(prev, curr)
    }

    /** 日期分隔线文案：今天 / 昨天 / M月d日 / yyyy年M月d日。 */
    fun dateLabel(ts: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { time = Date(ts) }
        return when {
            isSameDay(now, then) -> "今天"
            isYesterday(now, then) -> "昨天"
            isSameYear(now, then) -> "${then.get(Calendar.MONTH) + 1}月${then.get(Calendar.DAY_OF_MONTH)}日"
            else -> "${then.get(Calendar.YEAR)}年${then.get(Calendar.MONTH) + 1}月${then.get(Calendar.DAY_OF_MONTH)}日"
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, then: Calendar): Boolean {
        val y = now.clone() as Calendar
        y.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(y, then)
    }

    private fun isSameYear(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
    }
}
