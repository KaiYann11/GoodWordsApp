package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.codex.appgoodwords.data.RoutineCategories
import com.codex.appgoodwords.data.RoutineCheckEntity
import com.codex.appgoodwords.data.RoutineDraft
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.RoutineMergeChoice
import com.codex.appgoodwords.data.RoutineSchedule
import com.codex.appgoodwords.data.RoutineStatsCalculator
import com.codex.appgoodwords.data.RoutineStatsReport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun mergingStartsFromTheEditorAndHandsOverTheChosenNameAndMemo() {
        // 합치기는 고치던 루틴의 창에서 시작하고, 그 루틴이 남습니다(사용자가 정함, 2026-11-02).
        val merged = mutableListOf<List<String>>()
        val withNotes = listOf(
            routine(id = 1, title = "기상 후 물 한 컵", orderIndex = 0).copy(note = "일어나자마자"),
            routine(id = 2, title = "물 마시기", orderIndex = 1).copy(note = "물병을 책상에"),
            routine(id = 3, title = "책 한 쪽", orderIndex = 2)
        )
        showMerge(withNotes, checkCounts(withNotes, 1L to 12, 2L to 30)) { keep, gone, title, note ->
            merged += listOf("${keep.id}", "${gone.id}", title, note)
        }

        compose.onAllNodesWithContentDescription("루틴 수정")[0].performClick()
        // 요일·시각이 더해져 창이 길어졌습니다. 맨 아래 단추는 굴려서 누릅니다.
        compose.onNodeWithTag(routineMergeStartTag).performScrollTo().performClick()
        // 고치던 루틴은 고를 수 없습니다. 쌓인 체크 수를 보고 고릅니다.
        compose.onNodeWithTag(routineMergeCandidateTag(1)).assertDoesNotExist()
        compose.onNodeWithText("체크 30회").assertIsDisplayed()
        compose.onNodeWithTag(routineMergeCandidateTag(2)).performClick()

        // 이름은 고치던 것, 메모는 둘 다로 시작합니다. 이력은 모두 모읍니다.
        compose.onNodeWithTag(routineMergeTitleTag).assertTextContains("기상 후 물 한 컵")
        compose.onNodeWithTag(routineMergeChoiceTag("title", RoutineMergeChoice.THIS)).assertIsSelected()
        compose.onNodeWithTag(routineMergeChoiceTag("note", RoutineMergeChoice.BOTH)).assertIsSelected()
        compose.onNodeWithText("체크 42회(12+30)", substring = true).assertIsDisplayed()

        compose.onNodeWithTag(routineMergeChoiceTag("title", RoutineMergeChoice.BOTH)).performClick()
        compose.onNodeWithTag(routineMergeConfirmTag).performClick()

        assertEquals(listOf(listOf("1", "2", "기상 후 물 한 컵 · 물 마시기", "일어나자마자\n\n물병을 책상에")), merged)
    }

    @Test
    fun aChosenNameCanBeEditedBeforeMerging() {
        val merged = mutableListOf<String>()
        showMerge(routines, emptyMap()) { _, _, title, _ -> merged += title }

        compose.onAllNodesWithContentDescription("루틴 수정")[1].performClick()
        // 요일·시각이 더해져 창이 길어졌습니다. 맨 아래 단추는 굴려서 누릅니다.
        compose.onNodeWithTag(routineMergeStartTag).performScrollTo().performClick()
        compose.onNodeWithTag(routineMergeCandidateTag(3)).performClick()
        compose.onNodeWithTag(routineMergeChoiceTag("title", RoutineMergeChoice.THAT)).performClick()
        compose.onNodeWithTag(routineMergeTitleTag).assertTextContains("책 한 쪽")

        // 고른 글을 손으로 고치면 어느 알약도 아닙니다. 고친 글 그대로 합칩니다.
        compose.onNodeWithTag(routineMergeTitleTag).performTextReplacement("아침 스트레칭과 책")
        compose.onNodeWithTag(routineMergeChoiceTag("title", RoutineMergeChoice.THAT)).assertIsNotSelected()
        compose.onNodeWithTag(routineMergeConfirmTag).performClick()

        assertEquals(listOf("아침 스트레칭과 책"), merged)
    }

    @Test
    fun aNewRoutineOrTheOnlyRoutineHasNothingToMergeWith() {
        showMerge(listOf(routine(id = 1, title = "하나뿐인 루틴", orderIndex = 0)), emptyMap()) { _, _, _, _ -> }
        compose.onNodeWithContentDescription("루틴 수정").performClick()
        compose.onNodeWithTag(routineMergeStartTag).assertDoesNotExist()
        compose.onNodeWithText("취소").performClick()

        compose.onNodeWithText("루틴 추가").performClick()
        compose.onNodeWithTag(routineMergeStartTag).assertDoesNotExist()
    }

    private fun showMerge(
        routines: List<RoutineEntity>,
        stats: Map<Long, RoutineStatsReport>,
        onMerge: (RoutineEntity, RoutineEntity, String, String) -> Unit
    ) {
        compose.setContent {
            RoutineScreen(
                routines = routines,
                todayCounts = emptyMap(),
                stats = stats,
                memos = emptyList(),
                onSaveRoutine = {},
                onDeleteRoutine = {},
                onSaveMemo = { _, _, _ -> },
                onDeleteMemo = {},
                onCheckRoutine = { _, _ -> },
                onMoveRoutine = { _, _ -> },
                onMoveRoutineTo = { _, _ -> },
                onMergeRoutines = onMerge
            )
        }
    }

    /** 루틴마다 지난 며칠에 하나씩 체크한 기록. */
    private fun checkCounts(routines: List<RoutineEntity>, vararg counts: Pair<Long, Int>): Map<Long, RoutineStatsReport> =
        RoutineStatsCalculator.buildAll(
            routines = routines,
            checks = counts.flatMap { (id, count) ->
                (1..count).map { n ->
                    RoutineCheckEntity(
                        syncId = "check-$id-$n",
                        routineId = id,
                        routineSyncId = "routine-$id",
                        routineTitle = "루틴 $id",
                        checkedAt = today.minusDays(n.toLong()).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                    )
                }
            },
            today = today,
            zoneId = zone
        )

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
                onSaveMemo = { _, _, _ -> },
                onDeleteMemo = {},
                onCheckRoutine = { _, _ -> },
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
        onMoveRoutineTo: (RoutineEntity, Int) -> Unit = { _, _ -> },
        on: LocalDate = LocalDate.now(),
        checks: List<RoutineCheckEntity> = emptyList(),
        onCheckRoutine: (RoutineEntity, LocalDate) -> Unit = { _, _ -> },
        onSaveMemo: (RoutineEntity, String, LocalDate) -> Unit = { _, _, _ -> }
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
                onSaveMemo = onSaveMemo,
                onDeleteMemo = {},
                onCheckRoutine = onCheckRoutine,
                onMoveRoutine = onMoveRoutine,
                onMoveRoutineTo = onMoveRoutineTo,
                today = on,
                checks = checks,
                zoneId = zone
            )
        }
    }

    // ---- 요일·시각(사용자가 정함, 2026-11-17) ----

    /** 오늘(10월 16일)은 금요일입니다. 헬스장은 월·수·금 19:00, 대청소는 주말입니다. */
    private val weekRoutines = listOf(
        routine(id = 1, title = "기상 후 물 한 컵", orderIndex = 0),
        routine(id = 2, title = "주말 대청소", orderIndex = 1).copy(weekdays = RoutineSchedule.WEEKEND),
        routine(id = 3, title = "헬스장", orderIndex = 2)
            .copy(weekdays = RoutineSchedule.maskOf(listOf(1, 3, 5)), timeOfDay = 19 * 60)
    )

    private fun dayPill(day: DayOfWeek?) = compose.onNodeWithTag(routineDayTag(day))

    /** 요일 알약 줄은 옆으로 밀립니다. 화면 밖 알약은 굴려 와서 짚습니다. */
    private fun scrollToDay(day: DayOfWeek?) {
        compose.onNodeWithTag(routineDayRowTag).performScrollToNode(hasTestTag(routineDayTag(day)))
    }

    @Test
    fun theTabOpensOnTodayAndShowsOnlyTodaysRoutines() {
        showRoutines(routines = weekRoutines, on = today)

        dayPill(DayOfWeek.FRIDAY).assertIsSelected()
        dayPill(DayOfWeek.FRIDAY).assertTextContains("금 2").assertTextContains("오늘")
        compose.onNodeWithText("기상 후 물 한 컵").assertIsDisplayed()
        compose.onNodeWithText("헬스장").assertIsDisplayed()
        compose.onNodeWithText("주말 대청소").assertDoesNotExist()
        // 차례 번호는 전체 줄의 것입니다. 금요일에 쉬는 2번이 빠져도 헬스장은 3번입니다.
        compose.onNodeWithContentDescription("3 번째. 차례 옮기기").assertIsDisplayed()
        // 오늘 진행도 오늘 하는 루틴만 셉니다.
        compose.onNodeWithText("0 / 2 · 0%").assertIsDisplayed()
    }

    @Test
    fun anotherDayShowsItsRoutinesAndAllShowsEveryRoutine() {
        showRoutines(routines = weekRoutines, on = today)

        scrollToDay(DayOfWeek.SATURDAY)
        dayPill(DayOfWeek.SATURDAY).performClick()
        compose.onNodeWithText("주말 대청소").assertIsDisplayed()
        compose.onNodeWithText("헬스장").assertDoesNotExist()
        // 토요일 알약은 아직 오지 않은 내일(17일)입니다. 그 요일에 하는 루틴은 보입니다(2026-11-19).
        compose.onNodeWithTag(routineFilterTag("UNDONE")).assertIsDisplayed()

        scrollToDay(null)
        dayPill(null).performClick()
        compose.onNodeWithText("주말 대청소").assertIsDisplayed()
        compose.onNodeWithText("헬스장").assertIsDisplayed()
        compose.onNodeWithTag(routineFilterTag("UNDONE")).assertIsDisplayed()
    }

    @Test
    fun theCardShowsTheTimeAndTheChosenDaysOnly() {
        showRoutines(routines = weekRoutines, on = today)

        compose.onNodeWithTag(routineTimeTag(3)).assertTextContains("19:00")
        compose.onNodeWithText("월·수·금").assertIsDisplayed()
        // 매일·상시 루틴에는 아무것도 붙이지 않습니다 — 대부분이 그래서 줄마다 같은 글자가 됩니다.
        compose.onNodeWithTag(routineTimeTag(1)).assertDoesNotExist()
        assertTrue(compose.onAllNodesWithText("매일").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aDayWithoutRoutinesSaysSoInsteadOfShowingNothing() {
        // 10월 17일은 토요일입니다. 평일 루틴만 있으면 오늘은 할 것이 없는 날입니다.
        val weekdaysOnly = routines.map { it.copy(weekdays = RoutineSchedule.WEEKDAYS) }
        showRoutines(routines = weekdaysOnly, on = today.plusDays(1))

        compose.onNodeWithText("오늘 요일에 하는 루틴이 없습니다.").assertIsDisplayed()
        compose.onNodeWithText("오늘 하는 루틴이 없습니다.").assertIsDisplayed()

        scrollToDay(DayOfWeek.SUNDAY)
        dayPill(DayOfWeek.SUNDAY).performClick()
        compose.onNodeWithText("일요일에 하는 루틴이 없습니다.").assertIsDisplayed()
    }

    @Test
    fun theEditorSavesTheChosenDaysAndTime() {
        val saved = mutableListOf<RoutineDraft>()
        showRoutines(onSaveRoutine = { saved += it }, on = today)

        compose.onNodeWithText("루틴 추가").performClick()
        compose.onNodeWithText("루틴 이름").performTextInput("헬스장")
        // 새 루틴은 매일·상시로 시작합니다.
        compose.onNodeWithTag(routineEditPresetTag("EVERY_DAY")).performScrollTo().assertIsSelected()
        compose.onNodeWithTag(routineEditAnytimeTag).performScrollTo().assertIsSelected()

        compose.onNodeWithTag(routineEditPresetTag("WEEKDAYS")).performScrollTo().performClick()
        compose.onNodeWithTag(routineEditDayTag(DayOfWeek.MONDAY)).performScrollTo().performClick()
        compose.onNodeWithTag(routineEditDayTag(DayOfWeek.MONDAY)).assertIsOff()
        compose.onNodeWithTag(routineEditTimedTag).performScrollTo().performClick()
        compose.onNodeWithTag(routineEditTimeTag).performScrollTo().assertTextContains("알람 09:00")
        compose.onNodeWithText("저장").performClick()

        assertEquals(1, saved.size)
        assertEquals(RoutineSchedule.maskOf(listOf(2, 3, 4, 5)), saved.single().weekdays)
        assertEquals(9 * 60, saved.single().timeOfDay)
    }

    @Test
    fun turningOffEveryDayBlocksSaving() {
        // 어느 날에도 보이지 않는 루틴은 고칠 수도 지울 수도 없게 숨습니다.
        showRoutines(on = today)

        compose.onNodeWithText("루틴 추가").performClick()
        compose.onNodeWithText("루틴 이름").performTextInput("숨을 루틴")
        RoutineStatsCalculator.WEEK_ORDER.forEach { day ->
            compose.onNodeWithTag(routineEditDayTag(day)).performScrollTo().performClick()
        }

        compose.onNodeWithText("요일을 하나 이상 고르세요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("저장").assertIsNotEnabled()
    }

    @Test
    fun editingShowsTheRoutinesDaysAndTimeAndCanMakeItAnytime() {
        val saved = mutableListOf<RoutineDraft>()
        showRoutines(routines = weekRoutines, onSaveRoutine = { saved += it }, on = today)

        // 오늘(금) 줄에서 헬스장은 둘째 카드입니다.
        compose.onAllNodesWithContentDescription("루틴 수정")[1].performClick()
        compose.onNodeWithTag(routineEditDayTag(DayOfWeek.MONDAY)).performScrollTo().assertIsOn()
        compose.onNodeWithTag(routineEditDayTag(DayOfWeek.TUESDAY)).performScrollTo().assertIsOff()
        compose.onNodeWithTag(routineEditTimeTag).performScrollTo().assertTextContains("알람 19:00")

        compose.onNodeWithTag(routineEditAnytimeTag).performScrollTo().performClick()
        compose.onNodeWithTag(routineEditTimeTag).assertDoesNotExist()
        compose.onNodeWithText("저장").performClick()

        assertEquals(3L, saved.single().id)
        assertEquals(RoutineSchedule.maskOf(listOf(1, 3, 5)), saved.single().weekdays)
        assertNull(saved.single().timeOfDay)
    }

    @Test
    fun aRoutineFoundOnAnotherDayOpensWithAllDays() {
        // 검색이나 알람에서 고른 루틴이 오늘 쉬는 것이면, 오늘 줄에 없어 찾아 놓고도 빈 화면을 보게 됩니다.
        val focus = mutableStateOf<Long?>(null)
        compose.setContent {
            RoutineScreen(
                routines = weekRoutines,
                todayCounts = emptyMap(),
                memos = emptyList(),
                onSaveRoutine = {},
                onDeleteRoutine = {},
                onSaveMemo = { _, _, _ -> },
                onDeleteMemo = {},
                onCheckRoutine = { _, _ -> },
                onMoveRoutine = { _, _ -> },
                onMoveRoutineTo = { _, _ -> },
                focusId = focus.value,
                today = today
            )
        }
        compose.onNodeWithText("주말 대청소").assertDoesNotExist()

        compose.runOnIdle { focus.value = 2L }

        compose.onNodeWithText("주말 대청소").assertIsDisplayed()
        scrollToDay(null)
        dayPill(null).assertIsSelected()
    }

    // ---- 지난날의 체크(사용자가 정함, 2026-11-18), 앞날(2026-11-19)과 오늘 가운데 줄(2026-11-23) ----
    // 오늘(10월 16일)은 금요일이고, 요일 줄은 13일(화)~19일(월)입니다. 화·수·목 알약은 13~15일이고, 토·일·월 알약은 아직
    // 오지 않은 17~19일입니다 — 지난주 10~12일이 아닙니다.

    /** [date]의 [hour]시에 한 체크. */
    private fun checkOn(routineId: Long, date: LocalDate, hour: Int = 9) = RoutineCheckEntity(
        syncId = "check-$routineId-$date-$hour",
        routineId = routineId,
        routineSyncId = "routine-$routineId",
        routineTitle = "루틴 $routineId",
        checkedAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    )

    @Test
    fun eachDayPillShowsTheDateItPointsAt() {
        showRoutines(on = today)

        dayPill(DayOfWeek.FRIDAY).assertTextContains("오늘")
        compose.onNodeWithTag(routineWeekLabelTag).assertTextContains("오늘 앞뒤 · 10월 13일 ~ 19일")
        scrollToDay(DayOfWeek.TUESDAY)
        dayPill(DayOfWeek.TUESDAY).assertTextContains("13일")
        scrollToDay(DayOfWeek.THURSDAY)
        dayPill(DayOfWeek.THURSDAY).assertTextContains("15일")
        // 오늘 뒤 요일은 다가오는 날입니다 — 지난주 날로 미끄러지지 않습니다. 월요일도 나흘 전 12일이 아니라 19일입니다.
        scrollToDay(DayOfWeek.SATURDAY)
        dayPill(DayOfWeek.SATURDAY).assertTextContains("17일")
        scrollToDay(DayOfWeek.SUNDAY)
        dayPill(DayOfWeek.SUNDAY).assertTextContains("18일")
        scrollToDay(DayOfWeek.MONDAY)
        dayPill(DayOfWeek.MONDAY).assertTextContains("19일")
        scrollToDay(null)
        dayPill(null).assertTextContains("모든 요일")
    }

    @Test
    fun thePillsRunInDateOrderWithTodayInTheMiddle() {
        showRoutines(on = today)

        // 전체 다음 맨 앞은 사흘 전 화요일입니다.
        scrollToDay(null)
        assertInOrder(null, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
        // 맨 끝은 사흘 뒤 월요일입니다.
        scrollToDay(DayOfWeek.MONDAY)
        assertInOrder(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY)
    }

    /** 알약들이 이 차례로 왼쪽에서 오른쪽으로 놓였습니다. */
    private fun assertInOrder(vararg days: DayOfWeek?) {
        val lefts = days.map { dayPill(it).fetchSemanticsNode().boundsInRoot.left }
        assertTrue("${days.toList()}: $lefts", lefts.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun onSundayTheComingDaysAreEmptyAndLastWeeksMondayIsOneRowBack() {
        // 사용자가 말한 날(2026-11-23): 일요일에 열면 월~토 알약에 지난주 기록이 보였습니다. 이제 월·화·수는 다가오는 날이라
        // 비어 있고, 지난 월요일은 ‹로 한 줄 앞에 있습니다.
        val sunday = today.plusDays(2)
        val lastMonday = today.minusDays(4)
        showRoutines(
            on = sunday,
            checks = listOf(checkOn(1, lastMonday), checkOn(2, lastMonday), checkOn(1, today.plusDays(1)))
        )
        dayPill(DayOfWeek.SUNDAY).assertIsSelected().assertTextContains("오늘")
        compose.onNodeWithTag(routineWeekLabelTag).assertTextContains("오늘 앞뒤 · 10월 15일 ~ 21일")

        scrollToDay(DayOfWeek.MONDAY)
        dayPill(DayOfWeek.MONDAY).assertTextContains("19일").performClick()
        compose.onNodeWithText("19일(월)").assertIsDisplayed()
        compose.onNodeWithText("할 루틴 3개").assertIsDisplayed()
        compose.onNodeWithText("아직 오지 않은 날 · 그날 와서 적습니다.").assertIsDisplayed()
        assertEquals(3, compose.onAllNodesWithText("19일 0 회").fetchSemanticsNodes().size)

        // 어제(토요일)는 그날 기록 그대로입니다.
        scrollToDay(DayOfWeek.SATURDAY)
        dayPill(DayOfWeek.SATURDAY).assertTextContains("17일").performClick()
        compose.onNodeWithText("17일(토) 진행").assertIsDisplayed()
        compose.onNodeWithText("17일 1 회").assertExists()

        // 지난 월요일(12일)은 ‹로 한 줄 앞입니다.
        compose.onNodeWithTag(routineWeekPrevTag).performClick()
        compose.onNodeWithTag(routineWeekLabelTag).assertTextContains("1주 전 · 10월 8일 ~ 14일")
        scrollToDay(DayOfWeek.MONDAY)
        dayPill(DayOfWeek.MONDAY).assertTextContains("12일").performClick()
        compose.onNodeWithText("12일(월) 진행").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("12일 1 회").fetchSemanticsNodes().size)
    }

    @Test
    fun aPastDayShowsThatDaysChecksNotTodays() {
        val yesterday = today.minusDays(1)
        showRoutines(
            on = today,
            todayCounts = mapOf(2L to 1),
            checks = listOf(checkOn(1, yesterday, 7), checkOn(1, yesterday, 21), checkOn(2, today))
        )

        scrollToDay(DayOfWeek.THURSDAY)
        dayPill(DayOfWeek.THURSDAY).performClick()

        compose.onNodeWithText("15일(목) 진행").assertIsDisplayed()
        compose.onNodeWithText("1 / 3 · 33%").assertIsDisplayed()
        compose.onNodeWithText("지난날 기록 중 · +1은 15일로 적힙니다.").assertIsDisplayed()
        compose.onNodeWithText("15일 2 회").assertExists()
        // 오늘 한 스트레칭은 그날 0회입니다.
        assertTrue(compose.onAllNodesWithText("15일 0 회").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("오늘 1 회").fetchSemanticsNodes().isEmpty())
        // 지난날에는 지금 할 차례가 없습니다.
        assertTrue(compose.onAllNodesWithText("다음 차례").fetchSemanticsNodes().isEmpty())

        // 안 한 것도 그날 체크로 거릅니다.
        compose.onNodeWithTag(routineFilterTag("UNDONE")).performClick()
        compose.onNodeWithText("스트레칭").assertIsDisplayed()
        compose.onNodeWithText("기상 후 물 한 컵").assertDoesNotExist()
    }

    @Test
    fun anUpcomingDayShowsNoneOfLastWeeksChecksAndCannotBeWritten() {
        // 토요일 알약은 아직 오지 않은 17일입니다. 지난 토요일(10일)의 체크가 거기 비치면 안 됩니다(사용자가 정함, 2026-11-19).
        val written = mutableListOf<LocalDate>()
        val lastSaturday = today.minusDays(6)
        showRoutines(
            routines = weekRoutines,
            on = today,
            checks = listOf(checkOn(1, lastSaturday), checkOn(2, lastSaturday)),
            onCheckRoutine = { _, date -> written += date }
        )

        scrollToDay(DayOfWeek.SATURDAY)
        dayPill(DayOfWeek.SATURDAY).performClick()

        compose.onNodeWithText("17일(토)").assertIsDisplayed()
        compose.onNodeWithText("할 루틴 2개").assertIsDisplayed()
        compose.onNodeWithText("아직 오지 않은 날 · 그날 와서 적습니다.").assertIsDisplayed()
        // 그 요일에 하는 루틴은 보이고 회수는 모두 0입니다. 다음 차례도 없습니다.
        compose.onNodeWithText("주말 대청소").assertIsDisplayed()
        compose.onNodeWithText("헬스장").assertDoesNotExist()
        assertEquals(2, compose.onAllNodesWithText("17일 0 회").fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodesWithText("다음 차례").fetchSemanticsNodes().isEmpty())
        // 적을 수 없습니다.
        compose.onAllNodesWithText("수행 +1")[0].assertIsNotEnabled().performClick()
        assertTrue(written.isEmpty())
    }

    @Test
    fun anUpcomingDaysMemoDialogOnlyShowsTheMemos() {
        // 메모는 그날 한 번 한 것으로 세는데, 그날이 아직 오지 않았습니다.
        val saved = mutableListOf<LocalDate>()
        showRoutines(on = today, onSaveMemo = { _, _, date -> saved += date })

        scrollToDay(DayOfWeek.SUNDAY)
        dayPill(DayOfWeek.SUNDAY).performClick()
        compose.onAllNodesWithText("메모 0")[0].performClick()

        compose.onNodeWithText("아직 오지 않은 날입니다. 메모는 그날 와서 남깁니다.").assertIsDisplayed()
        compose.onNodeWithText("메모 기록").assertDoesNotExist()
        compose.onNodeWithText("메모 저장").assertDoesNotExist()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun theLeftArrowGoesAWeekBackOnTheSameWeekdayAndWritesThere() {
        // 사흘보다 앞선 날을 적는 길입니다(사용자가 정함, 2026-11-19 — 2026-11-23부터 줄 전체가 이레씩). 한 줄 앞은 모두
        // 지난날입니다.
        val written = mutableListOf<LocalDate>()
        val lastFriday = today.minusDays(7)
        showRoutines(
            on = today,
            checks = listOf(checkOn(1, lastFriday, 7), checkOn(1, lastFriday, 20)),
            onCheckRoutine = { _, date -> written += date }
        )
        // 오늘 가운데 줄보다 앞으로는 가지 않습니다.
        compose.onNodeWithTag(routineWeekNextTag).assertIsNotEnabled()

        compose.onNodeWithTag(routineWeekPrevTag).performClick()

        compose.onNodeWithTag(routineWeekLabelTag).assertTextContains("1주 전 · 10월 6일 ~ 12일")
        dayPill(DayOfWeek.FRIDAY).assertIsSelected().assertTextContains("9일")
        compose.onNodeWithText("9일(금) 진행").assertIsDisplayed()
        compose.onNodeWithText("9일 2 회").assertExists()

        scrollToDay(DayOfWeek.SUNDAY)
        dayPill(DayOfWeek.SUNDAY).assertTextContains("11일").performClick()
        compose.onNodeWithText("11일(일) 진행").assertIsDisplayed()
        compose.onAllNodesWithText("수행 +1")[0].performClick()
        assertEquals(listOf(LocalDate.of(2026, 10, 11)), written)

        // ›로 오늘 가운데 줄에 돌아오면 같은 요일(일요일)은 아직 오지 않은 18일입니다.
        compose.onNodeWithTag(routineWeekNextTag).assertIsEnabled().performClick()
        compose.onNodeWithTag(routineWeekLabelTag).assertTextContains("오늘 앞뒤 · 10월 13일 ~ 19일")
        dayPill(DayOfWeek.SUNDAY).assertIsSelected().assertTextContains("18일")
        compose.onNodeWithTag(routineWeekNextTag).assertIsNotEnabled()
    }

    @Test
    fun allDaysCountsTodaySoItBringsTheCenteredRowBack() {
        showRoutines(on = today)
        compose.onNodeWithTag(routineWeekPrevTag).performClick()
        compose.onNodeWithTag(routineWeekPrevTag).performClick()
        compose.onNodeWithTag(routineWeekLabelTag).assertTextContains("2주 전 · 9월 29일 ~ 10월 5일")

        scrollToDay(null)
        dayPill(null).performClick()

        compose.onNodeWithTag(routineWeekLabelTag).assertTextContains("오늘 앞뒤 · 10월 13일 ~ 19일")
        compose.onNodeWithText("오늘 진행").assertIsDisplayed()
    }

    @Test
    fun plusOneWritesToThePickedDayAndAllMeansToday() {
        val written = mutableListOf<Pair<Long, LocalDate>>()
        showRoutines(on = today, onCheckRoutine = { routine, date -> written += routine.id to date })

        scrollToDay(DayOfWeek.THURSDAY)
        dayPill(DayOfWeek.THURSDAY).performClick()
        compose.onAllNodesWithText("수행 +1")[0].performClick()

        scrollToDay(DayOfWeek.FRIDAY)
        dayPill(DayOfWeek.FRIDAY).performClick()
        compose.onAllNodesWithText("수행 +1")[0].performClick()

        scrollToDay(null)
        dayPill(null).performClick()
        compose.onAllNodesWithText("수행 +1")[0].performClick()

        assertEquals(listOf(1L to today.minusDays(1), 1L to today, 1L to today), written)
    }

    @Test
    fun aMemoOnAPastDaySaysItCountsForThatDay() {
        val saved = mutableListOf<Pair<String, LocalDate>>()
        showRoutines(on = today, onSaveMemo = { _, body, date -> saved += body to date })

        scrollToDay(DayOfWeek.THURSDAY)
        dayPill(DayOfWeek.THURSDAY).performClick()
        compose.onAllNodesWithText("메모 0")[0].performClick()
        compose.onNodeWithText("저장하면 15일(목)에 한 번 한 것으로 셉니다.").assertIsDisplayed()
        compose.onNodeWithText("메모 기록").performTextInput("늦게 적음")
        compose.onNodeWithText("메모 저장").performClick()

        assertEquals(listOf("늦게 적음" to today.minusDays(1)), saved)
    }

    @Test
    fun theDayCountsFollowEachDaysOwnChecks() {
        // 안 한 것을 고른 채 요일을 훑으면, 알약 숫자가 그날 남은 수여야 합니다.
        showRoutines(on = today, checks = listOf(checkOn(1, today.minusDays(1)), checkOn(2, today.minusDays(1))))
        compose.onNodeWithTag(routineFilterTag("UNDONE")).performClick()

        dayPill(DayOfWeek.FRIDAY).assertTextContains("금 3")
        scrollToDay(DayOfWeek.THURSDAY)
        dayPill(DayOfWeek.THURSDAY).assertTextContains("목 1")
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
