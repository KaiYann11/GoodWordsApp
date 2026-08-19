package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.codex.appgoodwords.data.StatsCalculator
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class StatsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun overallAndRoutineStatisticsAreOnTheSameScreen() {
        compose.setContent {
            StatsScreen(
                summary = StatsCalculator.build(
                    events = emptyList(),
                    items = emptyList(),
                    routineChecks = emptyList(),
                    today = LocalDate.now()
                ),
                routines = emptyList(),
                checks = emptyList()
            )
        }

        compose.onNodeWithText("돌아보기").assertIsDisplayed()
        compose.onNodeWithText("달성률 - (0/0)").performScrollTo().assertIsDisplayed()
    }
}
