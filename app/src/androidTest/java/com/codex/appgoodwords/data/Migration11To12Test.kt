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
 * 11->12는 책 표를 만들고, 글귀에 출처(책·쪽) 열을 붙입니다.
 *
 * AppContainer가 fallbackToDestructiveMigration을 켜 두어서, 마이그레이션이 실패하면
 * 사용자 DB가 조용히 통째로 지워집니다. 이미 모아 둔 글귀가 살아남는지를 특히 봅니다.
 */
class Migration11To12Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion9Schema(db)
                        // 같은 DDL을 여러 벌 적지 않는다. 앞선 마이그레이션이 11까지 데려다준다.
                        AppDatabase.MIGRATION_9_10.migrate(db)
                        AppDatabase.MIGRATION_10_11.migrate(db)
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
    fun migration_keepsQuotesCollectedBefore() {
        insertItem(id = 1, syncId = "item-a", title = "지키고 싶은 글귀")

        AppDatabase.MIGRATION_11_12.migrate(database)

        database.query("SELECT title, bookSyncId, bookPage FROM content_items WHERE syncId = 'item-a'").use { cursor ->
            assertTrue("전에 모아 둔 글귀가 사라졌습니다.", cursor.moveToFirst())
            assertEquals("지키고 싶은 글귀", cursor.getString(0))
            // 책에서 뽑은 적이 없으니 출처가 비어 있어야 합니다.
            assertEquals("", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
    }

    @Test
    fun migration_keepsDiaries() {
        database.insert(
            "diaries",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", "diary-a")
                put("updatedAt", 100L)
                put("entryDate", "2026-08-17")
                put("title", "")
                put("body", "남아야 하는 일기")
                put("weather", "")
                put("mood", "")
                put("imageUris", "[]")
                put("videoUris", "[]")
                put("audioUris", "[]")
                put("createdAt", 100L)
            }
        )

        AppDatabase.MIGRATION_11_12.migrate(database)

        database.query("SELECT body FROM diaries WHERE syncId = 'diary-a'").use { cursor ->
            assertTrue("일기가 사라졌습니다.", cursor.moveToFirst())
            assertEquals("남아야 하는 일기", cursor.getString(0))
        }
    }

    @Test
    fun migration_createsUsableBookTable() {
        AppDatabase.MIGRATION_11_12.migrate(database)

        insertBook(syncId = "book-a", title = "아주 작은 습관의 힘")

        database.query("SELECT title, currentPage, status, finishedAt FROM books WHERE syncId = 'book-a'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("아주 작은 습관의 힘", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("READING", cursor.getString(2))
            // 아직 다 읽지 않았으니 완독 시각이 없어야 합니다.
            assertTrue(cursor.isNull(3))
        }
    }

    @Test
    fun migration_rejectsDuplicateSyncIds() {
        AppDatabase.MIGRATION_11_12.migrate(database)
        insertBook(syncId = "book-same", title = "책")

        // 같은 syncId가 두 벌이면 병합이 어느 쪽인지 알 수 없게 된다.
        val second = runCatching { insertBook(syncId = "book-same", title = "책") }

        assertTrue("syncId 중복이 막히지 않았습니다.", second.isFailure)
    }

    @Test
    fun migration_leavesTheBookTableEmpty() {
        AppDatabase.MIGRATION_11_12.migrate(database)

        database.query("SELECT COUNT(*) FROM books").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private fun insertBook(syncId: String, title: String) {
        database.insert(
            "books",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", syncId)
                put("updatedAt", 100L)
                put("title", title)
                put("author", "")
                put("totalPages", 0)
                put("currentPage", 0)
                put("status", "READING")
                put("note", "")
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
        const val DB_NAME = "migration-11-12-test.db"
    }
}
