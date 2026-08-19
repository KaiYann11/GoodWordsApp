package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.codex.appgoodwords.data.CategoryCount
import com.codex.appgoodwords.data.DailyCount
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.DiarySummary
import com.codex.appgoodwords.data.MoodCount
import com.codex.appgoodwords.data.MoodPoint
import com.codex.appgoodwords.data.MoodTrend
import com.codex.appgoodwords.data.ReadingSummary
import com.codex.appgoodwords.data.StatsSummary
import com.codex.appgoodwords.data.TodoSummary
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * 통계 화면의 돌아보기 카드가 다섯 기능을 다 보여 주는지 확인합니다.
 *
 * 아직 안 쓰는 기능은 빈 칸으로 남기지 않고 아예 감춥니다.
 * 처음 켠 사람에게 0으로 채워진 표를 보여 주면 앱이 텅 비어 보입니다.
 */
class StatsCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun readingDiaryAndTodoAreAllShown() {
        compose.setContent {
            StatsCard(
                summary = summary(
                    reading = ReadingSummary(
                        readingCount = 2,
                        finishedCount = 5,
                        finishedThisYear = 3,
                        pagesRead = 1200,
                        quotesFromBooks = 7
                    ),
                    diary = DiarySummary(
                        totalCount = 12,
                        daysThisMonth = 9,
                        topMoods = listOf(MoodCount(DiaryMood.GOOD, 5))
                    ),
                    todo = TodoSummary(doneCount = 8, openCount = 2, overdueCount = 1)
                )
            )
        }

        compose.onNodeWithText("독서").assertIsDisplayed()
        compose.onNodeWithText("3권").assertIsDisplayed()
        compose.onNodeWithText("1200쪽").assertIsDisplayed()
        compose.onNodeWithText("책에서 뽑은 글귀 7개").assertIsDisplayed()

        compose.onNodeWithText("이번 달 9일 · 전체 12편").assertIsDisplayed()
        compose.onNodeWithText("🙂 좋음 5").assertIsDisplayed()

        compose.onNodeWithText("끝낸 일 8개 · 남은 일 2개 · 80%").assertIsDisplayed()
        compose.onNodeWithText("지난 일 1개").assertIsDisplayed()
    }

    @Test
    fun sectionsWithoutRecordsAreHidden() {
        compose.setContent { StatsCard(summary = summary()) }

        // 0권·0편만 늘어놓으면 앱이 비어 보입니다. 기록이 생기면 그때 나타납니다.
        compose.onNodeWithText("독서").assertDoesNotExist()
        compose.onNodeWithText("일기").assertDoesNotExist()
        compose.onNodeWithText("할 일").assertDoesNotExist()
    }

    @Test
    fun anEmptySummaryInvitesEveryFeature() {
        compose.setContent {
            StatsCard(
                summary = StatsSummary(
                    currentStreakDays = 0,
                    bestStreakDays = 0,
                    activeDays = 0,
                    confirmedTotal = 0,
                    routineCheckTotal = 0,
                    recentDays = emptyList(),
                    topCategories = emptyList()
                )
            )
        }

        compose.onNodeWithText(
            "글귀를 확인하거나 루틴을 체크하고, 일기·할 일·독서를 남기면 여기에 기록이 쌓입니다."
        ).assertIsDisplayed()
    }

    @Test
    fun theMoodTrendIsDrawnOnceThereIsAShape() {
        compose.setContent {
            StatsCard(
                summary = summary(
                    diary = DiarySummary(
                        totalCount = 4,
                        daysThisMonth = 4,
                        topMoods = listOf(MoodCount(DiaryMood.GOOD, 2)),
                        moodTrend = trend(
                            MoodPoint(LocalDate.of(2026, 8, 12), DiaryMood.SAD),
                            MoodPoint(LocalDate.of(2026, 8, 18), DiaryMood.GOOD)
                        )
                    )
                )
            )
        }

        compose.onNodeWithText("기분 흐름").assertIsDisplayed()
        // 어느 구간인지 알려면 시작 날짜가 보여야 합니다. 끝 날짜(8/18)는 위 막대 그래프에도
        // 있어서 여기서 세지 않습니다. 14일 전은 막대 그래프의 7일 밖이라 이 그래프에만 있습니다.
        compose.onNodeWithText("8/5").assertIsDisplayed()
    }

    @Test
    fun oneMoodAloneDrawsNoTrend() {
        compose.setContent {
            StatsCard(
                summary = summary(
                    diary = DiarySummary(
                        totalCount = 1,
                        daysThisMonth = 1,
                        topMoods = listOf(MoodCount(DiaryMood.GOOD, 1)),
                        moodTrend = trend(MoodPoint(LocalDate.of(2026, 8, 18), DiaryMood.GOOD))
                    )
                )
            )
        }

        // 점 하나는 오르내림이 없습니다. 빈 그래프를 그리면 자리만 차지합니다.
        compose.onNodeWithText("기분 흐름").assertDoesNotExist()
        // 그래도 일기 요약 자체는 남아야 합니다.
        compose.onNodeWithText("🙂 좋음 1").assertIsDisplayed()
    }

    @Test
    fun aDiaryWithoutAnyMoodDrawsNoTrend() {
        compose.setContent {
            StatsCard(
                summary = summary(
                    diary = DiarySummary(
                        totalCount = 3,
                        daysThisMonth = 3,
                        topMoods = emptyList(),
                        moodTrend = trend()
                    )
                )
            )
        }

        // 기분을 한 번도 안 고른 사람에게 빈 격자를 보여 주지 않습니다.
        compose.onNodeWithText("기분 흐름").assertDoesNotExist()
        compose.onNodeWithText("이번 달 3일 · 전체 3편").assertIsDisplayed()
    }

    @Test
    fun noOverdueLineWhenNothingIsLate() {
        compose.setContent {
            StatsCard(summary = summary(todo = TodoSummary(doneCount = 3, openCount = 1, overdueCount = 0)))
        }

        compose.onNodeWithText("지난 일 0개").assertDoesNotExist()
    }

    /** 오늘을 2026-08-18로 두고 14일 창을 만듭니다. StatsCalculator가 만드는 것과 같은 모양입니다. */
    private fun trend(vararg points: MoodPoint) = MoodTrend(
        from = LocalDate.of(2026, 8, 18).minusDays(13),
        to = LocalDate.of(2026, 8, 18),
        points = points.toList()
    )

    private fun summary(
        reading: ReadingSummary = ReadingSummary(0, 0, 0, 0, 0),
        diary: DiarySummary = DiarySummary(0, 0, emptyList()),
        todo: TodoSummary = TodoSummary(0, 0, 0)
    ) = StatsSummary(
        currentStreakDays = 3,
        bestStreakDays = 5,
        activeDays = 10,
        confirmedTotal = 20,
        routineCheckTotal = 4,
        recentDays = (6 downTo 0).map { daysAgo ->
            DailyCount(
                date = LocalDate.of(2026, 8, 18).minusDays(daysAgo.toLong()),
                confirmedCount = 1,
                routineCheckCount = 0
            )
        },
        topCategories = listOf(CategoryCount("동기부여", 4)),
        reading = reading,
        diary = diary,
        todo = todo
    )
}
