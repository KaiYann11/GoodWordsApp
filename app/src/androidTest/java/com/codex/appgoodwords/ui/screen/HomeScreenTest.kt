package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DailyCoachState
import com.codex.appgoodwords.data.DailyCoachTip
import com.codex.appgoodwords.data.DailyProgress
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.FeedbackKind
import com.codex.appgoodwords.data.FeedbackNote
import com.codex.appgoodwords.data.StatsCalculator
import com.codex.appgoodwords.data.StatsSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 홈은 돌아보는 자리입니다.
 *
 * 예전에는 여기에도 글귀 목록이 있어서 보관함과 같은 일을 두 곳에서 했습니다.
 * 지금은 오늘 할 일, 짚어 주는 문구, 통계만 둡니다.
 */
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val emptySummary: StatsSummary = StatsCalculator.build(
        events = emptyList(),
        items = emptyList(),
        routineChecks = emptyList(),
        today = LocalDate.now()
    )

    @Test
    fun overallAndRoutineStatisticsAreOnTheSameScreen() {
        compose.setContent { homeScreen() }

        compose.onNodeWithText("돌아보기").assertDoesNotExist()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(homeInsightsToggleTag))
        compose.onNodeWithTag(homeInsightsToggleTag).performClick()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasText("돌아보기"))
        compose.onNodeWithText("돌아보기").assertIsDisplayed()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasText("달성률 - (0/0)"))
        compose.onNodeWithText("달성률 - (0/0)").assertIsDisplayed()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(homeInsightsToggleTag))
        compose.onNodeWithTag(homeInsightsToggleTag).performClick()
        compose.onNodeWithText("돌아보기").assertDoesNotExist()
    }

    @Test
    fun theFeedbackLinesAreShownAsWritten() {
        compose.setContent {
            homeScreen(
                notes = listOf(
                    FeedbackNote("5일째 이어 가고 있습니다.", FeedbackKind.STREAK),
                    FeedbackNote("'물 한 컵'은 이레 내내 지켰습니다.", FeedbackKind.PRAISE)
                )
            )
        }

        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(feedbackCardTag))
        compose.onNodeWithTag(feedbackCardTag).assertIsDisplayed()
        compose.onNodeWithText("5일째 이어 가고 있습니다.").assertIsDisplayed()
        compose.onNodeWithText("'물 한 컵'은 이레 내내 지켰습니다.").assertIsDisplayed()
    }

    @Test
    fun anEmptyFeedbackCardStillSaysSomething() {
        // 카드만 덩그러니 비어 있으면 고장인지 비어 있는 것인지 알 수 없습니다.
        compose.setContent { homeScreen(notes = emptyList()) }

        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(feedbackCardTag))
        compose.onNodeWithText("기록이 쌓이면 여기에 짚어 드릴 말이 생깁니다.").assertIsDisplayed()
    }

    @Test
    fun theQuoteStepGoesToTheLibraryNow() {
        // 글귀 읽기가 보관함으로 옮겨 갔으니, 걸음도 그리로 데려가야 합니다.
        // 여기서 아무 데도 가지 않으면 사용자는 눌러 보고 나서 직접 찾아 들어가야 합니다.
        var opened: DailyStep? = null
        compose.setContent {
            homeScreen(
                dailyLoop = DailyProgress(
                    steps = DailyStep.DEFAULTS,
                    doneSteps = emptySet(),
                    streakDays = 0,
                    bestStreakDays = 0
                ),
                onOpenStep = { opened = it }
            )
        }

        compose.onNodeWithTag(dailyLoopStepTag(DailyStep.QUOTE)).performClick()

        assertEquals(DailyStep.QUOTE, opened)
    }

    @Test
    fun theVisibleCaptureButtonWorksWithoutDiscoveringTheSwipe() {
        var opened = false
        compose.setContent { homeScreen(onSwipeToCapture = { opened = true }) }

        compose.onNodeWithTag(homeCaptureButtonTag).assertIsDisplayed().performClick()

        assertTrue(opened)
    }

    @Test
    fun swipingLeftOpensTheCaptureField() {
        // 번뜩인 것은 담으러 들어가는 사이에 날아갑니다. 홈에서 바로 열립니다.
        var swiped = false
        compose.setContent { homeScreen(onSwipeToCapture = { swiped = true }) }

        compose.onNodeWithTag(homeListTag).performTouchInput {
            swipeLeft(startX = right - 10f, endX = left + 10f)
        }

        assertTrue(swiped)
    }

    @Test
    fun aShortDragDoesNotOpenIt() {
        // 굴리다 손가락이 옆으로 흐르는 것만으로 뜨면 성가십니다.
        var swiped = false
        compose.setContent { homeScreen(onSwipeToCapture = { swiped = true }) }

        compose.onNodeWithTag(homeListTag).performTouchInput {
            swipeLeft(startX = centerX + 20f, endX = centerX - 20f)
        }

        assertFalse(swiped)
    }

    @Test
    fun swipingRightDoesNothing() {
        var swiped = false
        compose.setContent { homeScreen(onSwipeToCapture = { swiped = true }) }

        compose.onNodeWithTag(homeListTag).performTouchInput {
            swipeRight(startX = left + 10f, endX = right - 10f)
        }

        assertFalse(swiped)
    }

    @Test
    fun comingForTheWordBringsTheCardIntoView() {
        // 한마디 카드는 오늘의 걸음과 기분 아래에 있어, 홈을 열면 화면 아래에 걸쳐 반쯤 가립니다.
        // 알림이나 "새 한마디 · 보기"를 눌러 왔으면 카드를 올려 보여 줍니다.
        var focusRequested by mutableStateOf(false)
        var focused = 0
        compose.setContent {
            Box(modifier = Modifier.height(300.dp)) {
                HomeScreen(
                    summary = emptySummary,
                    notes = emptyList(),
                    routines = emptyList(),
                    checks = emptyList(),
                    dailyLoop = DailyProgress(
                        steps = DailyStep.DEFAULTS,
                        doneSteps = emptySet(),
                        streakDays = 0,
                        bestStreakDays = 0
                    ),
                    coach = DailyCoachState(
                        date = LocalDate.now(),
                        tip = DailyCoachTip(message = "물 한 컵으로 하루를 여셨네요.", createdAt = 1L)
                    ),
                    coachFocusRequested = focusRequested,
                    onCoachFocused = {
                        focusRequested = false
                        focused += 1
                    }
                )
            }
        }
        assertFalse(coachCardIsOnScreen())

        focusRequested = true
        compose.waitForIdle()

        assertTrue(coachCardIsOnScreen())
        // 한 번 내리고 거둡니다. 남아 있으면 홈에 돌아올 때마다 카드로 끌려 내려갑니다.
        assertEquals(1, focused)
        assertFalse(focusRequested)
    }

    private fun coachCardIsOnScreen(): Boolean =
        compose.onAllNodesWithTag(dailyCoachCardTag).fetchSemanticsNodes().isNotEmpty() &&
            runCatching { compose.onNodeWithTag(dailyCoachCardTag).assertIsDisplayed() }.isSuccess

    @androidx.compose.runtime.Composable
    private fun homeScreen(
        notes: List<FeedbackNote> = emptyList(),
        dailyLoop: DailyProgress? = null,
        onOpenStep: (DailyStep) -> Unit = {},
        onSwipeToCapture: () -> Unit = {}
    ) {
        HomeScreen(
            summary = emptySummary,
            notes = notes,
            routines = emptyList(),
            checks = emptyList(),
            dailyLoop = dailyLoop,
            onOpenStep = onOpenStep,
            onSwipeToCapture = onSwipeToCapture
        )
    }
}
