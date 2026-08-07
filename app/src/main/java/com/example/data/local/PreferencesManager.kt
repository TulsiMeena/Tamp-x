package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled")
        val AUTO_REFRESH_INTERVAL = intPreferencesKey("auto_refresh_interval")
        val PUSH_NOTIFICATIONS = booleanPreferencesKey("push_notifications")
        val AUTO_AI_SUMMARIZE = booleanPreferencesKey("auto_ai_summarize")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val autoRefreshEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_REFRESH_ENABLED] ?: true
    }

    val autoRefreshInterval: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[AUTO_REFRESH_INTERVAL] ?: 10
    }

    val pushNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PUSH_NOTIFICATIONS] ?: true
    }

    val autoAiSummarize: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_AI_SUMMARIZE] ?: true
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "SYSTEM"
    }

    suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_REFRESH_ENABLED] = enabled }
    }

    suspend fun setAutoRefreshInterval(intervalSeconds: Int) {
        context.dataStore.edit { prefs -> prefs[AUTO_REFRESH_INTERVAL] = intervalSeconds }
    }

    suspend fun setPushNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PUSH_NOTIFICATIONS] = enabled }
    }

    suspend fun setAutoAiSummarize(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_AI_SUMMARIZE] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[THEME_MODE] = mode }
    }
}
