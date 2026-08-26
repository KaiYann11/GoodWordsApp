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
 * 15->16은 오늘 기분을 담는 표를 만듭니다.
 *
 * AppContainer가 fallbackToDestructiveMigration을 켜 두어서, 마이그레이션이 없거나 틀리면
 * 사용자 DB가 오류 없이 통째로 지워집니다. 이미 써 둔 일기가 살아남는지를 특히 봅니다.
 */
class Migration15To16Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion15DiaryTable(db)
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
        insertDiary(syncId = "diary-a", entryDate = "2026-08-20", mood = "SAD")

        AppDatabase.MIGRATION_15_16.migrate(database)

        database.query("SELECT entryDate, mood FROM diaries WHERE syncId = 'diary-a'").use { cursor ->
            assertTrue("전에 쓴 일기가 사라졌습니다.", cursor.moveToFirst())
            assertEquals("2026-08-20", cursor.getString(0))
            assertEquals("SAD", cursor.getString(1))
        }
    }

    @Test
    fun migration_leavesTheDiaryMoodWhereItWas() {
        // 옮기면 일기를 고칠 때 두 값이 어긋나기 시작합니다. 찍어 둔 것이 없는 날은
        // DayMood가 일기에서 읽으므로 옮길 필요가 없습니다.
        insertDiary(syncId = "diary-a", entryDate = "2026-08-20", mood = "SAD")

        AppDatabase.MIGRATION_15_16.migrate(database)

        database.query("SELECT COUNT(*) FROM mood_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migration_createsAUsableMoodTable() {
        AppDatabase.MIGRATION_15_16.migrate(database)

        insertMoodLog(syncId = "mood-a", entryDate = "2026-08-26", mood = "TIRED")

        database.query("SELECT entryDate, mood FROM mood_logs WHERE syncId = 'mood-a'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-08-26", cursor.getString(0))
            assertEquals("TIRED", cursor.getString(1))
        }
    }

    @Test
    fun migration_rejectsDuplicateSyncIds() {
        AppDatabase.MIGRATION_15_16.migrate(database)
        insertMoodLog(syncId = "mood-same", entryDate = "2026-08-26", mood = "GOOD")

        // 같은 syncId가 두 벌이면 병합이 어느 쪽인지 알 수 없게 됩니다.
        val second = runCatching { insertMoodLog(syncId = "mood-same", entryDate = "2026-08-25", mood = "SAD") }

        assertTrue("syncId 중복이 막히지 않았습니다.", second.isFailure)
    }

    private fun insertMoodLog(syncId: String, entryDate: String, mood: String) {
        database.insert(
            "mood_logs",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", syncId)
                put("updatedAt", 100L)
                put("entryDate", entryDate)
                put("mood", mood)
                put("createdAt", 100L)
            }
        )
    }

    private fun insertDiary(syncId: String, entryDate: String, mood: String) {
        database.insert(
            "diaries",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", syncId)
                put("updatedAt", 100L)
                put("entryDate", entryDate)
                put("title", "")
                put("body", "오늘은 길었다.")
                put("weather", "")
                put("mood", mood)
                put("kind", "FREE")
                put("answers", "[]")
                put("imageUris", "[]")
                put("createdAt", 100L)
            }
        )
    }

    /** 15의 일기 표. 이 시험에 필요한 표만 만듭니다. */
    private fun createVersion15DiaryTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS diaries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, entryDate TEXT NOT NULL, title TEXT NOT NULL, " +
                "body TEXT NOT NULL, weather TEXT NOT NULL, mood TEXT NOT NULL, " +
                "kind TEXT NOT NULL, answers TEXT NOT NULL, imageUris TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_diaries_syncId ON diaries (syncId)")
    }

    private companion object {
        const val DB_NAME = "migration-15-16-test.db"
    }
}
