package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.codex.appgoodwords.data.CompareSection
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.DayComparisons
import com.codex.appgoodwords.data.DayDigest
import com.codex.appgoodwords.data.DayPair
import com.codex.appgoodwords.data.DayScore
import com.codex.appgoodwords.data.DayStrip
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.MoodLogEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 어제와 오늘 화면(항목 카드 두 칸)과 홈의 어제보다 띠(글귀 아래 한 줄 띠) — 사용자가 고름, 2026-11-27.
 */
class DayCompareScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 27)
    private val yesterday = today.minusDays(1)

    @Test
    fun eachSectionIsACardWithYesterdayAndTodaySideBySide() {
        var opened: LocalDate? = null
        compose.setContent {
            DayCompareScreen(
                pair = DayPair(
                    today = day(today, routines = mapOf("물 2L" to 2, "독서 20분" to 1), moods = listOf(DiaryMood.GOOD to 9)),
                    yesterday = day(yesterday, routines = mapOf("물 2L" to 1), moods = listOf(DiaryMood.TIRED to 21), diaries = listOf("비 오던 날"))
                ),
                diaryLocked = true,
                onOpenDay = { opened = it }
            )
        }

        compose.onNodeWithTag(dayCompareCardTag(CompareSection.ROUTINE)).assertIsDisplayed()
        compose.onNodeWithText("어제 1번 · 오늘 3번").assertIsDisplayed()
        // 앞선 칸에는 빛 칩, 같은 루틴을 두 번 밟았으면 ×2.
        compose.onNodeWithText("+2").assertIsDisplayed()
        compose.onNodeWithText("×2").assertIsDisplayed()
        compose.onNodeWithText("3번").assertIsDisplayed()

        // 기분은 견주지 않고 나란히만 — 빛 칩도 수의 차이도 없습니다.
        compose.onNodeWithTag(dayCompareScreenTag).performScrollToNode(hasTestTag(dayCompareCardTag(CompareSection.MOOD)))
        compose.onNodeWithText("견주지 않고 나란히").assertIsDisplayed()
        compose.onNodeWithText(DiaryMood.TIRED.label).assertIsDisplayed()

        // 잠긴 일기는 제목 대신 편 수만(그날 요약과 같게).
        compose.onNodeWithTag(dayCompareScreenTag).performScrollToNode(hasText("잠긴 일기 1편"))
        compose.onNodeWithText("비 오던 날").assertDoesNotExist()

        compose.onNodeWithTag(dayCompareScreenTag).performScrollToNode(hasTestTag(dayCompareYesterdayTag))
        compose.onNodeWithTag(dayCompareYesterdayTag).performClick()
        assertEquals(yesterday, opened)
    }

    @Test
    fun twoEmptyDaysSaySoInsteadOfShowingEmptyCards() {
        compose.setContent {
            DayCompareScreen(pair = DayPair(today = day(today), yesterday = day(yesterday)), diaryLocked = false, onOpenDay = {})
        }

        compose.onNodeWithTag(dayCompareEmptyTag).assertIsDisplayed()
        compose.onNodeWithTag(dayCompareCardTag(CompareSection.ROUTINE)).assertDoesNotExist()
    }

    @Test
    fun theHomeStripNamesWhatTodayMatchedOrBeatAndOpensTheScreen() {
        var opened = 0
        val strip = DayComparisons.strip(
            DayPair(today = day(today, routines = mapOf("물 2L" to 3)), yesterday = day(yesterday, routines = mapOf("물 2L" to 2))).comparison
        )
        assertEquals(DayStrip.Ahead(listOf("루틴 +1"), 0), strip)
        compose.setContent { DayCompareStrip(strip = strip!!, onOpen = { opened += 1 }) }

        compose.onNodeWithTag(homeCompareStripTag).assertTextContains("어제보다", substring = true)
        compose.onNodeWithText("루틴 +1").assertIsDisplayed()
        compose.onNodeWithTag(homeCompareStripTag).performClick()
        assertEquals(1, opened)
    }

    private fun day(
        date: LocalDate,
        routines: Map<String, Int> = emptyMap(),
        moods: List<Pair<DiaryMood, Int>> = emptyList(),
        diaries: List<String> = emptyList()
    ) = DayDigest(
        score = DayScore(
            date = date,
            steps = DailyStep.DEFAULTS,
            doneSteps = emptySet(),
            routineChecks = routines.values.sum(),
            todosDone = 0,
            diaries = diaries.size,
            quotesRead = 0
        ),
        routineTitles = routines.keys.toList(),
        todoTitles = emptyList(),
        diaryTitles = diaries,
        quoteTitles = emptyList(),
        moodLogs = moods.map { (mood, hour) ->
            MoodLogEntity(
                entryDate = date.toString(),
                mood = mood.name,
                createdAt = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        },
        routineCounts = routines
    )
}
