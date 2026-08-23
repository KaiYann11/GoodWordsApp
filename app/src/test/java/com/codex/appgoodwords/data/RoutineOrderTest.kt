package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 하루 루틴의 차례를 지키는 규칙입니다.
 *
 * 서버 `moveRoutine`과 같은 규칙이어야 합니다. 한쪽만 다르게 옮기면 두 기기가 병합할 때마다
 * 서로의 차례를 고쳐 끝나지 않습니다.
 */
class RoutineOrderTest {
    @Test
    fun sorted_ordersByStepNotByCreatedAt() {
        val routines = listOf(
            routine(id = 1, syncId = "a", orderIndex = 2, createdAt = 100L),
            routine(id = 2, syncId = "b", orderIndex = 0, createdAt = 300L),
            routine(id = 3, syncId = "c", orderIndex = 1, createdAt = 200L)
        )

        assertEquals(listOf(2L, 3L, 1L), RoutineOrder.sorted(routines).map { it.id })
    }

    @Test
    fun sorted_putsTheOlderOneFirstWhenTwoDevicesPickedTheSameStep() {
        // 두 기기에서 각각 만든 루틴이 만나면 번호가 겹칠 수 있다.
        // 그래도 어느 기기에서나 같은 줄로 보여야 한다.
        val routines = listOf(
            routine(id = 1, syncId = "b", orderIndex = 1, createdAt = 500L),
            routine(id = 2, syncId = "a", orderIndex = 1, createdAt = 100L)
        )

        assertEquals(listOf("a", "b"), RoutineOrder.sorted(routines).map { it.syncId })
    }

    @Test
    fun moved_swapsWithTheNeighbourAbove() {
        val routines = listOf(
            routine(id = 1, syncId = "a", orderIndex = 0),
            routine(id = 2, syncId = "b", orderIndex = 1),
            routine(id = 3, syncId = "c", orderIndex = 2)
        )

        val changed = RoutineOrder.moved(routines, routineId = 3, up = true)

        assertEquals(listOf(3L to 1, 2L to 2), changed.map { it.id to it.orderIndex })
        assertEquals(listOf(1L, 3L, 2L), applied(routines, changed).map { it.id })
    }

    @Test
    fun moved_swapsWithTheNeighbourBelow() {
        val routines = listOf(
            routine(id = 1, syncId = "a", orderIndex = 0),
            routine(id = 2, syncId = "b", orderIndex = 1)
        )

        val changed = RoutineOrder.moved(routines, routineId = 1, up = false)

        assertEquals(listOf(2L, 1L), applied(routines, changed).map { it.id })
    }

    @Test
    fun moved_onlyReturnsTheOnesWhoseStepReallyChanged() {
        // 안 바뀐 것까지 저장하면 updatedAt이 올라 서버가 새 리비전을 붙이고,
        // 증분 동기화가 매번 루틴 전부를 실어 나른다.
        val routines = (0 until 6).map { index ->
            routine(id = index + 1L, syncId = "r$index", orderIndex = index)
        }

        val changed = RoutineOrder.moved(routines, routineId = 5, up = true)

        assertEquals(listOf(5L, 4L), changed.map { it.id })
    }

    @Test
    fun moved_doesNothingAtTheEnds() {
        val routines = listOf(
            routine(id = 1, syncId = "a", orderIndex = 0),
            routine(id = 2, syncId = "b", orderIndex = 1)
        )

        assertTrue(RoutineOrder.moved(routines, routineId = 1, up = true).isEmpty())
        assertTrue(RoutineOrder.moved(routines, routineId = 2, up = false).isEmpty())
        assertTrue(RoutineOrder.moved(routines, routineId = 99, up = true).isEmpty())
    }

    @Test
    fun moved_renumbersRoutinesThatShareOrWasteSteps() {
        // 순서를 모르던 시절에 만든 루틴은 번호가 전부 0이다. 한 번 옮기면 반듯해져야 한다.
        val routines = listOf(
            routine(id = 1, syncId = "a", orderIndex = 0, createdAt = 100L),
            routine(id = 2, syncId = "b", orderIndex = 0, createdAt = 200L),
            routine(id = 3, syncId = "c", orderIndex = 0, createdAt = 300L)
        )

        val changed = RoutineOrder.moved(routines, routineId = 3, up = true)
        val result = applied(routines, changed)

        assertEquals(listOf(1L, 3L, 2L), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.orderIndex })
    }

    @Test
    fun movedTo_bringsAFarRoutineToTheTopInOneGo() {
        // 서른 번째를 한 칸씩 올리면 스물아홉 번을 눌러야 합니다.
        val routines = (0 until 30).map { index ->
            routine(id = index + 1L, syncId = "r%02d".format(index), orderIndex = index)
        }

        val changed = RoutineOrder.movedTo(routines, routineId = 30L, targetIndex = 0)
        val result = applied(routines, changed)

        assertEquals(30L, result.first().id)
        assertEquals(listOf(30L, 1L, 2L), result.take(3).map { it.id })
        assertEquals((0 until 30).toList(), result.map { it.orderIndex })
    }

    @Test
    fun movedTo_pullsAPlacePastTheEndBackToTheLastOne() {
        // "맨 아래로"를 큰 수 하나로 부를 수 있어야 개수를 세지 않아도 됩니다.
        val routines = listOf(
            routine(id = 1, syncId = "a", orderIndex = 0),
            routine(id = 2, syncId = "b", orderIndex = 1),
            routine(id = 3, syncId = "c", orderIndex = 2)
        )

        val result = applied(routines, RoutineOrder.movedTo(routines, 1L, RoutineOrder.LAST_INDEX))

        assertEquals(listOf(2L, 3L, 1L), result.map { it.id })
    }

    @Test
    fun movedTo_doesNothingWhenTheRoutineIsAlreadyThere() {
        val routines = listOf(
            routine(id = 1, syncId = "a", orderIndex = 0),
            routine(id = 2, syncId = "b", orderIndex = 1)
        )

        assertTrue(RoutineOrder.movedTo(routines, routineId = 1L, targetIndex = 0).isEmpty())
        // 맨 위인 루틴을 더 위로 보내라고 해도 마찬가지입니다.
        assertTrue(RoutineOrder.movedTo(routines, routineId = 1L, targetIndex = -5).isEmpty())
        assertTrue(RoutineOrder.movedTo(routines, routineId = 99L, targetIndex = 0).isEmpty())
    }

    @Test
    fun movedTo_leavesTheRoutinesAboveTheTargetAlone() {
        // 다섯 번째를 세 번째로 옮기면 앞의 둘은 그대로여야 합니다.
        val routines = (0 until 6).map { index ->
            routine(id = index + 1L, syncId = "r$index", orderIndex = index)
        }

        val changed = RoutineOrder.movedTo(routines, routineId = 5L, targetIndex = 2)

        assertEquals(listOf(5L, 3L, 4L), changed.map { it.id })
        assertEquals(listOf(1L, 2L, 5L, 3L, 4L, 6L), applied(routines, changed).map { it.id })
    }

    @Test
    fun nextIndex_putsANewRoutineAtTheEnd() {
        // 루틴이 하나도 없으면 DAO가 -1을 준다. 첫 루틴은 0번이어야 한다.
        assertEquals(0, RoutineOrder.nextIndex(-1))
        assertEquals(4, RoutineOrder.nextIndex(3))
    }

    /** 바뀐 것만 되돌려받으므로, 저장한 뒤의 모습을 만들어 본다. */
    private fun applied(
        routines: List<RoutineEntity>,
        changed: List<RoutineEntity>
    ): List<RoutineEntity> {
        val byId = changed.associateBy { it.id }
        return RoutineOrder.sorted(routines.map { byId[it.id] ?: it })
    }

    private fun routine(
        id: Long,
        syncId: String,
        orderIndex: Int,
        createdAt: Long = id * 100L
    ) = RoutineEntity(
        id = id,
        syncId = syncId,
        updatedAt = 1_000L,
        title = "루틴 $id",
        orderIndex = orderIndex,
        createdAt = createdAt
    )
}
