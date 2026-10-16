package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.codex.appgoodwords.data.RoutineCheckEntity
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.RoutineStatsCalculator
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 루틴 하나의 기록 화면.
 *
 * 사용자가 묻는 것은 "며칠째 이어 오고 있나"와 "무슨 요일에 많이 하나"입니다.
 * 그래서 그 둘이 실제로 보이는지와, **못 한 것을 세어 들이밀지 않는지**를 봅니다.
 */
class RoutineStatsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Seoul")

    /** 2026-10-16은 금요일입니다. */
    private val today = LocalDate.of(2026, 10, 16)

    private val routine = RoutineEntity(id = 1, syncId = "routine-1", title = "아침 산책", category = "건강")

    private fun check(date: String, hour: Int = 9) = RoutineCheckEntity(
        syncId = "check-$date-$hour",
        routineId = 1,
        routineSyncId = "routine-1",
        routineTitle = routine.title,
        checkedAt = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    )

    private fun show(checks: List<RoutineCheckEntity>) {
        compose.setContent {
            AppGoodWordsTheme {
                RoutineStatsScreen(
                    routine = routine,
                    report = RoutineStatsCalculator.build(routine, checks, today, zone),
                    today = today
                )
            }
        }
    }

    @Test fun theRunningStreakIsTheFirstThingShown() {
        show(listOf(check("2026-10-14"), check("2026-10-15"), check("2026-10-16")))

        compose.onNodeWithTag(routineStatsStreakTag).assertTextEquals("3일")
        compose.onNodeWithText("아침 산책").assertIsDisplayed()
        // 같은 날 두 번 눌러도 하루입니다. 횟수와 날수를 나란히 둡니다.
        compose.onNodeWithText("최고 3일 연속 · 실천한 날 3일 · 모두 3회").assertIsDisplayed()
        compose.onNodeWithText("처음 10월 14일 · 마지막 오늘").assertIsDisplayed()
    }

    @Test fun theWeekdayWhereItHappensMostIsNamed() {
        // 10월 5일·12일이 월요일, 6일이 화요일입니다.
        show(listOf(check("2026-10-05"), check("2026-10-12"), check("2026-10-06")))

        compose.onNodeWithText("요일별").assertIsDisplayed()
        compose.onNodeWithText("월요일에 가장 많이 밟았습니다.").assertIsDisplayed()
    }

    @Test fun aTiedWeekIsNotGivenAFavouriteDay() {
        // 월·화가 나란히 둘이면 그중 하나를 집어 말할 수 없습니다.
        show(
            listOf(check("2026-10-05"), check("2026-10-12"), check("2026-10-06"), check("2026-10-13"))
        )

        compose.onNodeWithText("아직 요일이 갈리지 않았습니다. 조금 더 쌓이면 여기에 보입니다.")
            .assertIsDisplayed()
    }

    @Test fun aBrokenRunIsNotShownAsZeroDays() {
        // "0일 연속"을 들이미는 것은 다그치는 일입니다. 다시 시작할 자리만 알려 줍니다.
        show(listOf(check("2026-10-01"), check("2026-10-02")))

        assertTrue(compose.onAllNodesWithText("0일").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("오늘 밟으면 다시 1일째가 됩니다.").assertIsDisplayed()
        compose.onNodeWithText("최고 2일 연속 · 실천한 날 2일 · 모두 2회").assertIsDisplayed()
    }

    @Test fun aRoutineNeverSteppedSaysWhatToDoInstead() {
        show(emptyList())

        compose.onNodeWithText("아직 밟은 기록이 없습니다.").assertIsDisplayed()
        // 아직 셀 것이 없는데 요일 막대와 격자를 그리면 빈 눈금만 보여 줍니다.
        assertTrue(compose.onAllNodesWithText("요일별").fetchSemanticsNodes().isEmpty())
    }
}
