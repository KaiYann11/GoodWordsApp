package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 마지막으로 돌아본 것이 언제인지, 너무 오래 뜸했는지.
 *
 * **기준은 남아 있는 돌아보기 자체입니다**(`GrowthReportEntity.createdAt`).
 * 설정의 `AiFeedbackSettings.lastRunAt`을 쓰지 않는 이유가 있습니다. 그것은 이 기기에만 남는
 * 값이라, 다른 기기에서 돌렸거나 백업을 되넣으면 실제와 어긋납니다. 돌아보기는 동기화되므로
 * 기록을 세면 어느 기기에서 돌렸든 같은 답이 나옵니다.
 *
 * 날짜로 셉니다. 사람이 "며칠 전"이라고 할 때는 시간 차가 아니라 넘긴 날짜 수를 말합니다.
 * 어젯밤에 돌렸으면 스물몇 시간이 안 지났어도 "어제"입니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object GrowthCadence {
    /** 이만큼 지나면 알립니다. 사람이 삶을 조정하는 단위가 주라서 이레로 둡니다. */
    const val OVERDUE_DAYS = 7L

    /** 마지막으로 돌아본 시각. 한 편도 없으면 null. */
    fun lastReviewedAt(reports: List<GrowthReportEntity>): Long? =
        reports.maxOfOrNull { it.createdAt }?.takeIf { it > 0L }

    /**
     * 마지막으로 돌아본 지 며칠 지났는지. 한 편도 없으면 null.
     *
     * 시계가 어긋나 앞선 시각이 들어와도 음수를 내지 않습니다. "-2일 전"은 읽을 수 없습니다.
     */
    fun daysSince(
        reports: List<GrowthReportEntity>,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val lastAt = lastReviewedAt(reports) ?: return null
        val last = dateOf(lastAt, zoneId)
        val today = dateOf(now, zoneId)
        return ChronoUnit.DAYS.between(last, today).coerceAtLeast(0L)
    }

    /**
     * 알릴 만큼 뜸한지.
     *
     * **한 편도 없으면 알리지 않습니다.** 앱을 막 깔아 아직 돌아볼 기록도 없는 사람에게
     * 첫날부터 "안 했다"고 알리면 그저 성가십니다. 아직 없다는 것은 화면에 적어 두고,
     * 시작은 사용자가 정합니다.
     */
    fun isOverdue(
        reports: List<GrowthReportEntity>,
        now: Long,
        thresholdDays: Long = OVERDUE_DAYS,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val days = daysSince(reports, now, zoneId) ?: return false
        return days >= thresholdDays
    }

    /**
     * 이번에 또 알려도 되는지.
     *
     * 뜸해진 뒤로 매일 알리면 잔소리가 됩니다. 한 번 알린 뒤에는 같은 간격만큼 쉽니다.
     */
    fun shouldNudgeAgain(
        lastNudgedAt: Long,
        now: Long,
        thresholdDays: Long = OVERDUE_DAYS,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (lastNudgedAt <= 0L) return true
        val since = ChronoUnit.DAYS.between(dateOf(lastNudgedAt, zoneId), dateOf(now, zoneId))
        return since >= thresholdDays
    }

    private fun dateOf(timestamp: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
}
