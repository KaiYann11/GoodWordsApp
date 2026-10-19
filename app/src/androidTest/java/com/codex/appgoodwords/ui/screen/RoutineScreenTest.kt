package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.RoutineCategories
import com.codex.appgoodwords.data.RoutineCheckEntity
import com.codex.appgoodwords.data.RoutineDraft
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.RoutineStatsCalculator
import com.codex.appgoodwords.data.RoutineStatsReport
import java.time.LocalDate
import java.time.ZoneId
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

    private val zone = ZoneId.of("Asia/Seoul")

    /** 2026-10-16은 금요일입니다. */
    private val today = LocalDate.of(2026, 10, 16)

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
    fun theProgressCardCountsWhatWasSteppedToday() {
        showRoutines(todayCounts = mapOf(1L to 2))

        // 두 번 밟은 루틴도 하루에 한 몫입니다. 횟수를 세면 세 개짜리 하루가 200%가 됩니다.
        compose.onNodeWithText("1 / 3 · 33%").assertIsDisplayed()
        compose.onNodeWithText("다음 차례: 스트레칭").assertIsDisplayed()
    }

    @Test
    fun filteringDoesNotRenumberTheDay() {
        // 걸러 놓고 다시 세면 "안 한 것"만 볼 때 3번이던 루틴이 1번으로 보여,
        // 옮기기가 엉뚱한 자리를 가리킵니다.
        showRoutines(todayCounts = mapOf(1L to 1))

        compose.onNodeWithTag(routineFilterTag("UNDONE")).performClick()

        compose.onNodeWithText("스트레칭").assertIsDisplayed()
        compose.onNodeWithText("기상 후 물 한 컵").assertDoesNotExist()
        compose.onNodeWithContentDescription("2 번째. 차례 옮기기").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 번째. 차례 옮기기").assertDoesNotExist()
    }

    @Test
    fun theDoneFilterShowsOnlyWhatWasStepped() {
        showRoutines(todayCounts = mapOf(3L to 1))

        compose.onNodeWithTag(routineFilterTag("DONE")).performClick()

        compose.onNodeWithText("책 한 쪽").assertIsDisplayed()
        compose.onNodeWithText("스트레칭").assertDoesNotExist()
        // 걸러도 차례는 온전한 줄의 것입니다.
        compose.onNodeWithContentDescription("3 번째. 차례 옮기기").assertIsDisplayed()
    }

    @Test
    fun anEmptyFilterSaysWhyInsteadOfShowingNothing() {
        showRoutines(todayCounts = mapOf(1L to 1, 2L to 1, 3L to 1))

        compose.onNodeWithTag(routineFilterTag("UNDONE")).performClick()

        compose.onNodeWithText("오늘 루틴을 모두 밟았습니다.").assertIsDisplayed()
    }

    @Test
    fun everythingDoneLeavesNoNextStep() {
        showRoutines(todayCounts = mapOf(1L to 1, 2L to 1, 3L to 1))

        // 다 한 날까지 "다음 차례"를 붙이면 끝이 없는 목록처럼 보입니다.
        assertTrue(compose.onAllNodesWithText("다음 차례").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theCardSaysHowManyDaysInARow() {
        // 쌓아 온 것이 보이지 않으면 하루 거른 날 그만두기 쉽습니다.
        showRoutines(
            todayCounts = mapOf(1L to 1),
            stats = statsFor("2026-10-14", "2026-10-15", "2026-10-16")
        )

        compose.onNodeWithText("3일 연속").assertIsDisplayed()
        compose.onNodeWithText("· 모두 3회").assertIsDisplayed()
    }

    @Test
    fun aRoutineNeverSteppedGetsNoRecordRow() {
        // 0만 적힌 줄은 알려 주는 것이 없습니다.
        showRoutines(stats = statsFor())

        compose.onNodeWithTag(routineStatsOpenTag(1)).assertDoesNotExist()
    }

    @Test
    fun theRecordRowOpensThatRoutinesHistory() {
        var opened: RoutineEntity? = null
        showRoutines(
            stats = statsFor("2026-10-15", "2026-10-16"),
            onOpenStats = { routine -> opened = routine }
        )

        compose.onNodeWithTag(routineStatsOpenTag(1)).performClick()

        assertEquals(1L, opened?.id)
    }

    private fun statsFor(vararg dates: String): Map<Long, RoutineStatsReport> =
        RoutineStatsCalculator.buildAll(
            routines = routines,
            checks = dates.map { date ->
                RoutineCheckEntity(
                    syncId = "check-$date",
                    routineId = 1,
                    routineSyncId = "routine-1",
                    routineTitle = "기상 후 물 한 컵",
                    checkedAt = LocalDate.parse(date).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                )
            },
            today = today,
            zoneId = zone
        )

    @Test
    fun withoutAnyCategoryThereIsNoCategoryRow() {
        // "갈래 없음" 하나만 있는 줄은 고를 것이 없습니다.
        showRoutines()

        compose.onNodeWithTag(routineCategoryTag(null)).assertDoesNotExist()
    }

    @Test
    fun pickingACategoryKeepsTheDayNumbers() {
        // 갈래로 걸러도 차례는 하루 전체의 것입니다. 다시 세면 옮기기가 엉뚱한 자리를 가리킵니다.
        showRoutines(routines = filedRoutines)

        compose.onNodeWithTag(routineCategoryTag("공부")).performClick()

        compose.onNodeWithText("책 한 쪽").assertIsDisplayed()
        compose.onNodeWithText("스트레칭").assertDoesNotExist()
        compose.onNodeWithContentDescription("3 번째. 차례 옮기기").assertIsDisplayed()
    }

    @Test
    fun theUnfiledHaveTheirOwnPill() {
        showRoutines(routines = filedRoutines)

        compose.onNodeWithTag(routineCategoryTag(RoutineCategories.NONE)).performClick()

        compose.onNodeWithText("스트레칭").assertIsDisplayed()
        compose.onNodeWithText("책 한 쪽").assertDoesNotExist()
    }

    @Test
    fun theCountsSayWhatTappingWillShow() {
        // "습관 1"을 눌렀는데 아무것도 없으면 나머지가 어디 갔는지 찾게 됩니다.
        showRoutines(routines = filedRoutines, todayCounts = mapOf(1L to 1))

        compose.onNodeWithText("습관 1").assertIsDisplayed()
        compose.onNodeWithTag(routineFilterTag("UNDONE")).performClick()
        compose.onNodeWithText("습관 0").assertIsDisplayed()

        compose.onNodeWithTag(routineCategoryTag("습관")).performClick()
        // 오늘 거르개의 숫자도 고른 갈래 안에서 셉니다.
        compose.onNodeWithText("한 것 1").assertIsDisplayed()
        compose.onNodeWithText("오늘 '습관' 루틴을 모두 밟았습니다.").assertIsDisplayed()
    }

    @Test
    fun aSearchedRoutineHiddenByTheCategoryBringsEverythingBack() {
        // 검색에서 고른 루틴이 갈래에 걸려 안 보이면, 찾아 놓고도 빈 화면을 보게 됩니다.
        val focus = mutableStateOf<Long?>(null)
        compose.setContent {
            RoutineScreen(
                routines = filedRoutines,
                todayCounts = emptyMap(),
                memos = emptyList(),
                onSaveRoutine = {},
                onDeleteRoutine = {},
                onSaveMemo = { _, _ -> },
                onDeleteMemo = {},
                onCheckRoutine = {},
                onMoveRoutine = { _, _ -> },
                onMoveRoutineTo = { _, _ -> },
                focusId = focus.value
            )
        }
        compose.onNodeWithTag(routineCategoryTag("공부")).performClick()
        compose.onNodeWithText("스트레칭").assertDoesNotExist()

        compose.runOnIdle { focus.value = 2L }

        compose.onNodeWithText("스트레칭").assertIsDisplayed()
        compose.onNodeWithTag(routineCategoryTag(null)).assertIsSelected()
    }

    @Test
    fun theEditorOffersTheCategoriesAlreadyInUse() {
        // 서른 개에 갈래를 붙일 때 같은 말을 매번 치면 느리고, 한 글자만 달라도 알약이 둘로 갈라집니다.
        val saved = mutableListOf<RoutineDraft>()
        showRoutines(routines = filedRoutines, onSaveRoutine = { saved += it })

        compose.onNodeWithText("루틴 추가").performClick()
        compose.onNodeWithText("루틴 이름").performTextInput("영어 단어")
        compose.onNodeWithTag(routineCategorySuggestionTag("공부")).performClick()
        compose.onNodeWithText("저장").performClick()

        assertEquals(listOf("공부"), saved.map { it.category })
    }

    private val filedRoutines = listOf(
        routine(id = 1, title = "기상 후 물 한 컵", orderIndex = 0, category = "습관"),
        routine(id = 2, title = "스트레칭", orderIndex = 1),
        routine(id = 3, title = "책 한 쪽", orderIndex = 2, category = "공부")
    )

    private fun showRoutines(
        routines: List<RoutineEntity> = this.routines,
        todayCounts: Map<Long, Int> = emptyMap(),
        stats: Map<Long, RoutineStatsReport> = emptyMap(),
        onOpenStats: (RoutineEntity) -> Unit = {},
        onSaveRoutine: (RoutineDraft) -> Unit = {},
        onMoveRoutine: (RoutineEntity, Boolean) -> Unit = { _, _ -> },
        onMoveRoutineTo: (RoutineEntity, Int) -> Unit = { _, _ -> }
    ) {
        compose.setContent {
            RoutineScreen(
                routines = routines,
                todayCounts = todayCounts,
                stats = stats,
                onOpenStats = onOpenStats,
                memos = emptyList(),
                onSaveRoutine = onSaveRoutine,
                onDeleteRoutine = {},
                onSaveMemo = { _, _ -> },
                onDeleteMemo = {},
                onCheckRoutine = {},
                onMoveRoutine = onMoveRoutine,
                onMoveRoutineTo = onMoveRoutineTo
            )
        }
    }

    private fun routine(id: Long, title: String, orderIndex: Int, category: String = "") = RoutineEntity(
        id = id,
        syncId = "routine-$id",
        updatedAt = 1_000L,
        title = title,
        category = category,
        orderIndex = orderIndex,
        createdAt = id * 100L
    )
}
