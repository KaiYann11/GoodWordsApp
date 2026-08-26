package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기분과 실천을 나란히 놓아 보는 셈입니다.
 *
 * 두 기록이 각각 있었을 뿐 겹쳐 본 적이 없었습니다. 다만 인과로 말하지 않도록,
 * 숫자가 실제로 무엇을 세는지가 분명해야 합니다.
 */
class MoodPracticeTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun aMoodNeedsMoreThanOneDayToBeCounted() {
        // 하루치로 "이런 날엔"이라고 말할 수 없습니다.
        val rows = build(diaries = listOf(diary(1, "2026-08-26", DiaryMood.GOOD)))

        assertTrue(rows.isEmpty())
    }

    @Test
    fun everyKindOfPracticeCountsTowardTheDay() {
        val rows = build(
            diaries = listOf(
                diary(1, "2026-08-25", DiaryMood.GOOD),
                diary(2, "2026-08-26", DiaryMood.GOOD)
            ),
            checks = listOf(check("2026-08-25"), check("2026-08-25")),
            events = listOf(confirmed("2026-08-26")),
            todos = listOf(doneTodo("2026-08-26"))
        )

        // 이틀에 네 번(루틴 2 + 글귀 1 + 할 일 1)이라 하루 평균 2입니다.
        assertEquals(1, rows.size)
        assertEquals(DiaryMood.GOOD, rows.single().mood)
        assertEquals(2, rows.single().dayCount)
        assertEquals(2f, rows.single().averagePerDay, 0.001f)
    }

    @Test
    fun practiceOnDaysWithoutADiaryIsNotCounted() {
        // 기분을 모르는 날의 실천은 어느 칸에도 넣을 수 없습니다.
        val rows = build(
            diaries = listOf(
                diary(1, "2026-08-25", DiaryMood.SAD),
                diary(2, "2026-08-26", DiaryMood.SAD)
            ),
            checks = listOf(check("2026-08-01"), check("2026-08-02"))
        )

        assertEquals(0f, rows.single().averagePerDay, 0.001f)
    }

    @Test
    fun theBusiestMoodComesFirst() {
        val rows = build(
            diaries = listOf(
                diary(1, "2026-08-20", DiaryMood.GOOD),
                diary(2, "2026-08-21", DiaryMood.GOOD),
                diary(3, "2026-08-24", DiaryMood.TIRED),
                diary(4, "2026-08-25", DiaryMood.TIRED)
            ),
            checks = listOf(check("2026-08-20"), check("2026-08-21"), check("2026-08-24"))
        )

        assertEquals(listOf(DiaryMood.GOOD, DiaryMood.TIRED), rows.map { it.mood })
        assertEquals(1f, rows.first().averagePerDay, 0.001f)
        assertEquals(0.5f, rows.last().averagePerDay, 0.001f)
    }

    @Test
    fun aDayWithTwoDifferentMoodsIsLeftOut() {
        // 같은 날 감사 일기는 좋음, 반성 일기는 지침이면 그날을 어느 쪽이라 할 수 없습니다.
        val rows = build(
            diaries = listOf(
                diary(1, "2026-08-25", DiaryMood.GOOD),
                diary(2, "2026-08-25", DiaryMood.TIRED),
                diary(3, "2026-08-26", DiaryMood.GOOD),
                diary(4, "2026-08-27", DiaryMood.GOOD)
            )
        )

        // 25일은 빠지고 26·27일만 남아 좋음이 이틀입니다.
        assertEquals(1, rows.size)
        assertEquals(2, rows.single().dayCount)
    }

    @Test
    fun daysWithoutAMoodAreIgnored() {
        val rows = build(
            diaries = listOf(
                diary(1, "2026-08-25", null),
                diary(2, "2026-08-26", null)
            )
        )

        assertTrue(rows.isEmpty())
    }

    private fun build(
        diaries: List<DiaryEntity> = emptyList(),
        checks: List<RoutineCheckEntity> = emptyList(),
        events: List<ExposureEventEntity> = emptyList(),
        todos: List<TodoEntity> = emptyList()
    ) = MoodPractice.build(diaries, checks, events, todos, zone)

    private fun diary(id: Long, date: String, mood: DiaryMood?) = DiaryEntity(
        id = id,
        syncId = "diary-$id",
        updatedAt = 0L,
        entryDate = date,
        body = "본문",
        mood = mood?.name.orEmpty(),
        createdAt = 0L
    )

    private fun millis(date: String): Long =
        LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun check(date: String) = RoutineCheckEntity(
        id = 0,
        syncId = "check-$date-${millis(date)}",
        routineId = 1,
        routineSyncId = "r1",
        routineTitle = "루틴",
        checkedAt = millis(date)
    )

    private fun confirmed(date: String) = ExposureEventEntity(
        id = 0,
        syncId = "event-$date",
        contentItemId = 1,
        contentItemSyncId = "i1",
        contentTitle = "글귀",
        contentType = ContentType.QUOTE,
        eventType = ExposureEventType.CONFIRMED,
        trigger = ExposureTrigger.MANUAL_REFRESH,
        occurredAt = millis(date)
    )

    private fun doneTodo(date: String) = TodoEntity(
        id = 0,
        syncId = "todo-$date",
        title = "할 일",
        dueDate = date,
        doneAt = millis(date)
    )
}
