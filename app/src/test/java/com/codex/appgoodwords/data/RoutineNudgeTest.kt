package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 아직 안 밟은 루틴을 두고 건네는 한 줄.
 *
 * **AI를 부르지 않습니다.** 그래서 지킬 것이 하나 더 있습니다 — 값이 들지 않는 말이라도
 * 다그치면 안 됩니다. 남은 개수를 세지 않고 **다음 하나만** 말하는지, 그리고 이어 오던 것이
 * 있으면 그쪽을 먼저 짚는지를 봅니다.
 */
class RoutineNudgeTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 18)

    @Test
    fun theNextOneInMyOwnOrderIsNamed() {
        val nudge = build(
            routines = listOf(routine(1, "물 한 컵", order = 0), routine(2, "저녁 산책", order = 1)),
            checks = listOf(check(1, "물 한 컵", today))
        )!!

        assertEquals(RoutineNudge.Kind.NEXT, nudge.kind)
        assertEquals(2L, nudge.routineId)
        assertEquals("다음 차례는 저녁 산책이에요.", nudge.message)
    }

    @Test
    fun whatIsLeftIsNotCounted() {
        // "3개 남았습니다"는 할 일 목록처럼 읽혀 오히려 손이 무거워집니다.
        val nudge = build(
            routines = listOf(routine(1, "물 한 컵", 0), routine(2, "산책", 1), routine(3, "스트레칭", 2)),
            checks = emptyList()
        )!!

        assertFalse(nudge.message, nudge.message.contains("3"))
        assertFalse(nudge.message, nudge.message.contains("남"))
        assertEquals("물 한 컵", nudge.title)
    }

    @Test
    fun whatIsBeingKeptComesFirst() {
        // 차례로는 뒤에 있어도, 이어 오던 것을 지키는 쪽이 훨씬 큰 힘이 됩니다.
        val nudge = build(
            routines = listOf(routine(1, "물 한 컵", 0), routine(2, "저녁 산책", 1)),
            checks = listOf(
                check(2, "저녁 산책", today.minusDays(1)),
                check(2, "저녁 산책", today.minusDays(2)),
                check(2, "저녁 산책", today.minusDays(3))
            )
        )!!

        assertEquals(RoutineNudge.Kind.STREAK, nudge.kind)
        assertEquals(2L, nudge.routineId)
        assertEquals(3, nudge.streakDays)
        assertEquals("저녁 산책이(가) 3일째 이어지고 있어요. 오늘 한 번이면 이어집니다.", nudge.message)
    }

    @Test
    fun oneDayIsNotCalledAStreak() {
        // 어제 한 번 밟은 것을 두고 "이어지고 있다"고 하면 지킬 것이 있는 척이 됩니다.
        val nudge = build(
            routines = listOf(routine(1, "물 한 컵", 0), routine(2, "저녁 산책", 1)),
            checks = listOf(check(2, "저녁 산책", today.minusDays(1)))
        )!!

        assertEquals(RoutineNudge.Kind.NEXT, nudge.kind)
        assertEquals(1L, nudge.routineId)
    }

    @Test
    fun aFinishedDayGetsAWordToo() {
        val nudge = build(
            routines = listOf(routine(1, "물 한 컵", 0)),
            checks = listOf(check(1, "물 한 컵", today))
        )!!

        assertEquals(RoutineNudge.Kind.ALL_DONE, nudge.kind)
        assertFalse(nudge.hasPlaceToGo)
        assertEquals("오늘 루틴을 모두 밟았습니다.", nudge.message)
    }

    @Test
    fun withoutAnyRoutineThereIsNothingToSay() {
        // 적어 둔 루틴이 없는 사람에게 "루틴을 만들어 보세요"라고 하지 않습니다.
        assertNull(build(routines = emptyList(), checks = emptyList()))
    }

    @Test
    fun checksAreMatchedBySyncId() {
        // 숫자 id로 묶으면 다른 기기에서 온 남의 기록이 섞입니다.
        val routines = listOf(routine(1, "물 한 컵", 0), routine(2, "저녁 산책", 1))
        val fromAnotherDevice = RoutineCheckEntity(
            syncId = "c-other",
            // 저쪽 기기에서는 id 1이 "저녁 산책"이었습니다.
            routineId = 1,
            routineSyncId = "r2",
            routineTitle = "저녁 산책",
            checkedAt = at(today)
        )

        val nudge = build(routines = routines, checks = listOf(fromAnotherDevice))!!

        assertEquals("물 한 컵", nudge.title)
    }

    @Test
    fun anOldCheckWithoutASyncIdStillCounts() {
        // 아직 한 번도 동기화하지 않은 기기에는 syncId가 빈 옛 체크가 있습니다.
        val old = RoutineCheckEntity(
            syncId = "c-old",
            routineId = 1,
            routineSyncId = "",
            routineTitle = "물 한 컵",
            checkedAt = at(today)
        )

        val nudge = build(
            routines = listOf(routine(1, "물 한 컵", 0), routine(2, "저녁 산책", 1)),
            checks = listOf(old)
        )!!

        assertEquals("저녁 산책", nudge.title)
    }

    @Test
    fun yesterdaysCheckDoesNotCountAsToday() {
        val nudge = build(
            routines = listOf(routine(1, "물 한 컵", 0)),
            checks = listOf(check(1, "물 한 컵", today.minusDays(1)))
        )!!

        // 어제 밟았다고 오늘 다 한 것이 되지 않습니다. 하루치로는 "이어지고 있다"고도
        // 하지 않으므로 그냥 다음 차례입니다.
        assertEquals(RoutineNudge.Kind.NEXT, nudge.kind)
        assertEquals(1L, nudge.routineId)
    }

    @Test
    fun theListSentToTheAiIsTheSameOne() {
        // 화면의 한 줄과 AI에게 보내는 목록이 다른 루틴을 가리키면 안 됩니다.
        val routines = listOf(routine(1, "물 한 컵", 0), routine(2, "저녁 산책", 1), routine(3, "스트레칭", 2))
        val todayChecks = listOf(check(1, "물 한 컵", today))

        val rest = RoutineNudgeCalculator.restOfToday(routines, todayChecks)

        assertEquals(listOf("저녁 산책", "스트레칭"), rest.map { it.title })
        assertEquals(rest.first().id, build(routines, todayChecks)!!.routineId)
    }

    @Test
    fun theOrderIsMyOrderNotTheOrderTheyWereMade() {
        // 루틴에는 하루에 밟는 차례가 있습니다(`RoutineOrder`).
        val nudge = build(
            routines = listOf(routine(1, "나중 것", order = 5), routine(2, "먼저 것", order = 0)),
            checks = emptyList()
        )!!

        assertEquals("먼저 것", nudge.title)
        assertTrue(nudge.hasPlaceToGo)
    }

    private fun build(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>
    ): RoutineNudge? = RoutineNudgeCalculator.build(routines, checks, today, zone)

    private fun at(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun routine(id: Long, title: String, order: Int) =
        RoutineEntity(id = id, syncId = "r$id", title = title, orderIndex = order)

    private fun check(routineId: Long, title: String, date: LocalDate) = RoutineCheckEntity(
        syncId = "c-$routineId-$date",
        routineId = routineId,
        routineSyncId = "r$routineId",
        routineTitle = title,
        checkedAt = at(date)
    )
}
