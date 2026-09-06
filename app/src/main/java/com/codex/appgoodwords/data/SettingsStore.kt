package com.codex.appgoodwords.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
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
        /** AI 열쇠는 이 기기에만 둡니다. 동기화 스냅샷과 백업 파일에는 넣지 않습니다. */
        val aiApiKey = stringPreferencesKey("ai_api_key")
        /** Claude 열쇠. OpenAI 열쇠와 자리를 나눠 두어야 공급자를 바꿔도 지워지지 않습니다. */
        val aiAnthropicKey = stringPreferencesKey("ai_anthropic_key")
        val aiProvider = stringPreferencesKey("ai_provider")
        val aiModel = stringPreferencesKey("ai_model")
        /**
         * 복사해 둔 물음이 어느 기간의 것인지.
         *
         * 손으로 물어보는 길에서 씁니다. 앱을 나가 채팅창에 붙여넣고 오는 사이에 앱이 꺼질 수
         * 있어, 화면 상태가 아니라 여기에 적어 둡니다. 날짜와 기간만 있으면 그때의 구간을
         * 그대로 다시 계산할 수 있습니다.
         */
        val aiPromptPeriod = stringPreferencesKey("ai_prompt_period")
        val aiPromptDate = stringPreferencesKey("ai_prompt_date")
        val aiSchedule = stringPreferencesKey("ai_schedule")
        val aiHour = intPreferencesKey("ai_hour")
        val aiMinute = intPreferencesKey("ai_minute")
        val aiIncludeDiaryBody = booleanPreferencesKey("ai_include_diary_body")
        val aiLastRunAt = longPreferencesKey("ai_last_run_at")
        val aiLastError = stringPreferencesKey("ai_last_error")
        val aiNudgeEnabled = booleanPreferencesKey("ai_nudge_enabled")
        val aiLastNudgedAt = longPreferencesKey("ai_last_nudged_at")
        /**
         * 하루의 축으로 삼을 걸음들. 이름을 쉼표로 이어 둡니다.
         *
         * 이 기기에만 둡니다. 기기를 나눠 쓰는 사람은 폰에서 하루를 밟고 태블릿에서는 돌아보기만
         * 하기도 합니다. 한쪽에서 고른 축이 다른 쪽까지 따라가면 오히려 성가십니다.
         */
        val dailySteps = stringPreferencesKey("daily_steps")

        /** 앱 잠금. 비밀번호는 담지 않습니다. 기기에 있는 잠금을 빌려 씁니다. */
        val appLockEnabled = booleanPreferencesKey("app_lock_enabled")
        /**
         * 일기만 따로 거는 잠금.
         *
         * 앱 잠금과 별개입니다. 앱은 열어 두고 쓰면서도 일기는 가리고 싶을 수 있습니다.
         * 폰을 잠깐 건네줄 때 나머지는 보여 줘도 되지만 일기는 아닙니다.
         */
        val diaryLockEnabled = booleanPreferencesKey("diary_lock_enabled")
        val lastSyncError = stringPreferencesKey("last_sync_error")
        /** 서버에서 마지막으로 본 리비전 번호. 다음 동기화에서 "이 뒤에 바뀐 것만" 달라고 씁니다. */
        val serverRev = longPreferencesKey("server_rev")
        /** 이 시각 뒤에 고친 레코드만 서버로 보냅니다. */
        val lastPushAt = longPreferencesKey("last_push_at")
        /** 서버가 통째로 교체될 때마다 오르는 세대 번호. 다르면 리비전 번호를 믿을 수 없습니다. */
        val serverEpoch = longPreferencesKey("server_epoch")
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

    val aiFeedbackSettingsFlow: Flow<AiFeedbackSettings> = context.dataStore.data.map { preferences ->
        AiFeedbackSettings(
            provider = preferences[Keys.aiProvider].orEmpty().ifBlank { AiProvider.OPENAI.name },
            // 예전 열쇠 자리는 그대로 OpenAI 열쇠로 읽습니다. 이미 넣어 둔 사람이 다시 넣지 않아야 합니다.
            openAiKey = preferences[Keys.aiApiKey].orEmpty(),
            anthropicKey = preferences[Keys.aiAnthropicKey].orEmpty(),
            model = preferences[Keys.aiModel].orEmpty(),
            schedule = preferences[Keys.aiSchedule].orEmpty(),
            hour = preferences[Keys.aiHour] ?: AiFeedbackSettings.DEFAULT_HOUR,
            minute = preferences[Keys.aiMinute] ?: 0,
            includeDiaryBody = preferences[Keys.aiIncludeDiaryBody] ?: false,
            lastRunAt = preferences[Keys.aiLastRunAt] ?: 0L,
            lastError = preferences[Keys.aiLastError].orEmpty(),
            // 알림은 기본으로 켭니다. 오래 뜸해졌다는 것은 알려 주는 편이 낫고, 값도 들지 않습니다.
            nudgeEnabled = preferences[Keys.aiNudgeEnabled] ?: true,
            lastNudgedAt = preferences[Keys.aiLastNudgedAt] ?: 0L
        )
    }

    val dailyStepsFlow: Flow<List<DailyStep>> = context.dataStore.data.map { preferences ->
        DailyStep.parse(preferences[Keys.dailySteps].orEmpty())
    }

    /**
     * 하루의 축을 바꿉니다.
     *
     * 하나도 안 남기면 카드가 뜻을 잃으므로 그때는 저장하지 않고 되돌립니다. 연속 날수는
     * 어디에도 적혀 있지 않고 기록에서 다시 세므로, 바꾸는 순간 지난 날수도 새 기준이 됩니다.
     */
    suspend fun setDailySteps(steps: List<DailyStep>) {
        val kept = steps.distinct().ifEmpty { DailyStep.DEFAULTS }
        context.dataStore.edit { preferences ->
            preferences[Keys.dailySteps] = DailyStep.store(kept)
        }
    }

    val appLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.appLockEnabled] ?: false
    }

    val diaryLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[Keys.diaryLockEnabled] ?: false
    }

    suspend fun setDiaryLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[Keys.diaryLockEnabled] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[Keys.appLockEnabled] = enabled }
    }

    val syncStatusFlow: Flow<SyncStatus> = context.dataStore.data.map { preferences ->
        SyncStatus(
            lastSyncAt = preferences[Keys.lastSyncAt] ?: 0L,
            lastError = preferences[Keys.lastSyncError].orEmpty()
        )
    }

    suspend fun getSettings(): ReminderSettings = settingsFlow.first()

    suspend fun getServerSyncSettings(): ServerSyncSettings = serverSyncSettingsFlow.first()

    suspend fun getAiFeedbackSettings(): AiFeedbackSettings = aiFeedbackSettingsFlow.first()

    suspend fun updateAiFeedbackSettings(settings: AiFeedbackSettings) {
        context.dataStore.edit { preferences ->
            preferences[Keys.aiProvider] = settings.activeProvider.name
            preferences[Keys.aiApiKey] = settings.openAiKey.trim()
            preferences[Keys.aiAnthropicKey] = settings.anthropicKey.trim()
            // 빈 값은 "그 공급자의 기본"이라는 뜻이라 그대로 둡니다.
            preferences[Keys.aiModel] = settings.model.trim()
            preferences[Keys.aiSchedule] = settings.schedule
            preferences[Keys.aiHour] = settings.hour.coerceIn(0, 23)
            preferences[Keys.aiMinute] = settings.minute.coerceIn(0, 59)
            preferences[Keys.aiIncludeDiaryBody] = settings.includeDiaryBody
            preferences[Keys.aiNudgeEnabled] = settings.nudgeEnabled
        }
    }

    /**
     * 복사해 둔 물음.
     *
     * 붙여넣은 답을 저장할 때 "언제부터 언제까지를 보고 쓴 글인지"가 있어야 합니다.
     * 화면 상태로 들고 있으면, 채팅 앱에 다녀오는 사이에 앱이 꺼졌을 때 사라집니다.
     */
    val pendingGrowthPromptFlow: Flow<PendingGrowthPrompt?> = context.dataStore.data.map { preferences ->
        val date = preferences[Keys.aiPromptDate].orEmpty()
        val copiedOn = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@map null
        PendingGrowthPrompt(
            period = ReportPeriod.of(preferences[Keys.aiPromptPeriod]),
            copiedOn = copiedOn
        )
    }

    suspend fun getPendingGrowthPrompt(): PendingGrowthPrompt? = pendingGrowthPromptFlow.first()

    suspend fun setPendingGrowthPrompt(period: ReportPeriod, copiedOn: LocalDate) {
        context.dataStore.edit { preferences ->
            preferences[Keys.aiPromptPeriod] = period.name
            preferences[Keys.aiPromptDate] = copiedOn.toString()
        }
    }

    /** 답을 받아 적었으면 지웁니다. 남겨 두면 다음에 붙여넣는 답까지 옛 구간으로 들어갑니다. */
    suspend fun clearPendingGrowthPrompt() {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.aiPromptPeriod)
            preferences.remove(Keys.aiPromptDate)
        }
    }

    /** 만들어 본 결과. 배경에서 돌다 실패하면 화면에 뜨지 않아 여기에 남깁니다. */
    suspend fun recordAiFeedbackResult(runAt: Long, error: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.aiLastRunAt] = runAt
            preferences[Keys.aiLastError] = error
        }
    }

    /** 오래 뜸하다고 알린 시각. 한 번 알린 뒤에는 이 값을 보고 쉽니다. */
    suspend fun recordGrowthNudge(nudgedAt: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.aiLastNudgedAt] = nudgedAt
        }
    }

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
            val url = settings.serverUrl.trim()
            // 다른 서버의 리비전 번호를 그대로 쓰면 안 바뀐 것처럼 보여 아무것도 못 받습니다.
            if (preferences[Keys.serverUrl] != url) {
                preferences[Keys.serverRev] = 0L
                preferences[Keys.serverEpoch] = 0L
                preferences[Keys.lastPushAt] = 0L
            }
            preferences[Keys.serverUrl] = url
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

    /**
     * 서버와 어디까지 맞췄는지 적어 둡니다.
     *
     * 0이면 아직 한 번도 못 맞춘 것이라, 다음 동기화는 전체를 주고받습니다.
     * 서버 주소를 바꾸면 다른 서버의 번호를 그대로 쓸 수 없으므로 [clearSyncCursor]로 지웁니다.
     */
    suspend fun getSyncCursor(): SyncCursor {
        val preferences = context.dataStore.data.first()
        return SyncCursor(
            serverRev = preferences[Keys.serverRev] ?: 0L,
            serverEpoch = preferences[Keys.serverEpoch] ?: 0L,
            lastPushAt = preferences[Keys.lastPushAt] ?: 0L
        )
    }

    suspend fun updateSyncCursor(cursor: SyncCursor) {
        context.dataStore.edit { preferences ->
            preferences[Keys.serverRev] = cursor.serverRev
            preferences[Keys.serverEpoch] = cursor.serverEpoch
            preferences[Keys.lastPushAt] = cursor.lastPushAt
        }
    }

    suspend fun clearSyncCursor() = updateSyncCursor(SyncCursor())
}

/** 서버와 어디까지 맞췄는지. */
data class SyncCursor(
    /** 서버에서 마지막으로 본 리비전 번호. */
    val serverRev: Long = 0L,
    /** 그 번호가 어느 세대의 것인지. 서버가 통째로 교체되면 세대가 올라 번호는 뜻을 잃습니다. */
    val serverEpoch: Long = 0L,
    /**
     * 이 시각까지의 변경은 이미 보냈습니다.
     *
     * 이 기기의 시계로만 재는 값입니다. 다른 기기의 시계와 비교하지 않으므로 시차가 있어도 안전합니다.
     */
    val lastPushAt: Long = 0L
) {
    val isFresh: Boolean
        get() = serverRev <= 0L
}
