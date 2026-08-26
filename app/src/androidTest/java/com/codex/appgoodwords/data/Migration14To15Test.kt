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
 * 14->15는 AI가 써 준 돌아보기를 담는 표를 만듭니다.
 *
 * AppContainer가 fallbackToDestructiveMigration을 켜 두어서, 마이그레이션이 없거나 틀리면
 * 사용자 DB가 오류 없이 통째로 지워집니다. 이미 모아 둔 루틴이 살아남는지를 특히 봅니다.
 */
class Migration14To15Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion14RoutineTable(db)
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
    fun migration_keepsWhatWasAlreadyThere() {
        insertRoutine(syncId = "routine-a", title = "아침 물 한 컵")

        AppDatabase.MIGRATION_14_15.migrate(database)

        database.query("SELECT title, orderIndex FROM routines WHERE syncId = 'routine-a'").use { cursor ->
            assertTrue("전에 만든 루틴이 사라졌습니다.", cursor.moveToFirst())
            assertEquals("아침 물 한 컵", cursor.getString(0))
            assertEquals(3, cursor.getInt(1))
        }
    }

    @Test
    fun migration_createsAUsableReportTable() {
        AppDatabase.MIGRATION_14_15.migrate(database)

        insertReport(syncId = "report-a", period = "WEEKLY")

        database.query(
            "SELECT period, periodStart, strengths, suggestedRoutines FROM growth_reports WHERE syncId = 'report-a'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("WEEKLY", cursor.getString(0))
            assertEquals("2026-08-17", cursor.getString(1))
            // 목록 열은 JSON 문자열로 들어갑니다.
            assertEquals("""["꾸준했습니다."]""", cursor.getString(2))
            assertEquals("[]", cursor.getString(3))
        }
    }

    @Test
    fun migration_rejectsDuplicateSyncIds() {
        AppDatabase.MIGRATION_14_15.migrate(database)
        insertReport(syncId = "report-same", period = "WEEKLY")

        // 같은 syncId가 두 벌이면 병합이 어느 쪽인지 알 수 없게 됩니다.
        val second = runCatching { insertReport(syncId = "report-same", period = "DAILY") }

        assertTrue("syncId 중복이 막히지 않았습니다.", second.isFailure)
    }

    @Test
    fun migration_leavesTheReportTableEmpty() {
        AppDatabase.MIGRATION_14_15.migrate(database)

        database.query("SELECT COUNT(*) FROM growth_reports").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private fun insertReport(syncId: String, period: String) {
        database.insert(
            "growth_reports",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", syncId)
                put("updatedAt", 100L)
                put("period", period)
                put("periodStart", "2026-08-17")
                put("periodEnd", "2026-08-23")
                put("model", "gpt-4o-mini")
                put("strengths", """["꾸준했습니다."]""")
                put("improvements", "[]")
                put("suggestedQuote", "")
                put("suggestedQuoteAuthor", "")
                put("suggestedRoutines", "[]")
                put("guide", "")
                put("createdAt", 100L)
            }
        )
    }

    private fun insertRoutine(syncId: String, title: String) {
        database.insert(
            "routines",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", syncId)
                put("updatedAt", 100L)
                put("title", title)
                put("note", "")
                put("category", "")
                put("orderIndex", 3)
                put("reminderEnabled", 1)
                put("createdAt", 100L)
            }
        )
    }

    /** 14의 루틴 표. 이 시험에 필요한 표만 만듭니다. */
    private fun createVersion14RoutineTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routines (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, " +
                "category TEXT NOT NULL, orderIndex INTEGER NOT NULL DEFAULT 0, " +
                "reminderEnabled INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_routines_syncId ON routines (syncId)")
    }

    private companion object {
        const val DB_NAME = "migration-14-15-test.db"
    }
}
