package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 루틴 합치기가 실제 DB에서 이력을 어떻게 모으는지 봅니다(사용자가 정함, 2026-11-02).
 *
 * 체크는 동기화에서 한 번 적으면 바뀌지 않는 기록이라, 남는 루틴 앞으로 **새로 적고** 옛 것에는 삭제 표식을 남겨야
 * 다른 기기와 서버에도 같은 합치기가 퍼집니다. 고쳐 쓰기만 하면 이 기기에서만 합쳐집니다.
 */
class RoutineMergeRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val repository = container.repository
    private val database = container.database

    @Before
    fun setUp() = runBlocking { clear() }

    @After
    fun tearDown() = runBlocking { clear() }

    private suspend fun clear() {
        database.routineDao().clearAll()
        database.routineCheckDao().clearAll()
        database.routineMemoDao().clearAll()
        database.contentItemDao().clearAll()
        database.deletionDao().clearAll()
    }

    @Test
    fun theHistoryOfBothEndsUpOnTheKeptRoutineAndTheOtherIsGone() = runBlocking {
        val keep = routine("아침 물 한 컵", note = "일어나자마자", category = "건강", order = 0)
        val gone = routine("물 마시기", note = "물병을 책상에", order = 1)
        val keptCheck = check(keep, at = 1_000)
        // 같은 날 둘 다 체크한 것도 모두 남깁니다(사용자가 정함) — 그날은 두 번입니다.
        val goneChecks = listOf(check(gone, at = 1_500), check(gone, at = 9_000))
        // 메모만 넣습니다. 저장소의 saveRoutineMemo는 그날 밟은 것으로 체크도 하나 적어, 여기서 세는 체크 수가 달라집니다.
        val memoId = database.routineMemoDao().insert(
            RoutineMemoEntity(routineId = gone.id, routineSyncId = gone.syncId, routineTitle = gone.title, body = "어제는 미지근한 물")
        )

        val result = repository.mergeRoutines(keep.id, gone.id, title = "물 한 컵", note = "일어나자마자\n\n물병을 책상에")

        // 고치던 루틴이 남습니다. 이름과 메모만 바뀌고 차례·갈래는 그대로입니다.
        val kept = database.routineDao().getAll().single()
        assertEquals(keep.syncId, kept.syncId)
        assertEquals("물 한 컵", kept.title)
        assertEquals("일어나자마자\n\n물병을 책상에", kept.note)
        assertEquals("건강", kept.category)
        assertEquals(0, kept.orderIndex)
        assertEquals(2, result.movedChecks)
        assertEquals(1, result.movedMemos)

        // 체크는 모두 남는 루틴의 것이고, 체크한 시각과 그때의 이름은 그대로입니다.
        val checks = database.routineCheckDao().getAll().sortedBy { it.checkedAt }
        assertEquals(listOf(1_000L, 1_500L, 9_000L), checks.map { it.checkedAt })
        assertTrue(checks.all { it.routineId == kept.id && it.routineSyncId == kept.syncId })
        assertEquals(listOf("아침 물 한 컵", "물 마시기", "물 마시기"), checks.map { it.routineTitle })
        assertEquals(keptCheck.syncId, checks.first().syncId)
        // 옮긴 체크는 새 식별자입니다. 옛 식별자는 삭제 표식으로만 남아 다른 기기에서도 지워집니다.
        val goneIds = goneChecks.map { it.syncId }.toSet()
        assertTrue(checks.none { it.syncId in goneIds })

        val deletions = database.deletionDao().getAll().associate { it.syncId to it.entityType }
        assertEquals(SyncEntityType.ROUTINE, deletions[gone.syncId])
        goneIds.forEach { assertEquals(SyncEntityType.ROUTINE_CHECK, deletions[it]) }

        // 메모는 남는 루틴을 가리키게 고칩니다. 지운 것이 아니므로 표식을 남기지 않습니다.
        val memo = database.routineMemoDao().getById(memoId)!!
        assertEquals(kept.id, memo.routineId)
        assertEquals(kept.syncId, memo.routineSyncId)
        assertNull(deletions[memo.syncId])
    }

    @Test
    fun aGoalLinkedToTheGoneRoutineFollowsItToTheKeptOne() = runBlocking {
        val keep = routine("아침 물 한 컵", order = 0)
        val gone = routine("물 마시기", order = 1)
        val other = routine("스트레칭", order = 2)
        val goalId = repository.saveGoal(0, ContentType.GOAL, "건강하게", "")
        repository.setGoalRoutines(goalId, listOf(gone.syncId, other.syncId, keep.syncId))
        val before = database.contentItemDao().getById(goalId)!!

        repository.mergeRoutines(keep.id, gone.id, title = keep.title, note = "")

        val goal = database.contentItemDao().getById(goalId)!!
        // 둘 다 이어 두었으면 한 번만 남깁니다. 고친 것이 다른 기기로 가도록 수정 시각을 올립니다.
        assertEquals(listOf(keep.syncId, other.syncId), goal.linkedRoutineSyncIds)
        assertTrue(goal.updatedAt > before.updatedAt)
    }

    @Test
    fun aRoutineCannotBeMergedIntoItselfAndNothingChangesWhenItFails() = runBlocking {
        val keep = routine("아침 물 한 컵", order = 0)
        check(keep, at = 1_000)

        val failure = runCatching { repository.mergeRoutines(keep.id, keep.id, title = "x", note = "") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)

        val blank = routine("물 마시기", order = 1)
        val noName = runCatching { repository.mergeRoutines(keep.id, blank.id, title = "  ", note = "") }.exceptionOrNull()
        assertTrue(noName is IllegalArgumentException)
        assertEquals(2, database.routineDao().getAll().size)
        assertFalse(database.deletionDao().getAll().any())
    }

    private suspend fun routine(title: String, note: String = "", category: String = "", order: Int): RoutineEntity {
        val entity = RoutineEntity(title = title, note = note, category = category, orderIndex = order)
        val id = database.routineDao().insert(entity)
        return entity.copy(id = id)
    }

    private suspend fun check(routine: RoutineEntity, at: Long): RoutineCheckEntity {
        val entity = RoutineCheckEntity(routineId = routine.id, routineSyncId = routine.syncId, routineTitle = routine.title, checkedAt = at)
        database.routineCheckDao().insert(entity)
        return entity
    }
}
