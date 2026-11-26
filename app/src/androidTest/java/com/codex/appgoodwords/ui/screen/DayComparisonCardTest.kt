package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.data.ComparedCount
import com.codex.appgoodwords.data.ComparedKind
import com.codex.appgoodwords.data.DayComparison
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 어제보다 카드 — 한 일만 짚고, 두 날의 수는 눌러서 봅니다(사용자가 고름, 2026-11-19).
 * 아침마다 지는 수를 먼저 보여 주면 다그치는 카드가 됩니다.
 */
class DayComparisonCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun onlyWhatTodayMatchedOrBeatIsOnTheCard() {
        compose.setContent {
            DayComparisonCard(
                comparison = DayComparison(
                    today = today,
                    rows = listOf(
                        ComparedCount(ComparedKind.ROUTINE, today = 3, yesterday = 2),
                        ComparedCount(ComparedKind.QUOTE, today = 2, yesterday = 2),
                        ComparedCount(ComparedKind.DIARY, today = 0, yesterday = 1),
                        ComparedCount(ComparedKind.STEPS, today = 4_210, yesterday = 6_800)
                    )
                ),
                onOpenCompare = {}
            )
        }

        compose.onNodeWithText("어제보다").assertIsDisplayed()
        compose.onNodeWithText("루틴 3번 — 1번 더").assertIsDisplayed()
        compose.onNodeWithText("글귀 2개 — 어제만큼").assertIsDisplayed()
        // 적은 것은 숫자로 나오지 않습니다.
        compose.onNodeWithText("4,210걸음", substring = true).assertDoesNotExist()
        compose.onNodeWithText("일기", substring = true).assertDoesNotExist()
    }

    @Test
    fun withNothingAheadItSaysWhatBothDidAndTheButtonOpensTheCompareScreen() {
        var opened = 0
        compose.setContent {
            DayComparisonCard(
                comparison = DayComparison(
                    today = today,
                    rows = listOf(
                        ComparedCount(ComparedKind.ROUTINE, today = 1, yesterday = 4),
                        ComparedCount(ComparedKind.STEPS, today = 4_210, yesterday = 6_800)
                    )
                ),
                onOpenCompare = { opened += 1 }
            )
        }

        // 앞선 것이 없으면 둘 다 한 것만 숫자 없이.
        compose.onNodeWithText("어제처럼 오늘도 루틴을 밟고 걸었습니다.").assertIsDisplayed()
        compose.onNodeWithText("4,210", substring = true).assertDoesNotExist()
        // 두 날의 자세한 것은 작은 창이 아니라 어제와 오늘 화면입니다(2026-11-27).
        compose.onNodeWithTag(dayComparisonOpenTag).performClick()
        assertEquals(1, opened)
    }
}
