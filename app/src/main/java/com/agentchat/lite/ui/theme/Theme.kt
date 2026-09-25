package com.agentchat.lite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * AgentChat 全局主题。
 *
 * 品牌主色 Indigo 600 (#4F46E5)，浅色/深色两套配色。
 * 所有 Compose 页面通过 [AgentChatTheme] 包裹以获得统一配色。
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF3730A3),
    secondary = Color(0xFF6366F1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceTint = Color(0xFF4F46E5),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFC7D2FE),
    secondary = Color(0xFFA5B4FC),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF312E81),
    background = Color(0xFF0F0F14),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF1A1A24),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF242430),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceTint = Color(0xFF818CF8),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    outline = Color(0xFF4B5563),
    outlineVariant = Color(0xFF374151),
)

/** 消息气泡相关尺寸常量，集中管理避免硬编码。 */
object ChatDimens {
    val AvatarSize = 32.dp
    val MessageMaxWidth = 320.dp
    val BubbleCornerRadius = 18.dp
    val InputBarElevation = 2.dp
    val MinTouchTarget = 48.dp
}

/**
 * 应用主题入口。根据系统深色模式自动切换配色。
 */
@Composable
fun AgentChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
