package com.codex.appgoodwords.data

import java.time.LocalDate

/**
 * 이상향·목표·롤모델 하나를 어떻게 따라가고 있는지.
 *
 * **기한이나 숫자로 재지 않습니다**(사용자가 정함, 2026-10-29). 되새긴 기록과, 이어 둔 루틴을 실제로 밟은 날로
 * 봅니다. 그리고 **저장하지 않고 그때그때 셉니다** — 연속 날수·점수와 같은 이유로, 체크를 고치거나 다른 기기와
 * 합치면 저장해 둔 숫자와 기록이 어긋납니다.
 *
 * 못 한 것은 세지 않습니다. "28일 중 16일 빠짐"이 아니라 "최근 4주에 12일"입니다.
 */
data class GoalTrack(
    val goal: ContentItemEntity,
    /** 되새긴 기록. 최근 것이 먼저입니다. */
    val reflections: List<ContentMemoEntity>,
    /** 이어 둔 루틴 가운데 아직 있는 것. 지운 루틴은 화면에서만 뺍니다. */
    val routines: List<GoalRoutine>,
    /** 이어 둔 루틴을 하나라도 밟은 날이 최근 [GoalTracking.RECENT_DAYS]일 중 며칠인지. 같은 날 여럿 밟아도 하루입니다. */
    val recentActiveDays: Int,
    /** 롤모델이면 보관함에서 저자가 같은 글귀. 다른 종류는 비어 있습니다. */
    val quotes: List<ContentItemEntity>
) {
    val lastReflectedAt: Long?
        get() = reflections.firstOrNull()?.createdAt
}

/** 이어 둔 루틴 한 줄. */
data class GoalRoutine(
    val routine: RoutineEntity,
    /** 최근 [GoalTracking.RECENT_DAYS]일 중 밟은 날. */
    val recentDays: Int,
    /** 오늘까지 이어 온 날수. */
    val currentStreakDays: Int
)

/**
 * 목표 탭과 홈이 같은 셈을 쓰게 한곳에 둡니다. 순수 함수라 기기 없이 검증합니다.
 *
 * 루틴 하나의 날수·연속은 [RoutineStatsCalculator]가 정합니다 — 여기서 체크를 따로 세면 루틴 기록 화면과
 * 목표 카드가 다른 숫자를 말합니다.
 */
object GoalTracking {
    /** "최근"의 폭. 루틴 기록 화면의 최근 4주와 같습니다. */
    const val RECENT_DAYS = 28

    fun build(
        goals: List<ContentItemEntity>,
        memos: List<ContentMemoEntity>,
        routines: List<RoutineEntity>,
        routineStats: Map<Long, RoutineStatsReport>,
        items: List<ContentItemEntity>,
        today: LocalDate
    ): List<GoalTrack> {
        val routinesBySyncId = routines.associateBy { it.syncId }
        val memosByGoal = memos.groupBy { it.contentItemSyncId.ifBlank { "#${it.contentItemId}" } }
        val from = today.minusDays(RECENT_DAYS - 1L)
        return goals.filter { it.type.isGoal }.map { goal ->
            val linked = goal.linkedRoutineSyncIds.mapNotNull(routinesBySyncId::get)
            val rows = linked.map { routine ->
                val report = routineStats[routine.id]
                val recent = report?.doneDates.orEmpty().filter { it in from..today }.toSet()
                GoalRoutine(
                    routine = routine,
                    recentDays = recent.size,
                    currentStreakDays = report?.currentStreakDays ?: 0
                )
            }
            val activeDays = linked
                .flatMap { routine -> routineStats[routine.id]?.doneDates.orEmpty() }
                .filter { it in from..today }
                .toSet()
            GoalTrack(
                goal = goal,
                reflections = (memosByGoal[goal.syncId].orEmpty() + memosByGoal["#${goal.id}"].orEmpty())
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt },
                routines = rows,
                recentActiveDays = activeDays.size,
                quotes = if (goal.type == ContentType.ROLE_MODEL) quotesOf(goal.title, items) else emptyList()
            )
        }
    }

    /**
     * 롤모델의 글귀 — 보관함에서 저자가 그 이름인 글귀.
     *
     * 띄어쓰기와 대소문자는 가리지 않습니다("James Clear"와 "james  clear"). 내가 적은 것(메모·이상향 등)은 저자가
     * 같아도 넣지 않습니다 — 남의 좋은 말인 척 돌아오면 안 됩니다.
     */
    fun quotesOf(name: String, items: List<ContentItemEntity>): List<ContentItemEntity> {
        val key = normalizeName(name)
        if (key.isEmpty()) return emptyList()
        return items
            .filter { it.type == ContentType.QUOTE && normalizeName(it.author) == key }
            .sortedByDescending { it.createdAt }
    }

    /**
     * 홈 "오늘" 칸 맨 위에 둘 것 — 이상향 하나와, 목표·롤모델 하나.
     *
     * **홈에 두기**(대표, `isFavorite`)를 고른 것이 먼저입니다. 고른 것이 없거나 여럿이면 날마다 하나씩 돌아가며
     * 보여 줍니다 — 늘 같은 것만 뜨면 나머지는 목표 탭에서만 만나게 됩니다. 같은 날에는 같은 것입니다.
     */
    fun homeLine(goals: List<ContentItemEntity>, today: LocalDate): HomeGoalLine? {
        val visions = goals.filter { it.type == ContentType.VISION }
        val others = goals.filter { it.type == ContentType.GOAL || it.type == ContentType.ROLE_MODEL }
        val vision = pickForToday(visions, today)
        val other = pickForToday(others, today)
        if (vision == null && other == null) return null
        return HomeGoalLine(vision = vision, other = other)
    }

    private fun pickForToday(candidates: List<ContentItemEntity>, today: LocalDate): ContentItemEntity? {
        if (candidates.isEmpty()) return null
        val pool = candidates.filter { it.isFavorite }.ifEmpty { candidates }.sortedBy { it.createdAt }
        return pool[Math.floorMod(today.toEpochDay(), pool.size.toLong()).toInt()]
    }

    private fun normalizeName(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), "")
}

/** 홈 맨 위의 한 줄. 둘 중 하나는 비어 있을 수 있습니다. */
data class HomeGoalLine(
    val vision: ContentItemEntity?,
    val other: ContentItemEntity?
)
