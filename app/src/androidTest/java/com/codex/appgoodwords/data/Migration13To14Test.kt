package com.codex.appgoodwords.data

import android.content.ContentValues
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 13->14는 루틴에 하루의 차례(`orderIndex`) 열을 붙입니다.
 *
 * AppContainer가 fallbackToDestructiveMigration을 켜 두어서, 마이그레이션이 없거나 틀리면
 * 사용자 DB가 오류 없이 통째로 지워집니다. 전에 만든 루틴이 그대로 남는지,
 * 그리고 지금까지 보이던 줄이 그대로 번호가 되는지를 봅니다.
 */
class Migration13To14Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion13RoutineTables(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        database = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(DB_NAME)
    }

    @Test
    fun migration_keepsRoutinesMadeBefore() {
        insertRoutine(id = 1, syncId = "routine-a", title = "아침 물 한 컵", createdAt = 100L)

        AppDatabase.MIGRATION_13_14.migrate(database)

        database.query("SELECT title, note, reminderEnabled FROM routines WHERE syncId = 'routine-a'").use { cursor ->
            assertTrue("전에 만든 루틴이 사라졌습니다.", cursor.moveToFirst())
            assertEquals("아침 물 한 컵", cursor.getString(0))
            assertEquals("메모", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
        }
    }

    @Test
    fun migration_keepsTheOrderTheUserWasAlreadySeeing() {
        // 13까지는 새로 만든 루틴이 맨 위였습니다. 그 줄을 그대로 번호로 굳혀야
        // 사용자가 앱을 열었을 때 어제와 같은 순서를 봅니다.
        insertRoutine(id = 1, syncId = "oldest", title = "가장 먼저 만든 루틴", createdAt = 100L)
        insertRoutine(id = 2, syncId = "middle", title = "그다음", createdAt = 200L)
        insertRoutine(id = 3, syncId = "newest", title = "가장 나중", createdAt = 300L)

        AppDatabase.MIGRATION_13_14.migrate(database)

        assertEquals(listOf("newest", "middle", "oldest"), syncIdsInOrder())
        assertEquals(listOf(0, 1, 2), orderIndexesInOrder())
    }

    @Test
    fun migration_givesEveryRoutineItsOwnStepEvenWhenMadeInTheSameMillisecond() {
        // 한 번에 여러 개를 복원하면 createdAt이 같을 수 있습니다.
        // 번호가 겹치면 두 기기가 서로 다른 줄을 보여 줍니다.
        insertRoutine(id = 1, syncId = "same-a", title = "가", createdAt = 500L)
        insertRoutine(id = 2, syncId = "same-b", title = "나", createdAt = 500L)
        insertRoutine(id = 3, syncId = "same-c", title = "다", createdAt = 500L)

        AppDatabase.MIGRATION_13_14.migrate(database)

        assertEquals(listOf(0, 1, 2), orderIndexesInOrder())
    }

    @Test
    fun migration_letsANewRoutineTakeAStep() {
        AppDatabase.MIGRATION_13_14.migrate(database)

        insertRoutine(id = 1, syncId = "routine-new", title = "새 루틴", createdAt = 100L, orderIndex = 7)

        database.query("SELECT orderIndex FROM routines WHERE syncId = 'routine-new'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
        }
    }

    private fun syncIdsInOrder(): List<String> {
        val result = mutableListOf<String>()
        database.query("SELECT syncId FROM routines ORDER BY orderIndex ASC").use { cursor ->
            while (cursor.moveToNext()) result += cursor.getString(0)
        }
        return result
    }

    private fun orderIndexesInOrder(): List<Int> {
        val result = mutableListOf<Int>()
        database.query("SELECT orderIndex FROM routines ORDER BY orderIndex ASC").use { cursor ->
            while (cursor.moveToNext()) result += cursor.getInt(0)
        }
        return result
    }

    private fun insertRoutine(
        id: Long,
        syncId: String,
        title: String,
        createdAt: Long,
        orderIndex: Int? = null
    ) {
        database.insert(
            "routines",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("id", id)
                put("syncId", syncId)
                put("updatedAt", createdAt)
                put("title", title)
                put("note", "메모")
                put("category", "")
                put("reminderEnabled", 1)
                put("createdAt", createdAt)
                orderIndex?.let { put("orderIndex", it) }
            }
        )
    }

    /** 13의 루틴 표. 이 시험에 필요한 표만 만듭니다. */
    private fun createVersion13RoutineTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routines (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, " +
                "category TEXT NOT NULL, reminderEnabled INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_routines_syncId ON routines (syncId)")
    }

    private companion object {
        const val DB_NAME = "migration-13-14-test.db"
    }
}
