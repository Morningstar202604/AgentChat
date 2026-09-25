package com.agentchat.lite.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 会话实体。
 *
 * @property id 会话唯一 ID
 * @property title 会话标题（首条用户消息截取）
 * @property agentId 关联的 Agent ID
 * @property createdAt 创建时间（毫秒）
 * @property updatedAt 最后更新时间（毫秒）
 * @property pinned 是否置顶
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "agent_id") val agentId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val pinned: Boolean = false,
)
