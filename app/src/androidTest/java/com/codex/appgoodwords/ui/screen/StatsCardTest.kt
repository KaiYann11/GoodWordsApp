package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.codex.appgoodwords.data.CategoryCount
import com.codex.appgoodwords.data.DailyCount
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.DiarySummary
import com.codex.appgoodwords.data.MoodCount
import com.codex.appgoodwords.data.ReadingSummary
import com.codex.appgoodwords.data.StatsSummary
import com.codex.appgoodwords.data.TodoSummary
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * 돌아보기 화면이 다섯 기능을 다 보여 주는지 확인합니다.
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
    fun noOverdueLineWhenNothingIsLate() {
        compose.setContent {
            StatsCard(summary = summary(todo = TodoSummary(doneCount = 3, openCount = 1, overdueCount = 0)))
        }

        compose.onNodeWithText("지난 일 0개").assertDoesNotExist()
    }

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
