package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.ReportPeriod
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 돌아보기를 달력에 펼쳐 보는 자리입니다.
 *
 * **돌린 날과 다뤄진 날은 다릅니다.** 그 둘과, 아무것도 없는 빈 날이 화면에서 갈려 보여야
 * 다음에 무엇을 돌아볼지 정할 수 있습니다.
 */
class GrowthCalendarCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 9, 20)
    private val september = YearMonth.of(2026, 9)

    @Test
    fun theMonthAndItsTallyAreShown() {
        compose.setContent {
            calendar(
                reports = listOf(
                    report(ranOn = LocalDate.of(2026, 9, 8), start = "2026-09-01", end = "2026-09-07")
                )
            )
        }

        compose.onNodeWithTag(growthCalendarTag).assertIsDisplayed()
        compose.onNodeWithText("2026년 9월").assertIsDisplayed()
        // 빈 날이 몇인지가 이 달력의 값어치입니다.
        compose.onNodeWithText("돌아본 1일 · 다룬 7일 · 빈 22일").assertIsDisplayed()
    }

    @Test
    fun whatEachColourMeansIsWritten() {
        // 색만 칠해 두면 아는 사람만 읽습니다.
        compose.setContent { calendar(reports = emptyList()) }

        compose.onNodeWithText("돌아본 날").assertIsDisplayed()
        compose.onNodeWithText("다뤄진 날").assertIsDisplayed()
    }

    @Test
    fun tappingADaySaysWhatBelongsToIt() {
        compose.setContent {
            calendar(
                reports = listOf(
                    report(ranOn = LocalDate.of(2026, 9, 8), start = "2026-09-01", end = "2026-09-07")
                )
            )
        }

        compose.onNodeWithTag(growthCalendarDayTag(LocalDate.of(2026, 9, 3))).performClick()

        compose.onNodeWithTag(growthCalendarSelectedTag).assertIsDisplayed()
        compose.onNodeWithText("9월 3일").assertIsDisplayed()
        compose.onNodeWithText("· 한 주 돌아보기 (2026-09-01 ~ 2026-09-07)").assertIsDisplayed()
    }

    @Test
    fun anUntouchedDaySaysSo() {
        // 칸을 눌러 놓고 아무것도 안 뜨면 무엇을 누른 것인지 알 수 없습니다.
        compose.setContent { calendar(reports = emptyList()) }

        compose.onNodeWithTag(growthCalendarDayTag(LocalDate.of(2026, 9, 15))).performClick()

        compose.onNodeWithText("이날은 아직 돌아보지 않았습니다.").assertIsDisplayed()
    }

    @Test
    fun theFutureCannotBeOpened() {
        // 오지 않은 달에는 돌아볼 것이 없습니다.
        compose.setContent { calendar(reports = emptyList(), month = september) }

        compose.onNodeWithTag(growthCalendarNextTag).assertIsNotEnabled()
        compose.onNodeWithTag(growthCalendarPrevTag).assertIsEnabled()
    }

    @Test
    fun movingBackAsksForTheMonthBefore() {
        var moved: YearMonth? = null
        compose.setContent { calendar(reports = emptyList(), onMonthChanged = { moved = it }) }

        compose.onNodeWithTag(growthCalendarPrevTag).performClick()

        assertEquals(YearMonth.of(2026, 8), moved)
    }

    @Test
    fun aDayThatWasBothRunAndCoveredStillOpens() {
        // 오늘까지를 오늘 돌아본 경우입니다. 두 표시가 겹쳐도 눌러서 볼 수 있어야 합니다.
        compose.setContent {
            calendar(
                reports = listOf(
                    report(ranOn = LocalDate.of(2026, 9, 7), start = "2026-09-01", end = "2026-09-07")
                )
            )
        }

        compose.onNodeWithTag(growthCalendarDayTag(LocalDate.of(2026, 9, 7))).performClick()

        compose.onNodeWithText("· 한 주 돌아보기 (2026-09-01 ~ 2026-09-07)").assertIsDisplayed()
    }

    @Composable
    private fun calendar(
        reports: List<GrowthReportEntity>,
        month: YearMonth = september,
        onMonthChanged: (YearMonth) -> Unit = {}
    ) {
        var selected by remember { mutableStateOf<LocalDate?>(null) }
        GrowthCalendarCard(
            reports = reports,
            month = month,
            selectedDate = selected,
            onMonthChanged = onMonthChanged,
            onDateSelected = { selected = it },
            today = today
        )
    }

    private fun report(ranOn: LocalDate, start: String, end: String) = GrowthReportEntity(
        syncId = "r-$ranOn-$start",
        period = ReportPeriod.WEEKLY.name,
        periodStart = start,
        periodEnd = end,
        guide = "돌아본 글",
        createdAt = ranOn.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    )
}
