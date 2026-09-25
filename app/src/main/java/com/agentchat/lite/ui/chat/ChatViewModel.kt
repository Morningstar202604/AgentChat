package com.agentchat.lite.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentchat.lite.data.local.entity.AgentEntity
import com.agentchat.lite.data.local.entity.ConversationEntity
import com.agentchat.lite.data.local.entity.MessageEntity
import com.agentchat.lite.data.pref.SettingsRepository
import com.agentchat.lite.di.AppContainer
import com.agentchat.lite.network.model.ChatEvent
import com.agentchat.lite.network.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 聊天主界面 ViewModel。
 *
 * 负责：发送/停止/继续/重试/重答消息、会话管理、流式事件消费、网络状态检测。
 */
class ChatViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val database = container.database
    private val settings = container.settingsRepository

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 当前流式生成中的 assistant 消息（不持久化，Done 后写入 DB）。 */
    private var streamingMessage: UiMessage? = null

    /** 最新从 DB 读到的持久化消息。 */
    private var latestDbMessages: List<MessageEntity> = emptyList()

    /** 当前生成 Job，用于停止。 */
    private var currentJob: Job? = null

    // 设置快照
    private var baseUrl: String = SettingsRepository.DEFAULT_BASE_URL
    private var model: String = SettingsRepository.DEFAULT_MODEL
    private var temperature: Double = 0.7
    private var maxTokens: Int = 4096
    private var selectedAgentId: String = "general"
    private var allAgents: List<AgentEntity> = emptyList()

    init {
        collectSettings()
        collectAgents()
        collectConversations()
        collectNetwork()
        checkApiKey()
    }

    // ---------------------------------------------------------------
    // 初始化收集
    // ---------------------------------------------------------------

    private fun collectSettings() {
        viewModelScope.launch { settings.baseUrl.collect { v -> baseUrl = v } }
        viewModelScope.launch { settings.model.collect { v -> model = v } }
        viewModelScope.launch { settings.temperature.collect { v -> temperature = v } }
        viewModelScope.launch { settings.maxTokens.collect { v -> maxTokens = v } }
        viewModelScope.launch {
            settings.selectedAgentId.collect { v ->
                selectedAgentId = v
                refreshCurrentAgent()
            }
        }
    }

    private fun collectAgents() {
        viewModelScope.launch {
            database.agentDao().observeAll().collect { list ->
                allAgents = list
                _uiState.update { it.copy(agents = list) }
                refreshCurrentAgent()
            }
        }
    }

    private fun refreshCurrentAgent() {
        val agent = allAgents.firstOrNull { it.id == selectedAgentId } ?: allAgents.firstOrNull()
        _uiState.update { it.copy(currentAgent = agent) }
    }

    private fun collectConversations() {
        viewModelScope.launch {
            database.conversationDao().observeAll().collect { list ->
                _uiState.update { it.copy(conversations = list) }
            }
        }
    }

    private fun collectNetwork() {
        viewModelScope.launch {
            container.networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    private fun checkApiKey() {
        viewModelScope.launch {
            val key = settings.getApiKey()
            _uiState.update { it.copy(apiKeyMissing = key.isNullOrEmpty()) }
        }
    }

    /** 从设置页返回后刷新 API Key 状态。 */
    fun refreshApiKey() {
        viewModelScope.launch {
            val key = settings.getApiKey()
            _uiState.update { it.copy(apiKeyMissing = key.isNullOrEmpty()) }
        }
    }

    /** 切换当前会话时，收集该会话的消息流。 */
    private fun observeMessagesForConversation(convId: String) {
        viewModelScope.launch {
            database.messageDao().observeForConversation(convId).collect { entities ->
                latestDbMessages = entities
                rebuildMessageList()
            }
        }
    }

    /** 合并持久化消息 + 流式消息为 UI 列表。 */
    private fun rebuildMessageList() {
        val persisted = latestDbMessages
            .filter { it.role == "user" || it.role == "assistant" }
            .filter { it.status != "failed" || it.content.isNotBlank() }
            .map { it.toUiMessage() }
        val streaming = streamingMessage?.let { listOf(it) } ?: emptyList()
        _uiState.update { it.copy(messages = persisted + streaming) }
    }

    private fun MessageEntity.toUiMessage(): UiMessage = UiMessage(
        id = id,
        role = role,
        content = content,
        status = status,
        createdAt = createdAt,
        errorMessage = if (status == "failed") "请求失败，请点击重试" else null,
    )

    // ---------------------------------------------------------------
    // 用户操作
    // ---------------------------------------------------------------

    /** 更新输入框文本。 */
    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /** 发送输入框中的消息。 */
    fun send() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        if (_uiState.value.apiKeyMissing) return
        if (!_uiState.value.isOnline) return
        if (_uiState.value.isGenerating) return

        _uiState.update { it.copy(inputText = "", autoScrollEnabled = true) }
        startGeneration(userContent = text, presetUserMessageId = null)
    }

    /**
     * 启动一轮生成。
     *
     * @param userContent 用户消息文本
     * @param presetUserMessageId 若为重试/重答，指定已存在的用户消息 ID（不再新建）
     */
    private fun startGeneration(userContent: String, presetUserMessageId: String?) {
        val agent = _uiState.value.currentAgent ?: return
        val convId = ensureConversation(existingUserContent = userContent)

        if (presetUserMessageId == null) {
            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "user",
                content = userContent,
                createdAt = System.currentTimeMillis(),
                status = "success",
            )
            viewModelScope.launch { database.messageDao().insert(userMsg) }
        }

        val streamId = UUID.randomUUID().toString()
        streamingMessage = UiMessage(
            id = streamId,
            role = "assistant",
            content = "",
            status = "sending",
            createdAt = System.currentTimeMillis(),
            isStreaming = true,
        )
        rebuildMessageList()

        currentJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    connectionStatus = ChatUiState.ConnectionStatus.Connecting,
                )
            }

            val apiKey = settings.getApiKey() ?: run {
                finishStreamWithError(streamId, "未配置 API Key，请先在设置中填写")
                return@launch
            }

            val history = buildHistory(convId, includeUserContent = userContent, presetId = presetUserMessageId)
            val effectiveModel = agent.model.takeIf { it.isNotBlank() } ?: model
            val effectiveTemp = agent.temperature ?: temperature

            runCatching {
                container.createOrchestrator(baseUrl, apiKey)
                    .run(history, agent, effectiveModel, effectiveTemp, maxTokens)
                    .collect { event -> handleChatEvent(event, streamId) }
            }.onFailure { err ->
                Log.e(TAG, "Orchestrator failed", err)
                finishStreamWithError(streamId, err.message ?: "网络请求失败，请稍后重试")
            }
        }
    }

    /** 构建发送给 API 的历史消息。 */
    private suspend fun buildHistory(
        convId: String,
        includeUserContent: String,
        presetId: String?,
    ): List<ChatMessage> {
        val entities = database.messageDao().getForConversation(convId)
        val history = entities
            .filter { (it.role == "user" || it.role == "assistant") && it.status != "failed" }
            .map { ChatMessage(role = it.role, content = it.content) }
            .toMutableList()

        // Bug 3: 欢迎语竞态——新会话 DB 中可能还没写入欢迎语，这里手动补一条，
        // 保证 API 请求上下文一定包含欢迎语（不依赖异步 insert 时序）。
        val welcome = _uiState.value.currentAgent?.welcomeMessage.orEmpty()
        if (history.isEmpty() && welcome.isNotBlank()) {
            history.add(0, ChatMessage(role = "assistant", content = welcome))
        }

        if (presetId == null && includeUserContent.isNotBlank()) {
            history.add(ChatMessage(role = "user", content = includeUserContent))
        }
        return history
    }

    /** 确保会话存在，返回会话 ID。新会话自动写入当前 Agent 的欢迎语。 */
    private fun ensureConversation(existingUserContent: String): String {
        _uiState.value.activeConversationId?.let { return it }

        val convId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val conv = ConversationEntity(
            id = convId,
            title = existingUserContent.take(22),
            agentId = selectedAgentId,
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch {
            database.conversationDao().insert(conv)
            // 写入欢迎语（若有）
            _uiState.value.currentAgent?.welcomeMessage?.takeIf { it.isNotBlank() }?.let { welcome ->
                database.messageDao().insert(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "assistant",
                        content = welcome,
                        createdAt = System.currentTimeMillis(),
                        status = "success",
                    ),
                )
            }
        }
        _uiState.update {
            it.copy(activeConversationId = convId, showConversationDrawer = false)
        }
        observeMessagesForConversation(convId)
        return convId
    }

    // ---------------------------------------------------------------
    // 事件处理
    // ---------------------------------------------------------------

    private fun handleChatEvent(event: ChatEvent, streamId: String) {
        when (event) {
            is ChatEvent.Delta -> {
                _uiState.update { it.copy(connectionStatus = ChatUiState.ConnectionStatus.Streaming) }
                streamingMessage = streamingMessage?.copy(content = (streamingMessage?.content ?: "") + event.content)
                rebuildMessageList()
            }

            is ChatEvent.ToolCall -> {
                streamingMessage = streamingMessage?.copy(
                    toolCalls = streamingMessage?.toolCalls.orEmpty() + UiToolCall(
                        id = event.id,
                        name = event.name,
                        arguments = event.arguments,
                    ),
                )
                rebuildMessageList()
            }

            is ChatEvent.ToolResult -> {
                streamingMessage = streamingMessage?.copy(
                    toolCalls = streamingMessage?.toolCalls.orEmpty().map { tc ->
                        if (tc.name == event.name && tc.result == null) tc.copy(result = event.result) else tc
                    },
                )
                rebuildMessageList()
            }

            is ChatEvent.Done -> {
                val content = event.fullContent.ifBlank { streamingMessage?.content ?: "" }
                persistStreamingMessage(streamId, content, "success")
            }

            is ChatEvent.Error -> {
                finishStreamWithError(streamId, event.message, streamingMessage?.content ?: "")
            }
        }
    }

    /** 将流式消息落库为指定状态。 */
    private fun persistStreamingMessage(streamId: String, content: String, status: String) {
        val convId = _uiState.value.activeConversationId ?: return
        val msg = MessageEntity(
            id = streamId,
            conversationId = convId,
            role = "assistant",
            content = content,
            createdAt = System.currentTimeMillis(),
            status = status,
        )
        viewModelScope.launch {
            database.messageDao().insert(msg)
            touchConversation(convId)
        }
        streamingMessage = null
        rebuildMessageList()
        _uiState.update {
            it.copy(isGenerating = false, connectionStatus = ChatUiState.ConnectionStatus.Idle)
        }
    }

    /** 以失败状态结束流式消息。 */
    private fun finishStreamWithError(streamId: String, errorMsg: String, partialContent: String = "") {
        val convId = _uiState.value.activeConversationId
        if (convId != null) {
            val msg = MessageEntity(
                id = streamId,
                conversationId = convId,
                role = "assistant",
                content = partialContent,
                createdAt = System.currentTimeMillis(),
                status = "failed",
            )
            viewModelScope.launch { database.messageDao().insert(msg) }
        }
        streamingMessage = streamingMessage?.copy(status = "failed", errorMessage = errorMsg, isStreaming = false)
        rebuildMessageList()
        _uiState.update {
            it.copy(isGenerating = false, connectionStatus = ChatUiState.ConnectionStatus.Idle)
        }
    }

    // ---------------------------------------------------------------
    // 停止 / 继续 / 重试 / 重答
    // ---------------------------------------------------------------

    /** 停止当前生成，已生成内容保留。 */
    fun stopGeneration() {
        currentJob?.cancel()
        currentJob = null
        val sm = streamingMessage ?: return
        val content = sm.content
        if (content.isBlank()) {
            streamingMessage = null
            rebuildMessageList()
            _uiState.update { it.copy(isGenerating = false, connectionStatus = ChatUiState.ConnectionStatus.Idle) }
        } else {
            persistStreamingMessage(sm.id, content, "stopped")
        }
    }

    /** 继续生成已停止的消息。 */
    fun continueGeneration(messageId: String) {
        val convId = _uiState.value.activeConversationId ?: return
        val target = latestDbMessages.firstOrNull { it.id == messageId && it.status == "stopped" } ?: return
        if (_uiState.value.isGenerating) return

        streamingMessage = UiMessage(
            id = target.id,
            role = "assistant",
            content = target.content,
            status = "sending",
            createdAt = target.createdAt,
            isStreaming = true,
        )
        rebuildMessageList()

        currentJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isGenerating = true, connectionStatus = ChatUiState.ConnectionStatus.Connecting)
            }
            val apiKey = settings.getApiKey() ?: run {
                finishStreamWithError(target.id, "未配置 API Key")
                return@launch
            }
            val agent = _uiState.value.currentAgent ?: return@launch

            val entities = database.messageDao().getForConversation(convId)
            val history = entities
                .filter { (it.role == "user" || it.role == "assistant") && (it.status == "success" || it.status == "stopped") }
                .map { ChatMessage(role = it.role, content = it.content) }
                .toMutableList()
            history.add(ChatMessage(role = "user", content = "请继续"))

            val effectiveModel = agent.model.takeIf { it.isNotBlank() } ?: model
            val effectiveTemp = agent.temperature ?: temperature
            runCatching {
                container.createOrchestrator(baseUrl, apiKey)
                    .run(history, agent, effectiveModel, effectiveTemp, maxTokens)
                    .collect { event ->
                        when (event) {
                            is ChatEvent.Delta -> {
                                streamingMessage = streamingMessage?.copy(content = (streamingMessage?.content ?: "") + event.content)
                                rebuildMessageList()
                            }
                            is ChatEvent.Done -> {
                                // streamingMessage.content 已包含原始内容 + 全部 Delta 增量，无需再追加 fullContent
                                val merged = streamingMessage?.content ?: target.content
                                persistStreamingMessage(target.id, merged, "success")
                            }
                            is ChatEvent.Error -> finishStreamWithError(target.id, event.message, streamingMessage?.content ?: target.content)
                            else -> Unit
                        }
                    }
            }.onFailure { err ->
                finishStreamWithError(target.id, err.message ?: "继续生成失败", streamingMessage?.content ?: target.content)
            }
        }
    }

    /** 重试失败消息：删除该失败消息，重新发送对应的用户消息。 */
    fun retryMessage(messageId: String) {
        val convId = _uiState.value.activeConversationId ?: return
        val all = latestDbMessages
        val failedIndex = all.indexOfFirst { it.id == messageId }
        if (failedIndex < 0) return
        val userMsg = all.subList(0, failedIndex).lastOrNull { it.role == "user" } ?: return
        if (_uiState.value.isGenerating) return

        viewModelScope.launch {
            // 删除失败消息及其后所有消息
            all.filter { it.createdAt > userMsg.createdAt }.forEach {
                database.messageDao().deleteById(it.id)
            }
            latestDbMessages = all.filter { it.createdAt <= userMsg.createdAt && it.id != messageId }
            startGeneration(userContent = userMsg.content, presetUserMessageId = userMsg.id)
        }
    }

    /** 重答：删除最后一条 assistant 消息，重新发送最后一条用户消息。 */
    fun regenerateLast() {
        val all = latestDbMessages
        val lastAssistant = all.lastOrNull { it.role == "assistant" } ?: return
        val lastUser = all.lastOrNull { it.role == "user" } ?: return
        if (_uiState.value.isGenerating) return

        viewModelScope.launch {
            database.messageDao().deleteById(lastAssistant.id)
            latestDbMessages = all.filter { it.id != lastAssistant.id }
            startGeneration(userContent = lastUser.content, presetUserMessageId = lastUser.id)
        }
    }

    // ---------------------------------------------------------------
    // 会话操作
    // ---------------------------------------------------------------

    /** 切换当前 Agent（写入 DataStore，Flow 自动刷新）。 */
    fun selectAgent(agentId: String) {
        viewModelScope.launch { settings.setSelectedAgentId(agentId) }
    }

    /** 复制一个 Agent 为新的自定义 Agent。 */
    fun copyAgent(source: AgentEntity) {
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = source.name + " 副本",
            isBuiltin = false,
        )
        viewModelScope.launch { database.agentDao().insert(copy) }
    }

    /** 删除一个非内置 Agent。 */
    fun deleteAgent(agent: AgentEntity) {
        if (agent.isBuiltin) return
        viewModelScope.launch {
            database.agentDao().deleteById(agent.id)
            if (selectedAgentId == agent.id) {
                settings.setSelectedAgentId("general")
            }
        }
    }

    /** 新建对话。 */
    fun newConversation() {
        if (_uiState.value.isGenerating) stopGeneration()
        _uiState.update {
            it.copy(
                activeConversationId = null,
                messages = emptyList(),
                showConversationDrawer = false,
                autoScrollEnabled = true,
            )
        }
        latestDbMessages = emptyList()
        streamingMessage = null
    }

    /** 切换到指定会话。 */
    fun selectConversation(convId: String) {
        if (_uiState.value.isGenerating) stopGeneration()
        if (convId == _uiState.value.activeConversationId) {
            _uiState.update { it.copy(showConversationDrawer = false) }
            return
        }
        _uiState.update {
            it.copy(activeConversationId = convId, showConversationDrawer = false, autoScrollEnabled = true)
        }
        latestDbMessages = emptyList()
        streamingMessage = null
        observeMessagesForConversation(convId)
    }

    /** 删除会话。 */
    fun deleteConversation(convId: String) {
        viewModelScope.launch {
            database.messageDao().deleteForConversation(convId)
            database.conversationDao().deleteById(convId)
            if (_uiState.value.activeConversationId == convId) newConversation()
        }
    }

    /** 重命名会话。 */
    fun renameConversation(convId: String, newTitle: String) {
        viewModelScope.launch {
            val conv = database.conversationDao().getById(convId) ?: return@launch
            database.conversationDao().update(conv.copy(title = newTitle.ifBlank { conv.title }))
        }
    }

    private suspend fun touchConversation(convId: String) {
        val conv = database.conversationDao().getById(convId) ?: return
        database.conversationDao().update(conv.copy(updatedAt = System.currentTimeMillis()))
    }

    // ---------------------------------------------------------------
    // UI 状态切换
    // ---------------------------------------------------------------

    fun toggleDrawer(show: Boolean) {
        _uiState.update { it.copy(showConversationDrawer = show) }
    }

    fun setAutoScroll(enabled: Boolean) {
        _uiState.update { it.copy(autoScrollEnabled = enabled) }
    }

    /** 删除单条消息。 */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            database.messageDao().deleteById(messageId)
            latestDbMessages = latestDbMessages.filter { it.id != messageId }
            rebuildMessageList()
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
