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
 * 12->13은 일기에 종류(`kind`)와 물음의 답(`answers`) 열을 붙입니다.
 *
 * AppContainer가 fallbackToDestructiveMigration을 켜 두어서, 마이그레이션이 없거나 틀리면
 * 사용자 DB가 오류 없이 통째로 지워집니다. 전에 쓴 일기가 그대로 남는지를 특히 봅니다.
 */
class Migration12To13Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion12DiaryTable(db)
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
    fun migration_keepsDiariesWrittenBefore() {
        insertDiary(syncId = "diary-a", body = "전에 쓴 일기")

        AppDatabase.MIGRATION_12_13.migrate(database)

        database.query("SELECT body, kind, answers FROM diaries WHERE syncId = 'diary-a'").use { cursor ->
            assertTrue("전에 쓴 일기가 사라졌습니다.", cursor.moveToFirst())
            assertEquals("전에 쓴 일기", cursor.getString(0))
            // 물음 없이 쓴 일기여야 화면이 전과 똑같이 보입니다.
            assertEquals(DiaryKind.FREE.name, cursor.getString(1))
            assertEquals("[]", cursor.getString(2))
        }
    }

    @Test
    fun migration_letsGuidedDiariesBeSaved() {
        AppDatabase.MIGRATION_12_13.migrate(database)

        insertDiary(
            syncId = "diary-gratitude",
            body = "",
            kind = DiaryKind.GRATITUDE.name,
            // 가운데가 빈 답. 자리를 지키지 못하면 마지막 답이 첫 물음의 답이 됩니다.
            answers = """["커피","","비 그친 하늘"]"""
        )

        database.query("SELECT kind, answers FROM diaries WHERE syncId = 'diary-gratitude'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(DiaryKind.GRATITUDE.name, cursor.getString(0))
            assertEquals("""["커피","","비 그친 하늘"]""", cursor.getString(1))
        }
    }

    @Test
    fun migration_keepsWeatherAndMood() {
        insertDiary(syncId = "diary-mood", body = "", weather = "RAIN", mood = "TIRED")

        AppDatabase.MIGRATION_12_13.migrate(database)

        database.query("SELECT weather, mood FROM diaries WHERE syncId = 'diary-mood'").use { cursor ->
            assertTrue("날씨·기분만 남긴 일기가 사라졌습니다.", cursor.moveToFirst())
            assertEquals("RAIN", cursor.getString(0))
            assertEquals("TIRED", cursor.getString(1))
        }
    }

    private fun insertDiary(
        syncId: String,
        body: String,
        weather: String = "",
        mood: String = "",
        kind: String? = null,
        answers: String? = null
    ) {
        database.insert(
            "diaries",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", syncId)
                put("updatedAt", 100L)
                put("entryDate", "2026-08-18")
                put("title", "")
                put("body", body)
                put("weather", weather)
                put("mood", mood)
                put("imageUris", "[]")
                put("videoUris", "[]")
                put("audioUris", "[]")
                put("createdAt", 100L)
                kind?.let { put("kind", it) }
                answers?.let { put("answers", it) }
            }
        )
    }

    /** 12의 일기 표. 이 시험에 필요한 표만 만듭니다. */
    private fun createVersion12DiaryTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS diaries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, entryDate TEXT NOT NULL, title TEXT NOT NULL, " +
                "body TEXT NOT NULL, weather TEXT NOT NULL, mood TEXT NOT NULL, " +
                "imageUris TEXT NOT NULL, videoUris TEXT NOT NULL, audioUris TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_diaries_syncId ON diaries (syncId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diaries_entryDate ON diaries (entryDate)")
    }

    private companion object {
        const val DB_NAME = "migration-12-13-test.db"
    }
}
