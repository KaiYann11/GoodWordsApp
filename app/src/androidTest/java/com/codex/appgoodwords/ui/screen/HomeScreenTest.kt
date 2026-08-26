package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.codex.appgoodwords.data.DailyProgress
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.FeedbackKind
import com.codex.appgoodwords.data.FeedbackNote
import com.codex.appgoodwords.data.StatsCalculator
import com.codex.appgoodwords.data.StatsSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
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

        compose.onNodeWithText("돌아보기").assertIsDisplayed()
        compose.onNodeWithText("달성률 - (0/0)").performScrollTo().assertIsDisplayed()
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

        compose.onNodeWithTag(feedbackCardTag).assertIsDisplayed()
        compose.onNodeWithText("5일째 이어 가고 있습니다.").assertIsDisplayed()
        compose.onNodeWithText("'물 한 컵'은 이레 내내 지켰습니다.").assertIsDisplayed()
    }

    @Test
    fun anEmptyFeedbackCardStillSaysSomething() {
        // 카드만 덩그러니 비어 있으면 고장인지 비어 있는 것인지 알 수 없습니다.
        compose.setContent { homeScreen(notes = emptyList()) }

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

    @androidx.compose.runtime.Composable
    private fun homeScreen(
        notes: List<FeedbackNote> = emptyList(),
        dailyLoop: DailyProgress? = null,
        onOpenStep: (DailyStep) -> Unit = {}
    ) {
        HomeScreen(
            summary = emptySummary,
            notes = notes,
            routines = emptyList(),
            checks = emptyList(),
            dailyLoop = dailyLoop,
            onOpenStep = onOpenStep
        )
    }
}
