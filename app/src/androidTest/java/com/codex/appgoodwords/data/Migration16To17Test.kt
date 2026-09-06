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
 * 16->17은 글귀에 메모를 달 표를 만들고, 루틴에 "어느 글귀에서 뽑았는지"를 붙입니다.
 *
 * AppContainer가 fallbackToDestructiveMigration을 켜 두어서, 마이그레이션이 없거나 틀리면
 * 사용자 DB가 오류 없이 통째로 지워집니다. 이미 담아 둔 글귀와 루틴이 살아남는지를 특히 봅니다.
 */
class Migration16To17Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion16Tables(db)
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
        insertItem(syncId = "quote-a", title = "오늘의 기준", body = "행동이 먼저다.")
        insertRoutine(syncId = "routine-a", title = "물 한 컵")

        AppDatabase.MIGRATION_16_17.migrate(database)

        database.query("SELECT title, body FROM content_items WHERE syncId = 'quote-a'").use { cursor ->
            assertTrue("담아 둔 글귀가 사라졌습니다.", cursor.moveToFirst())
            assertEquals("오늘의 기준", cursor.getString(0))
            assertEquals("행동이 먼저다.", cursor.getString(1))
        }
        database.query("SELECT title FROM routines WHERE syncId = 'routine-a'").use { cursor ->
            assertTrue("만들어 둔 루틴이 사라졌습니다.", cursor.moveToFirst())
            assertEquals("물 한 컵", cursor.getString(0))
        }
    }

    @Test
    fun migration_leavesOldRoutinesWithoutASource() {
        // 글귀에서 뽑기 전에 만든 루틴은 출처가 없습니다. 화면에서는 직접 만든 루틴으로 보입니다.
        insertRoutine(syncId = "routine-a", title = "물 한 컵")

        AppDatabase.MIGRATION_16_17.migrate(database)

        database.query("SELECT sourceContentSyncId FROM routines WHERE syncId = 'routine-a'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }
    }

    @Test
    fun migration_createsAUsableContentMemoTable() {
        AppDatabase.MIGRATION_16_17.migrate(database)

        insertContentMemo(syncId = "memo-a", contentItemSyncId = "quote-a", body = "세 번째 읽으니 다르다.")

        database.query(
            "SELECT contentItemSyncId, body FROM content_memos WHERE syncId = 'memo-a'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("quote-a", cursor.getString(0))
            assertEquals("세 번째 읽으니 다르다.", cursor.getString(1))
        }
    }

    @Test
    fun migration_rejectsDuplicateSyncIds() {
        AppDatabase.MIGRATION_16_17.migrate(database)
        insertContentMemo(syncId = "memo-same", contentItemSyncId = "quote-a", body = "메모")

        // 같은 syncId가 두 벌이면 병합이 어느 쪽인지 알 수 없게 됩니다.
        val second = runCatching {
            insertContentMemo(syncId = "memo-same", contentItemSyncId = "quote-b", body = "다른 메모")
        }

        assertTrue("syncId 중복이 막히지 않았습니다.", second.isFailure)
    }

    private fun insertContentMemo(syncId: String, contentItemSyncId: String, body: String) {
        database.insert(
            "content_memos",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", syncId)
                put("updatedAt", 100L)
                put("contentItemId", 1L)
                put("contentItemSyncId", contentItemSyncId)
                put("contentTitle", "오늘의 기준")
                put("body", body)
                put("createdAt", 100L)
            }
        )
    }

    private fun insertItem(syncId: String, title: String, body: String) {
        database.insert(
            "content_items",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", syncId)
                put("updatedAt", 100L)
                put("type", "QUOTE")
                put("title", title)
                put("body", body)
                put("author", "")
                put("sourceUrl", "")
                put("thumbnailUrl", "")
                put("category", "")
                put("tags", "[]")
                put("imageUris", "[]")
                put("videoUris", "[]")
                put("bookSyncId", "")
                put("bookPage", 0)
                put("createdAt", 100L)
                put("showCount", 0)
                put("isFavorite", 0)
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
                put("orderIndex", 0)
                put("reminderEnabled", 1)
                put("createdAt", 100L)
            }
        )
    }

    /** 16의 글귀·루틴 표. 이 시험에 필요한 표만 만듭니다. */
    private fun createVersion16Tables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS content_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, lastSurfacedAt INTEGER, type TEXT NOT NULL, " +
                "title TEXT NOT NULL, body TEXT NOT NULL, author TEXT NOT NULL, " +
                "sourceUrl TEXT NOT NULL, thumbnailUrl TEXT NOT NULL, category TEXT NOT NULL, " +
                "tags TEXT NOT NULL, imageUris TEXT NOT NULL, videoUris TEXT NOT NULL, " +
                "bookSyncId TEXT NOT NULL, bookPage INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                "lastShownAt INTEGER, showCount INTEGER NOT NULL, isFavorite INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_content_items_syncId ON content_items (syncId)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routines (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, " +
                "category TEXT NOT NULL, orderIndex INTEGER NOT NULL, " +
                "reminderEnabled INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_routines_syncId ON routines (syncId)")
    }

    private companion object {
        const val DB_NAME = "migration-16-17-test.db"
    }
}
