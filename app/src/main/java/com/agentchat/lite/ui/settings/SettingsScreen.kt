package com.agentchat.lite.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agentchat.lite.di.AppContainer
import com.agentchat.lite.network.OpenAiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 服务商预设。 */
private data class ProviderPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val recommendedModel: String,
)

private val presets = listOf(
    ProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    ProviderPreset("bailian", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    ProviderPreset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo"),
    ProviderPreset("kimi", "Kimi 月之暗面", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
    ProviderPreset("glm", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
    ProviderPreset("minimax", "MiniMax", "https://api.minimax.chat/v1", "abab6.5s-chat"),
    ProviderPreset("custom", "自定义", "", ""),
)

private val modelChips = listOf(
    "deepseek-chat", "deepseek-reasoner",
    "qwen-plus", "qwen-turbo", "qwen-max",
    "moonshot-v1-8k", "glm-4-flash", "abab6.5s-chat",
)

/**
 * 设置页完整版：服务商/Key/地址/模型/温度/MaxTokens/主题/语言/备份/关于。
 *
 * @param onExport 触发导出备份（SAF）
 * @param onImport 触发导入备份（SAF）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val settings = container.settingsRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var providerId by remember { mutableStateOf("deepseek") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://api.deepseek.com") }
    var model by remember { mutableStateOf("deepseek-chat") }
    var keyVisible by remember { mutableStateOf(false) }

    var temperature by remember { mutableFloatStateOf(0.7f) }
    var maxTokensText by remember { mutableStateOf("4096") }
    var themeMode by remember { mutableStateOf("auto") }
    var language by remember { mutableStateOf("zh") }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val url = settings.baseUrl.first()
        val mdl = settings.model.first()
        val key = settings.getApiKey().orEmpty()
        baseUrl = url; model = mdl; apiKey = key
        temperature = settings.temperature.first().toFloat()
        maxTokensText = settings.maxTokens.first().toString()
        themeMode = settings.theme.first()
        language = settings.language.first()
        providerId = presets.firstOrNull { it.baseUrl == url }?.id
            ?: if (url.isBlank()) "deepseek" else "custom"
    }

    fun applyPreset(p: ProviderPreset) {
        providerId = p.id
        if (p.id != "custom") { baseUrl = p.baseUrl; model = p.recommendedModel }
        testResult = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("服务商预设")
            presets.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = providerId == p.id, onClick = { applyPreset(p) })
                    Text(p.label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it; testResult = null },
                label = { Text("API Key") },
                supportingText = { Text("将通过 Android Keystore 加密存储") },
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "隐藏" else "显示",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )

            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it; testResult = null },
                label = { Text("API 地址") },
                enabled = providerId == "custom",
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )

            OutlinedTextField(
                value = model, onValueChange = { model = it; testResult = null },
                label = { Text("模型") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modelChips.forEach { chip ->
                    FilledTonalButton(onClick = { model = chip; testResult = null }) { Text(chip) }
                }
            }

            OutlinedButton(
                onClick = {
                    if (testing) return@OutlinedButton
                    testing = true; testResult = null
                    scope.launch {
                        val start = System.currentTimeMillis()
                        val client = OpenAiClient(baseUrl, apiKey.ifBlank { "missing" }, container.okHttpClient)
                        val result = client.testConnection(model)
                        val elapsed = System.currentTimeMillis() - start
                        testing = false
                        result.onSuccess {
                            testSuccess = true
                            testResult = "✓ 连接成功（耗时 ${elapsed} ms，模型: $model）"
                        }.onFailure { err ->
                            testSuccess = false
                            testResult = "✗ ${err.message ?: "连接失败"}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !testing && baseUrl.isNotBlank() && model.isNotBlank(),
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("测试中…")
                } else Text("测试连接")
            }
            testResult?.let {
                Text(it, color = if (testSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    if (saving) return@Button
                    saving = true
                    scope.launch {
                        settings.setBaseUrl(baseUrl); settings.setModel(model)
                        if (apiKey.isNotBlank()) settings.setApiKey(apiKey)
                        saved = true; delay(600); saving = false
                        onSaved(); onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(), enabled = !saving,
            ) { Text(if (saved) "✓ 已保存" else "保存 Key/地址/模型") }

            SectionTitle("温度: %.1f".format(temperature))
            Slider(value = temperature, onValueChange = {
                temperature = it
                scope.launch { settings.setTemperature(it.toDouble()) }
            }, valueRange = 0f..2f, steps = 19)
            Text(
                when {
                    temperature <= 0.3f -> "精确（适合事实问答）"
                    temperature <= 1.0f -> "平衡（默认）"
                    else -> "创意（适合写作）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = maxTokensText,
                onValueChange = { v ->
                    maxTokensText = v
                    v.toIntOrNull()?.takeIf { it in 1..32768 }?.let {
                        scope.launch { settings.setMaxTokens(it) }
                    }
                },
                label = { Text("最大 Tokens") },
                supportingText = { Text("范围 1-32768，默认 4096") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )

            SectionTitle("主题")
            listOf("auto" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = themeMode == value, onClick = {
                        themeMode = value
                        scope.launch { settings.setTheme(value) }
                    })
                    Text(label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            SectionTitle("语言")
            listOf("zh" to "中文", "en" to "English").forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = language == value, onClick = {
                        language = value
                        scope.launch { settings.setLanguage(value) }
                        if (value == "en") Toast.makeText(context, "English 即将支持", Toast.LENGTH_SHORT).show()
                    })
                    Text(label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            SectionTitle("数据备份")
            Text(
                "包含会话、消息、助手配置；API Key 加密后一并导出。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("导出备份") }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("导入备份") }
            }

            SectionTitle("关于")
            Text("AgentChat v2.0.0", style = MaterialTheme.typography.bodyMedium)
            Text("数据仅存储在本机，不会上传。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("开源依赖：OkHttp / Markwon / Room / DataStore / exp4j", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
