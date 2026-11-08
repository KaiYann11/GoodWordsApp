package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 목표에 들인 시간이 실제 Room에서 남고 지워지는지 봅니다(사용자가 정함, 2026-11-08). 사용자 DB 대신 메모리 DB만 씁니다.
 *
 * 지울 때 표식이 없으면 다른 기기에서 다음 병합에 되살아납니다. 목표를 지우면 시간도 함께 지워야 주인 없는 기록이 남지 않습니다.
 */
class GoalTimeRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = AppRepository(
            database = database,
            contentItemDao = database.contentItemDao(),
            exposureEventDao = database.exposureEventDao(),
            routineDao = database.routineDao(),
            routineCheckDao = database.routineCheckDao(),
            routineMemoDao = database.routineMemoDao(),
            contentMemoDao = database.contentMemoDao(),
            linkMetadataFetcher = LinkMetadataFetcher(),
            deletionDao = database.deletionDao(),
            goalTimeLogDao = database.goalTimeLogDao()
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun timeIsKeptByTheGoalsSyncId() = runBlocking {
        val goalId = repository.saveGoal(0, ContentType.GOAL, "하프 마라톤 완주", "")
        val goal = database.contentItemDao().getById(goalId)!!

        repository.addGoalTime(goalId, 90, "  5km 템포런 ", loggedAt = 1_000L)

        val stored = database.goalTimeLogDao().getAll().single()
        assertEquals(goal.syncId, stored.goalSyncId)
        assertEquals(90, stored.minutes)
        assertEquals("5km 템포런", stored.note)
        assertEquals(1_000L, stored.loggedAt)
    }

    @Test
    fun onlyGoalsTakeTimeAndOnlyBetweenAMinuteAndADay() = runBlocking {
        val goalId = repository.saveGoal(0, ContentType.GOAL, "하프 마라톤 완주", "")
        val visionId = repository.saveGoal(0, ContentType.VISION, "아침을 지배하는 사람", "")

        // 이상향·롤모델은 들인 시간으로 따라가는 것이 아닙니다.
        assertTrue(runCatching { repository.addGoalTime(visionId, 30, "") }.isFailure)
        assertTrue(runCatching { repository.addGoalTime(goalId, 0, "") }.isFailure)
        assertTrue(runCatching { repository.addGoalTime(goalId, 24 * 60 + 1, "") }.isFailure)
        assertTrue(database.goalTimeLogDao().getAll().isEmpty())
    }

    @Test
    fun deletingALogLeavesATombstone() = runBlocking {
        val goalId = repository.saveGoal(0, ContentType.GOAL, "하프 마라톤 완주", "")
        val id = repository.addGoalTime(goalId, 30, "")
        val syncId = database.goalTimeLogDao().getById(id)!!.syncId

        assertEquals(1, repository.deleteGoalTime(id))

        assertTrue(database.goalTimeLogDao().getAll().isEmpty())
        assertEquals(SyncEntityType.GOAL_TIME_LOG, database.deletionDao().getAll().single { it.syncId == syncId }.entityType)
    }

    @Test
    fun deletingTheGoalTakesItsTimeWithIt() = runBlocking {
        val goalId = repository.saveGoal(0, ContentType.GOAL, "하프 마라톤 완주", "")
        val otherId = repository.saveGoal(0, ContentType.GOAL, "책 50권", "")
        repository.addGoalTime(goalId, 30, "")
        repository.addGoalTime(goalId, 45, "", loggedAt = 2_000L)
        repository.addGoalTime(otherId, 60, "")
        val gone = database.goalTimeLogDao().getAll().filter { it.minutes != 60 }.map { it.syncId }.toSet()

        repository.deleteContent(goalId)

        // 다른 목표의 시간은 그대로입니다.
        assertEquals(listOf(60), database.goalTimeLogDao().getAll().map { it.minutes })
        val tombstones = database.deletionDao().getAll().filter { it.entityType == SyncEntityType.GOAL_TIME_LOG }
        assertEquals(gone, tombstones.map { it.syncId }.toSet())
    }
}
