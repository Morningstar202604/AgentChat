package com.agentchat.lite.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * HTTP 非 2xx 响应异常，携带状态码与响应体片段。
 */
class HttpStatusException(
    val code: Int,
    val body: String,
) : Exception("HTTP $code: ${body.take(200)}")

/**
 * 基于 okhttp-sse 的 SSE 流解析器。
 *
 * 将 [EventSource] 的事件回调桥接为 Kotlin Flow：
 * 每个 `data:` 行（跳过 `[DONE]`）解析出 JSON payload 字符串并 emit。
 * Flow 被取消时自动 cancel EventSource。
 * 非 2xx 响应时以 [HttpStatusException] 关闭流。
 */
object SseStreamer {

    /**
     * 发起 SSE 请求并流式 emit 每一行 `data:` 的 JSON 字符串。
     *
     * @param request 已构造好的 OkHttp 请求
     * @param client  共享的 OkHttpClient（readTimeout 应为 0 以支持长连接）
     */
    fun streamSse(request: Request, client: OkHttpClient): Flow<String> = callbackFlow {
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") return
                trySend(data)
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (response != null) {
                    val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                    close(HttpStatusException(response.code, body))
                } else {
                    close(t)
                }
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)

        awaitClose { eventSource.cancel() }
    }
}
