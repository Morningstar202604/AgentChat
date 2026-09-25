package com.agentchat.lite.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agentchat.lite.data.local.entity.AgentEntity
import com.agentchat.lite.di.AppContainer
import com.agentchat.lite.ui.util.AgentIconMapper
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Agent 编辑器：新建/编辑/复制一个助手。
 *
 * @param container AppContainer
 * @param existing 若编辑已有 Agent 则传入；新建传 null；复制时传入被复制的源
 * @param onBack 返回
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEditorScreen(
    container: AppContainer,
    existing: AgentEntity?,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: "SmartToy") }
    var systemPrompt by remember { mutableStateOf(existing?.systemPrompt ?: "") }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    var followGlobalTemp by remember { mutableStateOf(existing?.temperature == null) }
    var tempValue by remember { mutableFloatStateOf((existing?.temperature ?: 0.7).toFloat()) }
    var welcome by remember { mutableStateOf(existing?.welcomeMessage ?: "") }

    val savedTools = remember(existing?.id) {
        runCatching { Json.decodeFromString<List<String>>(existing?.toolsEnabled ?: "[]") }
            .getOrDefault(emptyList())
    }
    var tools by remember { mutableStateOf(setOf<String>()) }

    // 工具选项（与真实注册名一致）
    val toolOptions = listOf(
        "get_current_time" to "当前时间",
        "calculate" to "计算器",
        "unit_converter" to "单位换算",
        "random" to "随机数",
    )

    val scope = rememberCoroutineScope()
    val dao = container.database.agentDao()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新建助手" else "编辑助手") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(text = "保存", onClick = {
                        val toolsJson = kotlinx.serialization.json.Json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                            tools.toList(),
                        )
                        val agent = AgentEntity(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.ifBlank { "未命名助手" },
                            icon = icon,
                            systemPrompt = systemPrompt,
                            model = model.trim(),
                            temperature = if (followGlobalTemp) null else tempValue.toDouble(),
                            toolsEnabled = toolsJson,
                            welcomeMessage = welcome,
                            isBuiltin = existing?.isBuiltin ?: false,
                            sortOrder = existing?.sortOrder ?: 99,
                        )
                        scope.launch {
                            dao.insert(agent)
                            onBack()
                        }
                    })
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("图标", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AgentIconMapper.all) { (iconName, vector) ->
                    val selected = icon == iconName
                    Column(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            )
                            .clickable { icon = iconName }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            vector,
                            contentDescription = iconName,
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Prompt") },
                supportingText = { Text("可用变量：{{date}} {{time}} {{weekday}} {{username}}  (${systemPrompt.length} 字)") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型") },
                placeholder = { Text("留空使用全局设置") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = followGlobalTemp,
                    onCheckedChange = { followGlobalTemp = it },
                )
                Spacer(Modifier.size(8.dp))
                Text("跟随全局温度")
            }
            if (!followGlobalTemp) {
                Text("温度: %.1f".format(tempValue))
                Slider(
                    value = tempValue,
                    onValueChange = { tempValue = it },
                    valueRange = 0f..2f,
                    steps = 19,
                )
            }

            Text("工具", style = MaterialTheme.typography.labelLarge)
            toolOptions.forEach { (key, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = tools.contains(key),
                        onCheckedChange = { on ->
                            tools = if (on) tools + key else tools - key
                        },
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(label)
                }
            }

            OutlinedTextField(
                value = welcome,
                onValueChange = { welcome = it },
                label = { Text("欢迎语") },
                placeholder = { Text("首次对话时自动发送，留空则不发送") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text) }
}
