package com.agentchat.lite.data.backup

import com.agentchat.lite.data.local.AppDatabase
import com.agentchat.lite.data.local.entity.AgentEntity
import com.agentchat.lite.data.local.entity.ConversationEntity
import com.agentchat.lite.data.local.entity.MessageEntity
import com.agentchat.lite.data.pref.SettingsRepository
import com.agentchat.lite.data.pref.SettingsSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID

/**
 * 备份文件格式（v2）。
 *
 * ```json
 * {"app":"agentchat","version":2,"settings":{...},"agents":[...],"conversations":[...]}
 * ```
 */
@Serializable
data class BackupFile(
    val app: String = "agentchat",
    val version: Int = 2,
    val settings: BackupSettings = BackupSettings(),
    val agents: List<BackupAgent> = emptyList(),
    val conversations: List<BackupConversation> = emptyList(),
)

@Serializable
data class BackupSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val theme: String = "auto",
)

@Serializable
data class BackupAgent(
    val id: String,
    val name: String,
    val icon: String = "SmartToy",
    val systemPrompt: String,
    val model: String = "",
    val temperature: Double? = null,
    val toolsEnabled: String = "[]",
    val welcomeMessage: String = "",
    val isBuiltin: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class BackupConversation(
    val id: String,
    val title: String,
    val agentId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val messages: List<BackupMessage> = emptyList(),
)

@Serializable
data class BackupMessage(
    val id: String = "",
    val role: String,
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val createdAt: Long = 0,
    val status: String = "success",
)

/** 备份预览摘要，用于导入前确认。 */
data class BackupPreview(
    val version: Int,
    val conversationCount: Int,
    val agentCount: Int,
    val messageCount: Int,
    val error: String? = null,
)

/**
 * JSON 备份导入导出管理器。
 *
 * 支持 v2 新版格式导出/导入，并兼容 v1.0.0（app.js）旧格式自动迁移。
 * 导入时先清表再插入，覆盖现有数据。
 */
class BackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * 导出全部数据为 JSON 字符串。
     */
    suspend fun export(db: AppDatabase, settings: SettingsRepository): String {
        val snap = settings.snapshot()
        val agents = db.agentDao().getAll().map { it.toBackup() }
        val convEntities = db.conversationDao().getAll()
        val conversations = convEntities.map { conv ->
            val msgs = db.messageDao().getForConversation(conv.id).map { it.toBackup() }
            conv.toBackup(msgs)
        }
        val file = BackupFile(
            settings = BackupSettings(
                baseUrl = snap.baseUrl,
                apiKey = snap.apiKey,
                model = snap.model,
                temperature = snap.temperature,
                maxTokens = snap.maxTokens,
                theme = snap.theme,
            ),
            agents = agents,
            conversations = conversations,
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /**
     * 预览备份：解析 JSON 但不写入，返回会话数/Agent 数/格式版本。
     * 格式无法识别时抛 IllegalArgumentException。
     */
    suspend fun parsePreview(raw: String): BackupPreview {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: throw IllegalArgumentException("不是有效的 JSON 对象")
        val version = root["version"]?.jsonPrimitive?.intOrNull
            ?: root["v"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("无法识别的备份格式（缺少 version/v 字段）")
        val preview = if (version >= 2) {
            json.decodeFromJsonElement(BackupFile.serializer(), root)
        } else {
            migrateV1(root)
        }
        return BackupPreview(
            version = version,
            conversationCount = preview.conversations.size,
            agentCount = preview.agents.size,
            messageCount = preview.conversations.sumOf { it.messages.size },
        )
    }

    /**
     * 从 JSON 字符串导入数据，覆盖现有数据。
     *
     * 自动识别 v1（`v` 字段，`convs`/`prompt`/`tools`）与 v2（`version` 字段）格式。
     */
    suspend fun import(raw: String, db: AppDatabase, settings: SettingsRepository): Result<Unit> {
        return runCatching {
            val root = json.parseToJsonElement(raw) as? JsonObject
                ?: throw IllegalArgumentException("不是有效的 JSON 对象")

            val version = root["version"]?.jsonPrimitive?.intOrNull
                ?: root["v"]?.jsonPrimitive?.intOrNull
                ?: 1 // 无 version 字段视为 v1

            val backup = if (version >= 2) {
                json.decodeFromJsonElement(BackupFile.serializer(), root)
            } else {
                migrateV1(root)
            }

            // 清表
            db.agentDao().clearAll()
            db.messageDao().clearAll()
            db.conversationDao().clearAll()

            // 插入 agents
            db.agentDao().insertAll(backup.agents.map { it.toEntity() })

            // 插入 conversations + messages
            for (conv in backup.conversations) {
                db.conversationDao().insert(conv.toEntity())
                val msgEntities = conv.messages.mapIndexed { _, m -> m.toEntity(conv.id) }
                db.messageDao().insertAll(msgEntities)
            }

            // 恢复设置
            settings.restore(
                SettingsSnapshot(
                    baseUrl = backup.settings.baseUrl.ifEmpty { "https://api.deepseek.com" },
                    model = backup.settings.model.ifEmpty { "deepseek-chat" },
                    temperature = backup.settings.temperature,
                    maxTokens = backup.settings.maxTokens,
                    theme = backup.settings.theme.ifEmpty { "auto" },
                    apiKey = backup.settings.apiKey,
                ),
            )
        }
    }

    /**
     * 将 v1.0.0（app.js）格式迁移为 v2。
     *
     * v1 格式：`{app, v:1, settings, agents:[{id,name,emoji,prompt,model,tools,builtin}],
     * convs:[{id,title,agentId,ts,msgs:[{role,content,ts}]}]}`
     */
    private fun migrateV1(root: JsonObject): BackupFile {
        // settings
        val settingsObj = root["settings"]?.jsonObject
        val settings = BackupSettings(
            baseUrl = settingsObj?.get("baseUrl")?.jsonPrimitive?.contentOrNull.orEmpty(),
            apiKey = settingsObj?.get("apiKey")?.jsonPrimitive?.contentOrNull.orEmpty(),
            model = settingsObj?.get("model")?.jsonPrimitive?.contentOrNull.orEmpty(),
            temperature = settingsObj?.get("temperature")?.jsonPrimitive?.doubleOrNull() ?: 0.7,
            maxTokens = settingsObj?.get("maxTokens")?.jsonPrimitive?.intOrNull ?: 4096,
            theme = settingsObj?.get("theme")?.jsonPrimitive?.contentOrNull ?: "auto",
        )

        // agents: v1 用 prompt/tools/builtin，v2 用 systemPrompt/toolsEnabled/isBuiltin
        val agents = root["agents"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            BackupAgent(
                id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: "未命名",
                icon = o["icon"]?.jsonPrimitive?.contentOrNull ?: "SmartToy",
                systemPrompt = o["systemPrompt"]?.jsonPrimitive?.contentOrNull
                    ?: o["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                model = o["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                toolsEnabled = o["toolsEnabled"]?.jsonPrimitive?.contentOrNull
                    ?: if (o["tools"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == false) "[]"
                    else """["get_current_time","calculate"]""",
                isBuiltin = o["isBuiltin"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: o["builtin"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: false,
                sortOrder = 0,
            )
        }.orEmpty()

        // conversations: v1 用 convs/ts/msgs，v2 用 conversations/createdAt/updatedAt/messages
        val conversations = root["convs"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val ts = o["ts"]?.jsonPrimitive?.longOrNull ?: 0L
            val msgs = o["msgs"]?.jsonArray?.mapNotNull { mEl ->
                val mo = mEl.jsonObject
                BackupMessage(
                    id = "m_" + UUID.randomUUID().toString(),
                    role = mo["role"]?.jsonPrimitive?.contentOrNull ?: "user",
                    content = mo["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    createdAt = mo["ts"]?.jsonPrimitive?.longOrNull ?: ts,
                    status = "success",
                )
            }.orEmpty()
            BackupConversation(
                id = id,
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                agentId = o["agentId"]?.jsonPrimitive?.contentOrNull ?: "general",
                createdAt = ts,
                updatedAt = ts,
                messages = msgs,
            )
        }.orEmpty()

        return BackupFile(
            app = "agentchat",
            version = 2,
            settings = settings,
            agents = agents,
            conversations = conversations,
        )
    }

    // ---------- Entity ↔ Backup 映射 ----------

    private fun AgentEntity.toBackup() = BackupAgent(
        id = id,
        name = name,
        icon = icon,
        systemPrompt = systemPrompt,
        model = model,
        temperature = temperature,
        toolsEnabled = toolsEnabled,
        welcomeMessage = welcomeMessage,
        isBuiltin = isBuiltin,
        sortOrder = sortOrder,
    )

    private fun BackupAgent.toEntity() = AgentEntity(
        id = id,
        name = name,
        icon = icon,
        systemPrompt = systemPrompt,
        model = model,
        temperature = temperature,
        toolsEnabled = toolsEnabled,
        welcomeMessage = welcomeMessage,
        isBuiltin = isBuiltin,
        sortOrder = sortOrder,
    )

    private fun ConversationEntity.toBackup(messages: List<BackupMessage>) = BackupConversation(
        id = id,
        title = title,
        agentId = agentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pinned = pinned,
        messages = messages,
    )

    private fun BackupConversation.toEntity() = ConversationEntity(
        id = id,
        title = title,
        agentId = agentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pinned = pinned,
    )

    private fun MessageEntity.toBackup() = BackupMessage(
        id = id,
        role = role,
        content = content,
        toolCallId = toolCallId,
        toolName = toolName,
        createdAt = createdAt,
        status = status,
    )

    private fun BackupMessage.toEntity(convId: String) = MessageEntity(
        id = id.ifEmpty { "m_" + UUID.randomUUID() },
        conversationId = convId,
        role = role,
        content = content,
        toolCallId = toolCallId,
        toolName = toolName,
        createdAt = createdAt,
        status = status,
    )

    private fun kotlinx.serialization.json.JsonPrimitive.doubleOrNull(): Double? {
        return this.contentOrNull?.toDoubleOrNull()
    }
}
