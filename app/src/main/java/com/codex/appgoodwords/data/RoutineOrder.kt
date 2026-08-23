package com.codex.appgoodwords.data

/**
 * 루틴을 하루에 밟는 차례로 늘어놓고, 원하는 자리로 옮깁니다.
 *
 * 같은 규칙이 앱과 서버(`moveRoutine`) 두 곳에 있습니다. 한쪽만 다르게 옮기면
 * 두 기기가 병합할 때마다 서로의 차례를 고쳐 끝나지 않습니다.
 *
 * 자리를 옮긴 뒤에는 0부터 빈틈없이 다시 번호를 매깁니다. 번호가 겹치거나 비어 있어도
 * (예전 기기가 만든 루틴이 전부 0이거나, 병합으로 같은 번호가 둘이 되는 경우)
 * 한 번 옮기면 저절로 반듯해집니다.
 */
object RoutineOrder {
    /**
     * 화면에 놓을 차례.
     *
     * 번호가 같으면 만든 지 오래된 쪽을 앞에 둡니다. 두 기기에서 각각 만든 루틴이 만나
     * 번호가 겹쳐도 어느 기기에서나 같은 줄로 보여야 하기 때문입니다.
     */
    fun sorted(routines: List<RoutineEntity>): List<RoutineEntity> {
        return routines.sortedWith(
            compareBy<RoutineEntity> { it.orderIndex }
                .thenBy { it.createdAt }
                .thenBy { it.syncId }
        )
    }

    /**
     * [routineId]를 [targetIndex] 자리로 옮긴 결과. 자리는 0부터 셉니다.
     *
     * 번호가 실제로 달라진 루틴만 돌려줍니다. 안 바뀐 것까지 저장하면 `updatedAt`이 올라가
     * 서버가 새 리비전을 붙이고, 증분 동기화가 매번 루틴 전부를 실어 나릅니다.
     * 줄 밖을 가리키면 맨 위/맨 아래로 당겨 붙입니다. 그래야 "맨 아래로"를 큰 수 하나로 부를 수
     * 있고, 루틴을 지운 뒤 옛 자리를 눌러도 엉뚱한 곳으로 가지 않습니다.
     * 옮길 곳이 지금 자리와 같으면 빈 목록입니다.
     */
    fun movedTo(
        routines: List<RoutineEntity>,
        routineId: Long,
        targetIndex: Int
    ): List<RoutineEntity> {
        val ordered = sorted(routines)
        val from = ordered.indexOfFirst { it.id == routineId }
        if (from < 0) return emptyList()
        val to = targetIndex.coerceIn(0, ordered.lastIndex)
        if (to == from) return emptyList()

        val moved = ordered.toMutableList()
        moved.add(to, moved.removeAt(from))
        return moved.mapIndexedNotNull { index, routine ->
            routine.takeIf { it.orderIndex != index }?.copy(orderIndex = index)
        }
    }

    /**
     * [routineId]를 한 칸 위([up]=true) 또는 아래로 옮긴 결과.
     *
     * 이미 맨 위/맨 아래라 옮길 곳이 없으면 빈 목록입니다.
     */
    fun moved(routines: List<RoutineEntity>, routineId: Long, up: Boolean): List<RoutineEntity> {
        val from = sorted(routines).indexOfFirst { it.id == routineId }
        if (from < 0) return emptyList()
        return movedTo(routines, routineId, if (up) from - 1 else from + 1)
    }

    /** 맨 아래를 가리키는 자리. 몇 개가 있든 [movedTo]가 줄 끝으로 당겨 붙입니다. */
    const val LAST_INDEX: Int = Int.MAX_VALUE

    /** 새로 만드는 루틴이 받을 번호. 맨 뒤에 붙습니다. */
    fun nextIndex(maxOrderIndex: Int): Int = maxOrderIndex + 1
}
