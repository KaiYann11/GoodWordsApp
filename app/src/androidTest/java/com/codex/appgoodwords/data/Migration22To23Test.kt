package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 22→23은 보관함 기록에 "이어 둔 루틴" 칸을 더합니다. 이상향·목표·롤모델 세 종류 자체는 표를 건드리지 않습니다.
 *
 * 마이그레이션이 없거나 틀리면 `fallbackToDestructiveMigration()`이 사용자 DB를 조용히 전부 지웁니다.
 * 그래서 쓰던 기록이 그대로 남는지와, 새 칸에 실제로 쓸 수 있는지를 봅니다.
 */
class Migration22To23Test {
    @Test fun theUpgradeKeepsRecordsAndAddsRoomForLinkedRoutines() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-22-23-test.db"
        context.deleteDatabase(name)

        val quote = ContentItemEntity(
            id = 1, syncId = "quote-1", type = ContentType.QUOTE, title = "글귀", body = "본문",
            tags = listOf("습관"), bookSyncId = "book-1", bookPage = 12
        )
        val memo = ContentMemoEntity(id = 1, syncId = "memo-1", contentItemId = 1, contentItemSyncId = "quote-1", contentTitle = "글귀", body = "덧메모")
        val routine = RoutineEntity(id = 1, syncId = "routine-1", title = "아침 산책")
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            before.contentItemDao().insert(quote)
            before.contentMemoDao().insert(memo)
            before.routineDao().insert(routine)
            // Room이 만든 표에는 이미 새 칸이 있습니다. 22의 모양으로 되돌려 놓아야
            // ALTER가 실제로 걸리는 길을 시험하게 됩니다(그러지 않으면 "칸이 이미 있다"로 끝납니다).
            before.openHelper.writableDatabase.execSQL("ALTER TABLE content_items DROP COLUMN linkedRoutineSyncIds")
            before.openHelper.writableDatabase.version = 22
        } finally {
            before.close()
        }

        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_22_23)
            .build()
        try {
            assertEquals(23, after.openHelper.writableDatabase.version)
            // 쓰던 기록이 그대로 있어야 합니다. 비었으면 통째로 지워진 것입니다.
            assertEquals(quote, after.contentItemDao().getById(quote.id))
            assertEquals(1, after.contentMemoDao().getAll().size)
            assertEquals(1, after.routineDao().getAll().size)
            // 옛 기록은 이어 둔 루틴이 빈 목록입니다. NULL이 아니어야 읽는 쪽이 갈라지지 않습니다.
            assertEquals(emptyList<String>(), after.contentItemDao().getById(quote.id)?.linkedRoutineSyncIds)

            // 새 종류는 새 칸과 함께 그대로 담깁니다.
            val goal = ContentItemEntity(
                id = 2, syncId = "goal-1", type = ContentType.GOAL, title = "올해 마라톤 완주", body = "",
                linkedRoutineSyncIds = listOf("routine-1")
            )
            after.contentItemDao().insert(goal)
            assertEquals(goal, after.contentItemDao().getById(goal.id))
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
