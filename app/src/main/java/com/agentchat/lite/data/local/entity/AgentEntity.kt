package com.agentchat.lite.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Agent（助手人格）实体。
 *
 * @property id Agent 唯一 ID
 * @property name 显示名称
 * @property icon Material Icon 名称（如 SmartToy/Code/Edit），替代旧版 emoji
 * @property systemPrompt System Prompt 模板，支持 {{date}} {{time}} {{weekday}} {{username}}
 * @property model 指定模型，空字符串则用全局默认
 * @property temperature 采样温度，null 则用全局默认
 * @property toolsEnabled 启用的工具名 JSON 数组，如 ["get_current_time","calculate"]
 * @property welcomeMessage 首次对话开场白，空则不自动发送
 * @property isBuiltin 是否内置（不可删除）
 * @property sortOrder 排序权重
 */
@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "SmartToy",
    @ColumnInfo(name = "system_prompt") val systemPrompt: String,
    val model: String = "",
    val temperature: Double? = null,
    @ColumnInfo(name = "tools_enabled") val toolsEnabled: String = "[]",
    @ColumnInfo(name = "welcome_message") val welcomeMessage: String = "",
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)
