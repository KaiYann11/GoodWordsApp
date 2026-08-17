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
 * 10->11은 일기에 날씨와 기분 열을 붙입니다.
 *
 * AppContainer가 fallbackToDestructiveMigration을 켜 두어서, 마이그레이션이 실패하면
 * 사용자 DB가 조용히 통째로 지워집니다. 이미 쓴 일기가 살아남는지를 특히 봅니다.
 */
class Migration10To11Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion9Schema(db)
                        // 10은 9에 일기와 할 일 표가 붙은 모양입니다. 같은 DDL을 두 벌 적지 않습니다.
                        AppDatabase.MIGRATION_9_10.migrate(db)
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
    fun migration_keepsDiariesWrittenBeforeTheFeature() {
        insertDiary(syncId = "diary-a", body = "날씨 기능 전에 쓴 일기")

        AppDatabase.MIGRATION_10_11.migrate(database)

        database.query("SELECT body, weather, mood FROM diaries WHERE syncId = 'diary-a'").use { cursor ->
            assertTrue("전에 쓴 일기가 사라졌습니다.", cursor.moveToFirst())
            assertEquals("날씨 기능 전에 쓴 일기", cursor.getString(0))
            // 고른 적이 없으니 비어 있어야 합니다. 화면에서는 아무것도 보이지 않습니다.
            assertEquals("", cursor.getString(1))
            assertEquals("", cursor.getString(2))
        }
    }

    @Test
    fun migration_keepsOtherTables() {
        insertItem(id = 1, syncId = "item-a", title = "지키고 싶은 글귀")

        AppDatabase.MIGRATION_10_11.migrate(database)

        database.query("SELECT title FROM content_items").use { cursor ->
            assertTrue("기존 글귀가 사라졌습니다.", cursor.moveToFirst())
            assertEquals("지키고 싶은 글귀", cursor.getString(0))
        }
    }

    @Test
    fun migration_acceptsWeatherAndMood() {
        AppDatabase.MIGRATION_10_11.migrate(database)

        database.insert(
            "diaries",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", "diary-b")
                put("updatedAt", 200L)
                put("entryDate", "2026-08-17")
                put("title", "")
                put("body", "비 오는 날")
                put("weather", DiaryWeather.RAIN.name)
                put("mood", DiaryMood.GOOD.name)
                put("imageUris", "[]")
                put("videoUris", "[]")
                put("audioUris", "[]")
                put("createdAt", 200L)
            }
        )

        database.query("SELECT weather, mood FROM diaries WHERE syncId = 'diary-b'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("RAIN", cursor.getString(0))
            assertEquals("GOOD", cursor.getString(1))
        }
    }

    @Test
    fun migration_fillsDefaultWhenColumnsAreOmitted() {
        AppDatabase.MIGRATION_10_11.migrate(database)

        // 날씨와 기분을 안 고르는 날이 가장 흔합니다. 기본값이 없으면 여기서 터집니다.
        insertDiary(syncId = "diary-c", body = "아무것도 안 고른 날")

        database.query("SELECT weather, mood FROM diaries WHERE syncId = 'diary-c'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
            assertEquals("", cursor.getString(1))
        }
    }

    private fun insertDiary(syncId: String, body: String) {
        database.insert(
            "diaries",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", syncId)
                put("updatedAt", 100L)
                put("entryDate", "2026-08-16")
                put("title", "")
                put("body", body)
                put("imageUris", "[]")
                put("videoUris", "[]")
                put("audioUris", "[]")
                put("createdAt", 100L)
            }
        )
    }

    /** 9는 8에 자식의 부모 syncId 열이 붙은 모양입니다. */
    private fun createVersion9Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS content_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, " +
                "author TEXT NOT NULL, sourceUrl TEXT NOT NULL, thumbnailUrl TEXT NOT NULL, " +
                "category TEXT NOT NULL, tags TEXT NOT NULL, imageUris TEXT NOT NULL, " +
                "videoUris TEXT NOT NULL, createdAt INTEGER NOT NULL, lastShownAt INTEGER, " +
                "lastSurfacedAt INTEGER, showCount INTEGER NOT NULL, isFavorite INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS exposure_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "contentItemId INTEGER NOT NULL, contentItemSyncId TEXT NOT NULL, " +
                "contentTitle TEXT NOT NULL, contentType TEXT NOT NULL, " +
                "eventType TEXT NOT NULL, trigger TEXT NOT NULL, occurredAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routines (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, " +
                "category TEXT NOT NULL, reminderEnabled INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routine_checks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "routineId INTEGER NOT NULL, routineSyncId TEXT NOT NULL, " +
                "routineTitle TEXT NOT NULL, checkedAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routine_memos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, routineId INTEGER NOT NULL, routineSyncId TEXT NOT NULL, " +
                "routineTitle TEXT NOT NULL, body TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS deletions (" +
                "syncId TEXT PRIMARY KEY NOT NULL, entityType TEXT NOT NULL, deletedAt INTEGER NOT NULL)"
        )
    }

    private fun insertItem(id: Long, syncId: String, title: String) {
        database.execSQL(
            "INSERT INTO content_items (" +
                "id, syncId, updatedAt, type, title, body, author, sourceUrl, thumbnailUrl, category, " +
                "tags, imageUris, videoUris, createdAt, lastShownAt, lastSurfacedAt, showCount, isFavorite" +
                ") VALUES (?, ?, 0, 'QUOTE', ?, '', '', '', '', '', '[]', '[]', '[]', 0, NULL, NULL, 0, 0)",
            arrayOf(id, syncId, title)
        )
    }

    private companion object {
        const val DB_NAME = "migration-10-11-test.db"
    }
}
