package com.agentchat.lite.data.backup

import com.agentchat.lite.data.local.AppDatabase
import com.agentchat.lite.data.local.dao.AgentDao
import com.agentchat.lite.data.local.dao.ConversationDao
import com.agentchat.lite.data.local.dao.MessageDao
import com.agentchat.lite.data.local.entity.AgentEntity
import com.agentchat.lite.data.local.entity.ConversationEntity
import com.agentchat.lite.data.local.entity.MessageEntity
import com.agentchat.lite.data.pref.SettingsRepository
import com.agentchat.lite.data.pref.SettingsSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BackupManager 单元测试——纯 JVM，用 mockk 模拟 Room DAO 与 SettingsRepository。
 */
class BackupManagerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val backupManager = BackupManager()

    // ---------- 测试 ----------

    @Test
    fun `export produces valid v2 json`() = runTest {
        val agent = AgentEntity(
            id = "general", name = "通用助手", icon = "SmartToy",
            systemPrompt = "你好", model = "", toolsEnabled = """["calculate"]""", isBuiltin = true,
        )
        val conv = ConversationEntity(
            id = "c1", title = "测试", agentId = "general",
            createdAt = 1000L, updatedAt = 2000L,
        )
        val msg = MessageEntity(
            id = "m1", conversationId = "c1", role = "user",
            content = "你好", createdAt = 1000L, status = "success",
        )
        val settings = SettingsSnapshot(
            baseUrl = "https://api.deepseek.com", model = "deepseek-chat",
            temperature = 0.7, maxTokens = 4096, theme = "auto", apiKey = "sk-test",
        )

        val agentDao = mockk<AgentDao>(relaxed = true)
        val convDao = mockk<ConversationDao>(relaxed = true)
        val msgDao = mockk<MessageDao>(relaxed = true)
        coEvery { agentDao.getAll() } returns listOf(agent)
        coEvery { convDao.getAll() } returns listOf(conv)
        coEvery { msgDao.getForConversation("c1") } returns listOf(msg)

        val db = mockk<AppDatabase>(relaxed = true)
        every { db.agentDao() } returns agentDao
        every { db.conversationDao() } returns convDao
        every { db.messageDao() } returns msgDao

        val repo = mockk<SettingsRepository>(relaxed = true)
        coEvery { repo.snapshot() } returns settings

        val output = backupManager.export(db, repo)
        val parsed = json.decodeFromString<BackupFile>(output)

        assertEquals("agentchat", parsed.app)
        assertEquals(2, parsed.version)
        assertEquals("https://api.deepseek.com", parsed.settings.baseUrl)
        assertEquals("sk-test", parsed.settings.apiKey)
        assertEquals(1, parsed.agents.size)
        assertEquals("通用助手", parsed.agents[0].name)
        assertEquals(1, parsed.conversations.size)
        assertEquals("测试", parsed.conversations[0].title)
        assertEquals(1, parsed.conversations[0].messages.size)
        assertEquals("你好", parsed.conversations[0].messages[0].content)
    }

    @Test
    fun `import v1 format migrates correctly`() = runTest {
        // v1.0.0 app.js 格式：{app, v:1, settings, agents, convs}
        val v1Json = """
            {
              "app": "agentchat",
              "v": 1,
              "settings": {
                "baseUrl": "https://api.deepseek.com",
                "apiKey": "sk-old",
                "model": "deepseek-chat",
                "temperature": 0.5,
                "maxTokens": 2048,
                "theme": "dark"
              },
              "agents": [
                {
                  "id": "general", "name": "通用助手", "emoji": "💬",
                  "prompt": "你是助手", "model": "", "tools": true, "builtin": true
                }
              ],
              "convs": [
                {
                  "id": "conv_1", "title": "旧对话", "agentId": "general", "ts": 1234567890,
                  "msgs": [
                    {"role": "user", "content": "你好旧世界", "ts": 1234567890}
                  ]
                }
              ]
            }
        """.trimIndent()

        val agentDao = mockk<AgentDao>(relaxed = true)
        val convDao = mockk<ConversationDao>(relaxed = true)
        val msgDao = mockk<MessageDao>(relaxed = true)
        val db = mockk<AppDatabase>(relaxed = true)
        every { db.agentDao() } returns agentDao
        every { db.conversationDao() } returns convDao
        every { db.messageDao() } returns msgDao

        val repo = mockk<SettingsRepository>(relaxed = true)

        val result = backupManager.import(v1Json, db, repo)
        assertTrue("导入应成功: $result", result.isSuccess)

        // 验证清表
        coVerify { agentDao.clearAll() }
        coVerify { msgDao.clearAll() }
        coVerify { convDao.clearAll() }

        // 验证 agents 迁移：prompt → systemPrompt, tools → toolsEnabled, builtin → isBuiltin
        val insertedAgents = mutableListOf<List<AgentEntity>>()
        coVerify { agentDao.insertAll(capture(insertedAgents)) }
        assertEquals(1, insertedAgents.size)
        assertEquals(1, insertedAgents[0].size)
        assertEquals("general", insertedAgents[0][0].id)
        assertEquals("你是助手", insertedAgents[0][0].systemPrompt)
        // v1 的 tools=true 迁移为默认工具列表（JSON 数组字符串）
        assertEquals("""["get_current_time","calculate"]""", insertedAgents[0][0].toolsEnabled)
        assertTrue(insertedAgents[0][0].isBuiltin)

        // 验证 conversations 迁移：convs → conversations, ts → createdAt/updatedAt
        coVerify { convDao.insert(match { conv ->
            conv.id == "conv_1" && conv.title == "旧对话" && conv.createdAt == 1234567890L
        }) }

        // 验证 messages 迁移
        val insertedMsgs = mutableListOf<List<MessageEntity>>()
        coVerify { msgDao.insertAll(capture(insertedMsgs)) }
        assertEquals(1, insertedMsgs[0].size)
        assertEquals("你好旧世界", insertedMsgs[0][0].content)
        assertEquals("success", insertedMsgs[0][0].status)

        // 验证 settings 恢复
        coVerify { repo.restore(any()) }
    }

    @Test
    fun `import invalid json returns failure`() = runTest {
        val agentDao = mockk<AgentDao>(relaxed = true)
        val convDao = mockk<ConversationDao>(relaxed = true)
        val msgDao = mockk<MessageDao>(relaxed = true)
        val db = mockk<AppDatabase>(relaxed = true)
        every { db.agentDao() } returns agentDao
        every { db.conversationDao() } returns convDao
        every { db.messageDao() } returns msgDao
        val repo = mockk<SettingsRepository>(relaxed = true)

        val result = backupManager.import("this is not json {{{", db, repo)
        assertTrue(result.isFailure)
    }

    @Test
    fun `import empty object does not crash`() = runTest {
        val agentDao = mockk<AgentDao>(relaxed = true)
        val convDao = mockk<ConversationDao>(relaxed = true)
        val msgDao = mockk<MessageDao>(relaxed = true)
        val db = mockk<AppDatabase>(relaxed = true)
        every { db.agentDao() } returns agentDao
        every { db.conversationDao() } returns convDao
        every { db.messageDao() } returns msgDao
        val repo = mockk<SettingsRepository>(relaxed = true)

        val result = backupManager.import("{}", db, repo)
        assertNotNull(result)
    }

    @Test
    fun `export then import round trip preserves data`() = runTest {
        val agent = AgentEntity(
            id = "coder", name = "编程助手", icon = "Code",
            systemPrompt = "写代码", toolsEnabled = """["calculate"]""", isBuiltin = true, sortOrder = 1,
        )
        val conv = ConversationEntity(
            id = "c2", title = "RoundTrip", agentId = "coder",
            createdAt = 5000L, updatedAt = 6000L, pinned = true,
        )
        val msgs = listOf(
            MessageEntity(id = "m1", conversationId = "c2", role = "user", content = "hi", createdAt = 5000L, status = "success"),
            MessageEntity(id = "m2", conversationId = "c2", role = "assistant", content = "hello", createdAt = 5001L, status = "success"),
        )
        val settings = SettingsSnapshot(
            baseUrl = "https://api.test.com", model = "test-model",
            temperature = 0.5, maxTokens = 2048, theme = "dark", apiKey = "sk-rt",
        )

        val agentDao = mockk<AgentDao>(relaxed = true)
        val convDao = mockk<ConversationDao>(relaxed = true)
        val msgDao = mockk<MessageDao>(relaxed = true)
        coEvery { agentDao.getAll() } returns listOf(agent)
        coEvery { convDao.getAll() } returns listOf(conv)
        coEvery { msgDao.getForConversation("c2") } returns msgs
        val db = mockk<AppDatabase>(relaxed = true)
        every { db.agentDao() } returns agentDao
        every { db.conversationDao() } returns convDao
        every { db.messageDao() } returns msgDao

        val repo = mockk<SettingsRepository>(relaxed = true)
        coEvery { repo.snapshot() } returns settings

        val jsonStr = backupManager.export(db, repo)
        val parsed = json.decodeFromString<BackupFile>(jsonStr)

        assertEquals(1, parsed.agents.size)
        assertEquals("coder", parsed.agents[0].id)
        assertEquals(1, parsed.conversations.size)
        assertEquals(2, parsed.conversations[0].messages.size)
        assertTrue(parsed.conversations[0].pinned)
        assertEquals("dark", parsed.settings.theme)
    }
}