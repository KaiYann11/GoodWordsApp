package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 숫자 id는 기기마다 따로 증가하므로 병합 결과에는 같은 id가 여러 번 들어옵니다.
 * 여기가 깨지면 저장할 때 서로를 덮어써서 항목이 조용히 사라집니다.
 */
class SnapshotReindexerTest {
    @Test
    fun reindex_keepsBothRecordsWhenTwoDevicesUsedTheSameId() {
        val merged = snapshot(
            items = listOf(
                item(id = 1L, syncId = "a-1", title = "A기기 글귀"),
                item(id = 1L, syncId = "b-1", title = "B기기 글귀")
            )
        )

        val result = SnapshotReindexer.reindex(merged)

        assertEquals(listOf(1L, 2L), result.items.map { it.id })
        assertEquals(listOf("A기기 글귀", "B기기 글귀"), result.items.map { it.title })
    }

    @Test
    fun reindex_movesEventToTheItemItActuallyBelongsTo() {
        val merged = snapshot(
            items = listOf(
                item(id = 1L, syncId = "a-1", title = "A기기 글귀"),
                item(id = 1L, syncId = "b-1", title = "B기기 글귀")
            ),
            events = listOf(event(contentItemId = 1L, contentItemSyncId = "b-1"))
        )

        val result = SnapshotReindexer.reindex(merged)

        val target = result.items.first { it.syncId == "b-1" }
        assertEquals("이벤트는 원래 항목을 계속 가리켜야 합니다.", target.id, result.events.single().contentItemId)
    }

    @Test
    fun reindex_keepsHistoryWhoseItemWasDeleted() {
        // 항목을 지워도 이력은 남는다. 제목만으로도 볼 수 있으니 버리지 않는다.
        val merged = snapshot(
            items = emptyList(),
            events = listOf(event(contentItemId = 7L, contentItemSyncId = "사라진-항목"))
        )

        val result = SnapshotReindexer.reindex(merged)

        assertEquals(1, result.events.size)
        assertEquals("끊어진 참조는 0으로 둔다.", 0L, result.events.single().contentItemId)
    }

    @Test
    fun reindex_reconnectsLegacyRecordsThatOnlyHaveNumericIds() {
        // 9 이전 기기가 올린 레코드에는 부모 syncId가 없다.
        val merged = snapshot(
            items = listOf(item(id = 4L, syncId = "a-1", title = "글귀")),
            events = listOf(event(contentItemId = 4L, contentItemSyncId = ""))
        )

        val result = SnapshotReindexer.reindex(merged)

        assertEquals(1L, result.events.single().contentItemId)
        assertEquals("다음 병합을 위해 참조를 채워 둔다.", "a-1", result.events.single().contentItemSyncId)
    }

    @Test
    fun reindex_doesNotGuessWhenLegacyIdIsAmbiguous() {
        // 같은 숫자 id가 둘이면 어느 항목인지 알 수 없다. 엉뚱한 곳에 붙이느니 끊는다.
        val merged = snapshot(
            items = listOf(
                item(id = 1L, syncId = "a-1", title = "A기기 글귀"),
                item(id = 1L, syncId = "b-1", title = "B기기 글귀")
            ),
            events = listOf(event(contentItemId = 1L, contentItemSyncId = ""))
        )

        val result = SnapshotReindexer.reindex(merged)

        assertEquals(0L, result.events.single().contentItemId)
    }

    @Test
    fun reindex_rewiresRoutineChecksAndMemos() {
        val merged = snapshot(
            routines = listOf(
                routine(id = 1L, syncId = "a-r", title = "A기기 루틴"),
                routine(id = 1L, syncId = "b-r", title = "B기기 루틴")
            ),
            routineChecks = listOf(check(routineId = 1L, routineSyncId = "b-r")),
            routineMemos = listOf(memo(routineId = 1L, routineSyncId = "b-r"))
        )

        val result = SnapshotReindexer.reindex(merged)

        val target = result.routines.first { it.syncId == "b-r" }
        assertEquals(target.id, result.routineChecks.single().routineId)
        assertEquals(target.id, result.routineMemos.single().routineId)
    }

    @Test
    fun reindex_dropsMemoWithoutARoutine() {
        // 메모는 루틴 화면 안에서만 보이므로 붙을 루틴이 없으면 볼 방법이 없다.
        val merged = snapshot(
            routines = emptyList(),
            routineMemos = listOf(memo(routineId = 3L, routineSyncId = "사라진-루틴"))
        )

        val result = SnapshotReindexer.reindex(merged)

        assertTrue(result.routineMemos.isEmpty())
    }

    @Test
    fun reindex_givesEveryTableGaplessIdsStartingAtOne() {
        val merged = snapshot(
            items = listOf(item(id = 90L, syncId = "a"), item(id = 12L, syncId = "b")),
            events = listOf(
                event(contentItemId = 90L, contentItemSyncId = "a"),
                event(contentItemId = 12L, contentItemSyncId = "b")
            ),
            routines = listOf(routine(id = 55L, syncId = "r"))
        )

        val result = SnapshotReindexer.reindex(merged)

        assertEquals(listOf(1L, 2L), result.items.map { it.id })
        assertEquals(listOf(1L, 2L), result.events.map { it.id })
        assertEquals(listOf(1L), result.routines.map { it.id })
    }

    @Test
    fun reindex_isStableWhenRunTwice() {
        val merged = snapshot(
            items = listOf(item(id = 1L, syncId = "a-1"), item(id = 1L, syncId = "b-1")),
            events = listOf(event(contentItemId = 1L, contentItemSyncId = "b-1"))
        )

        val once = SnapshotReindexer.reindex(merged)

        assertEquals(once, SnapshotReindexer.reindex(once))
    }

    private fun item(id: Long, syncId: String, title: String = "글귀") = ContentItemEntity(
        id = id,
        syncId = syncId,
        updatedAt = 1_000L,
        type = ContentType.QUOTE,
        title = title,
        body = "본문",
        createdAt = 1_000L
    )

    private fun event(contentItemId: Long, contentItemSyncId: String) = ExposureEventEntity(
        id = 1L,
        syncId = "event-$contentItemSyncId-$contentItemId",
        contentItemId = contentItemId,
        contentItemSyncId = contentItemSyncId,
        contentTitle = "제목",
        contentType = ContentType.QUOTE,
        eventType = ExposureEventType.SURFACED,
        trigger = ExposureTrigger.MANUAL_REFRESH,
        occurredAt = 1_000L
    )

    private fun routine(id: Long, syncId: String, title: String = "루틴") = RoutineEntity(
        id = id,
        syncId = syncId,
        updatedAt = 1_000L,
        title = title,
        note = "",
        category = "",
        reminderEnabled = true,
        createdAt = 1_000L
    )

    private fun check(routineId: Long, routineSyncId: String) = RoutineCheckEntity(
        id = 1L,
        syncId = "check-$routineSyncId",
        routineId = routineId,
        routineSyncId = routineSyncId,
        routineTitle = "루틴",
        checkedAt = 1_000L
    )

    private fun memo(routineId: Long, routineSyncId: String) = RoutineMemoEntity(
        id = 1L,
        syncId = "memo-$routineSyncId",
        updatedAt = 1_000L,
        routineId = routineId,
        routineSyncId = routineSyncId,
        routineTitle = "루틴",
        body = "메모",
        createdAt = 1_000L
    )

    private fun snapshot(
        items: List<ContentItemEntity> = emptyList(),
        events: List<ExposureEventEntity> = emptyList(),
        routines: List<RoutineEntity> = emptyList(),
        routineChecks: List<RoutineCheckEntity> = emptyList(),
        routineMemos: List<RoutineMemoEntity> = emptyList()
    ) = AppDataSnapshot(
        items = items,
        events = events,
        routines = routines,
        routineChecks = routineChecks,
        routineMemos = routineMemos,
        settings = ReminderSettings()
    )
}
