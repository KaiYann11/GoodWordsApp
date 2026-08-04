package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 병합이 틀리면 한쪽 기기의 편집이 조용히 사라집니다.
 * 각 규칙(최신 우선, 삭제 표식, 합집합)을 개별로 확인합니다.
 */
class SyncMergerTest {
    @Test
    fun keepsNewerSideForSameRecord() {
        val local = snapshot(items = listOf(item("a", title = "로컬이 최신", updatedAt = 200)))
        val remote = snapshot(items = listOf(item("a", title = "서버가 예전", updatedAt = 100)))

        val merged = SyncMerger.merge(local, remote)

        assertEquals("로컬이 최신", merged.items.single().title)
    }

    @Test
    fun takesRemoteWhenRemoteIsNewer() {
        val local = snapshot(items = listOf(item("a", title = "로컬 예전", updatedAt = 100)))
        val remote = snapshot(items = listOf(item("a", title = "서버 최신", updatedAt = 300)))

        val merged = SyncMerger.merge(local, remote)

        assertEquals("서버 최신", merged.items.single().title)
    }

    @Test
    fun sameTimestampKeepsLocalSoResultIsStable() {
        val local = snapshot(items = listOf(item("a", title = "로컬", updatedAt = 100)))
        val remote = snapshot(items = listOf(item("a", title = "서버", updatedAt = 100)))

        val merged = SyncMerger.merge(local, remote)

        assertEquals("로컬", merged.items.single().title)
    }

    @Test
    fun keepsRecordsThatOnlyOneSideHas() {
        val local = snapshot(items = listOf(item("a", title = "로컬만")))
        val remote = snapshot(items = listOf(item("b", title = "서버만")))

        val merged = SyncMerger.merge(local, remote)

        assertEquals(setOf("로컬만", "서버만"), merged.items.map { it.title }.toSet())
    }

    @Test
    fun deletionRemovesRecordFromTheOtherSide() {
        // 로컬에서 지운 항목이 서버에 남아 있어도 되살아나면 안 된다.
        val local = snapshot(deletions = listOf(deletion("a", deletedAt = 500)))
        val remote = snapshot(items = listOf(item("a", title = "서버에 남은 항목", updatedAt = 100)))

        val merged = SyncMerger.merge(local, remote)

        assertTrue(merged.items.isEmpty())
        assertEquals(listOf("a"), merged.deletions.map { it.syncId })
    }

    @Test
    fun editAfterDeletionWins() {
        // 지운 뒤 다른 기기에서 다시 고쳤다면 그 수정이 이긴다.
        val local = snapshot(deletions = listOf(deletion("a", deletedAt = 100)))
        val remote = snapshot(items = listOf(item("a", title = "삭제 후 수정", updatedAt = 200)))

        val merged = SyncMerger.merge(local, remote)

        assertEquals("삭제 후 수정", merged.items.single().title)
    }

    @Test
    fun deletionAtSameInstantWins() {
        val local = snapshot(deletions = listOf(deletion("a", deletedAt = 200)))
        val remote = snapshot(items = listOf(item("a", updatedAt = 200)))

        val merged = SyncMerger.merge(local, remote)

        assertTrue(merged.items.isEmpty())
    }

    @Test
    fun appendOnlyRecordsAreUnioned() {
        val local = snapshot(events = listOf(event("e1"), event("e2")))
        val remote = snapshot(events = listOf(event("e2"), event("e3")))

        val merged = SyncMerger.merge(local, remote)

        assertEquals(setOf("e1", "e2", "e3"), merged.events.map { it.syncId }.toSet())
    }

    @Test
    fun deletedEventsDoNotComeBack() {
        val local = snapshot(
            events = listOf(event("e1")),
            deletions = listOf(deletion("e2", deletedAt = 100))
        )
        val remote = snapshot(events = listOf(event("e1"), event("e2")))

        val merged = SyncMerger.merge(local, remote)

        assertEquals(listOf("e1"), merged.events.map { it.syncId })
    }

    @Test
    fun routineChecksAreUnionedNotDuplicated() {
        val local = snapshot(routineChecks = listOf(check("c1")))
        val remote = snapshot(routineChecks = listOf(check("c1"), check("c2")))

        val merged = SyncMerger.merge(local, remote)

        assertEquals(setOf("c1", "c2"), merged.routineChecks.map { it.syncId }.toSet())
    }

    @Test
    fun routinesAndMemosFollowTheSameNewerWinsRule() {
        val local = snapshot(
            routines = listOf(routine("r1", title = "로컬 루틴", updatedAt = 300)),
            routineMemos = listOf(memo("m1", body = "로컬 메모", updatedAt = 100))
        )
        val remote = snapshot(
            routines = listOf(routine("r1", title = "서버 루틴", updatedAt = 200)),
            routineMemos = listOf(memo("m1", body = "서버 메모", updatedAt = 400))
        )

        val merged = SyncMerger.merge(local, remote)

        assertEquals("로컬 루틴", merged.routines.single().title)
        assertEquals("서버 메모", merged.routineMemos.single().body)
    }

    @Test
    fun settingsFollowTheMoreRecentlyTouchedSide() {
        val local = snapshot(
            settings = ReminderSettings(intervalMinutes = 60),
            settingsUpdatedAt = 100
        )
        val remote = snapshot(
            settings = ReminderSettings(intervalMinutes = 240),
            settingsUpdatedAt = 500
        )

        val merged = SyncMerger.merge(local, remote)

        assertEquals(240, merged.settings.intervalMinutes)
        assertEquals(500, merged.settingsUpdatedAt)
    }

    @Test
    fun mergeIsIdempotent() {
        val local = snapshot(
            items = listOf(item("a", updatedAt = 100)),
            events = listOf(event("e1")),
            deletions = listOf(deletion("gone", deletedAt = 50))
        )
        val remote = snapshot(items = listOf(item("b", updatedAt = 200)))

        val once = SyncMerger.merge(local, remote)
        val twice = SyncMerger.merge(once, remote)

        assertEquals(once.items.map { it.syncId }.toSet(), twice.items.map { it.syncId }.toSet())
        assertEquals(once.events.map { it.syncId }, twice.events.map { it.syncId })
        assertEquals(once.deletions.map { it.syncId }.toSet(), twice.deletions.map { it.syncId }.toSet())
    }

    @Test
    fun newerDeletionTimestampWinsBetweenSides() {
        val local = snapshot(deletions = listOf(deletion("a", deletedAt = 100)))
        val remote = snapshot(deletions = listOf(deletion("a", deletedAt = 900)))

        val merged = SyncMerger.merge(local, remote)

        assertEquals(900L, merged.deletions.single().deletedAt)
    }

    private fun snapshot(
        items: List<ContentItemEntity> = emptyList(),
        events: List<ExposureEventEntity> = emptyList(),
        routines: List<RoutineEntity> = emptyList(),
        routineChecks: List<RoutineCheckEntity> = emptyList(),
        routineMemos: List<RoutineMemoEntity> = emptyList(),
        settings: ReminderSettings = ReminderSettings(),
        settingsUpdatedAt: Long = 0L,
        deletions: List<DeletionEntity> = emptyList()
    ) = AppDataSnapshot(
        items = items,
        events = events,
        routines = routines,
        routineChecks = routineChecks,
        routineMemos = routineMemos,
        settings = settings,
        settingsUpdatedAt = settingsUpdatedAt,
        deletions = deletions
    )

    private fun item(syncId: String, title: String = "제목", updatedAt: Long = 0L) = ContentItemEntity(
        syncId = syncId,
        updatedAt = updatedAt,
        type = ContentType.QUOTE,
        title = title,
        body = "본문"
    )

    private fun event(syncId: String) = ExposureEventEntity(
        syncId = syncId,
        contentItemId = 1L,
        contentTitle = "제목",
        contentType = ContentType.QUOTE,
        eventType = ExposureEventType.CONFIRMED,
        trigger = ExposureTrigger.MANUAL_REFRESH,
        occurredAt = 1_000L
    )

    private fun routine(syncId: String, title: String = "루틴", updatedAt: Long = 0L) = RoutineEntity(
        syncId = syncId,
        updatedAt = updatedAt,
        title = title
    )

    private fun check(syncId: String) = RoutineCheckEntity(
        syncId = syncId,
        routineId = 1L,
        routineTitle = "루틴",
        checkedAt = 1_000L
    )

    private fun memo(syncId: String, body: String = "메모", updatedAt: Long = 0L) = RoutineMemoEntity(
        syncId = syncId,
        updatedAt = updatedAt,
        routineId = 1L,
        routineTitle = "루틴",
        body = body
    )

    private fun deletion(syncId: String, deletedAt: Long) = DeletionEntity(
        syncId = syncId,
        entityType = SyncEntityType.CONTENT_ITEM,
        deletedAt = deletedAt
    )
}
