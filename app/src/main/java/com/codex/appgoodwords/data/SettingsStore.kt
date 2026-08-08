package com.codex.appgoodwords.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_good_words_settings")

class SettingsStore(
    private val context: Context
) {
    private object Keys {
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val intervalMinutes = intPreferencesKey("interval_minutes")
        val legacyIntervalHours = intPreferencesKey("interval_hours")
        val preferredHour = intPreferencesKey("preferred_hour")
        val preferredMinute = intPreferencesKey("preferred_minute")
        val repeatEndHour = intPreferencesKey("repeat_end_hour")
        val repeatEndMinute = intPreferencesKey("repeat_end_minute")
        val categoryFilter = stringPreferencesKey("category_filter")
        val showOnLaunch = booleanPreferencesKey("show_on_launch")
        val lockScreenVisible = booleanPreferencesKey("lock_screen_visible")
        val notificationSoundEnabled = booleanPreferencesKey("notification_sound_enabled")
        val dailySummaryEnabled = booleanPreferencesKey("daily_summary_enabled")
        val summaryHour = intPreferencesKey("summary_hour")
        val summaryMinute = intPreferencesKey("summary_minute")
        val serverUrl = stringPreferencesKey("server_url")
        val serverApiKey = stringPreferencesKey("server_api_key")
        val widgetContentId = longPreferencesKey("widget_content_id")
        val settingsUpdatedAt = longPreferencesKey("settings_updated_at")
        val autoSyncEnabled = booleanPreferencesKey("auto_sync_enabled")
        val autoSyncIntervalHours = intPreferencesKey("auto_sync_interval_hours")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
    }

    val settingsFlow: Flow<ReminderSettings> = context.dataStore.data.map { preferences ->
        val intervalMinutes = preferences[Keys.intervalMinutes]
            ?: ((preferences[Keys.legacyIntervalHours] ?: 6) * 60)

        ReminderSettings(
            remindersEnabled = preferences[Keys.remindersEnabled] ?: true,
            intervalMinutes = intervalMinutes.coerceAtLeast(ReminderSettings.MIN_INTERVAL_MINUTES),
            preferredHour = preferences[Keys.preferredHour] ?: 9,
            preferredMinute = preferences[Keys.preferredMinute] ?: 0,
            repeatEndHour = preferences[Keys.repeatEndHour] ?: 22,
            repeatEndMinute = preferences[Keys.repeatEndMinute] ?: 0,
            categoryFilter = preferences[Keys.categoryFilter].orEmpty(),
            showOnLaunch = preferences[Keys.showOnLaunch] ?: true,
            lockScreenVisible = preferences[Keys.lockScreenVisible] ?: true,
            notificationSoundEnabled = preferences[Keys.notificationSoundEnabled] ?: true,
            dailySummaryEnabled = preferences[Keys.dailySummaryEnabled] ?: true,
            summaryHour = preferences[Keys.summaryHour] ?: 21,
            summaryMinute = preferences[Keys.summaryMinute] ?: 0
        )
    }

    val serverSyncSettingsFlow: Flow<ServerSyncSettings> = context.dataStore.data.map { preferences ->
        ServerSyncSettings(
            serverUrl = preferences[Keys.serverUrl].orEmpty(),
            apiKey = preferences[Keys.serverApiKey].orEmpty(),
            autoSyncEnabled = preferences[Keys.autoSyncEnabled] ?: false,
            autoSyncIntervalHours = preferences[Keys.autoSyncIntervalHours]
                ?: ServerSyncSettings.DEFAULT_INTERVAL_HOURS
        )
    }

    val syncStatusFlow: Flow<SyncStatus> = context.dataStore.data.map { preferences ->
        SyncStatus(
            lastSyncAt = preferences[Keys.lastSyncAt] ?: 0L,
            lastError = preferences[Keys.lastSyncError].orEmpty()
        )
    }

    suspend fun getSettings(): ReminderSettings = settingsFlow.first()

    suspend fun getServerSyncSettings(): ServerSyncSettings = serverSyncSettingsFlow.first()

    /** 설정은 레코드가 아니라 한 덩어리여서, 병합에서 최근에 손댄 쪽을 고르려면 시각이 필요하다. */
    suspend fun getSettingsUpdatedAt(): Long = context.dataStore.data.first()[Keys.settingsUpdatedAt] ?: 0L

    /** 서버에서 받은 설정을 되쓸 때는 원래 시각을 유지해야 병합이 무한히 뒤집히지 않는다. */
    suspend fun updateSettings(settings: ReminderSettings, updatedAt: Long) {
        updateSettings(settings)
        context.dataStore.edit { preferences ->
            preferences[Keys.settingsUpdatedAt] = updatedAt
        }
    }

    suspend fun updateSettings(settings: ReminderSettings) {
        context.dataStore.edit { preferences ->
            preferences[Keys.settingsUpdatedAt] = System.currentTimeMillis()
            preferences[Keys.remindersEnabled] = settings.remindersEnabled
            preferences[Keys.intervalMinutes] = settings.effectiveIntervalMinutes
            preferences[Keys.preferredHour] = settings.preferredHour
            preferences[Keys.preferredMinute] = settings.preferredMinute
            preferences[Keys.repeatEndHour] = settings.repeatEndHour
            preferences[Keys.repeatEndMinute] = settings.repeatEndMinute
            preferences[Keys.categoryFilter] = settings.categoryFilter
            preferences[Keys.showOnLaunch] = settings.showOnLaunch
            preferences[Keys.lockScreenVisible] = settings.lockScreenVisible
            preferences[Keys.notificationSoundEnabled] = settings.notificationSoundEnabled
            preferences[Keys.dailySummaryEnabled] = settings.dailySummaryEnabled
            preferences[Keys.summaryHour] = settings.summaryHour
            preferences[Keys.summaryMinute] = settings.summaryMinute
        }
    }

    /** 위젯이 지금 보여주는 항목. 0이면 아직 고른 항목이 없다는 뜻이다. */
    suspend fun getWidgetContentId(): Long = context.dataStore.data.first()[Keys.widgetContentId] ?: 0L

    suspend fun setWidgetContentId(itemId: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.widgetContentId] = itemId
        }
    }

    suspend fun updateServerSyncSettings(settings: ServerSyncSettings) {
        context.dataStore.edit { preferences ->
            preferences[Keys.serverUrl] = settings.serverUrl.trim()
            preferences[Keys.serverApiKey] = settings.apiKey.trim()
            preferences[Keys.autoSyncEnabled] = settings.autoSyncEnabled
            preferences[Keys.autoSyncIntervalHours] = settings.effectiveIntervalHours
        }
    }

    suspend fun recordSyncResult(syncedAt: Long, error: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.lastSyncAt] = syncedAt
            preferences[Keys.lastSyncError] = error
        }
    }
}
