package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * AI 성장 피드백 설정.
 *
 * 열쇠([apiKey])는 이 기기에만 둡니다. 동기화 스냅샷과 백업 파일에는 넣지 않습니다.
 * 한 번 나가면 되돌릴 수 없고, 서버 DB나 백업 파일을 남에게 보여 줄 일이 생기기 때문입니다.
 */
data class AiFeedbackSettings(
    /** OpenAI 열쇠. 서버를 거쳐 부를 때는 비어 있어도 됩니다. */
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    /** [ReportPeriod]의 이름, 또는 꺼짐을 뜻하는 빈 문자열. */
    val schedule: String = "",
    /** 주기가 돌아온 날 이 시각에 만듭니다. */
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = 0,
    /**
     * 일기 본문까지 보낼지.
     *
     * 기본은 꺼짐입니다. 일기는 앱에서 가장 사적인 기록이라, 켜는 것은 사용자가 직접 정해야 합니다.
     */
    val includeDiaryBody: Boolean = false,
    /** 마지막으로 피드백을 만든 시각. 주기가 돌아왔는지 볼 때 씁니다. */
    val lastRunAt: Long = 0L,
    /** 마지막 실패 사유. 배경에서 돌다 실패하면 화면에 뜨지 않아 따로 남깁니다. */
    val lastError: String = ""
) {
    val scheduledPeriod: ReportPeriod?
        get() = ReportPeriod.entries.firstOrNull { it.name == schedule && it != ReportPeriod.MANUAL }

    val isScheduled: Boolean
        get() = scheduledPeriod != null

    val runTime: LocalTime
        get() = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    /**
     * 서버를 거치지 않고 이 기기가 바로 부를 수 있는지.
     *
     * 서버 주소가 있으면 서버를 먼저 씁니다. 열쇠가 한 곳에만 있으면 기기를 새로 붙일 때마다
     * 다시 넣지 않아도 되기 때문입니다.
     */
    val canCallDirectly: Boolean
        get() = apiKey.isNotBlank()

    /** 오늘 만들 차례인지. [lastRunAt]이 오늘 안이면 다시 만들지 않습니다. */
    fun isDue(now: Long, today: LocalDate, zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()): Boolean {
        val period = scheduledPeriod ?: return false
        if (lastRunAt <= 0L) return true
        val last = java.time.Instant.ofEpochMilli(lastRunAt).atZone(zoneId).toLocalDate()
        return last.plusDays(period.days.toLong()) <= today
    }

    companion object {
        /** 값이 싸고 이 정도 글에는 충분합니다. 설정에서 바꿀 수 있습니다. */
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_HOUR = 21
        val MODEL_CHOICES = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini")
    }
}
