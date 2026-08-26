package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * AI 성장 피드백 설정.
 *
 * 열쇠는 이 기기에만 둡니다. 동기화 스냅샷과 백업 파일에는 넣지 않습니다.
 * 한 번 나가면 되돌릴 수 없고, 서버 DB나 백업 파일을 남에게 보여 줄 일이 생기기 때문입니다.
 */
data class AiFeedbackSettings(
    /** [AiProvider]의 이름. 모르는 값이 들어와도 버리지 않고 OpenAI로 읽습니다. */
    val provider: String = AiProvider.OPENAI.name,
    /** OpenAI 열쇠. 서버를 거쳐 부를 때는 비어 있어도 됩니다. */
    val openAiKey: String = "",
    /** Claude 열쇠. 공급자를 바꿔 볼 때 앞서 넣어 둔 열쇠가 지워지지 않게 따로 둡니다. */
    val anthropicKey: String = "",
    /** 비어 있으면 고른 공급자의 기본 모델. [effectiveModel]로 읽습니다. */
    val model: String = "",
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
    val activeProvider: AiProvider
        get() = AiProvider.of(provider)

    /** 지금 고른 공급자의 열쇠. */
    val activeKey: String
        get() = when (activeProvider) {
            AiProvider.OPENAI -> openAiKey
            AiProvider.ANTHROPIC -> anthropicKey
        }

    /**
     * 실제로 부를 모델.
     *
     * 공급자를 바꾸면 앞서 고른 모델은 그 공급자에 없는 이름입니다. 그대로 보내면
     * "모델을 찾을 수 없다"는 오류만 돌아오므로, 여기서 그 공급자의 기본으로 당겨 붙입니다.
     */
    val effectiveModel: String
        get() = activeProvider.models.firstOrNull { it == model.trim() } ?: activeProvider.defaultModel

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
        get() = activeKey.isNotBlank()

    /** 오늘 만들 차례인지. [lastRunAt]이 오늘 안이면 다시 만들지 않습니다. */
    fun isDue(now: Long, today: LocalDate, zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()): Boolean {
        val period = scheduledPeriod ?: return false
        if (lastRunAt <= 0L) return true
        val last = java.time.Instant.ofEpochMilli(lastRunAt).atZone(zoneId).toLocalDate()
        return last.plusDays(period.days.toLong()) <= today
    }

    /** 열쇠는 그대로 두고 공급자만 바꿉니다. 모델은 [effectiveModel]이 알아서 당겨 붙입니다. */
    fun withProvider(provider: AiProvider): AiFeedbackSettings =
        copy(provider = provider.name, model = "")

    /** 지금 고른 공급자의 열쇠 자리에만 넣습니다. */
    fun withKey(key: String): AiFeedbackSettings = when (activeProvider) {
        AiProvider.OPENAI -> copy(openAiKey = key.trim())
        AiProvider.ANTHROPIC -> copy(anthropicKey = key.trim())
    }

    companion object {
        const val DEFAULT_HOUR = 21
    }
}
