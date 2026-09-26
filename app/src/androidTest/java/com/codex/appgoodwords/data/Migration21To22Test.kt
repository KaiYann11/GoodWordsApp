package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 21→22는 책에 독후감 칸을 더합니다. 원칙 종류는 표를 건드리지 않습니다.
 *
 * 마이그레이션이 없거나 틀리면 `fallbackToDestructiveMigration()`이 사용자 DB를 조용히
 * 전부 지웁니다. 그래서 쓰던 기록이 그대로 남는지와, 새 칸에 실제로 쓸 수 있는지를 봅니다.
 */
class Migration21To22Test {
    @Test fun theUpgradeKeepsRecordsAndAddsRoomForTheReview() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-21-22-test.db"
        context.deleteDatabase(name)

        val book = BookEntity(id = 1, syncId = "book-1", title = "아주 작은 습관의 힘", currentPage = 42, note = "2장까지")
        val quote = ContentItemEntity(id = 1, syncId = "quote-1", type = ContentType.QUOTE, title = "글귀", body = "본문")
        val log = BookReadingLogEntity(
            id = 1, syncId = "reading-1", bookId = 1, bookSyncId = "book-1", bookTitle = book.title,
            readDate = "2026-09-25", startPage = 20, endPage = 42
        )
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            before.bookDao().insert(book)
            before.contentItemDao().insert(quote)
            before.bookReadingLogDao().insert(log)
            // Room이 만든 표에는 이미 새 칸이 있습니다. 21의 모양으로 되돌려 놓아야
            // ALTER가 실제로 걸리는 길을 시험하게 됩니다(그러지 않으면 "칸이 이미 있다"로 끝납니다).
            before.openHelper.writableDatabase.execSQL("ALTER TABLE books DROP COLUMN review")
            before.openHelper.writableDatabase.version = 21
        } finally {
            before.close()
        }

        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_21_22)
            .build()
        try {
            assertEquals(22, after.openHelper.writableDatabase.version)
            // 쓰던 기록이 그대로 있어야 합니다. 비었으면 통째로 지워진 것입니다.
            assertEquals(book, after.bookDao().getById(book.id))
            assertEquals(quote, after.contentItemDao().getById(quote.id))
            assertEquals(1, after.bookReadingLogDao().getAll().size)
            // 옛 책은 독후감이 빈 글입니다. NULL이 아니어야 읽는 쪽이 갈라지지 않습니다.
            assertEquals("", after.bookDao().getById(book.id)?.review)

            val written = book.copy(review = "읽고 나서 남긴 글\n\n둘째 문단입니다.")
            after.bookDao().insert(written)
            assertEquals(written.review, after.bookDao().getById(book.id)?.review)

            // 원칙은 새 칸 없이 종류만 늘어난 것이라, 그대로 담기면 됩니다.
            val principle = ContentItemEntity(
                id = 2, syncId = "principle-1", type = ContentType.PRINCIPLE,
                title = "화가 난 채로 답하지 않는다", body = ""
            )
            after.contentItemDao().insert(principle)
            assertEquals(principle, after.contentItemDao().getById(principle.id))
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
