package com.agentchat.lite

import android.app.Application
import com.agentchat.lite.data.local.DEFAULT_AGENTS
import com.agentchat.lite.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AgentChat Application 类。
 *
 * 持有全局 [AppContainer]，并在首次启动时初始化内置 Agent。
 */
class AgentChatApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        seedDefaultAgents()
    }

    /** 首次启动时若 agents 表为空，插入内置默认 Agent。 */
    private fun seedDefaultAgents() {
        appScope.launch {
            val dao = container.database.agentDao()
            if (dao.count() == 0) {
                dao.insertAll(DEFAULT_AGENTS)
            }
        }
    }
}
