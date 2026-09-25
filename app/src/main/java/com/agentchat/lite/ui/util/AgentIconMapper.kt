package com.agentchat.lite.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalDining
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material Icon 名称 → ImageVector 映射。
 * 编辑器图标选择器和 Agent 头像共用。
 */
object AgentIconMapper {

    /** 可选图标列表（名称 → ImageVector）。 */
    val all: List<Pair<String, ImageVector>> = listOf(
        "SmartToy" to Icons.Rounded.SmartToy,
        "Code" to Icons.Rounded.Code,
        "Edit" to Icons.Rounded.Edit,
        "Translate" to Icons.Rounded.Translate,
        "Psychology" to Icons.Rounded.Psychology,
        "Analytics" to Icons.Rounded.Analytics,
        "Flight" to Icons.Rounded.Flight,
        "FitnessCenter" to Icons.Rounded.FitnessCenter,
        "School" to Icons.Rounded.School,
        "MusicNote" to Icons.Rounded.MusicNote,
        "Palette" to Icons.Rounded.Palette,
        "Cookie" to Icons.Rounded.Cookie,
        "EmojiEvents" to Icons.Rounded.EmojiEvents,
        "HealthAndSafety" to Icons.Rounded.HealthAndSafety,
        "LocalDining" to Icons.Rounded.LocalDining,
        "Lightbulb" to Icons.Rounded.Lightbulb,
        "Star" to Icons.Rounded.Star,
        "Favorite" to Icons.Rounded.Favorite,
        "Book" to Icons.Rounded.Book,
        "Work" to Icons.Rounded.Work,
    )

    /** 按名称取图标，找不到回退到 SmartToy。 */
    fun get(name: String): ImageVector =
        all.firstOrNull { it.first == name }?.second ?: Icons.Rounded.SmartToy
}
