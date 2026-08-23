package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.data.RoutineEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 루틴 화면의 차례.
 *
 * 루틴은 할 일과 달리 순서가 있습니다(일어나서 물 → 스트레칭 → 책 한 쪽).
 * 화면이 그 줄을 마음대로 바꾸면 사용자가 정한 순서라는 약속이 깨집니다.
 */
class RoutineScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val routines = listOf(
        routine(id = 1, title = "기상 후 물 한 컵", orderIndex = 0),
        routine(id = 2, title = "스트레칭", orderIndex = 1),
        routine(id = 3, title = "책 한 쪽", orderIndex = 2)
    )

    @Test
    fun aDoneRoutineKeepsItsPlaceInTheDay() {
        // 수행한 것을 아래로 내리면 어디까지 했는지 눈으로 짚기 어렵습니다.
        showRoutines(todayCounts = mapOf(1L to 3))

        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 번째 루틴을 뒤로").assertIsEnabled()
        // 첫 루틴을 이미 했으니, 지금 할 차례는 두 번째입니다.
        compose.onNodeWithText("다음 차례").assertIsDisplayed()
    }

    @Test
    fun theEndsCannotBeMovedFurther() {
        showRoutines()

        compose.onNodeWithContentDescription("1 번째 루틴을 앞으로").assertIsNotEnabled()
        compose.onNodeWithContentDescription("3 번째 루틴을 뒤로").assertIsNotEnabled()
        compose.onNodeWithContentDescription("3 번째 루틴을 앞으로").assertIsEnabled()
    }

    @Test
    fun theArrowTellsWhichRoutineAndWhichWay() {
        val moves = mutableListOf<Pair<Long, Boolean>>()
        showRoutines(onMoveRoutine = { routine, up -> moves += routine.id to up })

        compose.onNodeWithContentDescription("3 번째 루틴을 앞으로").performClick()

        assertEquals(listOf(3L to true), moves)
    }

    @Test
    fun theNumberOpensTheMoveDialogAndTopSendsItToTheFirstPlace() {
        // 화살표는 한 칸씩이라, 먼 자리로 가려면 여기가 있어야 합니다.
        val moves = mutableListOf<Pair<Long, Int>>()
        showRoutines(onMoveRoutineTo = { routine, index -> moves += routine.id to index })

        compose.onNodeWithContentDescription("3 번째. 차례 옮기기").performClick()
        compose.onNodeWithText("맨 위로").performClick()

        assertEquals(listOf(3L to 0), moves)
    }

    @Test
    fun pickingAPlaceInTheDialogMovesItThere() {
        val moves = mutableListOf<Pair<Long, Int>>()
        showRoutines(onMoveRoutineTo = { routine, index -> moves += routine.id to index })

        compose.onNodeWithContentDescription("1 번째. 차례 옮기기").performClick()
        // 대화상자의 줄에서 두 번째 자리를 짚습니다. 화면에는 1부터, 코드에는 0부터입니다.
        compose.onNodeWithTag(routineMoveTargetTag(2)).performClick()

        assertEquals(listOf(1L to 1), moves)
    }

    @Test
    fun theDialogDoesNotOfferAPlaceTheRoutineIsAlreadyIn() {
        showRoutines()

        compose.onNodeWithContentDescription("1 번째. 차례 옮기기").performClick()

        // 맨 위 루틴에게 "맨 위로"는 할 일이 없습니다.
        compose.onNodeWithText("맨 위로").assertIsNotEnabled()
        compose.onNodeWithText("맨 아래로").assertIsEnabled()
        compose.onNodeWithText("지금 자리").assertIsDisplayed()
    }

    @Test
    fun everythingDoneLeavesNoNextStep() {
        showRoutines(todayCounts = mapOf(1L to 1, 2L to 1, 3L to 1))

        // 다 한 날까지 "다음 차례"를 붙이면 끝이 없는 목록처럼 보입니다.
        assertTrue(compose.onAllNodesWithText("다음 차례").fetchSemanticsNodes().isEmpty())
    }

    private fun showRoutines(
        todayCounts: Map<Long, Int> = emptyMap(),
        onMoveRoutine: (RoutineEntity, Boolean) -> Unit = { _, _ -> },
        onMoveRoutineTo: (RoutineEntity, Int) -> Unit = { _, _ -> }
    ) {
        compose.setContent {
            RoutineScreen(
                routines = routines,
                todayCounts = todayCounts,
                memos = emptyList(),
                onSaveRoutine = {},
                onDeleteRoutine = {},
                onSaveMemo = { _, _ -> },
                onDeleteMemo = {},
                onCheckRoutine = {},
                onMoveRoutine = onMoveRoutine,
                onMoveRoutineTo = onMoveRoutineTo
            )
        }
    }

    private fun routine(id: Long, title: String, orderIndex: Int) = RoutineEntity(
        id = id,
        syncId = "routine-$id",
        updatedAt = 1_000L,
        title = title,
        orderIndex = orderIndex,
        createdAt = id * 100L
    )
}
