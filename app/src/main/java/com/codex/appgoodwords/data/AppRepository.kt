package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val contentItemDao: ContentItemDao,
    private val exposureEventDao: ExposureEventDao,
    private val routineDao: RoutineDao,
    private val routineCheckDao: RoutineCheckDao,
    private val routineMemoDao: RoutineMemoDao,
    private val linkMetadataFetcher: LinkMetadataFetcher
) {
    fun observeAllContent(): Flow<List<ContentItemEntity>> = contentItemDao.observeAll()

    fun observeVideos(): Flow<List<ContentItemEntity>> = contentItemDao.observeByType(ContentType.VIDEO)

    fun observeExposureEvents(): Flow<List<ExposureEventEntity>> = exposureEventDao.observeAll()

    fun observeRoutines(): Flow<List<RoutineEntity>> = routineDao.observeAll()

    fun observeRoutineChecks(): Flow<List<RoutineCheckEntity>> = routineCheckDao.observeAll()

    fun observeRoutineMemos(): Flow<List<RoutineMemoEntity>> = routineMemoDao.observeAll()

    fun observeRoutineCheckCounts(start: Long, end: Long): Flow<List<RoutineCheckCount>> {
        return routineCheckDao.observeCountsBetween(start, end)
    }

    suspend fun saveContent(draft: ContentDraft) {
        val existing = if (draft.id != 0L) contentItemDao.getById(draft.id) else null
        contentItemDao.insert(
            ContentItemEntity(
                id = if (draft.id == 0L) 0 else draft.id,
                type = draft.type,
                title = draft.title.trim(),
                body = draft.body.trim(),
                author = draft.author.trim(),
                sourceUrl = draft.sourceUrl.trim(),
                thumbnailUrl = draft.thumbnailUrl.trim(),
                category = draft.category.trim(),
                tags = draft.tags.map(String::trim).filter(String::isNotBlank),
                imageUris = draft.imageUris.distinct(),
                videoUris = draft.videoUris.distinct(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                lastShownAt = existing?.lastShownAt,
                showCount = existing?.showCount ?: 0,
                isFavorite = draft.isFavorite
            )
        )
    }

    suspend fun deleteContent(id: Long) {
        contentItemDao.deleteById(id)
    }

    suspend fun fetchLinkMetadata(url: String): LinkMetadata = linkMetadataFetcher.fetch(url)

    suspend fun getRandomContent(category: String): ContentItemEntity? {
        return contentItemDao.getRandomByCategory(category)
    }

    suspend fun pickFeaturedContent(
        category: String,
        trigger: ExposureTrigger
    ): ContentItemEntity? {
        val item = contentItemDao.getRandomByCategory(category) ?: return null
        recordContentSurfaced(item, trigger)
        return item
    }

    suspend fun recordContentSurfaced(
        item: ContentItemEntity,
        trigger: ExposureTrigger
    ) {
        val surfacedAt = System.currentTimeMillis()
        exposureEventDao.insert(
            ExposureEventEntity(
                contentItemId = item.id,
                contentTitle = item.title.ifBlank { item.body.take(24) },
                contentType = item.type,
                eventType = ExposureEventType.SURFACED,
                trigger = trigger,
                occurredAt = surfacedAt
            )
        )
    }

    suspend fun recordContentViewed(
        contentItemId: Long,
        trigger: ExposureTrigger
    ) {
        val item = contentItemDao.getById(contentItemId) ?: return
        val viewedAt = System.currentTimeMillis()

        exposureEventDao.insert(
            ExposureEventEntity(
                contentItemId = item.id,
                contentTitle = item.title.ifBlank { item.body.take(24) },
                contentType = item.type,
                eventType = ExposureEventType.SHOWN,
                trigger = trigger,
                occurredAt = viewedAt
            )
        )
    }

    suspend fun markContentConfirmed(
        contentItemId: Long,
        trigger: ExposureTrigger
    ): Boolean {
        val item = contentItemDao.getById(contentItemId) ?: return false
        val (start, end) = todayRange()
        val existingCount = exposureEventDao.countEventsForRange(
            contentItemId = contentItemId,
            eventType = ExposureEventType.CONFIRMED,
            start = start,
            end = end
        )
        if (existingCount > 0) {
            return false
        }

        val confirmedAt = System.currentTimeMillis()
        contentItemDao.markRead(item.id, confirmedAt)

        exposureEventDao.insert(
            ExposureEventEntity(
                contentItemId = item.id,
                contentTitle = item.title.ifBlank { item.body.take(24) },
                contentType = item.type,
                eventType = ExposureEventType.CONFIRMED,
                trigger = trigger,
                occurredAt = confirmedAt
            )
        )
        return true
    }

    suspend fun toggleContentConfirmed(
        contentItemId: Long,
        trigger: ExposureTrigger
    ): Boolean {
        val (start, end) = todayRange()
        val existingCount = exposureEventDao.countEventsForRange(
            contentItemId = contentItemId,
            eventType = ExposureEventType.CONFIRMED,
            start = start,
            end = end
        )

        return if (existingCount > 0) {
            exposureEventDao.deleteEventsForRange(
                contentItemId = contentItemId,
                eventType = ExposureEventType.CONFIRMED,
                start = start,
                end = end
            )
            false
        } else {
            markContentConfirmed(contentItemId, trigger)
        }
    }

    suspend fun clearTodayConfirmed(): Int {
        val (start, end) = todayRange()
        return exposureEventDao.deleteEventsByTypeForRange(
            eventType = ExposureEventType.CONFIRMED,
            start = start,
            end = end
        )
    }

    suspend fun deleteExposureEvents(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return exposureEventDao.deleteByIds(ids.toList())
    }

    suspend fun getTodayConfirmedIds(): Set<Long> {
        val (start, end) = todayRange()
        return exposureEventDao.getContentIdsForRange(
            eventType = ExposureEventType.CONFIRMED,
            start = start,
            end = end
        ).toSet()
    }

    suspend fun saveRoutine(draft: RoutineDraft) {
        val existing = if (draft.id != 0L) routineDao.getById(draft.id) else null
        routineDao.insert(
            RoutineEntity(
                id = if (draft.id == 0L) 0 else draft.id,
                title = draft.title.trim(),
                note = draft.note.trim(),
                category = draft.category.trim(),
                reminderEnabled = draft.reminderEnabled,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteRoutine(id: Long) {
        routineDao.deleteById(id)
    }

    suspend fun getRandomReminderRoutine(): RoutineEntity? {
        return routineDao.getRandomReminderRoutine()
    }

    suspend fun markRoutineDone(routineId: Long): Int {
        val routine = routineDao.getById(routineId) ?: return 0
        val checkedAt = System.currentTimeMillis()
        routineCheckDao.insert(
            RoutineCheckEntity(
                routineId = routine.id,
                routineTitle = routine.title,
                checkedAt = checkedAt
            )
        )
        val (start, end) = todayRange()
        return routineCheckDao.countForRange(routine.id, start, end)
    }

    suspend fun saveRoutineMemo(routineId: Long, body: String): Long {
        val routine = routineDao.getById(routineId) ?: error("루틴을 찾을 수 없습니다.")
        val normalized = body.trim()
        require(normalized.isNotBlank()) { "메모 내용을 입력해 주세요." }
        return routineMemoDao.insert(
            RoutineMemoEntity(
                routineId = routine.id,
                routineTitle = routine.title,
                body = normalized
            )
        )
    }

    suspend fun deleteRoutineMemo(id: Long): Int {
        return routineMemoDao.deleteById(id)
    }

    suspend fun getTodayRoutineCheckCount(routineId: Long): Int {
        val (start, end) = todayRange()
        return routineCheckDao.countForRange(routineId, start, end)
    }

    fun todayRangeMillis(): Pair<Long, Long> = todayRange()

    suspend fun buildTodaySummary(): DailySummary {
        val (start, end) = todayRange()
        val events = exposureEventDao.getEventsBetween(start, end)
        val shown = summarize(events, ExposureEventType.SHOWN)
        val confirmed = summarize(events, ExposureEventType.CONFIRMED)
        return DailySummary(
            shownItems = shown,
            confirmedItems = confirmed
        )
    }

    suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        contentItemDao.updateFavorite(id, isFavorite)
    }

    suspend fun resetViewCounts(): Int {
        return contentItemDao.resetReadCounts()
    }

    suspend fun removeCategory(category: String): Int {
        return contentItemDao.clearCategory(category.trim())
    }

    suspend fun seedDefaultsIfNeeded() {
        if (contentItemDao.count() > 0) return

        contentItemDao.insertAll(
            listOf(
                ContentItemEntity(
                    type = ContentType.QUOTE,
                    title = "오늘의 기준",
                    body = "행동은 감정이 따라올 때까지 기다리면 늘 늦다. 감정이 흔들려도 먼저 움직이는 쪽이 결국 이긴다.",
                    author = "AppGoodWords",
                    category = "동기부여",
                    tags = listOf("시작", "행동"),
                    isFavorite = true
                ),
                ContentItemEntity(
                    type = ContentType.QUOTE,
                    title = "집중",
                    body = "중요한 일을 자주 못 하는 이유는 시간이 없어서가 아니라 우선순위가 밀려 있기 때문이다.",
                    author = "AppGoodWords",
                    category = "집중",
                    tags = listOf("우선순위", "몰입")
                ),
                ContentItemEntity(
                    type = ContentType.LINK,
                    title = "Atomic Habits Summary",
                    body = "작은 습관을 시스템으로 만드는 방법을 다시 볼 수 있는 링크",
                    author = "James Clear",
                    sourceUrl = "https://jamesclear.com/atomic-habits",
                    category = "습관",
                    tags = listOf("습관", "시스템")
                ),
                ContentItemEntity(
                    type = ContentType.VIDEO,
                    title = "How to Focus Deeply",
                    body = "집중력을 회복하는 루틴 영상",
                    sourceUrl = "https://www.youtube.com/watch?v=4O2JK_94g3Y",
                    category = "집중",
                    tags = listOf("영상", "집중")
                ),
                ContentItemEntity(
                    type = ContentType.VIDEO,
                    title = "Motivation for Consistency",
                    body = "꾸준함에 대해 자극과 동기부여를 주는 영상",
                    sourceUrl = "https://www.youtube.com/watch?v=TQMbvJNRpLE",
                    category = "동기부여",
                    tags = listOf("영상", "꾸준함")
                )
            )
        )
    }

    private fun summarize(
        events: List<ExposureEventEntity>,
        eventType: ExposureEventType
    ): List<DailySummaryLine> {
        return events
            .filter { it.eventType == eventType }
            .groupingBy { it.contentTitle }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { (title, count) ->
                DailySummaryLine(
                    title = title,
                    count = count
                )
            }
    }

    private fun todayRange(): Pair<Long, Long> {
        val zoneId = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val tomorrowStart = LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return todayStart to (tomorrowStart - 1)
    }
}
