package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.DayDigest
import com.codex.appgoodwords.data.DayScore
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.ScorePoint
import com.codex.appgoodwords.data.ScoreTrend
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 점수 카드와 그날 요약입니다.
 *
 * 점수는 사람을 움직이려고 두는 것이라, 말이 어긋나면 오히려 힘이 빠집니다.
 * 특히 견줄 지난주가 없을 때 뜻 없는 칭찬을 하지 않는지 봅니다.
 */
class DayScoreCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 9, 5)

    @Test
    fun theScoreIsShown() {
        compose.setContent {
            DayScoreCard(
                todayScore = score(done = 3, checks = 3),
                trend = trend(thisWeek = 70, lastWeek = 50),
                onOpenDay = {}
            )
        }

        compose.onNodeWithTag(dayScoreCardTag).assertIsDisplayed()
        compose.onNodeWithTag(dayScoreValueTag).assertTextEquals("80")
    }

    @Test
    fun goingUpIsSaidPlainly() {
        // "어떻게 변해가는지"는 오늘 점수가 아니라 지난주와의 차이가 말해 줍니다.
        compose.setContent {
            DayScoreCard(
                todayScore = score(done = 1, checks = 1),
                trend = trend(thisWeek = 70, lastWeek = 50),
                onOpenDay = {}
            )
        }

        compose.onNodeWithTag(dayScoreChangeTag).assertTextEquals(
            "이번 주 평균 70점 · 지난주보다 20점 올랐습니다"
        )
    }

    @Test
    fun goingDownIsAlsoSaidWithoutScolding() {
        compose.setContent {
            DayScoreCard(
                todayScore = score(done = 0, checks = 0),
                trend = trend(thisWeek = 40, lastWeek = 60),
                onOpenDay = {}
            )
        }

        compose.onNodeWithTag(dayScoreChangeTag).assertTextEquals(
            "이번 주 평균 40점 · 지난주보다 20점 낮습니다"
        )
    }

    @Test
    fun withoutALastWeekItDoesNotPraiseForNothing() {
        // 처음 쓰는 사람에게 "0점에서 올랐습니다"라고 하면 뜻 없는 칭찬이 됩니다.
        compose.setContent {
            DayScoreCard(
                todayScore = score(done = 1, checks = 1),
                trend = trend(thisWeek = 30, lastWeek = 0),
                onOpenDay = {}
            )
        }

        compose.onNodeWithTag(dayScoreChangeTag).assertTextEquals("이번 주 평균 30점")
    }

    @Test
    fun tappingABarOpensThatDay() {
        var opened: LocalDate? = null
        compose.setContent {
            DayScoreCard(
                todayScore = score(done = 1, checks = 1),
                trend = trend(thisWeek = 30, lastWeek = 20),
                onOpenDay = { opened = it }
            )
        }

        compose.onNodeWithTag(dayScoreBarTag(today.minusDays(1))).performClick()

        assertEquals(today.minusDays(1), opened)
    }

    @Test
    fun anEmptyDaySaysSoWithoutScolding() {
        compose.setContent {
            DayScoreCard(
                todayScore = score(done = 0, checks = 0),
                trend = trend(thisWeek = 0, lastWeek = 0),
                onOpenDay = {}
            )
        }

        compose.onNodeWithText("아직 오늘이 비어 있습니다.").assertIsDisplayed()
    }

    @Test
    fun theDaySummarySaysWhatWasDone() {
        compose.setContent {
            DaySummaryDialog(
                digest = DayDigest(
                    score = score(done = 3, checks = 3),
                    routineTitles = listOf("물 한 컵", "스트레칭"),
                    todoTitles = listOf("장보기"),
                    diaryTitles = listOf("긴 하루"),
                    quoteTitles = listOf("오늘의 기준")
                ),
                onMove = {},
                onDismiss = {},
                today = today
            )
        }

        compose.onNodeWithTag(daySummaryScoreTag).assertTextEquals("80점")
        compose.onNodeWithText("· 물 한 컵").assertIsDisplayed()
        compose.onNodeWithText("· 장보기").assertIsDisplayed()
        compose.onNodeWithText("· 긴 하루").assertIsDisplayed()
        compose.onNodeWithText("· 오늘의 기준").assertIsDisplayed()
    }

    @Test
    fun theDayTitleReadsAsTodayWithItsWeekday() {
        compose.setContent {
            DaySummaryDialog(
                digest = digest(score(done = 1, checks = 1)),
                onMove = {},
                onDismiss = {},
                today = today
            )
        }

        // 2026-09-05는 토요일입니다.
        compose.onNodeWithTag(daySummaryTitleTag).assertTextEquals("오늘 (토)")
    }

    @Test
    fun movingBackAsksForTheDayBefore() {
        var moved: LocalDate? = null
        compose.setContent {
            DaySummaryDialog(
                digest = digest(score(done = 1, checks = 1)),
                onMove = { moved = it },
                onDismiss = {},
                today = today
            )
        }

        compose.onNodeWithTag(daySummaryPrevTag).performClick()

        assertEquals(today.minusDays(1), moved)
    }

    @Test
    fun aFarAwayDayCanBePickedFromACalendar() {
        // 화살표로만 옮기면 석 달 전을 보려고 아흔 번을 눌러야 합니다.
        compose.setContent {
            DaySummaryDialog(
                digest = digest(score(done = 1, checks = 1)),
                onMove = {},
                onDismiss = {},
                today = today
            )
        }

        compose.onNodeWithTag(daySummaryPickTag).assertIsDisplayed()
    }

    @Test
    fun anEmptyDayInTheSummarySaysSo() {
        compose.setContent {
            DaySummaryDialog(
                digest = digest(score(done = 0, checks = 0)),
                onMove = {},
                onDismiss = {},
                today = today
            )
        }

        compose.onNodeWithText("이날은 남긴 것이 없습니다.").assertIsDisplayed()
    }

    @Test
    fun theMoodSitsBesideTheScoreNotInsideIt() {
        // 기분은 점수를 깎지 않습니다. 다만 곁에는 남습니다.
        compose.setContent {
            DaySummaryDialog(
                digest = digest(score(done = 1, checks = 1, mood = DiaryMood.SAD)),
                onMove = {},
                onDismiss = {},
                today = today
            )
        }

        compose.onNodeWithText("😢 슬픔").assertIsDisplayed()
    }

    private fun digest(score: DayScore) = DayDigest(
        score = score,
        routineTitles = emptyList(),
        todoTitles = emptyList(),
        diaryTitles = emptyList(),
        quoteTitles = emptyList()
    )

    private fun score(done: Int, checks: Int, mood: DiaryMood? = null) = DayScore(
        date = today,
        steps = listOf(DailyStep.QUOTE, DailyStep.ROUTINE, DailyStep.DIARY),
        doneSteps = listOf(DailyStep.QUOTE, DailyStep.ROUTINE, DailyStep.DIARY).take(done).toSet(),
        routineChecks = checks,
        todosDone = 0,
        diaries = 0,
        quotesRead = 0,
        mood = mood
    )

    private fun trend(thisWeek: Int, lastWeek: Int) = ScoreTrend(
        points = (13 downTo 0).map { daysAgo ->
            ScorePoint(date = today.minusDays(daysAgo.toLong()), score = if (daysAgo < 7) thisWeek else lastWeek)
        },
        thisWeekAverage = thisWeek,
        lastWeekAverage = lastWeek
    )
}
