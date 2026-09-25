package com.agentchat.lite.agent

import com.agentchat.lite.network.model.ToolDefinition
import com.agentchat.lite.network.model.ToolFunction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 可扩展工具注册中心。
 *
 * 通过 [register] 注册工具，[toToolDefinitions] 转换为 API 请求所需的 tools 数组。
 */
class ToolRegistry {

    private val tools = linkedMapOf<String, Tool>()
    private val json = Json { ignoreUnknownKeys = true }

    /** 注册一个工具（同名覆盖）。 */
    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    /** 按名称查找工具。 */
    fun get(name: String): Tool? = tools[name]

    /** 所有已注册工具列表。 */
    fun all(): List<Tool> = tools.values.toList()

    /** 是否为空。 */
    fun isEmpty(): Boolean = tools.isEmpty()

    /**
     * 转换为 OpenAI Function Calling 需要的 tools 数组格式（全部工具）。
     */
    fun toToolDefinitions(): List<ToolDefinition> {
        return tools.values.map { tool -> toDef(tool) }
    }

    /**
     * 只保留指定名字的工具，转换为 tools 数组。
     * @param names 允许的工具名列表；空列表返回空数组。
     */
    fun toToolDefinitions(names: List<String>): List<ToolDefinition> {
        if (names.isEmpty()) return emptyList()
        return names.mapNotNull { tools[it] }.map { tool -> toDef(tool) }
    }

    private fun toDef(tool: Tool): ToolDefinition = ToolDefinition(
        function = ToolFunction(
            name = tool.name,
            description = tool.description,
            parameters = parseSchema(tool.jsonSchema),
        ),
    )

    private fun parseSchema(raw: String): JsonObject {
        return runCatching { json.parseToJsonElement(raw) as JsonObject }
            .getOrDefault(JsonObject(emptyMap()))
    }
}
