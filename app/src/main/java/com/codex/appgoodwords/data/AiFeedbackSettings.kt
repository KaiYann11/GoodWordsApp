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
    val lastError: String = "",
    /**
     * 오래 돌아보지 않았을 때 알릴지.
     *
     * 주기 자동 실행([schedule])과 별개입니다. 자동 실행은 값이 드는 요청을 저절로 보내는 것이고,
     * 이쪽은 "직접 한번 돌아보라"고 알리기만 합니다. 자동을 꺼 둔 사람에게 더 필요합니다.
     */
    val nudgeEnabled: Boolean = true,
    /** 마지막으로 알린 시각. 뜸해진 뒤 매일 알리면 잔소리가 되므로 이만큼 쉽니다. */
    val lastNudgedAt: Long = 0L,
    /**
     * 오늘 하루를 보고 한마디를 받을지([DailyCoachCoordinator]).
     *
     * **기본은 꺼짐입니다.** 하루에 여러 번 물을 수 있는 길이라, 켜는 것은 사용자가 정해야
     * 합니다. 돌아보기 주기([schedule])와 따로 둡니다 — 한 달에 한 번 돌아보면서도
     * 오늘은 곁에 두고 싶을 수 있고, 그 반대도 마찬가지입니다.
     */
    val coachEnabled: Boolean = false,
    /**
     * 하루에 **저절로** 물을 횟수. 0이면 저절로는 묻지 않습니다.
     *
     * 사용자가 직접 "다시 받기"를 누르는 것은 여기 세지 않습니다 — 자기가 눌러 자기 몫을
     * 쓰는 것이라 앱이 말릴 일이 아닙니다. 0으로 두면 **가벼운 알림만** 받는 셈이 됩니다.
     */
    val coachAskLimit: Int = DEFAULT_ASK_LIMIT,
    /** 저절로 다시 묻기까지 비워 두는 시간(분). 너무 짧으면 한 일이 그대로 요금이 됩니다. */
    val coachGapMinutes: Int = DEFAULT_GAP_MINUTES,
    /**
     * 아직 안 밟은 루틴을 한 줄로 알려 줄지([RoutineNudge]).
     *
     * **기본은 켜짐입니다.** AI를 부르지 않고 기기에서 세는 것이라 값이 들지 않고,
     * 연결이 없어도 뜹니다. 끄면 오늘의 한마디 카드에는 AI가 써 준 것만 남습니다.
     */
    val coachNudgeEnabled: Boolean = true
) {
    val activeProvider: AiProvider
        get() = AiProvider.of(provider)

    /** 지금 고른 공급자의 열쇠. 서버가 대신 묻는 공급자(CLI·Ollama)에는 열쇠가 없습니다. */
    val activeKey: String
        get() = when (activeProvider) {
            AiProvider.OPENAI -> openAiKey
            AiProvider.ANTHROPIC -> anthropicKey
            AiProvider.CLAUDE_CLI, AiProvider.CODEX_CLI, AiProvider.OLLAMA -> ""
        }

    /**
     * 실제로 부를 모델.
     *
     * 공급자를 바꾸면 앞서 고른 모델은 그 공급자에 없는 이름입니다. 그대로 보내면
     * "모델을 찾을 수 없다"는 오류만 돌아오므로, 여기서 그 공급자의 기본으로 당겨 붙입니다.
     * CLI·Ollama의 기본은 빈 값이고, 그러면 서버가 CLI에 설정해 둔 모델(Ollama는 서버가 고른 모델)로 돌립니다.
     */
    val effectiveModel: String
        get() = activeProvider.models.firstOrNull { it == model.trim() } ?: activeProvider.defaultModel

    /**
     * 돌아보기에 적어 둘 "누가 썼는지".
     *
     * CLI·Ollama 기본 모델로 돌리면 [effectiveModel]이 빈 값이라 무엇이 썼는지 남지 않습니다.
     * 그때는 공급자 이름을 적습니다.
     */
    val reportModel: String
        get() = effectiveModel.ifBlank { activeProvider.label }

    /** 서버가 있어야만 부를 수 있는지. CLI와 Ollama는 서버 PC에 있고 이 기기에는 없습니다. */
    val requiresServer: Boolean
        get() = activeProvider.serverOnly

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
     * 다시 넣지 않아도 되기 때문입니다. CLI·Ollama는 이 기기에서 부를 길이 없습니다.
     */
    val canCallDirectly: Boolean
        get() = !requiresServer && activeKey.isNotBlank()

    /**
     * 지금 설정으로 AI에 닿을 수 있는지.
     *
     * [AiFeedbackClient.ask]가 길을 고르는 규칙과 같아야 합니다. 어긋나면 "받을 수 있다"고
     * 해 놓고 부를 때마다 실패하거나, 부를 수 있는데도 조용히 아무것도 하지 않습니다.
     */
    fun canAsk(hasServer: Boolean): Boolean =
        if (requiresServer) hasServer else hasServer || canCallDirectly

    /** 저장된 값이 어떻든 셈에 쓸 수 있는 횟수. 옛 설정이나 손으로 고친 값이 들어와도 섭니다. */
    val askLimit: Int
        get() = coachAskLimit.coerceIn(0, MAX_ASK_LIMIT)

    /**
     * 저절로 다시 묻기까지 비워 둘 시간.
     *
     * **바닥을 둡니다.** 0분이면 오늘 한 것이 바뀔 때마다 묻게 되어, 루틴을 연달아 밟은 날
     * 그 횟수가 그대로 요금이 됩니다.
     */
    val gapMinutes: Long
        get() = coachGapMinutes.coerceAtLeast(MIN_GAP_MINUTES).toLong()

    /** 저절로 묻는 길을 아예 닫아 두었는지. 눌러서 받는 길은 그대로 있습니다. */
    val asksOnlyWhenAsked: Boolean
        get() = askLimit == 0

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

    /** 지금 고른 공급자의 열쇠 자리에만 넣습니다. 서버가 대신 묻는 공급자에는 넣을 자리가 없어 그대로 둡니다. */
    fun withKey(key: String): AiFeedbackSettings = when (activeProvider) {
        AiProvider.OPENAI -> copy(openAiKey = key.trim())
        AiProvider.ANTHROPIC -> copy(anthropicKey = key.trim())
        AiProvider.CLAUDE_CLI, AiProvider.CODEX_CLI, AiProvider.OLLAMA -> this
    }

    companion object {
        const val DEFAULT_HOUR = 21

        /**
         * 하루에 저절로 묻는 횟수의 기본값.
         *
         * 아침·낮·오후·저녁·밤이면 하루를 따라가기에 넉넉합니다.
         */
        const val DEFAULT_ASK_LIMIT = 5

        /** 더 크게 고를 수 없게 하는 천장. 없으면 설정 한 번으로 하루 요금이 끝없이 커집니다. */
        const val MAX_ASK_LIMIT = 20

        const val DEFAULT_GAP_MINUTES = 90

        /** 아무리 짧게 잡아도 이만큼은 비웁니다(알림 간격 `ReminderSettings`와 같은 바닥). */
        const val MIN_GAP_MINUTES = 15

        /** 설정 화면에서 고를 수 있는 것들. 0은 "저절로는 묻지 않음"입니다. */
        val ASK_LIMIT_CHOICES = listOf(0, 1, 3, 5, 10)

        val GAP_MINUTE_CHOICES = listOf(30, 60, 90, 180)
    }
}
