package com.agentchat.lite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentchat.lite.data.local.entity.AgentEntity
import com.agentchat.lite.ui.util.AgentIconMapper

/**
 * 空状态：显示当前 Agent 的图标、名称、人设摘要与动态建议。
 */
@Composable
fun EmptyState(
    agent: AgentEntity?,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = remember(agent?.id) { buildSuggestions(agent) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(agentColorFor(agent?.id)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AgentIconMapper.get(agent?.icon ?: "SmartToy"),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = agent?.name ?: "通用助手",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = agent?.systemPrompt?.take(60) ?: "你好，有什么可以帮你的？",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(
            modifier = Modifier.padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            suggestions.forEach { s ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clickable { onSuggestionClick(s) },
                ) {
                    Text(
                        text = s,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** 根据 Agent ID 生成动态建议。 */
private fun buildSuggestions(agent: AgentEntity?): List<String> = when (agent?.id) {
    "coder" -> listOf(
        "帮我写一段 Python 快速排序",
        "解释一下什么是闭包",
        "写一个 Kotlin 的单例模式",
        "这段代码有什么 bug？",
    )
    "writer" -> listOf(
        "帮我写一段产品发布会文案",
        "写一条朋友圈文案",
        "润色一下我的自我介绍",
        "写一个短视频脚本开头",
    )
    "translator" -> listOf(
        "把这句话翻译成英文：你好世界",
        "翻译这句技术文档",
        "中译英：今天天气不错",
        "英译中：artificial intelligence",
    )
    "thinker" -> listOf(
        "分析一下远程办公的利弊",
        "为什么公司需要做战略复盘？",
        "帮我拆解这个决策",
        "第二大脑是什么意思？",
    )
    "analyst" -> listOf(
        "帮我分析这组数据趋势",
        "怎么做一份月度数据报告？",
        "转化率下降了怎么排查？",
        "帮我算一下 ROI",
    )
    "traveler" -> listOf(
        "杭州 3 天亲子游怎么安排？",
        "成都 5 天美食行程",
        "东京自由行攻略",
        "周末短途游推荐",
    )
    "fitness" -> listOf(
        "新手增肌一周训练计划",
        "减脂期饮食怎么吃？",
        "在家无器械怎么练？",
        "久坐腰痛怎么改善？",
    )
    "teacher" -> listOf(
        "用通俗的话解释什么是通货膨胀",
        "怎么快速学会一个新框架？",
        "给我一个学习 Python 的路径",
        "什么是函数式编程？",
    )
    else -> listOf(
        "今天有什么新鲜事？",
        "帮我写一封邮件",
        "解释一个概念给我听",
        "推荐一本书",
    )
}
