package com.agentchat.lite.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentchat.lite.ui.chat.UiMessage
import com.agentchat.lite.ui.util.TimeFormatter

/**
 * 消息气泡列表项。根据角色渲染 UserBubble 或 AssistantBubble。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: UiMessage,
    agentIconName: String,
    agentColor: Color,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!message.isUser) {
            AssistantAvatar(iconName = agentIconName, backgroundColor = agentColor)
            Column(modifier = Modifier.padding(start = 8.dp).widthIn(max = 300.dp)) {
                AssistantBubble(
                    message = message,
                    onLongPress = onLongPress,
                    onRetry = onRetry,
                    onContinue = onContinue,
                )
                Text(
                    text = TimeFormatter.format(message.createdAt),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .combinedClickable(onClick = {}, onLongClick = onLongPress)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                Text(
                    text = TimeFormatter.format(message.createdAt),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp, top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun AssistantAvatar(iconName: String, backgroundColor: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = com.agentchat.lite.ui.util.AgentIconMapper.get(iconName),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantBubble(
    message: UiMessage,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Column {
        message.toolCalls.forEach { tc ->
            ToolCallCard(toolCall = tc, modifier = Modifier.padding(vertical = 2.dp))
        }

        if (message.isSending && message.content.isEmpty() && message.toolCalls.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                ThinkingIndicator(
                    connecting = true,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }

        if (message.isFailed) {
            ErrorMessage(message = message.errorMessage ?: "请求失败", onRetry = onRetry)
        }

        if (message.content.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    MarkdownText(
                        markdown = message.content + if (message.isStreaming) " ▌" else "",
                        textColor = MaterialTheme.colorScheme.onSurface,
                    )
                    if (message.isStopped) {
                        Text(
                            text = "▶ 继续生成",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .combinedClickable(
                                    onClick = onContinue,
                                    onLongClick = {},
                                )
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}
