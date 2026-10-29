package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 이상향·목표·롤모델을 어떻게 따라가고 있는지.
 *
 * 기한이나 숫자로 재지 않고, 되새긴 기록과 이어 둔 루틴을 밟은 날로 봅니다. 여기서 보는 것은
 * **같은 날을 두 번 세지 않는지**(두 루틴을 같은 날 밟아도 그 목표를 위해 움직인 날은 하루),
 * **지운 루틴을 지어내지 않는지**, **내가 쓴 것을 롤모델의 글귀로 섞지 않는지**입니다.
 */
class GoalTrackingTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 29)

    private val walk = RoutineEntity(id = 1, syncId = "routine-walk", title = "아침 산책")
    private val read = RoutineEntity(id = 2, syncId = "routine-read", title = "책 10쪽")

    private fun check(routine: RoutineEntity, date: LocalDate, hour: Int = 9) = RoutineCheckEntity(
        syncId = "check-${routine.id}-$date-$hour",
        routineId = routine.id,
        routineSyncId = routine.syncId,
        routineTitle = routine.title,
        checkedAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    )

    private fun goal(
        id: Long,
        type: ContentType = ContentType.GOAL,
        title: String = "목표 $id",
        routines: List<String> = emptyList(),
        favorite: Boolean = false,
        createdAt: Long = id
    ) = ContentItemEntity(
        id = id, syncId = "goal-$id", type = type, title = title, body = "",
        linkedRoutineSyncIds = routines, isFavorite = favorite, createdAt = createdAt
    )

    private fun build(
        goals: List<ContentItemEntity>,
        checks: List<RoutineCheckEntity> = emptyList(),
        memos: List<ContentMemoEntity> = emptyList(),
        items: List<ContentItemEntity> = emptyList(),
        routines: List<RoutineEntity> = listOf(walk, read)
    ) = GoalTracking.build(
        goals = goals,
        memos = memos,
        routines = routines,
        routineStats = RoutineStatsCalculator.buildAll(routines, checks, today, zone),
        items = items,
        today = today
    )

    @Test
    fun aDayCountsOnceEvenWhenTwoLinkedRoutinesWereDone() {
        val track = build(
            goals = listOf(goal(10, routines = listOf("routine-walk", "routine-read"))),
            checks = listOf(
                check(walk, today), check(read, today),
                check(walk, today.minusDays(1)), check(walk, today.minusDays(1), hour = 20)
            )
        ).single()

        assertEquals(2, track.recentActiveDays)
        assertEquals(listOf(2, 1), track.routines.map { it.recentDays })
    }

    @Test
    fun onlyTheLastFourWeeksAreRecent() {
        val track = build(
            goals = listOf(goal(10, routines = listOf("routine-walk"))),
            checks = listOf(
                check(walk, today.minusDays(GoalTracking.RECENT_DAYS - 1L)),
                check(walk, today.minusDays(GoalTracking.RECENT_DAYS.toLong()))
            )
        ).single()

        assertEquals(1, track.recentActiveDays)
    }

    @Test
    fun aDeletedRoutineIsLeftOutNotInvented() {
        // 이어 둔 루틴을 지웠으면 화면에서만 뺍니다. 없는 루틴의 줄을 지어내지 않습니다.
        val track = build(
            goals = listOf(goal(10, routines = listOf("routine-walk", "routine-gone"))),
            checks = listOf(check(walk, today))
        ).single()

        assertEquals(listOf("아침 산책"), track.routines.map { it.routine.title })
        assertEquals(1, track.recentActiveDays)
    }

    @Test
    fun reflectionsComeNewestFirstAndOnlyTheirOwn() {
        val goal = goal(10)
        val other = goal(11)
        fun memo(id: Long, item: ContentItemEntity, at: Long) = ContentMemoEntity(
            id = id, syncId = "memo-$id", contentItemId = item.id, contentItemSyncId = item.syncId,
            contentTitle = item.title, body = "되새김 $id", createdAt = at
        )
        val tracks = build(
            goals = listOf(goal, other),
            memos = listOf(memo(1, goal, 100), memo(2, goal, 300), memo(3, other, 200))
        )

        assertEquals(listOf("되새김 2", "되새김 1"), tracks[0].reflections.map { it.body })
        assertEquals(300L, tracks[0].lastReflectedAt)
        assertEquals(listOf("되새김 3"), tracks[1].reflections.map { it.body })
    }

    @Test
    fun aRoleModelGathersQuotesByTheSameAuthorButNotMyOwnNotes() {
        val model = goal(10, type = ContentType.ROLE_MODEL, title = "James Clear")
        val quote = ContentItemEntity(id = 1, type = ContentType.QUOTE, title = "습관", body = "작은 습관", author = "james  clear")
        val otherAuthor = ContentItemEntity(id = 2, type = ContentType.QUOTE, title = "집중", body = "집중", author = "Cal Newport")
        // 내가 적은 메모는 저자를 같게 적었어도 롤모델의 글귀가 아닙니다.
        val myNote = ContentItemEntity(id = 3, type = ContentType.MEMO, title = "메모", body = "내 생각", author = "James Clear")

        val track = build(goals = listOf(model), items = listOf(quote, otherAuthor, myNote)).single()

        assertEquals(listOf(1L), track.quotes.map { it.id })
        // 롤모델이 아니면 글귀를 모으지 않습니다.
        assertTrue(build(goals = listOf(goal(11, title = "James Clear")), items = listOf(quote)).single().quotes.isEmpty())
    }

    @Test
    fun theHomeLinePrefersWhatWasPinnedAndOtherwiseRotatesDaily() {
        val visions = listOf(
            goal(1, type = ContentType.VISION, title = "아침을 지배하는 사람"),
            goal(2, type = ContentType.VISION, title = "끝까지 듣는 사람")
        )
        // 고른 것이 없으면 날마다 돌아가며, 같은 날에는 같은 것입니다.
        val first = GoalTracking.homeLine(visions, today)!!.vision
        val again = GoalTracking.homeLine(visions, today)!!.vision
        val next = GoalTracking.homeLine(visions, today.plusDays(1))!!.vision
        assertEquals(first, again)
        assertTrue(first != next)

        // 홈에 두기를 고른 것이 있으면 그것입니다.
        val pinned = visions.map { if (it.id == 2L) it.copy(isFavorite = true) else it }
        assertEquals(2L, GoalTracking.homeLine(pinned, today)!!.vision!!.id)
        assertEquals(2L, GoalTracking.homeLine(pinned, today.plusDays(1))!!.vision!!.id)
        assertNull(GoalTracking.homeLine(pinned, today)!!.other)
    }

    @Test
    fun nothingWrittenMeansNoHomeLine() {
        assertNull(GoalTracking.homeLine(emptyList(), today))
        // 목표 탭의 종류가 아닌 것은 홈 줄에 오지 않습니다.
        assertNull(GoalTracking.homeLine(listOf(goal(1, type = ContentType.PRINCIPLE)), today))
    }
}
