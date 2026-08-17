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
    private val linkMetadataFetcher: LinkMetadataFetcher,
    private val deletionDao: DeletionDao? = null,
    private val diaryDao: DiaryDao? = null,
    private val todoDao: TodoDao? = null,
    private val bookDao: BookDao? = null
) {
    /** 삭제 표식을 남긴다. 표식이 없으면 다른 기기에서 지운 항목이 되살아난다. */
    private suspend fun recordDeletion(syncId: String, entityType: SyncEntityType) {
        if (syncId.isBlank()) return
        deletionDao?.insert(
            DeletionEntity(
                syncId = syncId,
                entityType = entityType,
                deletedAt = System.currentTimeMillis()
            )
        )
    }

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
                // 수정해도 syncId는 유지해야 다른 기기에서 같은 항목으로 인식된다.
                syncId = existing?.syncId ?: SyncIdentity.newId(),
                updatedAt = System.currentTimeMillis(),
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
                lastSurfacedAt = existing?.lastSurfacedAt,
                showCount = existing?.showCount ?: 0,
                isFavorite = draft.isFavorite
            )
        )
    }

    suspend fun deleteContent(id: Long) {
        val existing = contentItemDao.getById(id)
        contentItemDao.deleteById(id)
        existing?.let { recordDeletion(it.syncId, SyncEntityType.CONTENT_ITEM) }
    }

    suspend fun fetchLinkMetadata(url: String): LinkMetadata = linkMetadataFetcher.fetch(url)

    suspend fun getContentById(id: Long): ContentItemEntity? = contentItemDao.getById(id)

    suspend fun getRandomContent(category: String): ContentItemEntity? {
        return pickCandidate(category)
    }

    suspend fun pickFeaturedContent(
        category: String,
        trigger: ExposureTrigger
    ): ContentItemEntity? {
        val item = pickCandidate(category) ?: return null
        recordContentSurfaced(item, trigger)
        return item
    }

    private suspend fun pickCandidate(category: String): ContentItemEntity? {
        return contentItemDao.pickLeastRecentlySurfaced(
            category = category,
            poolSize = SURFACE_POOL_SIZE
        )
    }

    suspend fun recordContentSurfaced(
        item: ContentItemEntity,
        trigger: ExposureTrigger
    ) {
        val surfacedAt = System.currentTimeMillis()
        // 순환 기준을 항목에 직접 남긴다. 이력을 지워도 순환이 유지되어야 한다.
        contentItemDao.markSurfaced(item.id, surfacedAt)
        exposureEventDao.insert(
            ExposureEventEntity(
                contentItemId = item.id,
                contentItemSyncId = item.syncId,
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
                contentItemSyncId = item.syncId,
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
                contentItemSyncId = item.syncId,
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
        val doomed = exposureEventDao.getEventsBetween(start, end)
            .filter { it.eventType == ExposureEventType.CONFIRMED }
        val removed = exposureEventDao.deleteEventsByTypeForRange(
            eventType = ExposureEventType.CONFIRMED,
            start = start,
            end = end
        )
        doomed.forEach { recordDeletion(it.syncId, SyncEntityType.EXPOSURE_EVENT) }
        return removed
    }

    suspend fun deleteExposureEvents(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        val doomed = ids.mapNotNull { exposureEventDao.getById(it) }
        val removed = exposureEventDao.deleteByIds(ids.toList())
        doomed.forEach { recordDeletion(it.syncId, SyncEntityType.EXPOSURE_EVENT) }
        return removed
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
                syncId = existing?.syncId ?: SyncIdentity.newId(),
                updatedAt = System.currentTimeMillis(),
                title = draft.title.trim(),
                note = draft.note.trim(),
                category = draft.category.trim(),
                reminderEnabled = draft.reminderEnabled,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteRoutine(id: Long) {
        val existing = routineDao.getById(id)
        // 루틴을 지우면 딸린 체크와 메모도 함께 사라지므로 표식을 함께 남긴다.
        val checks = routineCheckDao.getByRoutineId(id)
        val memos = routineMemoDao.getByRoutineId(id)
        routineDao.deleteById(id)
        existing?.let { recordDeletion(it.syncId, SyncEntityType.ROUTINE) }
        checks.forEach { recordDeletion(it.syncId, SyncEntityType.ROUTINE_CHECK) }
        memos.forEach { recordDeletion(it.syncId, SyncEntityType.ROUTINE_MEMO) }
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
                routineSyncId = routine.syncId,
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
                routineSyncId = routine.syncId,
                routineTitle = routine.title,
                body = normalized
            )
        )
    }

    suspend fun deleteRoutineMemo(id: Long): Int {
        val existing = routineMemoDao.getById(id)
        val removed = routineMemoDao.deleteById(id)
        existing?.let { recordDeletion(it.syncId, SyncEntityType.ROUTINE_MEMO) }
        return removed
    }

    // ---- 일기 ----

    fun observeDiaries(): Flow<List<DiaryEntity>> =
        diaryDao?.observeAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getDiaryById(id: Long): DiaryEntity? = diaryDao?.getById(id)

    suspend fun saveDiary(draft: DiaryDraft): Long {
        val dao = diaryDao ?: error("일기를 저장할 수 없습니다.")
        require(draft.hasSomethingToSave) { "내용이나 첨부를 하나는 넣어 주세요." }
        val existing = if (draft.id != 0L) dao.getById(draft.id) else null
        return dao.insert(
            DiaryEntity(
                id = if (draft.id == 0L) 0 else draft.id,
                // 고쳐도 syncId는 지켜야 다른 기기에서 같은 일기로 인식된다.
                syncId = existing?.syncId ?: SyncIdentity.newId(),
                updatedAt = System.currentTimeMillis(),
                entryDate = draft.entryDate.toString(),
                title = draft.title.trim(),
                body = draft.body.trim(),
                weather = draft.weather.trim(),
                mood = draft.mood.trim(),
                imageUris = draft.imageUris.distinct(),
                videoUris = draft.videoUris.distinct(),
                audioUris = draft.audioUris.distinct(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteDiary(id: Long) {
        val dao = diaryDao ?: return
        val existing = dao.getById(id)
        dao.deleteById(id)
        existing?.let { recordDeletion(it.syncId, SyncEntityType.DIARY) }
    }

    // ---- 독서 ----

    fun observeBooks(): Flow<List<BookEntity>> =
        bookDao?.observeAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getBookById(id: Long): BookEntity? = bookDao?.getById(id)

    suspend fun saveBook(draft: BookDraft): BookEntity {
        val dao = bookDao ?: error("책을 저장할 수 없습니다.")
        val title = draft.title.trim()
        require(title.isNotBlank()) { "책 제목을 입력해 주세요." }
        val existing = if (draft.id != 0L) dao.getById(draft.id) else null
        val totalPages = draft.totalPages.coerceAtLeast(0)
        val book = BookEntity(
            id = if (draft.id == 0L) 0 else draft.id,
            // 고쳐도 syncId는 지켜야 다른 기기에서 같은 책으로 인식되고, 뽑아 둔 글귀도 출처를 잃지 않는다.
            syncId = existing?.syncId ?: SyncIdentity.newId(),
            updatedAt = System.currentTimeMillis(),
            title = title,
            author = draft.author.trim(),
            totalPages = totalPages,
            // 전체 쪽수를 줄였는데 현재 쪽이 그대로면 100%를 넘습니다.
            currentPage = draft.currentPage.coerceAtLeast(0).let { page ->
                if (totalPages > 0) page.coerceAtMost(totalPages) else page
            },
            status = draft.status.trim().ifBlank { BookStatus.READING.name },
            note = draft.note.trim(),
            startedAt = existing?.startedAt ?: System.currentTimeMillis(),
            finishedAt = existing?.finishedAt,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        val id = dao.insert(book)
        return book.copy(id = if (book.id == 0L) id else book.id)
    }

    /** 읽은 쪽수만 고칩니다. 다 읽으면 상태도 함께 넘깁니다. */
    suspend fun updateBookProgress(id: Long, currentPage: Int): BookEntity? {
        val dao = bookDao ?: return null
        val existing = dao.getById(id) ?: return null
        val page = currentPage.coerceAtLeast(0).let { value ->
            if (existing.totalPages > 0) value.coerceAtMost(existing.totalPages) else value
        }
        // 마지막 쪽에 닿으면 다 읽은 것으로 봅니다. 따로 한 번 더 누르게 하지 않습니다.
        val finished = existing.totalPages > 0 && page >= existing.totalPages
        val updated = existing.copy(
            currentPage = page,
            updatedAt = System.currentTimeMillis(),
            status = if (finished) BookStatus.FINISHED.name else BookStatus.READING.name,
            finishedAt = if (finished) existing.finishedAt ?: System.currentTimeMillis() else null
        )
        dao.insert(updated)
        return updated
    }

    /** 다 읽음/다시 읽는 중을 오갑니다. */
    suspend fun toggleBookFinished(id: Long): BookEntity? {
        val dao = bookDao ?: return null
        val existing = dao.getById(id) ?: return null
        val nowFinished = !existing.isFinished
        val updated = existing.copy(
            updatedAt = System.currentTimeMillis(),
            status = if (nowFinished) BookStatus.FINISHED.name else BookStatus.READING.name,
            // 다 읽었다고 하면 마지막 쪽까지 읽은 것으로 맞춰 줍니다.
            currentPage = if (nowFinished && existing.totalPages > 0) existing.totalPages else existing.currentPage,
            finishedAt = if (nowFinished) System.currentTimeMillis() else null
        )
        dao.insert(updated)
        return updated
    }

    suspend fun deleteBook(id: Long) {
        val dao = bookDao ?: return
        val existing = dao.getById(id) ?: return
        dao.deleteById(id)
        // 뽑아 둔 글귀는 남깁니다. 책을 정리했다고 밑줄 그은 문장까지 사라지면 안 됩니다.
        recordDeletion(existing.syncId, SyncEntityType.BOOK)
    }

    /**
     * 읽고 있는 책에서 글귀를 바로 뽑아 보관함에 넣습니다.
     *
     * 저자와 출처를 책에서 채워 주므로, 사용자는 문장과 쪽수만 적으면 됩니다.
     * 적은 쪽수가 지금 읽는 쪽보다 뒤면 진도도 함께 옮겨 줍니다. 뽑았다는 것은 거기까지 읽었다는 뜻입니다.
     */
    suspend fun extractQuoteFromBook(bookId: Long, body: String, page: Int): ContentItemEntity {
        val dao = bookDao ?: error("책을 찾을 수 없습니다.")
        val book = dao.getById(bookId) ?: error("책을 찾을 수 없습니다.")
        val text = body.trim()
        require(text.isNotBlank()) { "뽑아낼 글귀를 입력해 주세요." }

        val quote = ContentItemEntity(
            syncId = SyncIdentity.newId(),
            updatedAt = System.currentTimeMillis(),
            type = ContentType.QUOTE,
            title = book.title,
            body = text,
            author = book.author,
            category = BOOK_CATEGORY,
            bookSyncId = book.syncId,
            bookPage = page.coerceAtLeast(0),
            createdAt = System.currentTimeMillis()
        )
        val id = contentItemDao.insert(quote)

        if (page > book.currentPage) {
            updateBookProgress(bookId, page)
        }
        return quote.copy(id = id)
    }

    // ---- 할 일 ----

    fun observeTodos(): Flow<List<TodoEntity>> =
        todoDao?.observeAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getTodoById(id: Long): TodoEntity? = todoDao?.getById(id)

    suspend fun getPendingTodoReminders(): List<TodoEntity> = todoDao?.getPendingReminders().orEmpty()

    /** 저장한 할 일을 그대로 돌려줍니다. 부르는 쪽이 알람을 다시 걸어야 하기 때문입니다. */
    suspend fun saveTodo(draft: TodoDraft): TodoEntity {
        val dao = todoDao ?: error("할 일을 저장할 수 없습니다.")
        val title = draft.title.trim()
        require(title.isNotBlank()) { "할 일 내용을 입력해 주세요." }
        val existing = if (draft.id != 0L) dao.getById(draft.id) else null
        val todo = TodoEntity(
            id = if (draft.id == 0L) 0 else draft.id,
            syncId = existing?.syncId ?: SyncIdentity.newId(),
            updatedAt = System.currentTimeMillis(),
            title = title,
            note = draft.note.trim(),
            dueDate = draft.dueDate.toString(),
            remindAt = draft.remindAt,
            // 고칠 때 완료 상태를 잃으면 끝낸 일이 되살아난다.
            doneAt = existing?.doneAt,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        val id = dao.insert(todo)
        return todo.copy(id = if (todo.id == 0L) id else todo.id)
    }

    /** 완료 표시를 뒤집고 결과를 돌려줍니다. 끝낸 일의 알람은 부르는 쪽이 지웁니다. */
    suspend fun toggleTodoDone(id: Long): TodoEntity? {
        val dao = todoDao ?: return null
        val existing = dao.getById(id) ?: return null
        val updated = existing.copy(
            doneAt = if (existing.isDone) null else System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.insert(updated)
        return updated
    }

    suspend fun deleteTodo(id: Long) {
        val dao = todoDao ?: return
        val existing = dao.getById(id)
        dao.deleteById(id)
        existing?.let { recordDeletion(it.syncId, SyncEntityType.TODO) }
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
        contentItemDao.updateFavorite(id, isFavorite, System.currentTimeMillis())
    }

    suspend fun resetViewCounts(): Int {
        return contentItemDao.resetReadCounts(System.currentTimeMillis())
    }

    suspend fun removeCategory(category: String): Int {
        return contentItemDao.clearCategory(category.trim(), System.currentTimeMillis())
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

    companion object {
        /** 가장 오래 안 나온 항목만 뽑으면 순서가 뻔해지므로 상위 후보 몇 개 중에서 무작위로 고른다. */
        const val SURFACE_POOL_SIZE = 5

        /** 책에서 뽑은 글귀에 붙는 카테고리. 보관함에서 한데 모아 보려는 것입니다. */
        const val BOOK_CATEGORY = "독서"
    }
}
