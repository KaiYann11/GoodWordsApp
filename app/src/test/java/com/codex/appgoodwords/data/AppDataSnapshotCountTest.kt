package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 내보낸 파일에 모든 종류가 담기는지 봅니다.
 *
 * 예전에는 내보내기가 종류를 하나씩 인자로 받았고, 일기·할 일·책이 생겼을 때 더하는 것을
 * 빠뜨렸습니다. 내보낸 파일에는 그 셋이 없었는데, 그 파일로 `가져오기(교체)`를 하면
 * [AppDataImporter]가 먼저 전부 지우므로 **기기의 일기가 영영 사라졌습니다.**
 *
 * 그래서 여기서 두 가지를 못 박습니다. 하나는 스냅샷을 JSON으로 썼다 읽으면 모든 종류가
 * 살아 돌아온다는 것이고, 다른 하나는 새 종류를 만들었을 때 [AppDataSnapshot.recordCount]가
 * 그것을 세지 않으면 시험이 깨진다는 것입니다.
 */
class AppDataSnapshotCountTest {
    private val snapshot = AppDataSnapshot(
        items = listOf(
            ContentItemEntity(id = 1, syncId = "i1", type = ContentType.QUOTE, title = "글귀", body = "본문")
        ),
        events = listOf(
            ExposureEventEntity(
                id = 1,
                syncId = "e1",
                contentItemId = 1,
                contentItemSyncId = "i1",
                contentTitle = "글귀",
                contentType = ContentType.QUOTE,
                eventType = ExposureEventType.CONFIRMED,
                trigger = ExposureTrigger.MANUAL_REFRESH,
                occurredAt = 100L
            )
        ),
        routines = listOf(RoutineEntity(id = 1, syncId = "r1", title = "물 한 컵")),
        routineChecks = listOf(
            RoutineCheckEntity(id = 1, syncId = "c1", routineId = 1, routineSyncId = "r1", routineTitle = "물 한 컵", checkedAt = 100L)
        ),
        routineMemos = listOf(
            RoutineMemoEntity(id = 1, syncId = "m1", routineId = 1, routineSyncId = "r1", routineTitle = "물 한 컵", body = "메모")
        ),
        settings = ReminderSettings(),
        diaries = listOf(DiaryEntity(id = 1, syncId = "d1", entryDate = "2026-08-26", body = "일기 본문")),
        todos = listOf(TodoEntity(id = 1, syncId = "t1", title = "할 일", dueDate = "2026-08-26")),
        books = listOf(BookEntity(id = 1, syncId = "b1", title = "책")),
        growthReports = listOf(
            GrowthReportEntity(id = 1, syncId = "g1", strengths = listOf("꾸준했습니다."))
        ),
        moodLogs = listOf(
            MoodLogEntity(id = 1, syncId = "mo1", entryDate = "2026-08-26", mood = DiaryMood.TIRED.name)
        ),
        contentMemos = listOf(
            ContentMemoEntity(
                id = 1,
                syncId = "cm1",
                contentItemId = 1,
                contentItemSyncId = "i1",
                contentTitle = "글귀",
                body = "이 문장이 오늘 눈에 들어왔다."
            )
        )
    )

    @Test
    fun everyKindSurvivesTheRoundTrip() {
        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        // 하나라도 0이면 그 종류는 내보낸 파일에 담기지 않는다는 뜻입니다.
        assertEquals(1, restored.items.size)
        assertEquals(1, restored.events.size)
        assertEquals(1, restored.routines.size)
        assertEquals(1, restored.routineChecks.size)
        assertEquals(1, restored.routineMemos.size)
        assertEquals("일기가 파일에 담기지 않았습니다.", 1, restored.diaries.size)
        assertEquals("할 일이 파일에 담기지 않았습니다.", 1, restored.todos.size)
        assertEquals("책이 파일에 담기지 않았습니다.", 1, restored.books.size)
        assertEquals("돌아보기가 파일에 담기지 않았습니다.", 1, restored.growthReports.size)
        assertEquals("오늘 기분이 파일에 담기지 않았습니다.", 1, restored.moodLogs.size)
        assertEquals("2026-08-26", restored.moodLogs.single().entryDate)
        assertEquals(DiaryMood.TIRED, restored.moodLogs.single().moodOption)
        assertEquals("글귀 메모가 파일에 담기지 않았습니다.", 1, restored.contentMemos.size)
        assertEquals("i1", restored.contentMemos.single().contentItemSyncId)
    }

    @Test
    fun theCountAddsUpEveryKind() {
        // 새 종류를 만들고 recordCount에 더하지 않으면 여기서 걸립니다.
        assertEquals(11, snapshot.recordCount)
    }

    @Test
    fun anEmptySnapshotCountsZero() {
        val empty = AppDataSnapshot(
            items = emptyList(),
            events = emptyList(),
            routines = emptyList(),
            routineChecks = emptyList(),
            routineMemos = emptyList(),
            settings = ReminderSettings()
        )

        assertEquals(0, empty.recordCount)
    }

    @Test
    fun deletionsAreNotCountedAsRecords() {
        // 삭제 표식은 사용자가 만든 기록이 아니라 병합용 자취입니다.
        val withTombstones = snapshot.copy(
            deletions = listOf(DeletionEntity("gone", SyncEntityType.DIARY, 1L))
        )

        assertTrue(withTombstones.recordCount == snapshot.recordCount)
    }
}
