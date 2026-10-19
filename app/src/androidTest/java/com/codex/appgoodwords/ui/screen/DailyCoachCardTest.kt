package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.data.DailyCoachState
import com.codex.appgoodwords.data.DailyCoachTip
import com.codex.appgoodwords.data.RoutineNudge
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 오늘의 한마디 카드.
 *
 * 홈에서 스치듯 보는 자리입니다. 그래서 두 가지를 봅니다 — **권할 것이 없으면 그 자리를
 * 비우는지**(없는 글귀·루틴 자리에 빈 칸이 생기면 카드가 늘 어수선합니다), 그리고
 * **한 번 담은 것은 다시 담지 못하게 버튼을 거두는지**(두 번 눌리면 두 벌이 생깁니다).
 */
class DailyCoachCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.now()

    private fun state(
        tip: DailyCoachTip? = null,
        lastError: String = "",
        quoteKept: Boolean = false,
        routineKept: Boolean = false,
        actionKept: Boolean = false
    ) = DailyCoachState(
        date = today,
        tip = tip,
        lastError = lastError,
        quoteKept = quoteKept,
        routineKept = routineKept,
        actionKept = actionKept
    )

    private fun tip(
        message: String = "물 한 컵으로 하루를 여셨네요.",
        quote: String = "",
        quoteAuthor: String = "",
        routine: String = "",
        action: String = ""
    ) = DailyCoachTip(
        message = message,
        quote = quote,
        quoteAuthor = quoteAuthor,
        routine = routine,
        action = action,
        model = "claude-opus-5",
        createdAt = System.currentTimeMillis()
    )

    @Test
    fun theWordIsWhatTheCardIsFor() {
        compose.setContent { DailyCoachCard(state = state(tip = tip())) }

        compose.onNodeWithText("물 한 컵으로 하루를 여셨네요.").assertIsDisplayed()
        compose.onNodeWithTag(dailyCoachCardTag).assertExists()
    }

    @Test
    fun nothingToRecommendLeavesNoEmptyRows() {
        compose.setContent { DailyCoachCard(state = state(tip = tip())) }

        compose.onNodeWithTag(dailyCoachQuoteTag).assertDoesNotExist()
        compose.onNodeWithTag(dailyCoachRoutineTag).assertDoesNotExist()
        compose.onNodeWithTag(dailyCoachActionTag).assertDoesNotExist()
    }

    @Test
    fun whatIsRecommendedCanBeKept() {
        var keptQuote = false
        var keptRoutine = false
        var keptAction = false
        compose.setContent {
            DailyCoachCard(
                state = state(tip = tip(quote = "작게 시작하라", quoteAuthor = "작자 미상", routine = "저녁 스트레칭", action = "자기 전 물 한 컵")),
                onKeepQuote = { keptQuote = true },
                onKeepRoutine = { keptRoutine = true },
                onKeepAction = { keptAction = true }
            )
        }

        compose.onNodeWithTag(dailyCoachQuoteTag).performClick()
        compose.onNodeWithTag(dailyCoachActionTag).performClick()
        compose.onNodeWithTag(dailyCoachRoutineTag).performClick()

        assertTrue(keptQuote)
        assertTrue(keptRoutine)
        assertTrue(keptAction)
    }

    @Test
    fun whatWasKeptIsNotKeptTwice() {
        // 스치듯 누르는 자리라, 버튼이 남아 있으면 같은 루틴이 두 개 생깁니다.
        compose.setContent {
            DailyCoachCard(
                state = state(
                    tip = tip(quote = "작게 시작하라", routine = "저녁 스트레칭", action = "자기 전 물 한 컵"),
                    quoteKept = true,
                    routineKept = true,
                    actionKept = true
                )
            )
        }

        compose.onNodeWithTag(dailyCoachQuoteTag).assertDoesNotExist()
        compose.onNodeWithTag(dailyCoachRoutineTag).assertDoesNotExist()
        compose.onNodeWithTag(dailyCoachActionTag).assertDoesNotExist()
        compose.onNodeWithText("보관함에 담았습니다").assertIsDisplayed()
        compose.onNodeWithText("루틴에 담았습니다").assertIsDisplayed()
    }

    @Test
    fun aMorningWithNothingInItIsNotScolded() {
        // 아직 아무것도 안 한 아침입니다. 못 했다고 말하지 않습니다.
        compose.setContent { DailyCoachCard(state = state(), hasRecordToday = false) }

        compose.onNodeWithText("오늘 한 걸음을 밟으면 그것을 보고 한마디 건네 드립니다.").assertIsDisplayed()
    }

    @Test
    fun whyItIsSilentIsSaidOnTheCard() {
        // 저절로 도는 일이라 받아 줄 화면이 없습니다. 사유가 여기 없으면 아무 데도 없습니다.
        compose.setContent { DailyCoachCard(state = state(lastError = "AI에 닿을 길이 없습니다.")) }

        compose.onNodeWithText("AI에 닿을 길이 없습니다.").assertIsDisplayed()
    }

    @Test
    fun theRoutineLineShowsWithoutAnyAiAnswer() {
        // AI에 닿지 않아도, 하루 물을 횟수를 다 썼어도 이 줄은 뜹니다. 값이 들지 않는 쪽입니다.
        compose.setContent {
            DailyCoachCard(
                state = state(),
                hasRecordToday = false,
                nudge = RoutineNudge(
                    kind = RoutineNudge.Kind.STREAK,
                    routineId = 7,
                    title = "저녁 산책",
                    streakDays = 5,
                    message = "저녁 산책이(가) 5일째 이어지고 있어요. 오늘 한 번이면 이어집니다."
                )
            )
        }

        compose.onNodeWithText("저녁 산책이(가) 5일째 이어지고 있어요. 오늘 한 번이면 이어집니다.").assertIsDisplayed()
        // 루틴 줄이 제 몫을 하므로 "한 걸음 밟으면" 같은 빈 자리 문구를 겹쳐 두지 않습니다.
        compose.onNodeWithText("오늘 한 걸음을 밟으면 그것을 보고 한마디 건네 드립니다.").assertDoesNotExist()
    }

    @Test
    fun theRoutineLineLeadsToTheRoutines() {
        var opened = false
        compose.setContent {
            DailyCoachCard(
                state = state(),
                nudge = RoutineNudge(RoutineNudge.Kind.NEXT, 7, "저녁 산책", 0, "다음 차례는 저녁 산책이에요."),
                onOpenRoutines = { opened = true }
            )
        }

        compose.onNodeWithTag(dailyCoachNudgeOpenTag).performClick()

        assertTrue(opened)
    }

    @Test
    fun aFinishedDayHasNowhereToGo() {
        compose.setContent {
            DailyCoachCard(
                state = state(),
                nudge = RoutineNudge(RoutineNudge.Kind.ALL_DONE, 0, "", 0, "오늘 루틴을 모두 밟았습니다.")
            )
        }

        compose.onNodeWithText("오늘 루틴을 모두 밟았습니다.").assertIsDisplayed()
        compose.onNodeWithTag(dailyCoachNudgeOpenTag).assertDoesNotExist()
    }

    @Test
    fun turningTheRoutineLineOffLeavesTheCardToTheAi() {
        compose.setContent { DailyCoachCard(state = state(tip = tip()), nudge = null) }

        compose.onNodeWithTag(dailyCoachNudgeTag).assertDoesNotExist()
        compose.onNodeWithText("물 한 컵으로 하루를 여셨네요.").assertIsDisplayed()
    }

    @Test
    fun askingAgainIsOneTap() {
        var asked = false
        compose.setContent { DailyCoachCard(state = state(tip = tip()), onRefresh = { asked = true }) }

        compose.onNodeWithTag(dailyCoachRefreshTag).performClick()

        assertTrue(asked)
    }

    @Test
    fun whileAskingTheButtonIsOutOfReach() {
        // 기다리는 동안 다시 눌리면 같은 물음이 두 번 나갑니다.
        compose.setContent { DailyCoachCard(state = state(tip = tip()), loading = true) }

        compose.onNodeWithTag(dailyCoachRefreshTag).assertDoesNotExist()
    }

    @Test
    fun aNewWordStandsOutAndIsMarkedSeenAfterItIsShown() {
        // 평범한 카드 하나로는 왔는지조차 몰랐습니다. 새 것은 눈에 띄게, 다 보여 준 뒤에는 거둡니다.
        var seen = 0
        compose.setContent {
            DailyCoachCard(state = state().withTip(tip(), "abc"), onSeen = { seen += 1 })
        }

        compose.onNodeWithTag(dailyCoachNewBadgeTag).assertIsDisplayed()
        // 글자가 다 써지고 잠시 보여 준 뒤에 본 것으로 적습니다.
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        compose.onNodeWithText("물 한 컵으로 하루를 여셨네요.").assertIsDisplayed()
        assertTrue("본 것으로 적지 않았습니다", seen == 1)
    }

    @Test
    fun aWordAlreadySeenDoesNotFlash() {
        compose.setContent { DailyCoachCard(state = state(tip = tip())) }

        compose.onNodeWithTag(dailyCoachNewBadgeTag).assertDoesNotExist()
    }
}
