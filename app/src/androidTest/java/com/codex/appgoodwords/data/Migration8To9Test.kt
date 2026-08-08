package com.codex.appgoodwords.data

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
 * 사용자 DB가 조용히 지워집니다. 실제 v8 스키마를 만들어 놓고 8->9를 돌려 확인합니다.
 */
class Migration8To9Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersion8Schema(db)
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
    fun migration_pointsEventsAtTheirItemBySyncId() {
        insertItem(id = 1, syncId = "item-a", title = "첫 글귀")
        insertItem(id = 2, syncId = "item-b", title = "둘째 글귀")
        insertEvent(id = 1, contentItemId = 2)

        AppDatabase.MIGRATION_8_9.migrate(database)

        database.query("SELECT contentItemId, contentItemSyncId FROM exposure_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("숫자 참조는 그대로 둔다.", 2L, cursor.getLong(0))
            assertEquals("item-b", cursor.getString(1))
        }
    }

    @Test
    fun migration_leavesOrphanEventWithoutAReference() {
        // 항목을 지워도 이력은 남는다. 가리킬 항목이 없으면 빈 값이어야 한다.
        insertEvent(id = 1, contentItemId = 99)

        AppDatabase.MIGRATION_8_9.migrate(database)

        database.query("SELECT contentItemSyncId FROM exposure_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }
    }

    @Test
    fun migration_pointsChecksAndMemosAtTheirRoutine() {
        insertRoutine(id = 3, syncId = "routine-a")
        database.execSQL(
            "INSERT INTO routine_checks (id, syncId, routineId, routineTitle, checkedAt) " +
                "VALUES (1, 'check-1', 3, '루틴', 5000)"
        )
        database.execSQL(
            "INSERT INTO routine_memos (id, syncId, updatedAt, routineId, routineTitle, body, createdAt) " +
                "VALUES (1, 'memo-1', 5000, 3, '루틴', '메모', 5000)"
        )

        AppDatabase.MIGRATION_8_9.migrate(database)

        database.query("SELECT routineSyncId FROM routine_checks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("routine-a", cursor.getString(0))
        }
        database.query("SELECT routineSyncId FROM routine_memos").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("routine-a", cursor.getString(0))
        }
    }

    @Test
    fun migration_keepsExistingRows() {
        insertItem(id = 1, syncId = "item-a", title = "지켜야 할 글귀")
        insertEvent(id = 1, contentItemId = 1)

        AppDatabase.MIGRATION_8_9.migrate(database)

        database.query("SELECT title FROM content_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("지켜야 할 글귀", cursor.getString(0))
        }
        database.query("SELECT COUNT(*) FROM exposure_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun migration_onEmptyDatabaseSucceeds() {
        AppDatabase.MIGRATION_8_9.migrate(database)

        database.query("SELECT COUNT(*) FROM exposure_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private fun insertItem(id: Int, syncId: String, title: String) = database.execSQL(
        "INSERT INTO content_items (id, syncId, updatedAt, type, title, body, author, sourceUrl, " +
            "thumbnailUrl, category, tags, imageUris, videoUris, createdAt, lastShownAt, " +
            "lastSurfacedAt, showCount, isFavorite) " +
            "VALUES ($id, '$syncId', 1000, 'QUOTE', '$title', '본문', '', '', '', '', '[]', '[]', '[]', " +
            "1000, NULL, NULL, 0, 0)"
    )

    private fun insertRoutine(id: Int, syncId: String) = database.execSQL(
        "INSERT INTO routines (id, syncId, updatedAt, title, note, category, reminderEnabled, createdAt) " +
            "VALUES ($id, '$syncId', 1000, '루틴', '', '', 1, 1000)"
    )

    private fun insertEvent(id: Int, contentItemId: Int) = database.execSQL(
        "INSERT INTO exposure_events (id, syncId, contentItemId, contentTitle, contentType, eventType, " +
            "trigger, occurredAt) " +
            "VALUES ($id, 'event-$id', $contentItemId, '제목', 'QUOTE', 'SURFACED', 'MANUAL_REFRESH', 5000)"
    )

    private fun createVersion8Schema(db: SupportSQLiteDatabase) {
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
                "contentItemId INTEGER NOT NULL, contentTitle TEXT NOT NULL, contentType TEXT NOT NULL, " +
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
                "routineId INTEGER NOT NULL, routineTitle TEXT NOT NULL, checkedAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routine_memos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, syncId TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, routineId INTEGER NOT NULL, routineTitle TEXT NOT NULL, " +
                "body TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS deletions (" +
                "syncId TEXT PRIMARY KEY NOT NULL, entityType TEXT NOT NULL, deletedAt INTEGER NOT NULL)"
        )
    }

    private companion object {
        const val DB_NAME = "migration-8-9-test.db"
    }
}
