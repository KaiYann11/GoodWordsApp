package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.ReportPeriod
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 돌아보기가 쌓였을 때.
 *
 * 하루 주기로 돌리면 한 해에 삼백 장이 넘습니다. 목록은 한 줄로 끝없이 이어지고, 지우는 것은
 * 한 장씩뿐이었습니다. 쌓이는 것을 막을 수 없다면 **좁혀 보고 치울 수** 있어야 합니다.
 */
class GrowthListToolsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun report(id: Long, period: ReportPeriod, guide: String = "가이드 $id") = GrowthReportEntity(
        id = id,
        syncId = "g$id",
        period = period.name,
        periodStart = "2026-08-0${id}",
        periodEnd = "2026-08-1${id}",
        guide = guide,
        createdAt = id * 1_000L
    )

    private val many = listOf(
        report(5, ReportPeriod.WEEKLY),
        report(4, ReportPeriod.DAILY),
        report(3, ReportPeriod.WEEKLY),
        report(2, ReportPeriod.MONTHLY),
        report(1, ReportPeriod.DAILY)
    )

    @Test
    fun theToolsStayOutOfTheWayWhileThereAreFew() {
        // 두어 편뿐일 때 거르개와 정리 버튼은 자리만 차지합니다.
        compose.setContent { growthScreen(reports = many.take(2)) }

        compose.onNodeWithTag(growthTidyButtonTag).assertDoesNotExist()
        compose.onNodeWithTag(growthListFilterTag(ReportListFilterName.ALL)).assertDoesNotExist()
    }

    @Test
    fun theListCanBeNarrowedToOneKind() {
        compose.setContent { growthScreen(reports = many) }

        compose.onNodeWithTag(growthListFilterTag(ReportListFilterName.MONTHLY)).performClick()

        // 목록은 달력 아래에 있어 아직 안 그려져 있습니다. 굴려야 나옵니다.
        compose.onNodeWithTag(growthListTag).performScrollToNode(hasText("가이드 2"))

        // 한 달짜리는 하나뿐입니다.
        compose.onNodeWithText("가이드 2").assertExists()
        compose.onNodeWithText("가이드 5").assertDoesNotExist()
    }

    @Test
    fun anEmptyFilterSaysHowToGetBack() {
        // 거르고 나서 빈 화면만 나오면 고장으로 읽힙니다.
        compose.setContent { growthScreen(reports = listOf(report(1, ReportPeriod.DAILY), report(2, ReportPeriod.DAILY), report(3, ReportPeriod.DAILY), report(4, ReportPeriod.DAILY))) }

        compose.onNodeWithTag(growthListFilterTag(ReportListFilterName.MONTHLY)).performClick()

        compose.onNodeWithTag(growthListTag)
            .performScrollToNode(hasText("거르개를 전체로 두면", substring = true))

        compose.onNodeWithText("거르개를 전체로 두면", substring = true).assertExists()
    }

    @Test
    fun tidyingKeepsTheMostRecentOnes() {
        var removed: List<Long>? = null
        compose.setContent { growthScreen(reports = many, onDeleteReports = { removed = it }) }

        compose.onNodeWithTag(growthTidyButtonTag).performClick()
        compose.onNodeWithText("10편").performClick()
        compose.onNodeWithText("정리").performClick()

        // 다섯 편뿐이라 10편을 남기면 지울 것이 없습니다.
        assertEquals(null, removed)
    }

    @Test
    fun thereIsNothingToTidyWhenEverythingFits() {
        compose.setContent { growthScreen(reports = many) }

        compose.onNodeWithTag(growthTidyButtonTag).performClick()

        compose.onNodeWithText("지울 것이 없습니다", substring = true).assertExists()
        // 지울 것이 없는데 누를 수 있으면 눌러 보고서야 압니다.
        compose.onNodeWithText("정리").assertIsNotEnabled()
    }

    @Test
    fun theOldestOnesGoFirst() {
        var removed: List<Long>? = null
        val lots = (1..15L).map { report(it, ReportPeriod.DAILY) }.sortedByDescending { it.createdAt }
        compose.setContent { growthScreen(reports = lots, onDeleteReports = { removed = it }) }

        compose.onNodeWithTag(growthTidyButtonTag).performClick()
        compose.onNodeWithText("10편").performClick()
        compose.onNodeWithText("정리").performClick()

        // 최근 열 편을 남기고 오래된 다섯이 사라집니다. 남는 쪽을 고르게 해야 손이 미끄러져도
        // 최근 것은 그대로입니다.
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), removed)
    }

    @androidx.compose.runtime.Composable
    private fun growthScreen(
        reports: List<GrowthReportEntity>,
        onDeleteReports: (List<Long>) -> Unit = {}
    ) {
        GrowthFeedbackScreen(
            reports = reports,
            running = false,
            errorMessage = "",
            onRequest = {},
            onPreview = {},
            onDeleteReport = {},
            onDeleteReports = onDeleteReports,
            onKeepQuote = {},
            onKeepRoutine = {}
        )
    }
}

/** 화면 안 enum이 private이라, 표식 이름만 여기에 적어 둡니다. */
private object ReportListFilterName {
    const val ALL = "ALL"
    const val MONTHLY = "MONTHLY"
}
