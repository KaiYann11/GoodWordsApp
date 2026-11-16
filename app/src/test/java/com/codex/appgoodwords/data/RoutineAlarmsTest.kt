package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 시각을 정한 루틴을 언제 알리는지(사용자가 정함, 2026-11-17) — 30분 전에 한 번, 지나도 안 했으면 1시간마다 세 번까지.
 *
 * 특히 **이미 한 루틴을 다시 알리지 않는지**와 **자정을 넘겨 지난날을 알리지 않는지**를 봅니다. 둘 다 틀리면 기능이
 * 아니라 잔소리가 됩니다.
 */
class RoutineAlarmsTest {
    private val zone = ZoneId.of("Asia/Seoul")

    /** 2026-11-18은 수요일입니다. */
    private val wednesday = LocalDate.of(2026, 11, 18)

    private val gym = RoutineEntity(
        id = 1,
        syncId = "gym",
        title = "헬스장",
        weekdays = RoutineSchedule.maskOf(listOf(1, 3, 5)),
        timeOfDay = 19 * 60
    )

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun check(routine: RoutineEntity, at: Long, withSyncId: Boolean = true) = RoutineCheckEntity(
        syncId = "check-${routine.id}-$at",
        routineId = routine.id,
        routineSyncId = if (withSyncId) routine.syncId else "",
        routineTitle = routine.title,
        checkedAt = at
    )

    @Test
    fun halfAnHourBeforeThenHourlyThreeTimes() {
        val alarms = RoutineAlarms.alarmsOn(gym, wednesday, zone)

        assertEquals(
            listOf(at(wednesday, 18, 30), at(wednesday, 20), at(wednesday, 21), at(wednesday, 22)),
            alarms.map { it.at }
        )
        assertEquals(listOf(0, 1, 2, 3), alarms.map { it.repeat })
        assertTrue(alarms.first().isBefore)
    }

    @Test
    fun aDayOffOrAnAnytimeRoutineIsNeverAlarmed() {
        assertEquals(emptyList<RoutineAlarms.Alarm>(), RoutineAlarms.alarmsOn(gym, wednesday.plusDays(1), zone))
        assertEquals(emptyList<RoutineAlarms.Alarm>(), RoutineAlarms.alarmsOn(gym.copy(timeOfDay = null), wednesday, zone))
    }

    @Test
    fun theHourlyOnesStopAtMidnight() {
        // 자정이 지나면 체크가 다음 날 것으로 적혀, 지난날의 알림을 멈출 길이 없습니다.
        val late = gym.copy(timeOfDay = 22 * 60 + 30)
        assertEquals(
            listOf(at(wednesday, 22), at(wednesday, 23, 30)),
            RoutineAlarms.alarmsOn(late, wednesday, zone).map { it.at }
        )

        // 30분 전 알림만은 전날 밤에 옵니다.
        val justAfterMidnight = gym.copy(timeOfDay = 10)
        assertEquals(
            at(wednesday.minusDays(1), 23, 40),
            RoutineAlarms.alarmsOn(justAfterMidnight, wednesday, zone).first().at
        )
    }

    @Test
    fun theNextAlarmIsTheNearestOneStillToCome() {
        assertEquals(at(wednesday, 18, 30), RoutineAlarms.nextAt(listOf(gym), emptyList(), at(wednesday, 12), zone))
        // 울린 그 시각에 다시 셀 때는 그 다음 것입니다.
        assertEquals(at(wednesday, 20), RoutineAlarms.nextAt(listOf(gym), emptyList(), at(wednesday, 18, 30), zone))
        // 수요일 몫이 끝났으면 쉬는 목요일을 건너 금요일입니다.
        assertEquals(
            at(wednesday.plusDays(2), 18, 30),
            RoutineAlarms.nextAt(listOf(gym), emptyList(), at(wednesday, 22), zone)
        )
        assertNull(RoutineAlarms.nextAt(listOf(gym.copy(timeOfDay = null)), emptyList(), at(wednesday, 12), zone))
    }

    @Test
    fun aRoutineAlreadyDoneThatDayIsNotAlarmedAgain() {
        // 18:45에 했으면 그날의 20·21·22시는 오지 않습니다. 다음은 금요일 18:30입니다.
        val checks = listOf(check(gym, at(wednesday, 18, 45)))

        assertEquals(
            at(wednesday.plusDays(2), 18, 30),
            RoutineAlarms.nextAt(listOf(gym), checks, at(wednesday, 18, 45), zone)
        )
        assertEquals(emptyList<RoutineAlarms.Alarm>(), RoutineAlarms.due(listOf(gym), checks, at(wednesday, 20), at(wednesday, 20), zone))
        // 일찍(30분 전 알림보다 먼저) 했으면 30분 전 알림도 오지 않습니다.
        val early = listOf(check(gym, at(wednesday, 7)))
        assertEquals(at(wednesday.plusDays(2), 18, 30), RoutineAlarms.nextAt(listOf(gym), early, at(wednesday, 8), zone))
    }

    @Test
    fun aCheckFindsItsRoutineTheWayTheTodayListDoes() {
        // syncId가 있는 체크는 숫자 id를 보지 않습니다 — 다른 기기에서 온 체크의 숫자 id가 이쪽 루틴과 겹칠 수 있습니다.
        val stranger = RoutineCheckEntity(
            syncId = "other-device",
            routineId = gym.id,
            routineSyncId = "someone-else",
            routineTitle = "남의 루틴",
            checkedAt = at(wednesday, 18, 45)
        )
        assertEquals(1, RoutineAlarms.due(listOf(gym), listOf(stranger), at(wednesday, 20), at(wednesday, 20), zone).size)

        // 한 번도 동기화하지 않은 기기의 옛 체크(syncId 없음)는 숫자 id로 찾습니다.
        val legacy = check(gym, at(wednesday, 18, 45), withSyncId = false)
        assertEquals(emptyList<RoutineAlarms.Alarm>(), RoutineAlarms.due(listOf(gym), listOf(legacy), at(wednesday, 20), at(wednesday, 20), zone))
        assertEquals(setOf(gym.id), RoutineAlarms.doneOn(listOf(gym), listOf(legacy), wednesday, zone))
    }

    @Test
    fun aLateAlarmShowsOnlyTheLatestOneAndNeverAStaleHalfHourNotice() {
        // 잠자기로 늦게 닿았으면 그사이 지난 것 중 가장 늦은 하나만 알립니다(같은 자리에 덮어 씁니다).
        val late = RoutineAlarms.due(listOf(gym), emptyList(), at(wednesday, 18, 30), at(wednesday, 21, 5), zone)
        assertEquals(listOf(2), late.map { it.repeat })

        // 19시를 넘겨 닿은 "30분 뒤"는 거짓말이라 버립니다.
        assertEquals(emptyList<RoutineAlarms.Alarm>(), RoutineAlarms.due(listOf(gym), emptyList(), at(wednesday, 18, 30), at(wednesday, 19, 10), zone))
        assertEquals(listOf(0), RoutineAlarms.due(listOf(gym), emptyList(), at(wednesday, 18, 30), at(wednesday, 18, 31), zone).map { it.repeat })

        // 자정을 넘겨 닿았으면 그날 것은 알리지 않습니다.
        assertEquals(
            emptyList<RoutineAlarms.Alarm>(),
            RoutineAlarms.due(listOf(gym), emptyList(), at(wednesday, 22), at(wednesday.plusDays(1), 0, 10), zone)
        )
    }

    @Test
    fun routinesDueTogetherAreAllShownInTheirOrder() {
        val reading = RoutineEntity(id = 2, syncId = "reading", title = "독서", orderIndex = 1, timeOfDay = 20 * 60 + 30)
        val first = gym.copy(orderIndex = 0)

        val due = RoutineAlarms.due(listOf(reading, first), emptyList(), at(wednesday, 20), at(wednesday, 20), zone)

        // 헬스장은 지난 뒤 첫 번째, 독서는 30분 전입니다.
        assertEquals(listOf("헬스장", "독서"), due.map { it.routine.title })
        assertEquals(listOf(1, 0), due.map { it.repeat })
    }

    @Test
    fun theLineSaysWhichReminderItIsWithoutCountingWhatIsLeft() {
        val alarms = RoutineAlarms.alarmsOn(gym, wednesday, zone)

        assertEquals("30분 뒤 19:00 루틴입니다.", RoutineAlarms.message(alarms[0]))
        assertEquals("19:00 루틴 · 아직이면 지금 해도 됩니다 (2/3)", RoutineAlarms.message(alarms[2]))
    }
}
