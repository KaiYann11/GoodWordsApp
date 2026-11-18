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

class AppRepositorySummaryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(
            database = database,
            contentItemDao = database.contentItemDao(),
            exposureEventDao = database.exposureEventDao(),
            routineDao = database.routineDao(),
            routineCheckDao = database.routineCheckDao(),
            routineMemoDao = database.routineMemoDao(),
            contentMemoDao = database.contentMemoDao(),
            linkMetadataFetcher = LinkMetadataFetcher()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveRoutineMemoAlsoMarksTheRoutineDone() = runBlocking {
        val routineId = database.routineDao().insert(RoutineEntity(title = "물 마시기"))

        val memoId = repository.saveRoutineMemo(routineId, "한 컵 마심")

        val routine = database.routineDao().getById(routineId)!!
        val memo = database.routineMemoDao().getAll().single()
        val check = database.routineCheckDao().getAll().single()
        assertEquals(memoId, memo.id)
        assertEquals(routineId, memo.routineId)
        assertEquals(routine.syncId, memo.routineSyncId)
        assertEquals(routineId, check.routineId)
        assertEquals(routine.syncId, check.routineSyncId)
        assertEquals(routine.title, check.routineTitle)
        assertEquals(1, repository.getTodayRoutineCheckCount(routineId))
    }

    @Test
    fun blankRoutineMemoDoesNotCreateAMemoOrCheck() = runBlocking {
        val routineId = database.routineDao().insert(RoutineEntity(title = "물 마시기"))

        val result = runCatching { repository.saveRoutineMemo(routineId, "   ") }

        assertTrue(result.isFailure)
        assertTrue(database.routineMemoDao().getAll().isEmpty())
        assertTrue(database.routineCheckDao().getAll().isEmpty())
    }

    @Test
    fun failedRoutineCheckRollsBackTheMemo() = runBlocking {
        val routineId = database.routineDao().insert(RoutineEntity(title = "물 마시기"))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_routine_check " +
                "BEFORE INSERT ON routine_checks " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END"
        )

        val result = runCatching { repository.saveRoutineMemo(routineId, "한 컵 마심") }

        assertTrue(result.isFailure)
        assertTrue(database.routineMemoDao().getAll().isEmpty())
        assertTrue(database.routineCheckDao().getAll().isEmpty())
    }
}
