package com.codex.appgoodwords.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 요일 알약이 가리키는 날과, 지난날에 적는 체크.
 *
 * 요일 줄은 **한 주**입니다(사용자가 정함, 2026-11-19 — "일주일 중 과거 기록만 보여지는 개념"). 월~일 일곱 알약이 그 주의
 * 날을 하나씩 맡습니다. 처음에는 이번 주이고, ‹ ›로 한 주씩 넘깁니다. 앞으로는 이번 주까지만 갑니다.
 * - **오늘보다 앞 날**은 그날 체크를 보여 주고, 수행 +1·메모가 그날로 적힙니다(2026-11-18부터). 지난주·그 앞 주의 날도 같습니다.
 * - **오늘보다 뒤 날**(이번 주의 남은 날)은 아직 오지 않은 날입니다. 그 요일에 하는 루틴만 보이고, 회수는 모두 0이며 적을 수
 *   없습니다. 지난주 같은 요일의 체크가 거기 비치지 않습니다 — 1.24.1은 오늘 뒤 요일이 지난주 날을 가리켜서 지난주 기록이
 *   보였습니다.
 *
 * 지난 시각으로 적은 체크는 "바뀐 것만" 보내는 동기화([SyncDelta])에 잡히지 않습니다. 적을 때 다음 동기화를 통째로
 * 보내게 해야 서버에 갑니다([SettingsStore.requestFullPush]).
 */
object RoutineDays {
    /** [date]가 든 주의 첫날(월요일). 주는 요일 차례([RoutineStatsCalculator.WEEK_ORDER])의 첫 요일에서 시작합니다. */
    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays(RoutineStatsCalculator.WEEK_ORDER.indexOf(date.dayOfWeek).toLong())

    /** 오늘이 든 주에서 [offset]주 떨어진 주의 첫날. 0이면 이번 주, -1이면 지난주입니다. */
    fun weekOf(today: LocalDate, offset: Int): LocalDate = weekStart(today).plusWeeks(offset.toLong())

    /** [weekStart]로 시작하는 주의 [day]. */
    fun dateIn(weekStart: LocalDate, day: DayOfWeek): LocalDate =
        weekStart.plusDays(RoutineStatsCalculator.WEEK_ORDER.indexOf(day).toLong())

    /**
     * 요일 줄 위에 적는 그 주의 이름. "이번 주 · 11월 16일 ~ 22일", "지난주 · 11월 9일 ~ 15일", "3주 전 · 10월 26일 ~ 11월 1일".
     *
     * 해는 오늘과 다를 때만 적습니다. 주가 해를 넘기면 끝날에도 해를 붙입니다("12월 28일 ~ 2027년 1월 3일").
     */
    fun weekLabel(weekStart: LocalDate, today: LocalDate): String {
        val weeksAgo = (ChronoUnit.DAYS.between(weekStart, weekStart(today)) / 7).toInt()
        val name = when {
            weeksAgo == 0 -> "이번 주"
            weeksAgo == 1 -> "지난주"
            weeksAgo > 1 -> "${weeksAgo}주 전"
            else -> "${-weeksAgo}주 뒤"
        }
        val end = weekStart.plusDays(6)
        val from = (if (weekStart.year != today.year) "${weekStart.year}년 " else "") +
            "${weekStart.monthValue}월 ${weekStart.dayOfMonth}일"
        val to = when {
            end.year != weekStart.year -> "${end.year}년 ${end.monthValue}월 ${end.dayOfMonth}일"
            end.monthValue != weekStart.monthValue -> "${end.monthValue}월 ${end.dayOfMonth}일"
            else -> "${end.dayOfMonth}일"
        }
        return "$name · $from ~ $to"
    }

    /**
     * [date]에 적을 체크의 시각. 오늘이면 지금 그대로입니다.
     *
     * 지난날이면 그날의 지금 시각을 빌립니다 — 실제로 몇 시에 했는지는 알 수 없습니다. 두 번 눌러도 서로 다른 시각이 되고,
     * 날짜는 그날을 벗어나지 않습니다.
     */
    fun checkTime(date: LocalDate, now: Long, zoneId: ZoneId): Long {
        val clock = Instant.ofEpochMilli(now).atZone(zoneId)
        if (clock.toLocalDate() == date) return now
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return date.atTime(clock.toLocalTime()).atZone(zoneId).toInstant().toEpochMilli().coerceIn(start, end)
    }

    /** [from]~[to] 날마다 루틴별 체크 수. 체크가 없는 날은 담지 않습니다. */
    fun countsByDate(
        checks: List<RoutineCheckEntity>,
        from: LocalDate,
        to: LocalDate,
        zoneId: ZoneId
    ): Map<LocalDate, Map<Long, Int>> {
        if (to.isBefore(from)) return emptyMap()
        val start = from.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return checks
            .filter { it.checkedAt in start until end }
            .groupBy { Instant.ofEpochMilli(it.checkedAt).atZone(zoneId).toLocalDate() }
            .mapValues { (_, dayChecks) -> dayChecks.groupingBy { it.routineId }.eachCount() }
    }

    /** "16일(월)". 진행 카드·스낵바·메모 창이 같은 말로 그날을 부릅니다. */
    fun label(date: LocalDate): String = "${date.dayOfMonth}일(${RoutineSchedule.shortLabel(date.dayOfWeek)})"

    /** 오늘이면 "오늘", 아니면 [label]. */
    fun spoken(date: LocalDate, today: LocalDate): String = if (date == today) "오늘" else label(date)
}

/** 체크 한 번. [syncId]로 되돌리고, [count]는 그날 그 루틴을 몇 번 했는지입니다. */
data class RoutineCheckResult(
    val syncId: String,
    val date: LocalDate,
    val count: Int
)
