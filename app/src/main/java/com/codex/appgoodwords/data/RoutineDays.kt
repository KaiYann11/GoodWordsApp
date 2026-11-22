package com.codex.appgoodwords.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 요일 알약이 가리키는 날과, 지난날에 적는 체크.
 *
 * 요일 줄은 **오늘을 가운데 둔 이레**입니다(사용자가 정함, 2026-11-23 — "오늘 기준으로 앞뒤 요일 보여주고 뒤 요일은
 * 지난내용 다음 요일은 아무것도 입력되지 않은 상태로"). 오늘 앞 사흘·오늘·뒤 사흘을 날짜 차례로 늘어놓아 요일마다 알약이
 * 하나씩이고, 그래서 늘어놓는 요일 차례가 날마다 한 칸씩 돕니다. ‹ ›로 줄 전체를 이레씩 넘기고, 앞으로는 오늘 가운데 줄까지만
 * 갑니다.
 * - **오늘보다 앞 날**은 그날 체크를 보여 주고, 수행 +1·메모가 그날로 적힙니다(2026-11-18부터). 이레 앞 줄의 날도 같습니다.
 * - **오늘보다 뒤 날**은 아직 오지 않은 날입니다(2026-11-19). 그 요일에 하는 루틴만 보이고, 회수는 모두 0이며 적을 수
 *   없습니다. 지난주 같은 요일의 체크가 거기 비치지 않습니다.
 *
 * 앞선 모양: 1.24.1~1.25.0은 알약마다 그 요일이 마지막으로 있었던 날을 가리켜 오늘 뒤 요일에 지난주 기록이 보였고, 1.25.1은
 * 월~일 한 주라 일요일에는 일곱 칸이 모두 지난날, 월요일에는 모두 빈 칸이었습니다.
 *
 * 지난 시각으로 적은 체크는 "바뀐 것만" 보내는 동기화([SyncDelta])에 잡히지 않습니다. 적을 때 다음 동기화를 통째로
 * 보내게 해야 서버에 갑니다([SettingsStore.requestFullPush]).
 */
object RoutineDays {
    /** 요일 줄에서 오늘 앞에 놓는 날 수. 뒤에도 같은 수를 놓아 일곱 알약이 요일마다 하나가 됩니다. */
    const val DAYS_AROUND = 3

    /** 오늘을 가운데 둔 줄에서 [offset]주 떨어진 줄의 첫날. 0이면 오늘 사흘 전, -1이면 그보다 이레 앞입니다. */
    fun rowStart(today: LocalDate, offset: Int): LocalDate =
        today.minusDays(DAYS_AROUND.toLong()).plusWeeks(offset.toLong())

    /** [rowStart]로 시작하는 줄의 일곱 날. 날짜 차례이고 요일마다 하나씩입니다. */
    fun rowDays(rowStart: LocalDate): List<LocalDate> = (0L until 7L).map { rowStart.plusDays(it) }

    /** [rowStart]로 시작하는 줄에서 [day] 알약이 맡은 날. */
    fun dateIn(rowStart: LocalDate, day: DayOfWeek): LocalDate =
        rowStart.plusDays(((day.value - rowStart.dayOfWeek.value + 7) % 7).toLong())

    /**
     * 요일 줄 위에 적는 그 줄의 이름. "오늘 앞뒤 · 11월 19일 ~ 25일", "1주 전 · 11월 12일 ~ 18일", "3주 전 · 10월 29일 ~ 11월 4일".
     *
     * 해는 오늘과 다를 때만 적습니다. 줄이 해를 넘기면 끝날에도 해를 붙입니다("12월 28일 ~ 2027년 1월 3일").
     */
    fun rowLabel(rowStart: LocalDate, today: LocalDate): String {
        val weeksAgo = (ChronoUnit.DAYS.between(rowStart, rowStart(today, 0)) / 7).toInt()
        val name = when {
            weeksAgo == 0 -> "오늘 앞뒤"
            weeksAgo > 0 -> "${weeksAgo}주 전"
            else -> "${-weeksAgo}주 뒤"
        }
        val end = rowStart.plusDays(6)
        val from = (if (rowStart.year != today.year) "${rowStart.year}년 " else "") +
            "${rowStart.monthValue}월 ${rowStart.dayOfMonth}일"
        val to = when {
            end.year != rowStart.year -> "${end.year}년 ${end.monthValue}월 ${end.dayOfMonth}일"
            end.monthValue != rowStart.monthValue -> "${end.monthValue}월 ${end.dayOfMonth}일"
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
