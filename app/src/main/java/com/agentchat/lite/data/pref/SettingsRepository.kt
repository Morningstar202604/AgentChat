package com.agentchat.lite.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agentchat_settings")

/**
 * 基于 DataStore Preferences 的设置项封装。
 *
 * API Key 在存储前经 [CryptoManager] 用 Android Keystore 加密，
 * 读取时自动解密。其余设置项明文存储。
 *
 * 默认值与 v1.0.0 app.js 对齐：
 * baseUrl=https://api.deepseek.com, model=deepseek-chat,
 * temperature=0.7, maxTokens=4096。
 */
class SettingsRepository(private val context: Context) {

    private val crypto = CryptoManager()

    val baseUrl: Flow<String> = context.dataStore.data.map { it[Keys.BASE_URL] ?: DEFAULT_BASE_URL }
    val model: Flow<String> = context.dataStore.data.map { it[Keys.MODEL] ?: DEFAULT_MODEL }
    val temperature: Flow<Double> = context.dataStore.data.map { it[Keys.TEMPERATURE] ?: 0.7 }
    val maxTokens: Flow<Int> = context.dataStore.data.map { it[Keys.MAX_TOKENS] ?: 4096 }
    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "auto" }
    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "zh" }
    val selectedAgentId: Flow<String> = context.dataStore.data.map { it[Keys.SELECTED_AGENT_ID] ?: "general" }

    /** 读取解密后的 API Key；未设置时返回 null。 */
    suspend fun getApiKey(): String? {
        val encrypted = context.dataStore.data.first()[Keys.API_KEY_ENCRYPTED] ?: return null
        return runCatching { crypto.decrypt(encrypted) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** 加密并存储 API Key；传入空字符串则清除。 */
    suspend fun setApiKey(key: String) {
        context.dataStore.edit { prefs ->
            if (key.isEmpty()) {
                prefs.remove(Keys.API_KEY_ENCRYPTED)
            } else {
                prefs[Keys.API_KEY_ENCRYPTED] = crypto.encrypt(key)
            }
        }
    }

    suspend fun setBaseUrl(value: String) = context.dataStore.edit { it[Keys.BASE_URL] = value }
    suspend fun setModel(value: String) = context.dataStore.edit { it[Keys.MODEL] = value }
    suspend fun setTemperature(value: Double) = context.dataStore.edit { it[Keys.TEMPERATURE] = value }
    suspend fun setMaxTokens(value: Int) = context.dataStore.edit { it[Keys.MAX_TOKENS] = value }
    suspend fun setTheme(value: String) = context.dataStore.edit { it[Keys.THEME] = value }
    suspend fun setLanguage(value: String) = context.dataStore.edit { it[Keys.LANGUAGE] = value }
    suspend fun setSelectedAgentId(value: String) = context.dataStore.edit { it[Keys.SELECTED_AGENT_ID] = value }

    /** 一次性读取当前所有设置快照，供备份导出使用。 */
    suspend fun snapshot(): SettingsSnapshot {
        val prefs = context.dataStore.data.first()
        return SettingsSnapshot(
            baseUrl = prefs[Keys.BASE_URL] ?: DEFAULT_BASE_URL,
            model = prefs[Keys.MODEL] ?: DEFAULT_MODEL,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 4096,
            theme = prefs[Keys.THEME] ?: "auto",
            apiKey = getApiKey().orEmpty(),
        )
    }

    /** 导入时覆盖设置（备份恢复）。apiKey 为空则不覆盖。 */
    suspend fun restore(snapshot: SettingsSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = snapshot.baseUrl
            prefs[Keys.MODEL] = snapshot.model
            prefs[Keys.TEMPERATURE] = snapshot.temperature
            prefs[Keys.MAX_TOKENS] = snapshot.maxTokens
            prefs[Keys.THEME] = snapshot.theme
        }
        if (snapshot.apiKey.isNotEmpty()) {
            setApiKey(snapshot.apiKey)
        }
    }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY_ENCRYPTED = stringPreferencesKey("api_key_encrypted")
        val MODEL = stringPreferencesKey("model")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val SELECTED_AGENT_ID = stringPreferencesKey("selected_agent_id")
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}

/**
 * 设置项快照，用于备份导出/导入。
 */
data class SettingsSnapshot(
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val theme: String,
    val apiKey: String,
)
