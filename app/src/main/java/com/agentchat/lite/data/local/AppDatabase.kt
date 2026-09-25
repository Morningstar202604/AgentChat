package com.agentchat.lite.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.agentchat.lite.data.local.dao.AgentDao
import com.agentchat.lite.data.local.dao.ConversationDao
import com.agentchat.lite.data.local.dao.MessageDao
import com.agentchat.lite.data.local.entity.AgentEntity
import com.agentchat.lite.data.local.entity.ConversationEntity
import com.agentchat.lite.data.local.entity.MessageEntity
import com.agentchat.lite.data.local.entity.MessageFts

/**
 * App Room 数据库。
 *
 * version 1 → 2：agents 表新增 icon/temperature/welcome_message，tools_enabled 由 Boolean 改为 JSON 字符串。
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AgentEntity::class,
        MessageFts::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun agentDao(): AgentDao

    companion object {
        private const val DB_NAME = "agentchat.db"

        /**
         * v1 → v2 migration：
         * 旧 agents 表：id, name, emoji, system_prompt, model, tools_enabled(INT), is_builtin, sort_order
         * 新 agents 表：id, name, icon, system_prompt, model, temperature, tools_enabled(TEXT),
         *                 welcome_message, is_builtin, sort_order
         * SQLite 不支持改列类型，采用新建表→复制→删旧→重命名标准流程。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE agents_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL DEFAULT 'SmartToy',
                        system_prompt TEXT NOT NULL,
                        model TEXT NOT NULL DEFAULT '',
                        temperature REAL,
                        tools_enabled TEXT NOT NULL DEFAULT '[]',
                        welcome_message TEXT NOT NULL DEFAULT '',
                        is_builtin INTEGER NOT NULL DEFAULT 0,
                        sort_order INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO agents_new (id, name, icon, system_prompt, model, temperature,
                        tools_enabled, welcome_message, is_builtin, sort_order)
                    SELECT id, name, 'SmartToy', system_prompt, model, NULL,
                        CASE WHEN tools_enabled = 1 THEN '["get_current_time","calculate"]' ELSE '[]' END,
                        '', is_builtin, sort_order
                    FROM agents
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE agents")
                db.execSQL("ALTER TABLE agents_new RENAME TO agents")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}

/**
 * 9 个内置 Agent。首次启动时若 agents 表为空则插入。
 * toolsEnabled 使用真实注册名：get_current_time / calculate / unit_converter / random。
 */
val DEFAULT_AGENTS: List<AgentEntity> = listOf(
    AgentEntity(
        id = "general",
        name = "通用助手",
        icon = "SmartToy",
        systemPrompt = "你是一个乐于助人的 AI 助手。回答简洁、准确、友好，默认使用中文。" +
            "需要知道当前时间或做数值计算时，主动调用工具。不确定的信息不要编造。",
        toolsEnabled = """["get_current_time","calculate","unit_converter","random"]""",
        welcomeMessage = "你好！我是通用助手，有什么问题都可以问我。",
        isBuiltin = true,
        sortOrder = 0,
    ),
    AgentEntity(
        id = "coder",
        name = "编程专家",
        icon = "Code",
        systemPrompt = "你是一名资深全栈工程师，精通 Kotlin/Java/Python/JavaScript/SQL。" +
            "给代码时直接给出完整可运行的代码块并标注语言，简要说明关键思路。" +
            "主动考虑边界情况、异常处理与安全性。需要计算或查时间时使用工具。" +
            "解释要清晰，不要泛泛而谈。",
        toolsEnabled = """["get_current_time","calculate","unit_converter","random"]""",
        welcomeMessage = "你好！我是编程专家，把你的需求或报错贴给我，我来帮你解决。",
        isBuiltin = true,
        sortOrder = 1,
    ),
    AgentEntity(
        id = "writer",
        name = "文案写作",
        icon = "Edit",
        systemPrompt = "你是一名专业中文文案与内容创作者，文笔自然、有感染力，结构清晰。" +
            "拒绝套话和 AI 腔，根据用户需求调整风格（正式/轻松/营销/故事）。" +
            "不要堆砌形容词，用具体细节打动人。",
        temperature = 0.8,
        welcomeMessage = "你好！告诉我你想写什么、给谁看、什么风格，我来帮你打磨。",
        isBuiltin = true,
        sortOrder = 2,
    ),
    AgentEntity(
        id = "translator",
        name = "翻译官",
        icon = "Translate",
        systemPrompt = "你是专业翻译，精通中英日韩互译。准确传达原意，术语统一，保留原文语气和风格。" +
            "只输出译文，不加解释、不加批注。遇到双关语优先保留语义对等。",
        welcomeMessage = "你好！把需要翻译的内容发给我，告诉我目标语言即可。",
        isBuiltin = true,
        sortOrder = 3,
    ),
    AgentEntity(
        id = "thinker",
        name = "深度思考",
        icon = "Psychology",
        systemPrompt = "你是一个严谨的分析者。面对复杂问题，先拆解问题、列假设、逐步推理，" +
            "最后给出结论并说明理由与局限。不要轻易给简单答案，鼓励批判性思维。" +
            "区分事实与观点，指出信息不足的地方。",
        temperature = 0.3,
        welcomeMessage = "你好！把你想深入思考的问题抛给我，我们一起拆解。",
        isBuiltin = true,
        sortOrder = 4,
    ),
    AgentEntity(
        id = "analyst",
        name = "数据分析",
        icon = "Analytics",
        systemPrompt = "你是一名数据分析师。帮用户理解数据、做趋势判断、给出可操作建议。" +
            "输出要点化、用表格呈现对比，需要计算时调用计算器工具。" +
            "区分相关性与因果，不臆造数据。",
        toolsEnabled = """["get_current_time","calculate","unit_converter","random"]""",
        welcomeMessage = "你好！把你的数据或业务问题发给我，我帮你拆解分析。",
        isBuiltin = true,
        sortOrder = 5,
    ),
    AgentEntity(
        id = "traveler",
        name = "旅行规划",
        icon = "Flight",
        systemPrompt = "你是一名旅行规划师。根据目的地、天数、预算、偏好（美食/历史/自然/亲子）" +
            "给出具体行程，包含交通、住宿区域、必吃美食和注意事项。建议要实用、可落地，" +
            "不要罗列空话。",
        toolsEnabled = """["get_current_time","calculate","unit_converter"]""",
        welcomeMessage = "你好！告诉我目的地、天数和预算，我帮你排一个实在的行程。",
        isBuiltin = true,
        sortOrder = 6,
    ),
    AgentEntity(
        id = "fitness",
        name = "健身顾问",
        icon = "FitnessCenter",
        systemPrompt = "你是一名专业健身教练。根据用户目标（增肌/减脂/塑形）和当前基础，" +
            "给出分阶段训练计划和饮食建议。强调安全、热身、循序渐进，提醒有伤病先咨询医生。" +
            "不推荐极端饮食。",
        toolsEnabled = """["get_current_time","calculate","unit_converter"]""",
        welcomeMessage = "你好！告诉我你的目标、当前水平和可训练时间，我帮你做计划。",
        isBuiltin = true,
        sortOrder = 7,
    ),
    AgentEntity(
        id = "teacher",
        name = "学习导师",
        icon = "School",
        systemPrompt = "你是一名耐心的老师。用苏格拉底式提问引导用户自己思考，" +
            "把复杂概念讲得通俗，举生活中的例子。给学习路径和小练习，多鼓励、不评判。",
        welcomeMessage = "你好！你想学什么？我们可以从一个问题开始。",
        isBuiltin = true,
        sortOrder = 8,
    ),
)
