package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱과 서버는 각자 기본 글귀를 심습니다. 내용이 같아도 syncId가 달라서,
 * 처음 서버를 붙이면 같은 글귀가 두 벌이 됩니다.
 *
 * 합치는 것보다 더 중요한 것은 **사라진 쪽을 가리키던 이력이 부모를 잃지 않는 것**입니다.
 */
class SyncDeduplicatorTest {
    @Test
    fun theSameQuoteFromBothSidesBecomesOne() {
        val merged = snapshotOf(
            items = listOf(
                item("server-1", "오늘의 기준", "행동은 감정이 따라올 때까지 기다리면 늘 늦다.", updatedAt = 1_000L),
                item("app-1", "오늘의 기준", "행동은 감정이 따라올 때까지 기다리면 늘 늦다.", updatedAt = 2_000L)
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(1, result.items.size)
        assertEquals("최근에 손댄 쪽이 남아야 합니다.", "app-1", result.items.single().syncId)
    }

    @Test
    fun historyFollowsTheSurvivor() {
        val merged = snapshotOf(
            items = listOf(
                item("server-1", "오늘의 기준", "본문", updatedAt = 2_000L),
                item("app-1", "오늘의 기준", "본문", updatedAt = 1_000L)
            ),
            events = listOf(event("event-1", contentItemSyncId = "app-1"))
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(
            "사라진 쪽을 가리키면 이력이 부모를 잃습니다.",
            "server-1",
            result.events.single().contentItemSyncId
        )
    }

    @Test
    fun routineChecksAndMemosFollowTheSurvivor() {
        val merged = snapshotOf(
            routines = listOf(
                routine("server-r", "물 마시기", updatedAt = 2_000L),
                routine("app-r", "물 마시기", updatedAt = 1_000L)
            ),
            routineChecks = listOf(
                RoutineCheckEntity(syncId = "check-1", routineId = 0, routineSyncId = "app-r", routineTitle = "물 마시기", checkedAt = 1_000L)
            ),
            routineMemos = listOf(
                RoutineMemoEntity(syncId = "memo-1", routineId = 0, routineSyncId = "app-r", routineTitle = "물 마시기", body = "메모", createdAt = 1_000L)
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(1, result.routines.size)
        assertEquals("server-r", result.routineChecks.single().routineSyncId)
        assertEquals("server-r", result.routineMemos.single().routineSyncId)
    }

    @Test
    fun historyIsNeverCollapsed() {
        // 같은 글귀를 두 번 본 것은 진짜로 두 번 본 것이다.
        val merged = snapshotOf(
            items = listOf(item("item-1", "제목", "본문", updatedAt = 1_000L)),
            events = listOf(
                event("event-1", contentItemSyncId = "item-1"),
                event("event-2", contentItemSyncId = "item-1")
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(2, result.events.size)
    }

    @Test
    fun onlySpacingAndCaseDifferencesStillCount() {
        val merged = snapshotOf(
            items = listOf(
                item("a", "Atomic Habits Summary", "본문", updatedAt = 1_000L),
                item("b", "atomic  habits summary", "본문", updatedAt = 2_000L)
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(1, result.items.size)
    }

    @Test
    fun differentContentIsLeftAlone() {
        val merged = snapshotOf(
            items = listOf(
                item("a", "오늘의 기준", "본문 하나", updatedAt = 1_000L),
                item("b", "오늘의 기준", "본문 둘", updatedAt = 2_000L)
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(2, result.items.size)
    }

    @Test
    fun twoAttachmentOnlyDiariesOnTheSameDayAreKept() {
        // 글 없이 사진만 올린 두 기록을 합치면 한쪽 사진이 사라진다.
        val merged = snapshotOf(
            diaries = listOf(
                DiaryEntity(syncId = "d1", entryDate = "2026-08-12", imageUris = listOf("content://a"), updatedAt = 1_000L),
                DiaryEntity(syncId = "d2", entryDate = "2026-08-12", imageUris = listOf("content://b"), updatedAt = 2_000L)
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(2, result.diaries.size)
    }

    @Test
    fun theSameDiaryFromTwoDevicesBecomesOne() {
        val merged = snapshotOf(
            diaries = listOf(
                DiaryEntity(syncId = "d1", entryDate = "2026-08-12", body = "같은 하루", updatedAt = 1_000L),
                DiaryEntity(syncId = "d2", entryDate = "2026-08-12", body = "같은 하루", updatedAt = 2_000L)
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(1, result.diaries.size)
    }

    @Test
    fun theSameTodoOnTheSameDayBecomesOne() {
        val merged = snapshotOf(
            todos = listOf(
                TodoEntity(syncId = "t1", title = "우체국 가기", dueDate = "2026-08-12", updatedAt = 1_000L),
                TodoEntity(syncId = "t2", title = "우체국 가기", dueDate = "2026-08-12", updatedAt = 2_000L)
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals(1, result.todos.size)
    }

    @Test
    fun theSameTodoOnDifferentDaysIsLeftAlone() {
        val merged = snapshotOf(
            todos = listOf(
                TodoEntity(syncId = "t1", title = "우체국 가기", dueDate = "2026-08-12", updatedAt = 1_000L),
                TodoEntity(syncId = "t2", title = "우체국 가기", dueDate = "2026-08-13", updatedAt = 1_000L)
            )
        )

        val result = SyncDeduplicator.deduplicate(merged)

        assertEquals("날마다 하는 일까지 합치면 안 됩니다.", 2, result.todos.size)
    }

    @Test
    fun theResultDoesNotDependOnWhichSideRunsIt() {
        // 같은 시각이면 순서만 뒤집혀도 승자가 달라질 수 있다. 그러면 두 기기가 영원히 서로를 고친다.
        val a = item("aaa", "제목", "본문", updatedAt = 1_000L)
        val b = item("bbb", "제목", "본문", updatedAt = 1_000L)

        val forward = SyncDeduplicator.deduplicate(snapshotOf(items = listOf(a, b)))
        val backward = SyncDeduplicator.deduplicate(snapshotOf(items = listOf(b, a)))

        assertEquals(forward.items.single().syncId, backward.items.single().syncId)
        // 서버도 같은 시각이면 syncId가 큰 쪽을 남긴다. 규칙이 어긋나면 두 기기가 서로를 계속 고친다.
        assertEquals("bbb", forward.items.single().syncId)
    }

    @Test
    fun mergeRunsDeduplicationSoFirstConnectDoesNotDouble() {
        val local = snapshotOf(items = listOf(item("app-1", "오늘의 기준", "본문", updatedAt = 1_000L)))
        val remote = snapshotOf(items = listOf(item("server-1", "오늘의 기준", "본문", updatedAt = 1_000L)))

        val merged = SyncMerger.merge(local, remote)

        assertEquals("처음 붙일 때 같은 글귀가 두 벌이 됩니다.", 1, merged.items.size)
        assertTrue(merged.items.single().syncId in listOf("app-1", "server-1"))
    }

    private fun item(syncId: String, title: String, body: String, updatedAt: Long) = ContentItemEntity(
        syncId = syncId,
        updatedAt = updatedAt,
        type = ContentType.QUOTE,
        title = title,
        body = body,
        createdAt = updatedAt
    )

    private fun routine(syncId: String, title: String, updatedAt: Long) = RoutineEntity(
        syncId = syncId,
        updatedAt = updatedAt,
        title = title,
        createdAt = updatedAt
    )

    private fun event(syncId: String, contentItemSyncId: String) = ExposureEventEntity(
        syncId = syncId,
        contentItemId = 0L,
        contentItemSyncId = contentItemSyncId,
        contentTitle = "제목",
        contentType = ContentType.QUOTE,
        eventType = ExposureEventType.SURFACED,
        trigger = ExposureTrigger.MANUAL_REFRESH,
        occurredAt = 1_000L
    )

    private fun snapshotOf(
        items: List<ContentItemEntity> = emptyList(),
        events: List<ExposureEventEntity> = emptyList(),
        routines: List<RoutineEntity> = emptyList(),
        routineChecks: List<RoutineCheckEntity> = emptyList(),
        routineMemos: List<RoutineMemoEntity> = emptyList(),
        diaries: List<DiaryEntity> = emptyList(),
        todos: List<TodoEntity> = emptyList()
    ) = AppDataSnapshot(
        items = items,
        events = events,
        routines = routines,
        routineChecks = routineChecks,
        routineMemos = routineMemos,
        settings = ReminderSettings(),
        diaries = diaries,
        todos = todos
    )
}
