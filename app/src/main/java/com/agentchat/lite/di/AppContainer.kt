package com.agentchat.lite.di

import android.content.Context
import com.agentchat.lite.agent.AgentOrchestrator
import com.agentchat.lite.agent.ToolRegistry
import com.agentchat.lite.agent.tools.CalculatorTool
import com.agentchat.lite.agent.tools.CurrentTimeTool
import com.agentchat.lite.agent.tools.RandomTool
import com.agentchat.lite.agent.tools.UnitConverterTool
import com.agentchat.lite.data.local.AppDatabase
import com.agentchat.lite.data.pref.SettingsRepository
import com.agentchat.lite.network.OpenAiClient
import com.agentchat.lite.ui.util.NetworkMonitor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 轻量手动依赖注入容器（不上 Koin/Hilt）。
 *
 * 数据库、设置仓库、OkHttp、工具注册中心为单例懒加载；
 * OpenAiClient 与 AgentOrchestrator 需要运行时的 baseUrl/apiKey，
 * 通过工厂方法按需创建。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // SSE 长连接不超时
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry().apply {
            register(CurrentTimeTool())
            register(CalculatorTool())
            register(UnitConverterTool())
            register(RandomTool())
        }
    }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(appContext) }

    /**
     * 用运行时的 baseUrl/apiKey 创建 OpenAiClient。
     */
    fun createOpenAiClient(baseUrl: String, apiKey: String): OpenAiClient {
        return OpenAiClient(
            baseUrl = baseUrl,
            apiKey = apiKey,
            okHttpClient = okHttpClient,
        )
    }

    /**
     * 用运行时的 baseUrl/apiKey 创建 AgentOrchestrator。
     */
    fun createOrchestrator(baseUrl: String, apiKey: String): AgentOrchestrator {
        return AgentOrchestrator(
            openAiClient = createOpenAiClient(baseUrl, apiKey),
            toolRegistry = toolRegistry,
        )
    }
}
