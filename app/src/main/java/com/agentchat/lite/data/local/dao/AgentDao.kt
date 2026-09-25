package com.agentchat.lite.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agentchat.lite.data.local.entity.AgentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Agent 表 DAO。
 */
@Dao
interface AgentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(agent: AgentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(agents: List<AgentEntity>)

    @Query("SELECT * FROM agents ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents ORDER BY sort_order ASC, name ASC")
    suspend fun getAll(): List<AgentEntity>

    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun getById(id: String): AgentEntity?

    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM agents")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM agents")
    suspend fun count(): Int
}
