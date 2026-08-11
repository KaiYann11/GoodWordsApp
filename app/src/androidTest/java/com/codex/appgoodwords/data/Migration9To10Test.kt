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
 * AppContainer가 fallbackToDestructiveMigration을 켜 두어서, 마이그레이션이 실패하면
 * 사용자 DB가 조용히 지워집니다. 9->10은 표를 새로 만들기만 하지만,
 * 그래도 기존 데이터가 살아남는지와 새 표가 실제로 쓸 수 있는 모양인지 확인합니다.
 */
class Migration9To10Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersion9Schema(db)
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
    fun migration_keepsExistingContent() {
        insertItem(id = 1, syncId = "item-a", title = "지키고 싶은 글귀")

        AppDatabase.MIGRATION_9_10.migrate(database)

        database.query("SELECT title FROM content_items").use { cursor ->
            assertTrue("기존 글귀가 사라졌습니다.", cursor.moveToFirst())
            assertEquals("지키고 싶은 글귀", cursor.getString(0))
        }
    }

    @Test
    fun migration_createsUsableDiaryTable() {
        AppDatabase.MIGRATION_9_10.migrate(database)

        database.insert(
            "diaries",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", "diary-a")
                put("updatedAt", 100L)
                put("entryDate", "2026-08-12")
                put("title", "오늘")
                put("body", "적어 둔 내용")
                put("imageUris", "[]")
                put("videoUris", "[]")
                put("audioUris", "[]")
                put("createdAt", 100L)
            }
        )

        database.query("SELECT entryDate, body FROM diaries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-08-12", cursor.getString(0))
            assertEquals("적어 둔 내용", cursor.getString(1))
        }
    }

    @Test
    fun migration_createsUsableTodoTableWithNullableTimes() {
        AppDatabase.MIGRATION_9_10.migrate(database)

        // 알람도 완료도 없는 할 일이 가장 흔하다. NOT NULL이면 여기서 터진다.
        database.insert(
            "todos",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", "todo-a")
                put("updatedAt", 100L)
                put("title", "우체국 가기")
                put("note", "")
                put("dueDate", "2026-08-12")
                put("createdAt", 100L)
            }
        )

        database.query("SELECT title, remindAt, doneAt FROM todos").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("우체국 가기", cursor.getString(0))
            assertTrue("알람이 없어야 합니다.", cursor.isNull(1))
            assertTrue("완료 표시가 없어야 합니다.", cursor.isNull(2))
        }
    }

    @Test
    fun migration_rejectsDuplicateSyncIds() {
        AppDatabase.MIGRATION_9_10.migrate(database)
        insertTodo(syncId = "todo-same")

        // 같은 syncId가 두 벌 들어가면 병합이 어느 쪽인지 알 수 없게 된다.
        val second = runCatching { insertTodo(syncId = "todo-same") }

        assertTrue("syncId 중복이 막히지 않았습니다.", second.isFailure)
    }

    @Test
    fun migration_leavesNewTablesEmpty() {
        AppDatabase.MIGRATION_9_10.migrate(database)

        database.query("SELECT COUNT(*) FROM diaries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        database.query("SELECT COUNT(*) FROM todos").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private fun insertTodo(syncId: String) {
        database.insert(
            "todos",
            android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("syncId", syncId)
                put("updatedAt", 100L)
                put("title", "할 일")
                put("note", "")
                put("dueDate", "2026-08-12")
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
        const val DB_NAME = "migration-9-10-test.db"
    }
}
