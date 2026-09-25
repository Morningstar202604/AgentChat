package com.agentchat.lite.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 长按消息弹出的底部操作菜单：复制 / 重答 / 编辑（仅用户消息）/ 删除 / 分享。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionMenu(
    isUserMessage: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            ActionItem(icon = Icons.Default.ContentCopy, label = "复制") { onCopy(); onDismiss() }
            if (!isUserMessage) {
                ActionItem(icon = Icons.Default.Refresh, label = "重答") { onRegenerate(); onDismiss() }
            }
            if (isUserMessage) {
                ActionItem(icon = Icons.Default.Edit, label = "编辑") { onEdit(); onDismiss() }
            }
            ActionItem(icon = Icons.Default.Delete, label = "删除") { onDelete(); onDismiss() }
            ActionItem(icon = Icons.Default.Share, label = "分享") { onShare(); onDismiss() }
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            modifier = Modifier.padding(start = 20.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
