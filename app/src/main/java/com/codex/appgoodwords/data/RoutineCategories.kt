package com.codex.appgoodwords.data

/**
 * 루틴을 갈래로 묶어 볼 때의 규칙.
 *
 * 루틴이 서른 개를 넘으면 한 줄로는 무엇이 어디쯤 있는지 찾기 어렵습니다. 갈래([RoutineEntity.category])는
 * 예전부터 있었지만 카드에 글자로만 붙어 있어 묶어 볼 길이 없었습니다.
 *
 * **갈래는 루틴 탭 안에서만 거릅니다.** 홈·위젯·다음 루틴 알림은 하루 전체의 차례를 따릅니다.
 * 갈래로 고른 것만 보다가 다른 갈래의 루틴이 "다음 차례"에서 빠지면 하루가 쪼개집니다.
 */
object RoutineCategories {
    /** 갈래를 붙이지 않은 루틴. 화면에는 [NONE_LABEL]로 적습니다. */
    const val NONE = ""

    const val NONE_LABEL = "갈래 없음"

    /**
     * 앞뒤 공백을 뗀 갈래.
     *
     * 저장할 때도 떼지만(`AppRepository.saveRoutine`), 웹이나 예전 기기에서 온 값까지 같게 봅니다.
     * `공부`와 `공부 `가 두 갈래로 갈라지면 같은 것을 두 번 눌러 봐야 합니다.
     */
    fun of(routine: RoutineEntity): String = routine.category.trim()

    /** [category]가 null이면 전체입니다. */
    fun matches(routine: RoutineEntity, category: String?): Boolean =
        category == null || of(routine) == category

    /**
     * 알약으로 놓을 갈래들.
     *
     * 차례는 루틴 차례([RoutineOrder.sorted])에서 처음 나온 순서입니다. 하루를 밟는 순서와 갈래 순서가
     * 따로 놀면 "공부"가 어디쯤인지 매번 다시 찾아야 합니다. 갈래 없는 루틴이 있으면 [NONE]을 맨 뒤에 둡니다.
     *
     * 갈래를 단 루틴이 하나도 없으면 빈 목록입니다 — 그때는 알약 줄을 두지 않습니다.
     * "갈래 없음" 하나만 있는 줄은 고를 것이 없습니다.
     */
    fun ordered(routines: List<RoutineEntity>): List<String> {
        val named = RoutineOrder.sorted(routines).map(::of).filter { it.isNotEmpty() }.distinct()
        if (named.isEmpty()) return emptyList()
        return if (routines.any { of(it).isEmpty() }) named + NONE else named
    }

    /** 이미 쓰고 있는 갈래 이름. 고치기 창에서 눌러 채우게 합니다. */
    fun names(routines: List<RoutineEntity>): List<String> = ordered(routines).filter { it != NONE }

    /** 알약에 적을 이름. */
    fun label(category: String): String = category.ifEmpty { NONE_LABEL }
}
