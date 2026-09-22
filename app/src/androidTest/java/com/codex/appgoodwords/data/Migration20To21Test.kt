package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 20→21은 날짜별 독서 기록을 담을 표를 새로 만듭니다.
 *
 * 마이그레이션이 없거나 틀리면 `fallbackToDestructiveMigration()`이 사용자 DB를 조용히 전부
 * 지웁니다. 그래서 두 가지를 봅니다 — 쓰던 기록이 그대로 남는지, 새 표에 실제로 쓸 수 있는지.
 */
class Migration20To21Test {
    @Test fun theUpgradeKeepsBooksAndAddsAPlaceForReadingDays() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-20-21-test.db"
        context.deleteDatabase(name)

        val book = BookEntity(id = 1, syncId = "book-1", title = "아주 작은 습관의 힘", currentPage = 42)
        val quote = ContentItemEntity(id = 1, syncId = "quote-1", type = ContentType.QUOTE, title = "글귀", body = "본문")
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            before.bookDao().insert(book)
            before.contentItemDao().insert(quote)
            before.openHelper.writableDatabase.version = 20
        } finally {
            before.close()
        }

        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_20_21)
            .build()
        try {
            assertEquals(21, after.openHelper.writableDatabase.version)
            // 쓰던 기록이 그대로 있어야 합니다. 비었으면 통째로 지워진 것입니다.
            assertEquals(book, after.bookDao().getById(book.id))
            assertEquals(quote, after.contentItemDao().getById(quote.id))

            val log = BookReadingLogEntity(
                bookId = book.id,
                bookSyncId = book.syncId,
                bookTitle = book.title,
                readDate = "2026-09-23",
                startPage = 20,
                endPage = 42
            )
            val id = after.bookReadingLogDao().insert(log)
            val stored = after.bookReadingLogDao().getByBookAndDate(book.id, "2026-09-23")
            assertEquals(log.copy(id = id), stored)
            assertEquals(22, stored!!.pagesRead)
            assertTrue(after.bookReadingLogDao().getAll().size == 1)
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
