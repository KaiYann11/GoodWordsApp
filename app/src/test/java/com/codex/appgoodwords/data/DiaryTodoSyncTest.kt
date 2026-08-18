package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 일기와 할 일이 기기 사이를 오갈 때 무엇을 잃는지 봅니다.
 *
 * 숫자 id는 기기마다 따로 증가하므로 병합 결과에 같은 값이 여러 벌 들어옵니다.
 * 그대로 저장하면 뒤에 넣은 쪽이 앞의 것을 덮어써 조용히 사라집니다.
 */
class DiaryTodoSyncTest {
    @Test
    fun jsonKeepsEverythingTheScreenShows() {
        val snapshot = snapshotOf(
            diaries = listOf(
                DiaryEntity(
                    id = 3,
                    syncId = "diary-1",
                    updatedAt = 5_000L,
                    entryDate = "2026-08-12",
                    title = "오늘",
                    body = "적어 둔 내용",
                    weather = DiaryWeather.RAIN.name,
                    mood = DiaryMood.GOOD.name,
                    kind = DiaryKind.GRATITUDE.name,
                    answers = listOf("따뜻한 커피", "", "비 그친 하늘"),
                    imageUris = listOf("content://photo/1"),
                    videoUris = listOf("content://video/1"),
                    audioUris = listOf("content://audio/1"),
                    createdAt = 4_000L
                )
            ),
            todos = listOf(
                TodoEntity(
                    id = 7,
                    syncId = "todo-1",
                    updatedAt = 5_000L,
                    title = "우체국 가기",
                    note = "등기",
                    dueDate = "2026-08-12",
                    remindAt = 9_000L,
                    doneAt = 9_500L,
                    createdAt = 4_000L
                )
            )
        )

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertEquals(snapshot.diaries, restored.diaries)
        assertEquals(snapshot.todos, restored.todos)
    }

    @Test
    fun anUnknownWeatherOrMoodSurvivesTheRoundTrip() {
        // 다음 버전이 선택지를 늘리면 이 앱이 모르는 값이 들어온다.
        // 읽을 때 지워 버리면, 다음 업로드에서 서버와 다른 기기의 값까지 함께 지워진다.
        val snapshot = snapshotOf(
            diaries = listOf(
                DiaryEntity(
                    syncId = "diary-1",
                    entryDate = "2026-08-12",
                    weather = "AURORA",
                    mood = "SLEEPY"
                )
            )
        )

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        val diary = restored.diaries.single()
        assertEquals("AURORA", diary.weather)
        assertEquals("SLEEPY", diary.mood)
        // 화면에서는 고르지 않은 것으로 보입니다. 앱이 죽지 않는 것이 먼저입니다.
        assertNull(diary.weatherOption)
        assertNull(diary.moodOption)
    }

    @Test
    fun blankAnswersKeepTheirPlaceAcrossDevices() {
        // 빈칸을 버리고 보내면, 받은 기기에서는 마지막 답이 첫 물음의 답이 됩니다.
        val snapshot = snapshotOf(
            diaries = listOf(
                DiaryEntity(
                    syncId = "diary-1",
                    entryDate = "2026-08-12",
                    kind = DiaryKind.GRATITUDE.name,
                    answers = listOf("", "", "비 그친 하늘")
                )
            )
        )

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertEquals(listOf("", "", "비 그친 하늘"), restored.diaries.single().answers)
    }

    @Test
    fun aDiaryFromAnOlderDeviceIsReadAsAFreeDiary() {
        // 종류를 모르던 시절의 기기가 보낸 일기. 빈 값으로 두면 화면에서 아무 종류도 안 고른 것이 됩니다.
        val json = AppDataJson.toJson(snapshotOf()).toString().replace(
            "\"diaries\":[]",
            """"diaries":[{"syncId":"old","entryDate":"2026-08-12","body":"예전 일기"}]"""
        )

        val restored = AppDataJson.fromJsonText(json)

        assertEquals(DiaryKind.FREE.name, restored.diaries.single().kind)
        assertEquals(emptyList<String>(), restored.diaries.single().answers)
    }

    @Test
    fun anUnknownKindSurvivesTheRoundTrip() {
        // 다음 버전이 종류를 늘리면 이 앱이 모르는 값이 들어옵니다.
        // 읽을 때 FREE로 바꿔 버리면 다음 업로드에서 다른 기기의 종류까지 지워집니다.
        val snapshot = snapshotOf(
            diaries = listOf(
                DiaryEntity(syncId = "diary-1", entryDate = "2026-08-12", kind = "MORNING_PAGE")
            )
        )

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertEquals("MORNING_PAGE", restored.diaries.single().kind)
        // 화면에서는 자유 일기로 보입니다. 앱이 죽지 않는 것이 먼저입니다.
        assertEquals(DiaryKind.FREE, restored.diaries.single().kindOption)
    }

    @Test
    fun anUnsetAlarmStaysUnset() {
        // 0으로 돌아오면 1970년에 울린 알람, 1970년에 끝낸 일이 된다.
        val snapshot = snapshotOf(
            todos = listOf(TodoEntity(syncId = "todo-1", title = "물 사기", dueDate = "2026-08-12"))
        )

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertNull(restored.todos.single().remindAt)
        assertNull(restored.todos.single().doneAt)
    }

    @Test
    fun aDiaryWithoutADateIsDropped() {
        val json = AppDataJson.toJson(snapshotOf()).toString()
            .replace("\"diaries\":[]", """"diaries":[{"syncId":"bad","body":"날짜 없음"}]""")

        val restored = AppDataJson.fromJsonText(json)

        assertTrue("놓을 자리가 없는 일기는 버려야 합니다.", restored.diaries.isEmpty())
    }

    @Test
    fun reindexKeepsBothDevicesRecords() {
        // 두 기기가 각각 id=1을 쓴 상태로 병합되어 들어온 모양.
        val merged = snapshotOf(
            diaries = listOf(
                DiaryEntity(id = 1, syncId = "diary-a", entryDate = "2026-08-12", body = "A기기"),
                DiaryEntity(id = 1, syncId = "diary-b", entryDate = "2026-08-12", body = "B기기")
            ),
            todos = listOf(
                TodoEntity(id = 1, syncId = "todo-a", title = "A기기 할 일", dueDate = "2026-08-12"),
                TodoEntity(id = 1, syncId = "todo-b", title = "B기기 할 일", dueDate = "2026-08-12")
            )
        )

        val reindexed = SnapshotReindexer.reindex(merged)

        assertEquals(listOf(1L, 2L), reindexed.diaries.map { it.id })
        assertEquals(listOf(1L, 2L), reindexed.todos.map { it.id })
        assertEquals(
            "syncId는 그대로여야 다음 병합에서 같은 레코드로 인식됩니다.",
            listOf("todo-a", "todo-b"),
            reindexed.todos.map { it.syncId }
        )
    }

    @Test
    fun mergeTakesTheLatestDoneMark() {
        val local = snapshotOf(
            todos = listOf(
                TodoEntity(syncId = "todo-1", updatedAt = 1_000L, title = "우체국", dueDate = "2026-08-12")
            )
        )
        val remote = snapshotOf(
            todos = listOf(
                TodoEntity(
                    syncId = "todo-1",
                    updatedAt = 5_000L,
                    title = "우체국",
                    dueDate = "2026-08-12",
                    doneAt = 4_900L
                )
            )
        )

        val merged = SyncMerger.merge(local, remote)

        assertEquals("한쪽에서 끝냈으면 양쪽에서 끝난 것입니다.", 4_900L, merged.todos.single().doneAt)
    }

    @Test
    fun mergeHonoursDeletionMarks() {
        val local = snapshotOf(
            diaries = listOf(DiaryEntity(syncId = "diary-1", updatedAt = 1_000L, entryDate = "2026-08-12")),
            todos = listOf(TodoEntity(syncId = "todo-1", updatedAt = 1_000L, title = "지울 일", dueDate = "2026-08-12"))
        )
        val remote = snapshotOf(
            deletions = listOf(
                DeletionEntity("diary-1", SyncEntityType.DIARY, deletedAt = 2_000L),
                DeletionEntity("todo-1", SyncEntityType.TODO, deletedAt = 2_000L)
            )
        )

        val merged = SyncMerger.merge(local, remote)

        assertTrue("지운 일기가 되살아났습니다.", merged.diaries.isEmpty())
        assertTrue("지운 할 일이 되살아났습니다.", merged.todos.isEmpty())
    }

    @Test
    fun editingAfterDeletionWins() {
        // 지운 뒤 다른 기기에서 고쳤다면 그 수정이 이겨야 한다.
        val local = snapshotOf(
            todos = listOf(TodoEntity(syncId = "todo-1", updatedAt = 3_000L, title = "다시 살린 일", dueDate = "2026-08-12"))
        )
        val remote = snapshotOf(
            deletions = listOf(DeletionEntity("todo-1", SyncEntityType.TODO, deletedAt = 2_000L))
        )

        val merged = SyncMerger.merge(local, remote)

        assertEquals("다시 살린 일", merged.todos.single().title)
    }

    private fun snapshotOf(
        diaries: List<DiaryEntity> = emptyList(),
        todos: List<TodoEntity> = emptyList(),
        deletions: List<DeletionEntity> = emptyList()
    ) = AppDataSnapshot(
        items = emptyList(),
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings(),
        deletions = deletions,
        diaries = diaries,
        todos = todos
    )
}
