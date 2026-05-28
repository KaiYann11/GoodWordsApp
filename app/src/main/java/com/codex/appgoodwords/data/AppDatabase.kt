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
        RoutineMemoEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contentItemDao(): ContentItemDao
    abstract fun exposureEventDao(): ExposureEventDao
    abstract fun routineDao(): RoutineDao
    abstract fun routineCheckDao(): RoutineCheckDao
    abstract fun routineMemoDao(): RoutineMemoDao

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
    }
}
