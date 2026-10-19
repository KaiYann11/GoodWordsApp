package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 루틴을 갈래로 묶어 보는 규칙입니다.
 *
 * 서른 개가 넘는 루틴을 한 줄로 굴리면 찾는 것이 어디쯤인지 알 수 없습니다.
 */
class RoutineCategoriesTest {
    @Test
    fun ordered_followsTheDayOrderNotTheAlphabet() {
        // 하루를 밟는 순서와 갈래 순서가 따로 놀면 매번 다시 찾아야 합니다.
        val routines = listOf(
            routine(id = 1, orderIndex = 2, category = "공부"),
            routine(id = 2, orderIndex = 0, category = "습관"),
            routine(id = 3, orderIndex = 1, category = "마음가짐"),
            routine(id = 4, orderIndex = 3, category = "습관")
        )

        assertEquals(listOf("습관", "마음가짐", "공부"), RoutineCategories.ordered(routines))
    }

    @Test
    fun ordered_putsTheUnfiledLast() {
        val routines = listOf(
            routine(id = 1, orderIndex = 0),
            routine(id = 2, orderIndex = 1, category = "공부")
        )

        assertEquals(listOf("공부", RoutineCategories.NONE), RoutineCategories.ordered(routines))
        assertEquals("갈래 없음", RoutineCategories.label(RoutineCategories.NONE))
    }

    @Test
    fun ordered_isEmptyWhenNothingIsFiled() {
        // "갈래 없음" 하나만 있는 알약 줄은 고를 것이 없습니다.
        val routines = listOf(routine(id = 1, orderIndex = 0), routine(id = 2, orderIndex = 1, category = "  "))

        assertTrue(RoutineCategories.ordered(routines).isEmpty())
    }

    @Test
    fun ordered_doesNotSplitOnSurroundingSpaces() {
        // `공부`와 `공부 `가 두 갈래로 보이면 같은 것을 두 번 눌러 봐야 합니다.
        val routines = listOf(
            routine(id = 1, orderIndex = 0, category = "공부"),
            routine(id = 2, orderIndex = 1, category = " 공부 ")
        )

        assertEquals(listOf("공부"), RoutineCategories.ordered(routines))
        assertTrue(RoutineCategories.matches(routines[1], "공부"))
    }

    @Test
    fun matches_nullMeansEverything() {
        val filed = routine(id = 1, orderIndex = 0, category = "공부")
        val unfiled = routine(id = 2, orderIndex = 1)

        assertTrue(RoutineCategories.matches(filed, null))
        assertTrue(RoutineCategories.matches(unfiled, null))
        assertTrue(RoutineCategories.matches(unfiled, RoutineCategories.NONE))
        assertFalse(RoutineCategories.matches(filed, RoutineCategories.NONE))
        assertFalse(RoutineCategories.matches(unfiled, "공부"))
    }

    @Test
    fun names_leavesOutTheUnfiled() {
        // 고치기 창에 "갈래 없음"을 눌러 채우는 알약이 있으면 그 글자가 갈래 이름이 됩니다.
        val routines = listOf(
            routine(id = 1, orderIndex = 0),
            routine(id = 2, orderIndex = 1, category = "AI 추천")
        )

        assertEquals(listOf("AI 추천"), RoutineCategories.names(routines))
    }

    private fun routine(id: Long, orderIndex: Int, category: String = "") = RoutineEntity(
        id = id,
        syncId = "routine-$id",
        updatedAt = 1_000L,
        title = "루틴 $id",
        category = category,
        orderIndex = orderIndex,
        createdAt = id * 100L
    )
}
