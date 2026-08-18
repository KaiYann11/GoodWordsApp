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
        DeletionEntity::class,
        DiaryEntity::class,
        TodoEntity::class,
        BookEntity::class
    ],
    version = 13,
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
    abstract fun diaryDao(): DiaryDao
    abstract fun todoDao(): TodoDao
    abstract fun bookDao(): BookDao

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

        /**
         * 자식 레코드가 부모를 기기와 무관하게 가리키도록 syncId 참조를 추가합니다.
         *
         * 8에서는 이벤트가 부모를 contentItemId(기기마다 따로 증가하는 값)로만 가리켰습니다.
         * 그래서 두 기기를 병합하면 A기기 id=1과 B기기 id=1이 한 DB에 함께 들어와,
         * 이벤트가 엉뚱한 항목에 붙고 같은 id끼리 서로를 덮어썼습니다.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE exposure_events ADD COLUMN contentItemSyncId TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL("ALTER TABLE routine_checks ADD COLUMN routineSyncId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE routine_memos ADD COLUMN routineSyncId TEXT NOT NULL DEFAULT ''")

                // 한 기기 안에서는 숫자 id가 정확하므로, 지금 참조를 syncId로 옮겨 둔다.
                database.execSQL(
                    "UPDATE exposure_events SET contentItemSyncId = COALESCE((" +
                        "SELECT syncId FROM content_items WHERE content_items.id = exposure_events.contentItemId" +
                        "), '')"
                )
                database.execSQL(
                    "UPDATE routine_checks SET routineSyncId = COALESCE((" +
                        "SELECT syncId FROM routines WHERE routines.id = routine_checks.routineId" +
                        "), '')"
                )
                database.execSQL(
                    "UPDATE routine_memos SET routineSyncId = COALESCE((" +
                        "SELECT syncId FROM routines WHERE routines.id = routine_memos.routineId" +
                        "), '')"
                )
            }
        }

        /**
         * 일기와 할 일을 추가합니다.
         *
         * 둘 다 기존 표에 손대지 않고 새 표만 만듭니다. 기존 데이터는 그대로 남습니다.
         * 날짜는 기기 시간대가 달라도 같은 날로 읽히도록 ISO 문자열(`yyyy-MM-dd`)로 둡니다.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS diaries (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "syncId TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "entryDate TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "body TEXT NOT NULL, " +
                        "imageUris TEXT NOT NULL, " +
                        "videoUris TEXT NOT NULL, " +
                        "audioUris TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_diaries_syncId ON diaries(syncId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_diaries_entryDate ON diaries(entryDate)")

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS todos (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "syncId TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "dueDate TEXT NOT NULL, " +
                        // 알람과 완료 시각은 없을 수 있어 NULL을 허용한다.
                        "remindAt INTEGER, " +
                        "doneAt INTEGER, " +
                        "createdAt INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_todos_syncId ON todos(syncId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_todos_dueDate ON todos(dueDate)")
            }
        }

        /**
         * 일기에 날씨와 기분을 붙입니다.
         *
         * 이미 쓴 일기는 고른 적이 없으므로 빈 문자열이 됩니다. 화면에서는 아무것도 안 보입니다.
         * 값은 [DiaryWeather]·[DiaryMood]의 이름이고, 모르는 값이 들어와도 읽기만 실패합니다.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE diaries ADD COLUMN weather TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE diaries ADD COLUMN mood TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 독서 관리를 추가합니다.
         *
         * 책 표를 새로 만들고, 글귀가 어느 책 몇 쪽에서 나왔는지 가리킬 열을 붙입니다.
         * 책은 `bookSyncId`로 가리킵니다. 숫자 id는 기기마다 따로 증가해서
         * 다른 기기로 넘어가면 엉뚱한 책을 가리킵니다.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS books (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "syncId TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "author TEXT NOT NULL, " +
                        "totalPages INTEGER NOT NULL, " +
                        "currentPage INTEGER NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        // 시작·완독 시각은 없을 수 있어 NULL을 허용한다.
                        "startedAt INTEGER, " +
                        "finishedAt INTEGER, " +
                        "createdAt INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_books_syncId ON books(syncId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_books_status ON books(status)")

                database.execSQL("ALTER TABLE content_items ADD COLUMN bookSyncId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE content_items ADD COLUMN bookPage INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 감사·반성 일기를 추가합니다.
         *
         * 이미 쓴 일기는 자유 일기(`FREE`)가 되고 답은 비어 있습니다. 화면에서 달라지는 것이 없습니다.
         * 물음마다 열을 따로 두지 않고 답을 한 목록으로 담는 이유는,
         * 물음이 바뀌거나 늘 때마다 스키마를 또 고쳐야 하기 때문입니다.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE diaries ADD COLUMN kind TEXT NOT NULL DEFAULT 'FREE'")
                database.execSQL("ALTER TABLE diaries ADD COLUMN answers TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}
