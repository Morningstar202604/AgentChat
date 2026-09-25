package com.agentchat.lite.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agentchat.lite.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 消息表 DAO。
 */
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM messages WHERE conversation_id = :convId ORDER BY created_at ASC")
    fun observeForConversation(convId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :convId ORDER BY created_at ASC")
    suspend fun getForConversation(convId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversation_id = :convId")
    suspend fun deleteForConversation(convId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    /**
     * FTS4 全文搜索：在指定会话内匹配消息内容。
     */
    @Query(
        """
        SELECT messages.* FROM messages
        JOIN messages_fts ON messages.id = messages_fts.rowid
        WHERE messages_fts MATCH :query AND messages.conversation_id = :convId
        ORDER BY messages.created_at ASC
        """,
    )
    suspend fun searchInConversation(convId: String, query: String): List<MessageEntity>
}
