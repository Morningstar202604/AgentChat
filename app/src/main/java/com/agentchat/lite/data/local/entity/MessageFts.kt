package com.agentchat.lite.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * 消息全文搜索（FTS4）虚拟表，content 关联 messages 表。
 *
 * 通过 `messages_fts MATCH :query` 进行全文检索，
 * 再 JOIN messages 表取出完整消息记录。
 */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFts(
    @ColumnInfo(name = "content") val content: String,
)
