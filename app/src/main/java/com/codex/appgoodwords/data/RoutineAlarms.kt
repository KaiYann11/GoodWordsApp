package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 시각을 정한 루틴을 언제 알릴지(사용자가 정함, 2026-11-17).
 *
 * - 정한 시각 **30분 전**에 한 번 알립니다.
 * - 시각이 지나도 그날 체크가 없으면 **1시간마다 세 번까지** 다시 알립니다. "그날이 끝날 때까지"가 아니라 세 번입니다
 *   (사용자가 골랐습니다). 그 뒤로는 조용합니다.
 * - 그날 체크했으면 남은 것은 오지 않습니다. 앱·위젯·알림 어디서 체크해도 같습니다.
 * - **자정을 넘기지 않습니다.** 자정이 지나면 체크가 다음 날 것으로 적혀, 지난날의 알림을 멈출 길이 없습니다.
 *   30분 전 알림만은 전날 밤에 올 수 있습니다(00:10 루틴은 23:40).
 *
 * 상시 루틴과 쉬는 요일에는 알리지 않습니다. 주기 알림([RoutineEntity.reminderEnabled])과는 따로입니다.
 * 알람은 하나만 걸고 울릴 때마다 다음 것을 다시 셉니다([com.codex.appgoodwords.work.RoutineAlarm]). 루틴마다 걸면 다른
 * 기기에서 지우거나 고친 루틴의 알람이 남습니다. 순수 함수라 기기 없이 검증합니다.
 */
object RoutineAlarms {
    const val LEAD_MINUTES = 30L
    const val REPEAT_MINUTES = 60L

    /** 시각이 지난 뒤 다시 알리는 횟수(사용자가 정함, 2026-11-17). */
    const val REPEAT_TIMES = 3

    /** 다음 알람을 찾을 때 볼 날수. 요일 규칙은 일주일마다 되풀이되고, 30분 전 알림이 전날 밤일 수 있어 하루를 더 봅니다. */
    private const val LOOKAHEAD_DAYS = 8L

    data class Alarm(
        val routine: RoutineEntity,
        /** 그 루틴을 하는 날. */
        val date: LocalDate,
        /** 울릴 때. */
        val at: Long,
        /** 정해 둔 시각 그 자체. */
        val timeAt: Long,
        /** 0이면 30분 전, 1~[REPEAT_TIMES]는 시각이 지난 뒤 몇 번째인지. */
        val repeat: Int,
        /** 그날이 끝나는 때. 알림은 이때 거둡니다 — 지나면 체크해도 그날 몫이 되지 않습니다. */
        val dayEndsAt: Long
    ) {
        val isBefore: Boolean
            get() = repeat == 0
    }

    /** 그 루틴이 [date]에 울릴 때들. 시각이 없거나 쉬는 요일이면 비어 있습니다. */
    fun alarmsOn(routine: RoutineEntity, date: LocalDate, zoneId: ZoneId): List<Alarm> {
        val minutes = RoutineSchedule.normalizedTime(routine.timeOfDay) ?: return emptyList()
        if (!RoutineSchedule.isOn(routine, date)) return emptyList()

        val time = date.atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(zoneId)
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId)
        fun alarm(at: ZonedDateTime, repeat: Int) = Alarm(
            routine = routine,
            date = date,
            at = at.toInstant().toEpochMilli(),
            timeAt = time.toInstant().toEpochMilli(),
            repeat = repeat,
            dayEndsAt = dayEnd.toInstant().toEpochMilli()
        )

        val before = alarm(time.minusMinutes(LEAD_MINUTES), 0)
        val after = (1..REPEAT_TIMES)
            .map { repeat -> repeat to time.plusMinutes(REPEAT_MINUTES * repeat) }
            .filter { (_, at) -> at.isBefore(dayEnd) }
            .map { (repeat, at) -> alarm(at, repeat) }
        return listOf(before) + after
    }

    /** [now] 뒤로 처음 울릴 때. 시각을 정한 루틴이 없으면 null입니다. */
    fun nextAt(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>,
        now: Long,
        zoneId: ZoneId
    ): Long? {
        val today = toDate(now, zoneId)
        val done = Done(checks, zoneId)
        return routines.asSequence()
            .flatMap { routine ->
                (0L..LOOKAHEAD_DAYS).asSequence().flatMap { alarmsOn(routine, today.plusDays(it), zoneId).asSequence() }
            }
            .filter { it.at > now && !done.has(it.routine, it.date) }
            .minOfOrNull { it.at }
    }

    /**
     * [from]부터 [to]까지 울렸어야 할 알림 — 루틴마다 가장 늦은 것 하나.
     *
     * 알람이 늦게 닿았거나(잠자기) 시계를 옮겼으면 여럿이 한꺼번에 지났을 수 있습니다. 같은 루틴의 알림은 한 자리에 덮어
     * 쓰므로 가장 늦은 것만 보이면 됩니다. 30분 전 알림이 그 시각을 넘겨 닿았으면 버립니다 — "30분 뒤"가 거짓이 됩니다.
     */
    fun due(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>,
        from: Long,
        to: Long,
        zoneId: ZoneId
    ): List<Alarm> {
        if (to < from) return emptyList()
        // 며칠씩 지난 것을 다 훑지 않습니다. 하루를 넘긴 알림은 어차피 그날이 끝나 거둡니다.
        val start = maxOf(from, to - LOOKAHEAD_DAYS * DAY_MILLIS)
        val firstDate = toDate(start, zoneId)
        val lastDate = toDate(to, zoneId).plusDays(1)
        val done = Done(checks, zoneId)
        return RoutineOrder.sorted(routines).mapNotNull { routine ->
            generateSequence(firstDate) { it.plusDays(1) }
                .takeWhile { !it.isAfter(lastDate) }
                .flatMap { alarmsOn(routine, it, zoneId).asSequence() }
                .filter { it.at in start..to && !done.has(routine, it.date) }
                .filterNot { it.isBefore && to >= it.timeAt }
                .filter { to < it.dayEndsAt }
                .maxByOrNull { it.at }
        }
    }

    /** [date]에 체크한 루틴의 숫자 id. 그 루틴의 그날 알림을 거둘 때 씁니다. */
    fun doneOn(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>,
        date: LocalDate,
        zoneId: ZoneId
    ): Set<Long> {
        val done = Done(checks, zoneId)
        return routines.filter { done.has(it, date) }.mapTo(mutableSetOf()) { it.id }
    }

    /** 알림의 한 줄. 못 한 것을 세지 않고, 몇 번째 알림인지만 적습니다(미리 보기에서 사용자가 본 꼴). */
    fun message(alarm: Alarm): String {
        val clock = RoutineSchedule.clockText(alarm.routine.timeOfDay)
        return if (alarm.isBefore) {
            "30분 뒤 $clock 루틴입니다."
        } else {
            "$clock 루틴 · 아직이면 지금 해도 됩니다 (${alarm.repeat}/$REPEAT_TIMES)"
        }
    }

    /**
     * 그날 체크한 루틴. **syncId가 있는 체크는 숫자 id를 보지 않습니다**([RoutineNudgeCalculator.restOfToday]와 같은 규칙) —
     * 다른 기기에서 온 체크의 숫자 id가 이쪽의 엉뚱한 루틴과 겹칠 수 있습니다.
     */
    private class Done(checks: List<RoutineCheckEntity>, zoneId: ZoneId) {
        private val bySyncId = mutableMapOf<LocalDate, MutableSet<String>>()
        private val byId = mutableMapOf<LocalDate, MutableSet<Long>>()

        init {
            for (check in checks) {
                val date = toDate(check.checkedAt, zoneId)
                if (check.routineSyncId.isNotBlank()) {
                    bySyncId.getOrPut(date) { mutableSetOf() } += check.routineSyncId
                } else {
                    byId.getOrPut(date) { mutableSetOf() } += check.routineId
                }
            }
        }

        fun has(routine: RoutineEntity, date: LocalDate): Boolean =
            bySyncId[date]?.contains(routine.syncId) == true || byId[date]?.contains(routine.id) == true
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}

private fun toDate(timestamp: Long, zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
