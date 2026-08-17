package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 보낼 것을 고르는 규칙입니다.
 *
 * 너무 적게 고르면 그 변경이 영영 서버에 안 올라갑니다. 너무 많이 고르면 줄인 보람이 없습니다.
 * 놓치는 쪽이 훨씬 나쁘므로 경계에서는 넉넉히 보냅니다.
 */
class SyncDeltaTest {
    @Test
    fun theFirstSyncSendsEverything() {
        val snapshot = snapshotOf(items = listOf(item("a", updatedAt = 1_000L)))

        // 기준이 0이면 아직 한 번도 못 맞춘 것이라 전체를 보냅니다.
        assertEquals(snapshot, SyncDelta.changedSince(snapshot, 0L))
    }

    @Test
    fun onlyRecordsChangedAfterTheMarkAreSent() {
        val snapshot = snapshotOf(
            items = listOf(
                item("old", updatedAt = 1_000L),
                item("new", updatedAt = 3_000L)
            )
        )

        val delta = SyncDelta.changedSince(snapshot, 2_000L)

        assertEquals(listOf("new"), delta.items.map { it.syncId })
    }

    @Test
    fun aRecordChangedExactlyAtTheMarkIsSentAgain() {
        val snapshot = snapshotOf(items = listOf(item("edge", updatedAt = 2_000L)))

        // 경계에서 빠뜨리면 그 변경이 영영 안 올라갑니다. 한 번 더 보내는 쪽이 안전합니다.
        val delta = SyncDelta.changedSince(snapshot, 1_999L)

        assertEquals(1, delta.items.size)
    }

    @Test
    fun historyIsFilteredByWhenItHappened() {
        val snapshot = snapshotOf(
            events = listOf(event("old", occurredAt = 1_000L), event("new", occurredAt = 3_000L))
        )

        val delta = SyncDelta.changedSince(snapshot, 2_000L)

        // 이력은 한 번 생기면 바뀌지 않으므로 생긴 시각으로 봅니다.
        assertEquals(listOf("new"), delta.events.map { it.syncId })
    }

    @Test
    fun deletionsAreFilteredByWhenTheyHappened() {
        val snapshot = snapshotOf(
            deletions = listOf(
                DeletionEntity(syncId = "old", entityType = SyncEntityType.CONTENT_ITEM, deletedAt = 1_000L),
                DeletionEntity(syncId = "new", entityType = SyncEntityType.CONTENT_ITEM, deletedAt = 3_000L)
            )
        )

        val delta = SyncDelta.changedSince(snapshot, 2_000L)

        assertEquals(listOf("new"), delta.deletions.map { it.syncId })
    }

    @Test
    fun unchangedSettingsAreNotClaimedAsNew() {
        val snapshot = snapshotOf(items = emptyList()).copy(settingsUpdatedAt = 1_000L)

        val delta = SyncDelta.changedSince(snapshot, 2_000L)

        // 시각을 그대로 보내면 서버가 "이쪽이 최신"이라 보고 자기 설정을 덮어씁니다.
        assertEquals(0L, delta.settingsUpdatedAt)
    }

    @Test
    fun changedSettingsAreSent() {
        val snapshot = snapshotOf(items = emptyList()).copy(settingsUpdatedAt = 3_000L)

        assertEquals(3_000L, SyncDelta.changedSince(snapshot, 2_000L).settingsUpdatedAt)
    }

    @Test
    fun everyKindIsFiltered() {
        // 한 종류라도 빠뜨리면 그 종류만 매번 전부 실려 갑니다.
        val snapshot = AppDataSnapshot(
            items = listOf(item("i", 1_000L)),
            events = listOf(event("e", 1_000L)),
            routines = listOf(RoutineEntity(syncId = "r", updatedAt = 1_000L, title = "루틴")),
            routineChecks = listOf(
                RoutineCheckEntity(
                    syncId = "c",
                    routineId = 1,
                    routineSyncId = "r",
                    routineTitle = "루틴",
                    checkedAt = 1_000L
                )
            ),
            routineMemos = listOf(
                RoutineMemoEntity(
                    syncId = "m",
                    updatedAt = 1_000L,
                    routineId = 1,
                    routineSyncId = "r",
                    routineTitle = "루틴",
                    body = "메모"
                )
            ),
            settings = ReminderSettings(),
            diaries = listOf(DiaryEntity(syncId = "d", updatedAt = 1_000L, entryDate = "2026-08-17")),
            todos = listOf(TodoEntity(syncId = "t", updatedAt = 1_000L, title = "할 일", dueDate = "2026-08-17")),
            books = listOf(BookEntity(syncId = "b", updatedAt = 1_000L, title = "책"))
        )

        val delta = SyncDelta.changedSince(snapshot, 2_000L)

        assertTrue(delta.items.isEmpty())
        assertTrue(delta.events.isEmpty())
        assertTrue(delta.routines.isEmpty())
        assertTrue(delta.routineChecks.isEmpty())
        assertTrue(delta.routineMemos.isEmpty())
        assertTrue(delta.diaries.isEmpty())
        assertTrue(delta.todos.isEmpty())
        assertTrue(delta.books.isEmpty())
    }

    @Test
    fun theServerRevisionIsReadFromTheResponse() {
        val json = """{"rev":42,"partial":true,"items":[]}"""

        val restored = AppDataJson.fromJsonText(json)

        assertEquals(42L, restored.serverRev)
        assertTrue("부분 응답인 줄 모르면 DB를 통째로 갈아엎습니다.", restored.partial)
    }

    @Test
    fun aFullResponseIsNotMistakenForPartial() {
        val restored = AppDataJson.fromJsonText("""{"items":[]}""")

        assertEquals(0L, restored.serverRev)
        assertTrue(!restored.partial)
    }

    private fun item(syncId: String, updatedAt: Long) = ContentItemEntity(
        syncId = syncId,
        updatedAt = updatedAt,
        type = ContentType.QUOTE,
        title = syncId,
        body = "본문"
    )

    private fun event(syncId: String, occurredAt: Long) = ExposureEventEntity(
        syncId = syncId,
        contentItemId = 1,
        contentItemSyncId = "a",
        contentTitle = "제목",
        contentType = ContentType.QUOTE,
        eventType = ExposureEventType.SHOWN,
        trigger = ExposureTrigger.APP_LAUNCH,
        occurredAt = occurredAt
    )

    private fun snapshotOf(
        items: List<ContentItemEntity> = emptyList(),
        events: List<ExposureEventEntity> = emptyList(),
        deletions: List<DeletionEntity> = emptyList()
    ) = AppDataSnapshot(
        items = items,
        events = events,
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings(),
        deletions = deletions
    )
}
