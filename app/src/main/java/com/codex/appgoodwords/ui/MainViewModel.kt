package com.codex.appgoodwords.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codex.appgoodwords.data.AppContainer
import com.codex.appgoodwords.data.AppImportResult
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.data.LinkMetadata
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.RoutineDraft
import com.codex.appgoodwords.work.AppNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    val routineChecks = container.repository.observeRoutineChecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routineMemos = container.repository.observeRoutineMemos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    private val _openItemRequest = MutableStateFlow<Long?>(null)
    val openItemRequest: StateFlow<Long?> = _openItemRequest.asStateFlow()

    private val _confirmedTodayIds = MutableStateFlow<Set<Long>>(emptySet())
    val confirmedTodayIds: StateFlow<Set<Long>> = _confirmedTodayIds.asStateFlow()

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

            val currentSettings = container.settingsStore.getSettings()
            if (currentSettings.showOnLaunch) {
                container.repository.pickFeaturedContent(
                    category = currentSettings.categoryFilter,
                    trigger = ExposureTrigger.APP_LAUNCH
                )
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
}
