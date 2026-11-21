package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kotha_settings")

enum class AppLanguageMode(val label: String, val bengaliLabel: String, val localeTag: String) {
    AUTO("Auto (BN + EN)", "স্বয়ংক্রিয় (বাংলা+EN)", "bn-BD"),
    BENGALI("বাংলা (Bengali)", "বাংলা", "bn-BD"),
    ENGLISH("English", "ইংরেজি", "en-US")
}

data class UserPreferences(
    val languageMode: AppLanguageMode = AppLanguageMode.AUTO,
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val autoSpeakReplies: Boolean = true,
    val autoExecuteActions: Boolean = true,
    val handsFreeContinuous: Boolean = false,
    val wakePhraseMode: Boolean = false,
    val offlineModeOnly: Boolean = false,
    val incognitoMode: Boolean = false,
    val accessibilityLargeMode: Boolean = false,
    val edgeGlowEnabled: Boolean = true,
    val edgeGlowAlwaysOn: Boolean = true,
    val activeProfileId: Long = 1L
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val AUTO_EXECUTE_ACTIONS = booleanPreferencesKey("auto_execute_actions")
        val HANDS_FREE = booleanPreferencesKey("hands_free")
        val WAKE_PHRASE = booleanPreferencesKey("wake_phrase")
        val OFFLINE_ONLY = booleanPreferencesKey("offline_only")
        val INCOGNITO = booleanPreferencesKey("incognito")
        val ACCESSIBILITY_LARGE = booleanPreferencesKey("accessibility_large")
        val EDGE_GLOW_ENABLED = booleanPreferencesKey("edge_glow_enabled")
        val EDGE_GLOW_ALWAYS_ON = booleanPreferencesKey("edge_glow_always_on")
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val langName = prefs[Keys.LANGUAGE_MODE] ?: AppLanguageMode.AUTO.name
        val langMode = runCatching { AppLanguageMode.valueOf(langName) }
            .getOrDefault(AppLanguageMode.AUTO)

        UserPreferences(
            languageMode = langMode,
            ttsSpeed = prefs[Keys.TTS_SPEED] ?: 1.0f,
            ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.0f,
            autoSpeakReplies = prefs[Keys.AUTO_SPEAK] ?: true,
            autoExecuteActions = prefs[Keys.AUTO_EXECUTE_ACTIONS] ?: true,
            handsFreeContinuous = prefs[Keys.HANDS_FREE] ?: false,
            wakePhraseMode = prefs[Keys.WAKE_PHRASE] ?: false,
            offlineModeOnly = prefs[Keys.OFFLINE_ONLY] ?: false,
            incognitoMode = prefs[Keys.INCOGNITO] ?: false,
            accessibilityLargeMode = prefs[Keys.ACCESSIBILITY_LARGE] ?: false,
            edgeGlowEnabled = prefs[Keys.EDGE_GLOW_ENABLED] ?: true,
            edgeGlowAlwaysOn = prefs[Keys.EDGE_GLOW_ALWAYS_ON] ?: true,
            activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID] ?: 1L
        )
    }

    suspend fun setLanguageMode(mode: AppLanguageMode) {
        context.dataStore.edit { it[Keys.LANGUAGE_MODE] = mode.name }
    }

    suspend fun setTtsSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.TTS_SPEED] = speed.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setTtsPitch(pitch: Float) {
        context.dataStore.edit { it[Keys.TTS_PITCH] = pitch.coerceIn(0.7f, 1.4f) }
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setAutoExecuteActions(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_EXECUTE_ACTIONS] = enabled }
    }

    suspend fun setHandsFreeContinuous(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HANDS_FREE] = enabled }
    }

    suspend fun setWakePhraseMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WAKE_PHRASE] = enabled }
    }

    suspend fun setOfflineModeOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OFFLINE_ONLY] = enabled }
    }

    suspend fun setIncognitoMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INCOGNITO] = enabled }
    }

    suspend fun setAccessibilityLargeMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ACCESSIBILITY_LARGE] = enabled }
    }

    suspend fun setEdgeGlowEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EDGE_GLOW_ENABLED] = enabled }
    }

    suspend fun setEdgeGlowAlwaysOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EDGE_GLOW_ALWAYS_ON] = enabled }
    }

    suspend fun setActiveProfileId(profileId: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = profileId }
    }
}
