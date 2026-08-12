package com.codex.appgoodwords.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codex.appgoodwords.data.AppContainer
import com.codex.appgoodwords.data.AppDataSnapshot
import com.codex.appgoodwords.data.AppImportResult
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.DiaryDraft
import com.codex.appgoodwords.data.DiaryEntity
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.data.LinkMetadata
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.RoutineDraft
import com.codex.appgoodwords.data.ServerConnectionInfo
import com.codex.appgoodwords.data.ServerSyncResult
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.StatsCalculator
import com.codex.appgoodwords.data.SyncBackup
import com.codex.appgoodwords.data.SyncBackupKind
import com.codex.appgoodwords.data.SyncStatus
import com.codex.appgoodwords.data.TodoDraft
import com.codex.appgoodwords.data.TodoEntity
import com.codex.appgoodwords.work.AppNotifications
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel(
    private val container: AppContainer
) : ViewModel() {
    val allItems = container.repository.observeAllContent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyEvents = container.repository.observeExposureEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routines = container.repository.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val diaries = container.repository.observeDiaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todos = container.repository.observeTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routineChecks = container.repository.observeRoutineChecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routineMemos = container.repository.observeRoutineMemos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats = combine(historyEvents, allItems, routineChecks) { events, items, checks ->
        StatsCalculator.build(
            events = events,
            items = items,
            routineChecks = checks,
            today = LocalDate.now()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatsCalculator.build(
            events = emptyList(),
            items = emptyList(),
            routineChecks = emptyList(),
            today = LocalDate.now()
        )
    )

    val categories = allItems
        .map { items ->
            items.map { it.category.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = container.settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())

    val serverSyncSettings = container.settingsStore.serverSyncSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerSyncSettings())

    val syncStatus = container.settingsStore.syncStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    private val _openItemRequest = MutableStateFlow<Long?>(null)
    val openItemRequest: StateFlow<Long?> = _openItemRequest.asStateFlow()

    private val _confirmedTodayIds = MutableStateFlow<Set<Long>>(emptySet())
    val confirmedTodayIds: StateFlow<Set<Long>> = _confirmedTodayIds.asStateFlow()

    private val _syncBackups = MutableStateFlow<List<SyncBackup>>(emptyList())
    val syncBackups: StateFlow<List<SyncBackup>> = _syncBackups.asStateFlow()

    private val _syncBackupDirectory = MutableStateFlow("")
    val syncBackupDirectory: StateFlow<String> = _syncBackupDirectory.asStateFlow()

    private val routineDayRange = MutableStateFlow(container.repository.todayRangeMillis())
    val routineTodayCounts = combine(routineChecks, routineDayRange) { checks, range ->
        val (start, end) = range
        checks
            .filter { it.checkedAt in start..end }
            .groupingBy { it.routineId }
            .eachCount()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            container.repository.seedDefaultsIfNeeded()
            _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
            _syncBackupDirectory.value = container.syncBackupStore.directoryPath()
            reloadSyncBackups()
            // 앱을 다시 깔거나 기기를 껐다 켜면 예약이 사라질 수 있어 시작할 때 맞춰 둔다.
            container.reminderScheduler.syncAutoSync(container.settingsStore.getServerSyncSettings())

            val currentSettings = container.settingsStore.getSettings()
            if (currentSettings.showOnLaunch) {
                container.repository.pickFeaturedContent(
                    category = currentSettings.categoryFilter,
                    trigger = ExposureTrigger.APP_LAUNCH
                )
            }
        }

        // 배경 동기화는 화면 밖에서 데이터를 바꿉니다.
        // 목록은 Room이 흘려 주지만 백업 파일과 오늘 읽음 표시는 여기서 직접 읽으므로,
        // 동기화 결과가 기록될 때 함께 다시 읽지 않으면 앱을 껐다 켤 때까지 옛 값이 남습니다.
        viewModelScope.launch {
            container.settingsStore.syncStatusFlow
                .distinctUntilChanged()
                // 첫 값은 시작할 때 이미 읽었습니다.
                .drop(1)
                .collect {
                    reloadSyncBackups()
                    _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
                }
        }
    }

    fun handleSharedText(text: String?) {
        _sharedText.value = text?.trim()?.takeIf { it.isNotBlank() }
    }

    fun clearSharedText() {
        _sharedText.value = null
    }

    fun handleOpenItemRequest(
        itemId: Long,
        markConfirmed: Boolean,
        recordView: Boolean
    ) {
        viewModelScope.launch {
            if (recordView) {
                container.repository.recordContentViewed(
                    contentItemId = itemId,
                    trigger = ExposureTrigger.NOTIFICATION_TAP
                )
            }
            if (markConfirmed) {
                val confirmed = container.repository.markContentConfirmed(
                    contentItemId = itemId,
                    trigger = ExposureTrigger.NOTIFICATION_TAP
                )
                if (confirmed) {
                    _confirmedTodayIds.value = _confirmedTodayIds.value + itemId
                }
            }
            _openItemRequest.value = itemId
        }
    }

    fun consumeOpenItemRequest() {
        _openItemRequest.value = null
    }

    fun refreshConfirmedToday() {
        viewModelScope.launch {
            _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        }
    }

    fun refreshRoutineToday() {
        routineDayRange.value = container.repository.todayRangeMillis()
    }

    fun refreshFeatured() {
        viewModelScope.launch {
            val currentSettings = container.settingsStore.getSettings()
            container.repository.pickFeaturedContent(
                category = currentSettings.categoryFilter,
                trigger = ExposureTrigger.MANUAL_REFRESH
            )
        }
    }

    fun recordContentViewed(
        itemId: Long,
        trigger: ExposureTrigger = ExposureTrigger.DETAIL_OPEN
    ) {
        viewModelScope.launch {
            container.repository.recordContentViewed(
                contentItemId = itemId,
                trigger = trigger
            )
        }
    }

    fun toggleFavorite(itemId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            container.repository.setFavorite(itemId, isFavorite)
        }
    }

    fun updateSettings(updated: ReminderSettings) {
        viewModelScope.launch {
            val normalized = updated.copy(intervalMinutes = updated.effectiveIntervalMinutes)
            container.settingsStore.updateSettings(normalized)
            container.reminderScheduler.sync(normalized)
        }
    }

    fun updateServerSyncSettings(updated: ServerSyncSettings) {
        viewModelScope.launch {
            container.settingsStore.updateServerSyncSettings(updated)
            container.reminderScheduler.syncAutoSync(updated)
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            val currentSettings = container.settingsStore.getSettings()
            val item = container.repository.getRandomContent(currentSettings.categoryFilter)
            val routine = container.repository.getRandomReminderRoutine()
            val shouldShowRoutine = routine != null && (item == null || Random.nextBoolean())

            if (shouldShowRoutine && routine != null) {
                val todayCount = container.repository.getTodayRoutineCheckCount(routine.id)
                AppNotifications.showRoutineNotification(
                    context = container.appContext,
                    routine = routine,
                    todayCount = todayCount,
                    settings = currentSettings
                )
            } else if (item != null) {
                container.repository.recordContentSurfaced(
                    item = item,
                    trigger = ExposureTrigger.TEST_NOTIFICATION
                )
                AppNotifications.showContentNotification(container.appContext, item, currentSettings)
            }
        }
    }

    suspend fun exportData(uri: Uri): Result<Int> = runCatching {
        container.appDataExporter.export(
            uri = uri,
            items = allItems.value,
            events = historyEvents.value,
            routines = routines.value,
            routineChecks = routineChecks.value,
            routineMemos = routineMemos.value,
            settings = container.settingsStore.getSettings()
        )
    }

    suspend fun importData(uri: Uri): Result<AppImportResult> = runCatching {
        val result = container.appDataImporter.import(uri)
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        result
    }

    /** 서버 없이 다른 기기의 내보내기 파일과 합칩니다. 교체가 아니라 병합입니다. */
    suspend fun mergeFromFile(uri: Uri): Result<ServerSyncResult> = runCatching {
        // 되돌릴 수 있도록 합치기 전 상태를 남긴다.
        val backup = container.syncBackupStore.save(SyncBackupKind.BEFORE_MERGE, currentSnapshot())
        val result = container.appDataImporter.mergeFromFile(uri, currentSnapshot())
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        reloadSyncBackups()
        ServerSyncResult(counts = result, backup = backup)
    }

    suspend fun testServerConnection(): Result<ServerConnectionInfo> = runCatching {
        container.serverSyncClient.testConnection(container.settingsStore.getServerSyncSettings())
    }

    suspend fun syncWithServer(): Result<ServerSyncResult> = runCatching {
        // 배경 동기화와 같은 절차를 쓴다.
        val result = container.syncCoordinator.merge(SyncBackupKind.BEFORE_MERGE)
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        reloadSyncBackups()
        result
    }

    suspend fun uploadDataToServer(): Result<ServerSyncResult> = runCatching {
        val syncSettings = container.settingsStore.getServerSyncSettings()
        // 서버 데이터를 통째로 덮어쓰기 전에 현재 서버 상태를 백업한다.
        val serverSnapshotBefore = container.serverSyncClient.downloadSnapshot(syncSettings)
        val backup = container.syncBackupStore.save(SyncBackupKind.BEFORE_UPLOAD, serverSnapshotBefore)
        val serverSnapshot = container.serverSyncClient.uploadSnapshot(
            settings = syncSettings,
            snapshot = currentSnapshot()
        )
        reloadSyncBackups()
        ServerSyncResult(
            counts = AppImportResult(
                itemCount = serverSnapshot.items.size,
                eventCount = serverSnapshot.events.size,
                routineCount = serverSnapshot.routines.size,
                routineCheckCount = serverSnapshot.routineChecks.size,
                routineMemoCount = serverSnapshot.routineMemos.size
            ),
            backup = backup
        )
    }

    suspend fun downloadDataFromServer(): Result<ServerSyncResult> = runCatching {
        val syncSettings = container.settingsStore.getServerSyncSettings()
        // 내려받기에 실패하면 기기 데이터를 건드리지 않도록 스냅샷을 먼저 받는다.
        val snapshot = container.serverSyncClient.downloadSnapshot(syncSettings)
        val backup = container.syncBackupStore.save(SyncBackupKind.BEFORE_DOWNLOAD, currentSnapshot())
        val result = container.appDataImporter.importSnapshot(snapshot)
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        reloadSyncBackups()
        ServerSyncResult(counts = result, backup = backup)
    }

    suspend fun restoreSyncBackup(backup: SyncBackup): Result<AppImportResult> = runCatching {
        val snapshot = container.syncBackupStore.load(backup)
        container.syncBackupStore.save(SyncBackupKind.BEFORE_RESTORE, currentSnapshot())
        val result = container.appDataImporter.importSnapshot(snapshot)
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        reloadSyncBackups()
        result
    }

    // ---- 일기 ----

    suspend fun getDiary(id: Long): DiaryEntity? = container.repository.getDiaryById(id)

    suspend fun saveDiary(draft: DiaryDraft): Result<Unit> = runCatching {
        container.repository.saveDiary(draft)
        Unit
    }

    suspend fun deleteDiary(id: Long): Result<Unit> = runCatching {
        container.repository.deleteDiary(id)
    }

    // ---- 할 일 ----

    suspend fun getTodo(id: Long): TodoEntity? = container.repository.getTodoById(id)

    /** 저장한 뒤 알람을 다시 겁니다. 시각을 바꿨는데 예약이 그대로면 옛 시각에 울립니다. */
    suspend fun saveTodo(draft: TodoDraft): Result<Unit> = runCatching {
        val saved = container.repository.saveTodo(draft)
        container.todoAlarmScheduler.sync(saved)
    }

    suspend fun toggleTodoDone(id: Long): Result<Unit> = runCatching {
        // 끝낸 일의 알람이 그대로 울리면 이미 한 일을 다시 하라고 하는 셈이다.
        container.repository.toggleTodoDone(id)?.let(container.todoAlarmScheduler::sync)
        Unit
    }

    suspend fun deleteTodo(id: Long): Result<Unit> = runCatching {
        container.repository.getTodoById(id)?.let(container.todoAlarmScheduler::cancel)
        container.repository.deleteTodo(id)
    }

    /** 정확한 알람 권한이 없으면 알람이 늦게 울릴 수 있어 화면에서 알려 줘야 합니다. */
    fun canScheduleExactAlarms(): Boolean = container.todoAlarmScheduler.canScheduleExact()

    fun exactAlarmSettingsIntent() = container.todoAlarmScheduler.exactAlarmSettingsIntent()

    private suspend fun reloadSyncBackups() {
        _syncBackups.value = container.syncBackupStore.list()
    }

    suspend fun resetTodayConfirmed(): Result<Int> = runCatching {
        val removedCount = container.repository.clearTodayConfirmed()
        _confirmedTodayIds.value = emptySet()
        removedCount
    }

    suspend fun resetViewCounts(): Result<Int> = runCatching {
        container.repository.resetViewCounts()
    }

    suspend fun saveRoutine(draft: RoutineDraft): Result<Unit> = runCatching {
        val normalized = draft.copy(
            title = draft.title.trim(),
            note = draft.note.trim(),
            category = draft.category.trim()
        )
        require(normalized.title.isNotBlank()) { "루틴 이름을 입력해 주세요." }
        container.repository.saveRoutine(normalized)
    }

    suspend fun deleteRoutine(routineId: Long): Result<Unit> = runCatching {
        container.repository.deleteRoutine(routineId)
    }

    suspend fun checkRoutine(routineId: Long): Result<Int> = runCatching {
        container.repository.markRoutineDone(routineId)
    }

    suspend fun saveRoutineMemo(routineId: Long, body: String): Result<Long> = runCatching {
        container.repository.saveRoutineMemo(routineId, body)
    }

    suspend fun deleteRoutineMemo(memoId: Long): Result<Int> = runCatching {
        container.repository.deleteRoutineMemo(memoId)
    }

    suspend fun deleteHistoryEvents(eventIds: Set<Long>): Result<Int> = runCatching {
        require(eventIds.isNotEmpty()) { "삭제할 이력이 없습니다." }
        val removedCount = container.repository.deleteExposureEvents(eventIds)
        _confirmedTodayIds.value = container.repository.getTodayConfirmedIds()
        removedCount
    }

    suspend fun deleteCategory(category: String): Result<Int> = runCatching {
        val normalized = category.trim()
        require(normalized.isNotBlank()) { "삭제할 카테고리가 없습니다." }

        val affected = container.repository.removeCategory(normalized)
        val currentSettings = container.settingsStore.getSettings()
        if (currentSettings.categoryFilter == normalized) {
            val updated = currentSettings.copy(categoryFilter = "")
            container.settingsStore.updateSettings(updated)
            container.reminderScheduler.sync(updated)
        }
        affected
    }

    suspend fun saveContent(draft: ContentDraft): Result<Unit> = runCatching {
        val normalized = normalizeDraft(draft)
        validate(normalized)
        container.repository.saveContent(normalized)
    }

    suspend fun deleteContent(itemId: Long): Result<Unit> = runCatching {
        container.repository.deleteContent(itemId)
        _confirmedTodayIds.value = _confirmedTodayIds.value - itemId
    }

    suspend fun toggleContentConfirmed(itemId: Long): Result<Boolean> {
        val previousConfirmedIds = _confirmedTodayIds.value
        val optimisticConfirmed = itemId !in previousConfirmedIds
        _confirmedTodayIds.value = applyConfirmedState(
            base = previousConfirmedIds,
            itemId = itemId,
            confirmed = optimisticConfirmed
        )

        val result = runCatching {
            container.repository.toggleContentConfirmed(
                contentItemId = itemId,
                trigger = ExposureTrigger.DETAIL_CHECK
            )
        }

        if (result.isSuccess) {
            _confirmedTodayIds.value = applyConfirmedState(
                base = previousConfirmedIds,
                itemId = itemId,
                confirmed = result.getOrThrow()
            )
        } else {
            _confirmedTodayIds.value = previousConfirmedIds
        }

        return result
    }

    suspend fun fetchLinkMetadata(url: String): Result<LinkMetadata> = runCatching {
        require(url.isNotBlank()) { "링크를 먼저 입력해 주세요." }
        container.repository.fetchLinkMetadata(url.trim())
    }

    private fun normalizeDraft(draft: ContentDraft): ContentDraft {
        val generatedTitle = when {
            draft.title.isNotBlank() -> draft.title.trim()
            draft.body.isNotBlank() -> draft.body.trim().take(24)
            draft.sourceUrl.isNotBlank() -> draft.sourceUrl.trim()
            draft.imageUris.isNotEmpty() -> displayNameFromUri(draft.imageUris.first())
            draft.videoUris.isNotEmpty() -> displayNameFromUri(draft.videoUris.first())
            else -> ""
        }

        return draft.copy(
            type = detectContentType(draft),
            title = generatedTitle,
            body = draft.body.trim(),
            author = draft.author.trim(),
            sourceUrl = draft.sourceUrl.trim(),
            thumbnailUrl = draft.thumbnailUrl.trim(),
            category = draft.category.trim(),
            tags = draft.tags.map(String::trim).filter(String::isNotBlank),
            imageUris = draft.imageUris.map(String::trim).filter(String::isNotBlank).distinct(),
            videoUris = draft.videoUris.map(String::trim).filter(String::isNotBlank).distinct()
        )
    }

    private fun validate(draft: ContentDraft) {
        require(
            draft.body.isNotBlank() ||
                draft.sourceUrl.isNotBlank() ||
                draft.imageUris.isNotEmpty() ||
                draft.videoUris.isNotEmpty()
        ) { "본문, 링크, 사진, 영상 중 하나는 넣어야 합니다." }
    }

    private fun detectContentType(draft: ContentDraft): ContentType {
        val url = draft.sourceUrl.trim().lowercase()
        return when {
            url.contains("youtube.com") ||
                url.contains("youtu.be") ||
                url.contains("vimeo.com") ||
                url.contains("tiktok.com") -> ContentType.VIDEO
            url.isNotBlank() -> ContentType.LINK
            else -> ContentType.QUOTE
        }
    }

    private fun displayNameFromUri(uriString: String): String {
        val lastSegment = Uri.parse(uriString).lastPathSegment.orEmpty()
        return lastSegment.substringAfterLast('/').substringAfterLast(':').ifBlank { "새 게시글" }
    }

    private fun applyConfirmedState(
        base: Set<Long>,
        itemId: Long,
        confirmed: Boolean
    ): Set<Long> {
        return if (confirmed) {
            base + itemId
        } else {
            base - itemId
        }
    }

    private suspend fun currentSnapshot(): AppDataSnapshot = container.syncCoordinator.currentSnapshot()
}
