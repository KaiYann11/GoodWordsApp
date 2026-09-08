package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 달력 한 칸이 아는 것.
 *
 * 돌아보기에는 두 가지 날짜가 있습니다. **돌린 날**과 **다뤄진 날**입니다.
 * 지난 한 주를 오늘 돌아봤다면, 오늘은 돌린 날이고 지난 이레는 다뤄진 날입니다.
 * 둘을 한 칸에 섞어 칠하면 "언제 돌아봤나"와 "어디까지 들여다봤나"를 가릴 수 없습니다.
 */
data class GrowthCalendarDay(
    val date: LocalDate,
    /** 이날 만든 돌아보기 편수. */
    val ranCount: Int,
    /** 이날을 기간에 품은 돌아보기 편수. */
    val coveredCount: Int
) {
    val ran: Boolean
        get() = ranCount > 0

    val covered: Boolean
        get() = coveredCount > 0

    /**
     * 아직 한 번도 들여다보지 않은 날.
     *
     * 이것이 이 달력의 값어치입니다. 돌아본 날을 세는 것보다, **빠뜨린 구간이 눈에 보이는 것**이
     * 다음에 무엇을 돌아볼지 정하게 해 줍니다.
     */
    val untouched: Boolean
        get() = !ran && !covered
}

data class GrowthCalendarMonth(
    val month: YearMonth,
    val days: List<GrowthCalendarDay>
) {
    val ranDays: Int
        get() = days.count { it.ran }

    val coveredDays: Int
        get() = days.count { it.covered }

    /** 이 달에서 아직 아무 돌아보기도 다루지 않은 날수. */
    val untouchedDays: Int
        get() = days.count { it.untouched }
}

/**
 * 돌아보기를 달력에 펼칩니다.
 *
 * 목록만 있으면 "요즘 자주 돌아봤나", "어느 구간을 빠뜨렸나"를 알 수 없습니다. 날짜에 놓아야
 * 비어 있는 자리가 보입니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object GrowthCalendar {
    fun month(
        reports: List<GrowthReportEntity>,
        month: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): GrowthCalendarMonth {
        val ranByDate = reports
            .mapNotNull { report -> report.createdAt.takeIf { it > 0L }?.let { toDate(it, zoneId) } }
            .groupingBy { it }
            .eachCount()

        // 기간은 하루하루 펼치지 않고 그때그때 견줍니다. 한 달이 서른 칸뿐이라 이 편이 간단하고,
        // 여러 해치 리포트를 미리 펼치면 쓰지도 않을 날짜를 잔뜩 만듭니다.
        val ranges = reports.mapNotNull { report ->
            val start = parseDate(report.periodStart) ?: return@mapNotNull null
            val end = parseDate(report.periodEnd) ?: return@mapNotNull null
            // 시작과 끝이 뒤집혀 들어와도 읽습니다. 손으로 붙여 넣은 답에서 그럴 수 있습니다.
            if (start <= end) start to end else end to start
        }

        val days = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            GrowthCalendarDay(
                date = date,
                ranCount = ranByDate[date] ?: 0,
                coveredCount = ranges.count { (start, end) -> date >= start && date <= end }
            )
        }

        return GrowthCalendarMonth(month = month, days = days)
    }

    /** 그날에 매인 돌아보기. 돌린 것과 다룬 것을 함께 봅니다. */
    fun reportsOn(
        reports: List<GrowthReportEntity>,
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<GrowthReportEntity> = reports.filter { report ->
        val ranHere = report.createdAt > 0L && toDate(report.createdAt, zoneId) == date
        val start = parseDate(report.periodStart)
        val end = parseDate(report.periodEnd)
        val coversHere = start != null && end != null &&
            date >= minOf(start, end) && date <= maxOf(start, end)
        ranHere || coversHere
    }

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim()) }.getOrNull()

    private fun toDate(timestamp: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
}
