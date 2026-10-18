package com.codex.appgoodwords.data

import java.time.LocalDate
import org.json.JSONObject

/**
 * 오늘 하루를 보고 받은 한마디.
 *
 * 돌아보기([GrowthReportEntity])와 다릅니다. 돌아보기는 지난 기간을 정리해 **남겨 두는 글**이고,
 * 이것은 하루가 흘러가는 동안 곁에서 건네는 말입니다. 그래서 레코드로 쌓지 않고 이 기기에만
 * 둡니다 — 자정이면 버립니다.
 *
 * 남길 만한 것은 **사용자가 집어 가야 남습니다.** 글귀는 보관함으로, 루틴은 루틴으로,
 * 해 볼 일은 할 일로 담깁니다. 집어 간 것만 동기화되는 진짜 기록이 됩니다.
 */
data class DailyCoachTip(
    /** 오늘 한 것에서 하나를 짚어 주는 말. 늘 있습니다. */
    val message: String = "",
    /** 지금 곁에 둘 만한 글귀. 권할 것이 없으면 빈 값입니다. */
    val quote: String = "",
    val quoteAuthor: String = "",
    /** 새로 만들어 볼 만한 루틴 이름. 대개 비어 있습니다. */
    val routine: String = "",
    /** 오늘 안에 해 볼 작은 한 가지. */
    val action: String = "",
    /** 누가 썼는지. CLI 기본 모델이면 공급자 이름입니다([AiFeedbackSettings.reportModel]). */
    val model: String = "",
    val createdAt: Long = 0L
) {
    val hasContent: Boolean
        get() = message.isNotBlank() || quote.isNotBlank() || routine.isNotBlank() || action.isNotBlank()
}

/**
 * 오늘 코치와 주고받은 것 전부.
 *
 * **이 기기에만 둡니다**([SettingsStore]). 동기화 스냅샷에 넣지 않는 이유는 두 가지입니다.
 * 자정이면 버리는 것이라 새 레코드 종류를 만들면 삭제 표식·병합·중복 합치기가 하루짜리
 * 글을 위해 따라붙고, 기기마다 묻는 때가 달라 합칠 기준도 없습니다.
 *
 * 물어본 횟수와 마지막으로 물은 때를 함께 들고 다닙니다. 값이 드는 요청이라
 * "언제 다시 물을지"를 정하려면 그 둘이 있어야 합니다([needsAsk]).
 */
data class DailyCoachState(
    /** 어느 날의 것인지. 날이 바뀌면 [forDate]가 비웁니다. */
    val date: LocalDate? = null,
    val tip: DailyCoachTip? = null,
    /**
     * 그 한마디를 받을 때 본 오늘 기록의 지문([DailyCoachDigest.fingerprint]).
     *
     * 같은 지문으로 두 번 묻지 않습니다. 기록이 그대로인데 다시 물으면 거의 같은 말이
     * 돌아오고, 값만 한 번 더 듭니다.
     */
    val fingerprint: String = "",
    /** 오늘 몇 번 물었는지. **실패도 셉니다** — 안 되는 설정으로 하루 종일 두드리지 않습니다. */
    val askCount: Int = 0,
    val lastAskedAt: Long = 0L,
    /** 마지막 실패 사유. 저절로 도는 일이라 화면에 뜨지 않아 여기에 남깁니다. */
    val lastError: String = "",
    /** 집어 간 것. 두 번 눌러 같은 루틴이 두 개 생기지 않게 합니다. */
    val quoteKept: Boolean = false,
    val routineKept: Boolean = false,
    val actionKept: Boolean = false
) {
    /**
     * 오늘의 것만 남깁니다.
     *
     * 자정에 지우는 일꾼을 따로 두지 않습니다. 읽을 때마다 날짜를 보면 되는 일이라,
     * 앱이 꺼져 있던 날에도 틀리지 않습니다.
     *
     * **비울 때도 날짜는 오늘로 적습니다.** 날짜 없는 빈 상태를 돌려주면, 거기에 실패 사유를
     * 적어 저장해도 화면이 다시 읽으며 "오늘 것이 아니다"라고 버립니다 — 눌러도 아무 일도
     * 일어나지 않는 것처럼 보입니다.
     */
    fun forDate(today: LocalDate): DailyCoachState =
        if (date == today) this else DailyCoachState(date = today)

    /**
     * 저절로 다시 물을 때인지.
     *
     * 무엇을 했는지 바뀌었을 때만, 그것도 [gapMinutes]분에 한 번까지만 묻습니다.
     * 루틴을 연달아 다섯 개 체크하면 지문이 다섯 번 바뀌는데, 그때마다 물으면
     * 사용자가 한 일이 그대로 요금이 됩니다.
     *
     * **몇 번까지, 얼마 만에 물을지는 사용자가 정합니다**([AiFeedbackSettings.askLimit] ·
     * [AiFeedbackSettings.gapMinutes]). 여기 적어 두지 않는 이유는, 값이 드는 정도를
     * 사람마다 다르게 느끼기 때문입니다.
     *
     * @param fingerprint 지금 오늘 기록의 지문. 오늘 한 것이 없으면 빈 값입니다.
     * @param limit 하루에 저절로 물을 횟수. 0이면 저절로는 묻지 않습니다.
     */
    fun needsAsk(
        fingerprint: String,
        now: Long,
        limit: Int = AiFeedbackSettings.DEFAULT_ASK_LIMIT,
        gapMinutes: Long = AiFeedbackSettings.DEFAULT_GAP_MINUTES.toLong()
    ): Boolean {
        // 오늘 한 것이 없으면 물어볼 거리가 없습니다. 빈 기록으로 물으면 누구에게나 같은
        // "오늘을 시작해 보세요"가 돌아옵니다. 그 말은 앱이 공짜로 할 수 있습니다.
        if (fingerprint.isBlank()) return false
        if (askCount >= limit) return false
        // 실패했을 때는 지문을 적어 두지 않으므로, 같은 기록이라도 틈을 두고 한 번 더 갑니다.
        if (fingerprint == this.fingerprint) return false
        return now - lastAskedAt >= gapMinutes * 60_000L
    }

    /** 물어본 사실만 먼저 적습니다. 실패해도 남아야 다음 물음이 틈을 둡니다. */
    fun asked(today: LocalDate, now: Long): DailyCoachState {
        val fresh = forDate(today)
        return fresh.copy(askCount = fresh.askCount + 1, lastAskedAt = now)
    }

    fun withTip(tip: DailyCoachTip, fingerprint: String): DailyCoachState = copy(
        tip = tip,
        fingerprint = fingerprint,
        lastError = "",
        quoteKept = false,
        routineKept = false,
        actionKept = false
    )

    /** 지문은 적지 않습니다. 같은 기록으로 한 번 더 가 볼 수 있어야 합니다. */
    fun withError(message: String): DailyCoachState = copy(lastError = message)

}

/**
 * 한마디를 이 기기에 적어 두는 꼴.
 *
 * 설정([SettingsStore])에 글 한 덩어리로 넣습니다. 칸을 늘릴 때마다 열쇠를 새로 만들면
 * 하루짜리 글 때문에 설정이 자꾸 늘어납니다. 읽을 수 없는 글이 들어 있어도 비운 상태로
 * 돌려줍니다 — 오늘 한마디를 못 읽는 것보다 앱이 서지 않는 쪽이 나쁩니다.
 */
object DailyCoachJson {
    fun encode(state: DailyCoachState): String {
        val json = JSONObject()
            .put("date", state.date?.toString().orEmpty())
            .put("fingerprint", state.fingerprint)
            .put("askCount", state.askCount)
            .put("lastAskedAt", state.lastAskedAt)
            .put("lastError", state.lastError)
            .put("quoteKept", state.quoteKept)
            .put("routineKept", state.routineKept)
            .put("actionKept", state.actionKept)
        state.tip?.let { tip ->
            json.put(
                "tip",
                JSONObject()
                    .put("message", tip.message)
                    .put("quote", tip.quote)
                    .put("quoteAuthor", tip.quoteAuthor)
                    .put("routine", tip.routine)
                    .put("action", tip.action)
                    .put("model", tip.model)
                    .put("createdAt", tip.createdAt)
            )
        }
        return json.toString()
    }

    fun decode(stored: String): DailyCoachState {
        if (stored.isBlank()) return DailyCoachState()
        val json = runCatching { JSONObject(stored) }.getOrNull() ?: return DailyCoachState()
        val tip = json.optJSONObject("tip")?.let { saved ->
            DailyCoachTip(
                message = saved.optString("message"),
                quote = saved.optString("quote"),
                quoteAuthor = saved.optString("quoteAuthor"),
                routine = saved.optString("routine"),
                action = saved.optString("action"),
                model = saved.optString("model"),
                createdAt = saved.optLong("createdAt")
            )
        }
        return DailyCoachState(
            date = runCatching { LocalDate.parse(json.optString("date")) }.getOrNull(),
            tip = tip?.takeIf { it.hasContent },
            fingerprint = json.optString("fingerprint"),
            askCount = json.optInt("askCount"),
            lastAskedAt = json.optLong("lastAskedAt"),
            lastError = json.optString("lastError"),
            quoteKept = json.optBoolean("quoteKept"),
            routineKept = json.optBoolean("routineKept"),
            actionKept = json.optBoolean("actionKept")
        )
    }
}
