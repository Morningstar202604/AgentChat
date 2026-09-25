package com.agentchat.lite

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.agentchat.lite.data.backup.BackupManager
import com.agentchat.lite.ui.chat.ChatScreen
import com.agentchat.lite.ui.theme.AgentChatTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主 Activity：主题流、启动屏、分享接收（onCreate + onNewIntent）、桌面快捷方式、备份 SAF。
 */
class MainActivity : ComponentActivity() {

    /** 待消费的分享文本；onCreate 初始化，onNewIntent 更新，ChatScreen 消费后置空。 */
    private val sharedTextState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        val container = (application as AgentChatApp).container
        setupShortcut()

        // 冷启动分享
        handleSendIntent(intent)

        setContent {
            val themeMode by container.settingsRepository.theme.collectAsState(initial = "auto")
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val backupManager = remember { BackupManager() }

            // 导出备份 launcher
            val exportLauncher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                lifecycleScope.launch {
                    runCatching {
                        val json = backupManager.export(container.database, container.settingsRepository)
                        contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    }.onSuccess {
                        Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            // 导入备份 launcher
            val importLauncher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                lifecycleScope.launch {
                    runCatching {
                        val raw = contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
                            ?: return@launch
                        // Bug 2a: parsePreview 失败要显式提示，不能吞成空预览
                        val preview = runCatching { backupManager.parsePreview(raw) }.getOrElse { err ->
                            android.app.AlertDialog.Builder(context)
                                .setTitle("导入失败")
                                .setMessage("无法解析备份文件: ${err.message ?: "格式错误"}")
                                .setPositiveButton("确定", null)
                                .show()
                            return@launch
                        }
                        android.app.AlertDialog.Builder(context)
                            .setTitle("导入备份")
                            .setMessage("即将导入：${preview.conversationCount} 个会话、${preview.agentCount} 个助手、${preview.messageCount} 条消息（格式 v${preview.version}）。\n导入将覆盖现有数据，是否继续？")
                            .setPositiveButton("导入") { _, _ ->
                                lifecycleScope.launch {
                                    // Bug 2b: 检查 import 结果，失败要提示
                                    val result = backupManager.import(raw, container.database, container.settingsRepository)
                                    result.onSuccess {
                                        Toast.makeText(context, "导入完成", Toast.LENGTH_SHORT).show()
                                    }.onFailure { e ->
                                        Toast.makeText(context, "导入失败: ${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }

            AgentChatTheme(darkTheme = darkTheme) {
                ChatScreen(
                    container = container,
                    onExportBackup = {
                        // Bug 4: 导出前确认提示
                        android.app.AlertDialog.Builder(context)
                            .setTitle("导出备份")
                            .setMessage("备份文件包含会话记录、助手配置和加密后的 API Key。请妥善保管，不要分享给他人。")
                            .setPositiveButton("继续导出") { _, _ ->
                                val name = "agentchat-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()) + ".json"
                                exportLauncher.launch(name)
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    },
                    onImportBackup = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    sharedText = sharedTextState.value,
                    onSharedTextConsumed = { sharedTextState.value = null },
                )
            }
        }
    }

    /** 应用已在后台时从其他 app 分享文本会走这里。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedTextState.value = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }

    private fun setupShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, "new_chat")
            .setShortLabel("新对话")
            .setLongLabel("开始新对话")
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
            )
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }
}
