package com.agentchat.lite.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentchat.lite.data.local.entity.AgentEntity
import com.agentchat.lite.ui.util.AgentIconMapper

/**
 * 右侧 Agent 列表抽屉。
 *
 * @param agents 全部 Agent
 * @param selectedId 当前选中 Agent ID
 * @param onSelect 选中回调
 * @param onEdit 编辑回调
 * @param onCopy 复制回调
 * @param onDelete 删除回调
 * @param onCreateNew 新建回调
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AgentDrawer(
    agents: List<AgentEntity>,
    selectedId: String?,
    onSelect: (AgentEntity) -> Unit,
    onEdit: (AgentEntity) -> Unit,
    onCopy: (AgentEntity) -> Unit,
    onDelete: (AgentEntity) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuTarget by remember { mutableStateOf<AgentEntity?>(null) }

    ModalDrawerSheet(modifier = modifier.width(300.dp)) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                text = "选择助手",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(agents, key = { it.id }) { agent ->
                    AgentRow(
                        agent = agent,
                        isSelected = agent.id == selectedId,
                        onClick = { onSelect(agent) },
                        onLongClick = { menuTarget = agent },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCreateNew() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("新建助手", color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    menuTarget?.let { target ->
        ModalBottomSheet(onDismissRequest = { menuTarget = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                MenuItem(Icons.Default.Edit, "编辑") { onEdit(target); menuTarget = null }
                MenuItem(Icons.Default.ContentCopy, "复制为新助手") { onCopy(target); menuTarget = null }
                if (!target.isBuiltin) {
                    MenuItem(Icons.Default.Delete, "删除") { onDelete(target); menuTarget = null }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgentRow(
    agent: AgentEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bg = agentColorFor(agent.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AgentIconMapper.get(agent.icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = agent.name,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = agent.systemPrompt.take(30) + if (agent.systemPrompt.length > 30) "…" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "当前选中",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 20.dp))
    }
}

/** Agent 头像背景色，按 id hash 取色板。 */
internal fun agentColorFor(id: String?): Color {
    val palette = listOf(
        Color(0xFF6366F1), Color(0xFF10B981), Color(0xFFF59E0B),
        Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF06B6D4),
    )
    val idx = (id?.hashCode() ?: 0).mod(palette.size)
    return palette[idx]
}

private fun Int.mod(n: Int): Int = ((this % n) + n) % n
