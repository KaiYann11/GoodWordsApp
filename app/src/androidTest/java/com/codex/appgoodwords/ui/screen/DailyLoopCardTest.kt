package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.data.DailyProgress
import com.codex.appgoodwords.data.DailyStep
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 오늘의 세 걸음 카드.
 *
 * 이 카드는 다그치지 않는 것이 전부입니다. 못 한 것을 세어 보여 주면 할 일 목록이 되고,
 * 하루 빠뜨린 날에는 앱을 열기가 싫어집니다.
 */
class DailyLoopCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theCardShowsHowFarTodayHasCome() {
        compose.setContent {
            DailyLoopCard(
                progress = DailyProgress(
                    doneSteps = setOf(DailyStep.QUOTE),
                    streakDays = 0,
                    bestStreakDays = 0
                ),
                onOpenStep = {}
            )
        }

        compose.onNodeWithText("오늘의 세 걸음").assertIsDisplayed()
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        // 지금 할 것에만 안내를 답니다. 세 줄이 다 설명을 달면 어느 것부터인지 모릅니다.
        compose.onNodeWithText(DailyStep.ROUTINE.hint).assertIsDisplayed()
        compose.onNodeWithText(DailyStep.DIARY.hint).assertDoesNotExist()
        // 격려 문구가 그 안내문을 그대로 되풀이하면 같은 말이 카드에 두 번 나옵니다.
        compose.onNodeWithText("루틴까지 하면 오늘이 채워집니다.").assertIsDisplayed()
    }

    @Test
    fun tappingAStepGoesThere() {
        var opened: DailyStep? = null
        compose.setContent {
            DailyLoopCard(
                progress = DailyProgress(doneSteps = emptySet(), streakDays = 0, bestStreakDays = 0),
                onOpenStep = { opened = it }
            )
        }

        compose.onNodeWithTag(dailyLoopStepTag(DailyStep.DIARY)).performClick()

        // 알려만 주고 데려가지 않으면 사용자가 다시 찾아 들어가야 합니다.
        assertEquals(DailyStep.DIARY, opened)
    }

    @Test
    fun aRunningStreakIsWhatTheCardTalksAbout() {
        compose.setContent {
            DailyLoopCard(
                progress = DailyProgress(
                    doneSteps = setOf(DailyStep.QUOTE, DailyStep.ROUTINE),
                    streakDays = 4,
                    bestStreakDays = 9
                ),
                onOpenStep = {}
            )
        }

        compose.onNodeWithText("4일째 이어 오고 있습니다. 일기만 하면 오늘도 이어집니다.").assertIsDisplayed()
        compose.onNodeWithText("가장 길게 이어 간 날 9일").assertIsDisplayed()
    }

    @Test
    fun aFinishedDaySaysSo() {
        compose.setContent {
            DailyLoopCard(
                progress = DailyProgress(
                    doneSteps = DailyStep.entries.toSet(),
                    streakDays = 3,
                    bestStreakDays = 3
                ),
                onOpenStep = {}
            )
        }

        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.onNodeWithText("오늘도 세 걸음을 다 밟았습니다. 3일째 이어 가는 중입니다.").assertIsDisplayed()
    }

    @Test
    fun aBeginnerIsNotShownSomeoneElsesRecord() {
        compose.setContent {
            DailyLoopCard(
                progress = DailyProgress(
                    doneSteps = emptySet(),
                    streakDays = 0,
                    // 예전에 9일을 이어 간 적이 있지만 지금은 끊긴 상태.
                    bestStreakDays = 9
                ),
                onOpenStep = {}
            )
        }

        // 0일인 사람에게 "최고 9일"을 보여 주면 격려가 아니라 비교처럼 읽힙니다.
        compose.onNodeWithText("가장 길게 이어 간 날 9일").assertDoesNotExist()
    }
}
