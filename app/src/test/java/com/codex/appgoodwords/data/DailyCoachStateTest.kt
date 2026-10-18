package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 언제 다시 물을지.
 *
 * 값이 드는 요청이라 이 규칙이 곧 요금입니다. 루틴을 연달아 다섯 개 체크하면 오늘 기록도
 * 다섯 번 바뀌는데, 그때마다 물으면 사용자가 한 일이 그대로 돈이 됩니다.
 */
class DailyCoachStateTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val noon = 1_760_000_000_000L
    private val minute = 60_000L

    @Test
    fun theFirstAskOfTheDayGoesRightAway() {
        assertTrue(DailyCoachState().needsAsk("abc", noon))
    }

    @Test
    fun theSameRecordIsNotAskedTwice() {
        // 기록이 그대로인데 다시 물으면 거의 같은 말이 돌아오고 값만 한 번 더 듭니다.
        val state = asked().withTip(tip(), "abc")

        assertFalse(state.needsAsk("abc", noon + 10 * 60 * minute))
    }

    @Test
    fun aNewRecordWaitsForTheGap() {
        val state = asked().withTip(tip(), "abc")

        assertFalse(state.needsAsk("def", noon + 10 * minute))
        assertTrue(state.needsAsk("def", noon + AiFeedbackSettings.DEFAULT_GAP_MINUTES * minute))
    }

    @Test
    fun aDayWithNothingInItIsNotAsked() {
        // 빈 지문은 "오늘 한 것이 없다"는 뜻입니다.
        assertFalse(DailyCoachState().needsAsk("", noon))
    }

    @Test
    fun thereIsACeilingForTheDay() {
        var state = DailyCoachState()
        repeat(AiFeedbackSettings.DEFAULT_ASK_LIMIT) { index ->
            state = state.asked(today, noon + index * AiFeedbackSettings.DEFAULT_GAP_MINUTES * minute)
        }

        assertEquals(AiFeedbackSettings.DEFAULT_ASK_LIMIT, state.askCount)
        assertFalse(state.needsAsk("새 기록", noon + 10 * 60 * minute))
    }

    @Test
    fun failingCountsToo() {
        // 안 되는 설정으로 하루 종일 두드리지 않습니다. 사유는 그대로 남습니다.
        val state = DailyCoachState().asked(today, noon).withError("열쇠가 없습니다.")

        assertEquals(1, state.askCount)
        assertEquals("열쇠가 없습니다.", state.lastError)
        // 지문은 적지 않습니다. 같은 기록으로 틈을 두고 한 번 더 가 볼 수 있어야 합니다.
        assertEquals("", state.fingerprint)
        assertTrue(state.needsAsk("abc", noon + AiFeedbackSettings.DEFAULT_GAP_MINUTES * minute))
    }

    @Test
    fun theLimitAndTheGapAreWhateverWasChosen() {
        // 값이 드는 정도는 사람마다 다르게 느낍니다. 앱이 정해 주지 않습니다.
        var state = DailyCoachState()
        repeat(2) { index -> state = state.asked(today, noon + index * 30 * minute) }

        // 하루 2번으로 줄여 두었으면 거기서 멈춥니다.
        assertFalse(state.needsAsk("새 기록", noon + 10 * 60 * minute, limit = 2, gapMinutes = 30))
        assertTrue(state.needsAsk("새 기록", noon + 10 * 60 * minute, limit = 5, gapMinutes = 30))

        // 틈을 30분으로 줄여 두었으면 30분 뒤에 갑니다.
        val once = DailyCoachState().asked(today, noon)
        assertFalse(once.needsAsk("새 기록", noon + 20 * minute, limit = 5, gapMinutes = 30))
        assertTrue(once.needsAsk("새 기록", noon + 30 * minute, limit = 5, gapMinutes = 30))
    }

    @Test
    fun turningTheLimitOffStopsTheAutomaticPath() {
        // "안 함"으로 두면 저절로는 묻지 않습니다. 눌러서 받는 길은 그대로 있습니다
        // (`force`는 needsAsk를 거치지 않습니다).
        assertFalse(DailyCoachState().needsAsk("abc", noon, limit = 0, gapMinutes = 30))
    }

    @Test
    fun aReasonWrittenBeforeTheFirstAskSurvivesToTheCard() {
        // 아직 한 번도 묻지 않은 날에 "닿을 길이 없다"를 적었습니다. 날짜가 비어 있으면
        // 화면이 다시 읽으며 오늘 것이 아니라고 버려서, 눌러도 아무 일도 없어 보입니다.
        val written = DailyCoachState().forDate(today).withError("AI에 닿을 길이 없습니다.")

        assertEquals("AI에 닿을 길이 없습니다.", written.forDate(today).lastError)
    }

    @Test
    fun yesterdaysWordIsNotShownToday() {
        val yesterday = DailyCoachState().asked(today.minusDays(1), noon).withTip(tip(), "abc")

        val fresh = yesterday.forDate(today)
        assertNull(fresh.tip)
        assertEquals(0, fresh.askCount)
        // 어제 다섯 번 물었어도 오늘은 처음부터입니다.
        assertTrue(fresh.needsAsk("abc", noon))
    }

    @Test
    fun aNewWordClearsWhatWasKept() {
        // 담은 표시가 남아 있으면 새 글귀를 담을 버튼이 사라진 채로 뜹니다.
        val state = asked().withTip(tip(), "abc").copy(quoteKept = true, routineKept = true, actionKept = true)

        val next = state.withTip(tip(message = "다음 한마디"), "def")
        assertFalse(next.quoteKept)
        assertFalse(next.routineKept)
        assertFalse(next.actionKept)
        assertEquals("", next.lastError)
    }

    @Test
    fun whatWasSavedComesBackTheSame() {
        val state = asked()
            .withTip(tip(), "abc")
            .copy(quoteKept = true, lastError = "")

        assertEquals(state, DailyCoachJson.decode(DailyCoachJson.encode(state)))
    }

    @Test
    fun unreadableStorageIsAnEmptyDayNotACrash() {
        assertEquals(DailyCoachState(), DailyCoachJson.decode(""))
        assertEquals(DailyCoachState(), DailyCoachJson.decode("{망가진 글"))
        assertNull(DailyCoachJson.decode("""{"date":"어제"}""").date)
    }

    private fun asked() = DailyCoachState().asked(today, noon)

    private fun tip(message: String = "물 한 컵으로 하루를 여셨네요.") = DailyCoachTip(
        message = message,
        quote = "작은 일을 소홀히 하지 않는다",
        quoteAuthor = "",
        routine = "저녁 스트레칭",
        action = "자기 전 물 한 컵",
        model = "claude-opus-5",
        createdAt = noon
    )
}
