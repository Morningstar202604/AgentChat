package com.agentchat.lite.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息实体。
 *
 * role 取值："user" | "assistant" | "tool" | "system"
 * status 取值："sending" | "success" | "failed" | "stopped"
 *
 * @property id 消息唯一 ID
 * @property conversationId 所属会话 ID
 * @property role 角色
 * @property content 消息文本内容
 * @property toolCallId 工具调用 ID（role=tool 时对应）
 * @property toolName 工具名（role=tool 时记录）
 * @property createdAt 创建时间（毫秒）
 * @property status 消息状态
 */
@Entity(
    tableName = "messages",
    indices = [Index("conversation_id")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val role: String,
    val content: String,
    @ColumnInfo(name = "tool_call_id") val toolCallId: String? = null,
    @ColumnInfo(name = "tool_name") val toolName: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val status: String,
)
