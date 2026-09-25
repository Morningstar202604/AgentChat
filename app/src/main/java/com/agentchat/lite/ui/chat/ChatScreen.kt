package com.agentchat.lite.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentchat.lite.di.AppContainer
import com.agentchat.lite.ui.components.ConversationDrawer
import com.agentchat.lite.ui.components.DateSeparator
import com.agentchat.lite.ui.components.EmptyState
import com.agentchat.lite.ui.components.InputComposer
import com.agentchat.lite.ui.components.MessageActionMenu
import com.agentchat.lite.ui.components.MessageBubble
import com.agentchat.lite.ui.components.NetworkBanner
import com.agentchat.lite.ui.util.TimeFormatter
import kotlinx.coroutines.launch

/**
 * 聊天主屏幕。
 *
 * @param container AppContainer 依赖注入容器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
) {
    val vm: ChatViewModel = viewModel(
        factory = remember { ChatViewModelFactory(container) },
    )
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 分享进来的文本自动填入输入框，然后消费清空
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            vm.onInputChange(sharedText)
            onSharedTextConsumed()
        }
    }

    var actionMenuTarget by remember { mutableStateOf<UiMessage?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showAgentDrawer by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<com.agentchat.lite.data.local.entity.AgentEntity?>(null) }
    var showNewEditor by remember { mutableStateOf(false) }
    var pendingSwitchAgent by remember { mutableStateOf<com.agentchat.lite.data.local.entity.AgentEntity?>(null) }

    if (showSettings) {
        com.agentchat.lite.ui.settings.SettingsScreen(
            container = container,
            onBack = { showSettings = false },
            onSaved = { vm.refreshApiKey() },
            onExport = onExportBackup,
            onImport = onImportBackup,
        )
        return
    }
    editorTarget?.let { target ->
        com.agentchat.lite.ui.agent.AgentEditorScreen(
            container = container,
            existing = target,
            onBack = { editorTarget = null },
        )
        return
    }
    if (showNewEditor) {
        com.agentchat.lite.ui.agent.AgentEditorScreen(
            container = container,
            existing = null,
            onBack = { showNewEditor = false },
        )
        return
    }

    // 自动滚到底部
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.autoScrollEnabled && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    // 用户手动上滑时关闭自动滚动
    val userScrolledUp by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            firstVisible > 0
        }
    }
    LaunchedEffect(userScrolledUp) {
        if (userScrolledUp && state.isGenerating) {
            vm.setAutoScroll(false)
        }
    }

    val drawerState = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed,
    )
    val agentDrawerState = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed,
    )

    // 切换 Agent 确认弹窗
    pendingSwitchAgent?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingSwitchAgent = null },
            title = { Text("切换助手") },
            text = { Text("切换助手将停止当前生成中的回复，是否继续？") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.stopGeneration()
                    vm.selectAgent(target.id)
                    pendingSwitchAgent = null
                    showAgentDrawer = false
                }) { Text("切换") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingSwitchAgent = null }) { Text("取消") }
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = agentDrawerState,
        drawerContent = {
            com.agentchat.lite.ui.components.AgentDrawer(
                agents = state.agents,
                selectedId = state.currentAgent?.id,
                onSelect = { agent ->
                    if (state.isGenerating) {
                        pendingSwitchAgent = agent
                    } else {
                        vm.selectAgent(agent.id)
                        showAgentDrawer = false
                    }
                },
                onEdit = { editorTarget = it; showAgentDrawer = false },
                onCopy = { vm.copyAgent(it) },
                onDelete = { vm.deleteAgent(it) },
                onCreateNew = { showNewEditor = true; showAgentDrawer = false },
            )
        },
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ConversationDrawer(
                    conversations = state.conversations,
                    activeConversationId = state.activeConversationId,
                    onSelect = vm::selectConversation,
                    onNewConversation = vm::newConversation,
                    onRename = vm::renameConversation,
                    onDelete = vm::deleteConversation,
                    drawerState = drawerState,
                    scope = scope,
                )
            },
        ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(state.currentAgent?.name ?: "AgentChat")
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "打开会话列表")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAgentDrawer = true }) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "切换助手",
                            )
                        }
                        IconButton(onClick = vm::newConversation) {
                            Icon(Icons.Default.Add, contentDescription = "新对话")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                if (!state.isOnline) {
                    NetworkBanner()
                }
                if (state.apiKeyMissing) {
                    ApiKeyGuideBanner()
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (state.messages.isEmpty() && !state.isGenerating) {
                        EmptyState(
                            agent = state.currentAgent,
                            onSuggestionClick = { suggestion ->
                                vm.onInputChange(suggestion)
                                vm.send()
                            },
                        )
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                            items(state.messages, key = { it.id }) { msg ->
                                val idx = state.messages.indexOf(msg)
                                val prevTs = state.messages.getOrNull(idx - 1)?.createdAt
                                if (TimeFormatter.shouldShowDateSeparator(prevTs, msg.createdAt)) {
                                    DateSeparator(timestamp = msg.createdAt)
                                }
                                MessageBubble(
                                    message = msg,
                                    agentIconName = state.currentAgent?.icon ?: "SmartToy",
                                    agentColor = agentColorFor(state.currentAgent?.id),
                                    onLongPress = { actionMenuTarget = msg },
                                    onRetry = { vm.retryMessage(msg.id) },
                                    onContinue = { vm.continueGeneration(msg.id) },
                                    modifier = Modifier.semantics {
                                        contentDescription =
                                            if (msg.isUser) "用户说: ${msg.content}" else "助手说: ${msg.content}"
                                    },
                                )
                            }
                        }

                        // 回到最新按钮
                        if (!state.autoScrollEnabled) {
                            FloatingActionButton(
                                onClick = {
                                    vm.setAutoScroll(true)
                                    scope.launch {
                                        listState.animateScrollToItem(state.messages.lastIndex)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                            ) {
                                Icon(
                                    Icons.Default.ArrowDownward,
                                    contentDescription = "回到最新",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }

                InputComposer(
                    inputText = state.inputText,
                    onInputChange = vm::onInputChange,
                    onSend = vm::send,
                    onStop = vm::stopGeneration,
                    isGenerating = state.isGenerating,
                    enabled = state.isOnline && !state.apiKeyMissing,
                )
            }
        }
    }
    }

    // 长按操作菜单
    actionMenuTarget?.let { msg ->
        MessageActionMenu(
            isUserMessage = msg.isUser,
            onDismiss = { actionMenuTarget = null },
            onCopy = { copyToClipboard(context, msg.content) },
            onRegenerate = { vm.regenerateLast() },
            onEdit = { /* 阶段 4 实现编辑 */ },
            onDelete = { vm.deleteMessage(msg.id) },
            onShare = { shareText(context, msg.content) },
        )
    }
}

@Composable
private fun ApiKeyGuideBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "请先在设置中配置 API Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("message", text))
    Toast.makeText(context, "已复制 ✓", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "分享"))
}

/** 根据 Agent ID 稳定分配一个主题色作为头像背景。 */
private fun agentColorFor(agentId: String?): Color {
    val palette = listOf(
        Color(0xFFEEF2FF), Color(0xFFDCFCE7), Color(0xFFFEF3C7),
        Color(0xFFFCE7F3), Color(0xFFE0E7FF), Color(0xFFF3E8FF),
    )
    val idx = (agentId?.hashCode() ?: 0).mod(palette.size)
    return palette[idx]
}

private fun Int.mod(n: Int): Int = ((this % n) + n) % n
