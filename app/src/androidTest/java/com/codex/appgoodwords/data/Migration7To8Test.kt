package com.codex.appgoodwords.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AppContainer가 fallbackToDestructiveMigration을 켜 두어서, 마이그레이션이 실패하면
 * 사용자 DB가 조용히 지워집니다. 실제 v7 스키마를 만들어 놓고 7->8을 돌려 확인합니다.
 */
class Migration7To8Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersion7Schema(db)
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
    fun migration_keepsExistingRowsAndFillsSyncFields() {
        database.execSQL(
            "INSERT INTO content_items (id, type, title, body, author, sourceUrl, thumbnailUrl, category, " +
                "tags, imageUris, videoUris, createdAt, lastShownAt, showCount, isFavorite) " +
                "VALUES (1, 'QUOTE', '기존 항목', '본문', '', '', '', '동기부여', '[]', '[]', '[]', 1000, NULL, 3, 1)"
        )
        database.execSQL(
            "INSERT INTO routines (id, title, note, category, reminderEnabled, createdAt) " +
                "VALUES (5, '기존 루틴', '메모', '건강', 1, 2000)"
        )

        AppDatabase.MIGRATION_7_8.migrate(database)

        database.query("SELECT syncId, updatedAt, title, showCount, isFavorite FROM content_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("syncId가 채워져야 합니다.", cursor.getString(0).isNotBlank())
            assertEquals("updatedAt은 createdAt으로 채운다.", 1000L, cursor.getLong(1))
            assertEquals("기존 항목", cursor.getString(2))
            assertEquals(3, cursor.getInt(3))
            assertEquals(1, cursor.getInt(4))
        }

        database.query("SELECT syncId, updatedAt, title FROM routines").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getString(0).isNotBlank())
            assertEquals(2000L, cursor.getLong(1))
            assertEquals("기존 루틴", cursor.getString(2))
        }
    }

    @Test
    fun migration_givesEveryRowADistinctSyncId() {
        repeat(5) { index ->
            database.execSQL(
                "INSERT INTO content_items (id, type, title, body, author, sourceUrl, thumbnailUrl, category, " +
                    "tags, imageUris, videoUris, createdAt, lastShownAt, showCount, isFavorite) " +
                    "VALUES (${index + 1}, 'QUOTE', '항목$index', '본문', '', '', '', '', '[]', '[]', '[]', 1000, NULL, 0, 0)"
            )
        }

        AppDatabase.MIGRATION_7_8.migrate(database)

        val syncIds = mutableSetOf<String>()
        database.query("SELECT syncId FROM content_items").use { cursor ->
            while (cursor.moveToNext()) {
                syncIds += cursor.getString(0)
            }
        }

        assertEquals("각 행이 서로 다른 syncId를 가져야 합니다.", 5, syncIds.size)
    }

    @Test
    fun migration_backfillsLastSurfacedAtFromHistory() {
        database.execSQL(
            "INSERT INTO content_items (id, type, title, body, author, sourceUrl, thumbnailUrl, category, " +
                "tags, imageUris, videoUris, createdAt, lastShownAt, showCount, isFavorite) " +
                "VALUES (1, 'QUOTE', '노출된 항목', '본문', '', '', '', '', '[]', '[]', '[]', 1000, NULL, 0, 0)"
        )
        database.execSQL(
            "INSERT INTO content_items (id, type, title, body, author, sourceUrl, thumbnailUrl, category, " +
                "tags, imageUris, videoUris, createdAt, lastShownAt, showCount, isFavorite) " +
                "VALUES (2, 'QUOTE', '노출 안 된 항목', '본문', '', '', '', '', '[]', '[]', '[]', 1000, NULL, 0, 0)"
        )
        database.execSQL(
            "INSERT INTO exposure_events (id, contentItemId, contentTitle, contentType, eventType, trigger, occurredAt) " +
                "VALUES (1, 1, '노출된 항목', 'QUOTE', 'SURFACED', 'MANUAL_REFRESH', 5000)"
        )
        database.execSQL(
            "INSERT INTO exposure_events (id, contentItemId, contentTitle, contentType, eventType, trigger, occurredAt) " +
                "VALUES (2, 1, '노출된 항목', 'QUOTE', 'SURFACED', 'MANUAL_REFRESH', 9000)"
        )
        // CONFIRMED는 노출이 아니므로 기준에 들어가면 안 된다.
        database.execSQL(
            "INSERT INTO exposure_events (id, contentItemId, contentTitle, contentType, eventType, trigger, occurredAt) " +
                "VALUES (3, 2, '노출 안 된 항목', 'QUOTE', 'CONFIRMED', 'MANUAL_REFRESH', 12000)"
        )

        AppDatabase.MIGRATION_7_8.migrate(database)

        database.query("SELECT id, lastSurfacedAt FROM content_items ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("마지막 SURFACED 시각이어야 합니다.", 9000L, cursor.getLong(1))

            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(0))
            assertTrue("노출된 적 없으면 비어 있어야 합니다.", cursor.isNull(1))
        }
    }

    @Test
    fun migration_createsDeletionsTable() {
        AppDatabase.MIGRATION_7_8.migrate(database)

        database.execSQL(
            "INSERT INTO deletions (syncId, entityType, deletedAt) VALUES ('abc', 'CONTENT_ITEM', 1234)"
        )
        database.query("SELECT syncId, entityType, deletedAt FROM deletions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("abc", cursor.getString(0))
            assertEquals("CONTENT_ITEM", cursor.getString(1))
            assertEquals(1234L, cursor.getLong(2))
        }
    }

    @Test
    fun migration_onEmptyDatabaseSucceeds() {
        AppDatabase.MIGRATION_7_8.migrate(database)

        database.query("SELECT COUNT(*) FROM content_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migration_syncIdIndexRejectsDuplicates() {
        database.execSQL(
            "INSERT INTO content_items (id, type, title, body, author, sourceUrl, thumbnailUrl, category, " +
                "tags, imageUris, videoUris, createdAt, lastShownAt, showCount, isFavorite) " +
                "VALUES (1, 'QUOTE', 'a', 'b', '', '', '', '', '[]', '[]', '[]', 1000, NULL, 0, 0)"
        )

        AppDatabase.MIGRATION_7_8.migrate(database)

        val existing = database.query("SELECT syncId FROM content_items").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }
        val failed = runCatching {
            database.execSQL(
                "INSERT INTO content_items (id, syncId, updatedAt, type, title, body, author, sourceUrl, " +
                    "thumbnailUrl, category, tags, imageUris, videoUris, createdAt, lastShownAt, " +
                    "lastSurfacedAt, showCount, isFavorite) " +
                    "VALUES (2, '$existing', 1000, 'QUOTE', 'c', 'd', '', '', '', '', '[]', '[]', '[]', 1000, NULL, NULL, 0, 0)"
            )
        }.isFailure

        assertTrue("같은 syncId는 들어가면 안 됩니다.", failed)
        assertNotEquals("", existing)
    }

    @Test
    fun migration_leavesLastShownAtUntouched() {
        database.execSQL(
            "INSERT INTO content_items (id, type, title, body, author, sourceUrl, thumbnailUrl, category, " +
                "tags, imageUris, videoUris, createdAt, lastShownAt, showCount, isFavorite) " +
                "VALUES (1, 'QUOTE', 'a', 'b', '', '', '', '', '[]', '[]', '[]', 1000, 7777, 2, 0)"
        )

        AppDatabase.MIGRATION_7_8.migrate(database)

        database.query("SELECT lastShownAt, lastSurfacedAt FROM content_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7777L, cursor.getLong(0))
            assertNull("노출 이력이 없으면 비어 있어야 합니다.", cursor.getLongOrNull(1))
        }
    }

    private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    private fun createVersion7Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS content_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "type TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, author TEXT NOT NULL, " +
                "sourceUrl TEXT NOT NULL, thumbnailUrl TEXT NOT NULL, category TEXT NOT NULL, " +
                "tags TEXT NOT NULL, imageUris TEXT NOT NULL, videoUris TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, lastShownAt INTEGER, showCount INTEGER NOT NULL, " +
                "isFavorite INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS exposure_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contentItemId INTEGER NOT NULL, " +
                "contentTitle TEXT NOT NULL, contentType TEXT NOT NULL, eventType TEXT NOT NULL, " +
                "trigger TEXT NOT NULL, occurredAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routines (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, " +
                "category TEXT NOT NULL, reminderEnabled INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routine_checks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, routineId INTEGER NOT NULL, " +
                "routineTitle TEXT NOT NULL, checkedAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS routine_memos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, routineId INTEGER NOT NULL, " +
                "routineTitle TEXT NOT NULL, body TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
    }

    private companion object {
        const val DB_NAME = "migration-7-8-test.db"
    }
}
