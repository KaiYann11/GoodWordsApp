package com.codex.appgoodwords.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ContentItemEntity::class,
        ExposureEventEntity::class,
        RoutineEntity::class,
        RoutineCheckEntity::class,
        RoutineMemoEntity::class,
        DeletionEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contentItemDao(): ContentItemDao
    abstract fun exposureEventDao(): ExposureEventDao
    abstract fun routineDao(): RoutineDao
    abstract fun routineCheckDao(): RoutineCheckDao
    abstract fun routineMemoDao(): RoutineMemoDao
    abstract fun deletionDao(): DeletionDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE content_items ADD COLUMN imageUris TEXT NOT NULL DEFAULT '[]'"
                )
                database.execSQL(
                    "ALTER TABLE content_items ADD COLUMN videoUris TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("UPDATE content_items SET showCount = 0, lastShownAt = NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS routines (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "reminderEnabled INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS routine_checks (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "routineId INTEGER NOT NULL, " +
                        "routineTitle TEXT NOT NULL, " +
                        "checkedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(routineId) REFERENCES routines(id) ON UPDATE NO ACTION ON DELETE CASCADE" +
                        ")"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_routine_checks_checkedAt ON routine_checks(checkedAt)")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_checks_routineId_checkedAt " +
                        "ON routine_checks(routineId, checkedAt)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS routine_memos (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "routineId INTEGER NOT NULL, " +
                        "routineTitle TEXT NOT NULL, " +
                        "body TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(routineId) REFERENCES routines(id) ON UPDATE NO ACTION ON DELETE CASCADE" +
                        ")"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_routine_memos_createdAt ON routine_memos(createdAt)")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_memos_routineId_createdAt " +
                        "ON routine_memos(routineId, createdAt)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS routine_checks_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "routineId INTEGER NOT NULL, " +
                        "routineTitle TEXT NOT NULL, " +
                        "checkedAt INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL(
                    "INSERT INTO routine_checks_new (id, routineId, routineTitle, checkedAt) " +
                        "SELECT id, routineId, routineTitle, checkedAt FROM routine_checks"
                )
                database.execSQL("DROP TABLE routine_checks")
                database.execSQL("ALTER TABLE routine_checks_new RENAME TO routine_checks")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_routine_checks_checkedAt ON routine_checks(checkedAt)")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_checks_routineId_checkedAt " +
                        "ON routine_checks(routineId, checkedAt)"
                )

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS routine_memos_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "routineId INTEGER NOT NULL, " +
                        "routineTitle TEXT NOT NULL, " +
                        "body TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL(
                    "INSERT INTO routine_memos_new (id, routineId, routineTitle, body, createdAt) " +
                        "SELECT id, routineId, routineTitle, body, createdAt FROM routine_memos"
                )
                database.execSQL("DROP TABLE routine_memos")
                database.execSQL("ALTER TABLE routine_memos_new RENAME TO routine_memos")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_routine_memos_createdAt ON routine_memos(createdAt)")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_memos_routineId_createdAt " +
                        "ON routine_memos(routineId, createdAt)"
                )
            }
        }

        /**
         * 기기 간 병합을 위한 스키마.
         *
         * 각 레코드에 기기와 무관한 syncId를 부여하고, 수정 가능한 레코드에는 updatedAt을 둡니다.
         * 삭제는 deletions 표식으로 남겨야 상대 기기에서 되살아나지 않습니다.
         * 노출 순환 기준인 lastSurfacedAt도 여기서 항목으로 옮겨, 이력을 지워도 순환이 유지되게 합니다.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQLite에는 uuid()가 없어 randomblob으로 충분히 고유한 값을 만든다.
                val newSyncId = "lower(hex(randomblob(16)))"

                database.execSQL("ALTER TABLE content_items ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE content_items ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE content_items ADD COLUMN lastSurfacedAt INTEGER")
                database.execSQL("UPDATE content_items SET syncId = $newSyncId WHERE syncId = ''")
                database.execSQL("UPDATE content_items SET updatedAt = createdAt WHERE updatedAt = 0")
                // 기존 노출 이력에서 마지막 노출 시각을 끌어와 순환 상태를 잃지 않게 한다.
                database.execSQL(
                    "UPDATE content_items SET lastSurfacedAt = (" +
                        "SELECT MAX(occurredAt) FROM exposure_events " +
                        "WHERE exposure_events.contentItemId = content_items.id " +
                        "AND exposure_events.eventType = 'SURFACED')"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_content_items_syncId ON content_items(syncId)"
                )

                database.execSQL("ALTER TABLE routines ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE routines ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE routines SET syncId = $newSyncId WHERE syncId = ''")
                database.execSQL("UPDATE routines SET updatedAt = createdAt WHERE updatedAt = 0")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_routines_syncId ON routines(syncId)"
                )

                database.execSQL("ALTER TABLE routine_memos ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE routine_memos ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE routine_memos SET syncId = $newSyncId WHERE syncId = ''")
                database.execSQL("UPDATE routine_memos SET updatedAt = createdAt WHERE updatedAt = 0")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_routine_memos_syncId ON routine_memos(syncId)"
                )

                database.execSQL("ALTER TABLE routine_checks ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE routine_checks SET syncId = $newSyncId WHERE syncId = ''")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_routine_checks_syncId ON routine_checks(syncId)"
                )

                database.execSQL("ALTER TABLE exposure_events ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE exposure_events SET syncId = $newSyncId WHERE syncId = ''")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_exposure_events_syncId ON exposure_events(syncId)"
                )

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS deletions (" +
                        "syncId TEXT PRIMARY KEY NOT NULL, " +
                        "entityType TEXT NOT NULL, " +
                        "deletedAt INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_deletions_deletedAt ON deletions(deletedAt)")
            }
        }
    }
}
