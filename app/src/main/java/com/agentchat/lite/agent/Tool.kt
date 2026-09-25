package com.agentchat.lite.agent

/**
 * Agent 可调用工具的抽象接口。
 *
 * 每个工具需提供 OpenAI Function Calling 所需的 name / description / JSON Schema，
 * 以及一个挂起执行函数。[execute] 的参数为 JSON 字符串，返回值也为字符串
 *（通常是 JSON 或纯文本结果，会作为 tool 角色消息回传给模型）。
 */
interface Tool {

    /** 工具名，需在 tools 数组中唯一。 */
    val name: String

    /** 工具描述，告诉模型何时该调用此工具。 */
    val description: String

    /** 参数的 JSON Schema（字符串形式），作为 tools 数组中 function.parameters。 */
    val jsonSchema: String

    /**
     * 执行工具。
     *
     * @param arguments 模型给出的 JSON 参数字符串
     * @return 工具执行结果字符串
     */
    suspend fun execute(arguments: String): String
}
