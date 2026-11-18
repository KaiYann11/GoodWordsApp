package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.DayDigest
import com.codex.appgoodwords.data.DayScore
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.RoutineEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 하루 마무리 — 한 장에 네 칸(사용자가 고름, 2026-11-19). 기분·루틴은 누르는 즉시, 감사 한 줄과 내일 할 일은 [마무리]에서.
 */
class DayCloseScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 19)
    private val stretch = RoutineEntity(id = 7, syncId = "r7", title = "스트레칭")

    @Test
    fun theFourBoxesAreOnOnePageAndTapsGoOutRightAway() {
        val moods = mutableListOf<DiaryMood>()
        val checked = mutableListOf<RoutineEntity>()
        compose.setContent {
            DayCloseScreen(
                today = today,
                digest = digest(routines = 3, quotes = 2),
                mood = null,
                restRoutines = listOf(stretch),
                hasRoutinesToday = true,
                onPickMood = { moods += it },
                onCheckRoutine = { checked += it },
                onFinish = { _, _ -> }
            )
        }

        compose.onNodeWithText("11월 19일(목)").assertIsDisplayed()
        compose.onNodeWithTag(dayCloseDoneLineTag).assertTextEquals("오늘 한 것  루틴 3번 · 글귀 2개")
        compose.onNodeWithTag(dayCloseMoodTag(DiaryMood.GOOD)).performClick()
        compose.onNodeWithText("2  했는데 못 적은 루틴이 있나요?").assertIsDisplayed()
        compose.onNodeWithTag(dayCloseCheckTag(7)).performClick()
        compose.onNodeWithText("3  오늘 감사한 일 한 줄").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4  내일 할 일 하나").performScrollTo().assertIsDisplayed()

        assertEquals(listOf(DiaryMood.GOOD), moods)
        assertEquals(listOf(stretch), checked)
    }

    @Test
    fun finishingHandsOverTheGratitudeLineAndTomorrowsTodo() {
        var finished: Pair<String, String>? = null
        compose.setContent {
            DayCloseScreen(
                today = today,
                digest = digest(),
                mood = DiaryMood.TIRED,
                restRoutines = emptyList(),
                hasRoutinesToday = false,
                onPickMood = {},
                onCheckRoutine = {},
                onFinish = { gratitude, todo -> finished = gratitude to todo }
            )
        }

        // 한 것이 없는 날에도 마무리할 수 있습니다. 오늘 할 루틴이 없으면 루틴 칸이 없고 번호가 당겨집니다.
        compose.onNodeWithTag(dayCloseDoneLineTag).assertTextEquals("아직 적은 것이 없습니다. 지금 남겨도 됩니다.")
        compose.onNodeWithText("했는데 못 적은 루틴이 있나요?", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(dayCloseGratitudeTag).performScrollTo().performTextInput("Warm tea")
        compose.onNodeWithTag(dayCloseTomorrowTag).performScrollTo().performTextInput("Post office")
        compose.onNodeWithTag(dayCloseFinishTag).performScrollTo().performClick()

        assertEquals("Warm tea" to "Post office", finished)
    }

    @Test
    fun allRoutinesWrittenSaysSoAndSavingLocksTheButton() {
        compose.setContent {
            DayCloseScreen(
                today = today,
                digest = digest(routines = 1),
                mood = null,
                restRoutines = emptyList(),
                hasRoutinesToday = true,
                saving = true,
                onPickMood = {},
                onCheckRoutine = {},
                onFinish = { _, _ -> }
            )
        }

        compose.onNodeWithText("오늘 할 루틴을 모두 적었습니다.").assertIsDisplayed()
        compose.onNodeWithTag(dayCloseFinishTag).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun theHomeCardOpensTheScreen() {
        var opened = false
        compose.setContent { DayCloseCard(doneLine = "루틴 3번 · 글귀 2개", onOpen = { opened = true }) }

        compose.onNodeWithText("오늘 루틴 3번 · 글귀 2개").assertIsDisplayed()
        compose.onNodeWithTag(dayCloseCardOpenTag).performClick()
        assertEquals(true, opened)
    }

    private fun digest(routines: Int = 0, quotes: Int = 0) = DayDigest(
        score = DayScore(
            date = today,
            steps = DailyStep.DEFAULTS,
            doneSteps = emptySet(),
            routineChecks = routines,
            todosDone = 0,
            diaries = 0,
            quotesRead = quotes
        ),
        routineTitles = emptyList(),
        todoTitles = emptyList(),
        diaryTitles = emptyList(),
        quoteTitles = emptyList()
    )
}
