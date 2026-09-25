package com.agentchat.lite.network

import com.agentchat.lite.network.model.ChatEvent
import com.agentchat.lite.network.model.ChatMessage
import com.agentchat.lite.network.model.ChatRequest
import com.agentchat.lite.network.model.ChatResponse
import com.agentchat.lite.network.model.StreamChunk
import com.agentchat.lite.network.model.ToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI 兼容协议客户端。
 *
 * 使用 OkHttp + okhttp-sse + kotlinx.serialization 自研轻量实现，
 * 不引入官方 Java SDK。支持：
 * - 无 tools 时 SSE 流式输出（Delta → Done）
 * - 带 tools 时非流式请求，解析 tool_calls 或 content（ToolCall → Done）
 * - 连接测试与中文错误提示
 */
class OpenAiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val okHttpClient: OkHttpClient,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val endpoint: String = run {
        var base = baseUrl.trim().trimEnd('/')
        if (!base.endsWith("/chat/completions", ignoreCase = true)) {
            base = "$base/chat/completions"
        }
        base
    }

    /**
     * 发起一轮对话。
     *
     * @param tools 非空时走非流式（stream=false），返回 tool_calls 或 content；
     *              为 null 时走 SSE 流式，逐段 emit Delta。
     */
    fun streamChat(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double,
        maxTokens: Int,
        tools: List<ToolDefinition>? = null,
    ): Flow<ChatEvent> = flow {
        if (tools != null) {
            emitNonStreaming(messages, model, temperature, maxTokens, tools)
        } else {
            emitStreaming(messages, model, temperature, maxTokens)
        }
    }

    /**
     * 发送一个 max_tokens=1 的 ping 请求测试连通性。
     */
    suspend fun testConnection(model: String): Result<Unit> {
        return runCatching {
            val req = buildRequest(
                ChatRequest(
                    model = model,
                    messages = listOf(ChatMessage(role = "user", content = "ping")),
                    temperature = 0.0,
                    stream = false,
                    maxTokens = 1,
                ),
            )
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    throw Exception(apiError(resp.code, body))
                }
            }
        }
    }

    // ---------- 非流式（带 tools） ----------

    private suspend fun FlowCollector<ChatEvent>.emitNonStreaming(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double,
        maxTokens: Int,
        tools: List<ToolDefinition>,
    ) {
        val req = buildRequest(
            ChatRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                stream = false,
                maxTokens = maxTokens,
                tools = tools,
                toolChoice = "auto",
            ),
        )
        try {
            okHttpClient.newCall(req).execute().use { resp ->
                val bodyText = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    emit(ChatEvent.Error(apiError(resp.code, bodyText), resp.code))
                    return
                }
                val parsed = json.decodeFromString<ChatResponse>(bodyText)
                val msg = parsed.choices.firstOrNull()?.message
                val toolCalls = msg?.toolCalls.orEmpty()
                if (toolCalls.isNotEmpty()) {
                    for (tc in toolCalls) {
                        emit(ChatEvent.ToolCall(tc.id, tc.function.name, tc.function.arguments))
                    }
                    emit(ChatEvent.Done(""))
                } else {
                    val content = msg?.content.orEmpty()
                    if (content.isNotEmpty()) emit(ChatEvent.Delta(content))
                    emit(ChatEvent.Done(content))
                }
            }
        } catch (e: HttpStatusException) {
            emit(ChatEvent.Error(apiError(e.code, e.body), e.code))
        } catch (e: Exception) {
            emit(ChatEvent.Error("请求失败: ${e.message ?: "网络错误"}"))
        }
    }

    // ---------- 流式（SSE） ----------

    private suspend fun FlowCollector<ChatEvent>.emitStreaming(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double,
        maxTokens: Int,
    ) {
        val req = buildRequest(
            ChatRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                stream = true,
                maxTokens = maxTokens,
            ),
        )
        val sb = StringBuilder()
        try {
            SseStreamer.streamSse(req, okHttpClient).collect { payload ->
                val chunk = runCatching { json.decodeFromString<StreamChunk>(payload) }.getOrNull()
                val deltaText = chunk?.choices?.firstOrNull()?.delta?.content
                if (!deltaText.isNullOrEmpty()) {
                    sb.append(deltaText)
                    emit(ChatEvent.Delta(deltaText))
                }
            }
            emit(ChatEvent.Done(sb.toString()))
        } catch (e: HttpStatusException) {
            emit(ChatEvent.Error(apiError(e.code, e.body), e.code))
        } catch (e: Exception) {
            emit(ChatEvent.Error("请求失败: ${e.message ?: "网络错误"}"))
        }
    }

    // ---------- 公共工具 ----------

    private fun buildRequest(body: ChatRequest): Request {
        val payload = json.encodeToString(ChatRequest.serializer(), body)
        return Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
    }

    /**
     * 将 HTTP 状态码映射为面向用户的中文错误消息，对齐 v1.0.0 app.js 的 apiError。
     */
    private fun apiError(status: Int, body: String): String {
        val b = body.take(300)
        return when (status) {
            401, 403 -> "认证失败 ($status)，请检查 API Key 是否正确"
            404 -> "接口地址或模型名错误 (404)，请检查「API 地址 / 模型」设置"
            429 -> "请求过于频繁或额度不足 (429)"
            400 -> "请求参数被拒绝 (400): $b"
            in 500..599 -> "服务端错误 ($status)，请稍后重试"
            else -> "请求失败 ($status): $b"
        }
    }
}
