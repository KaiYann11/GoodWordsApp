package com.codex.appgoodwords.data

import java.time.LocalDate

/** 책의 상태·날짜별 기록을 바꾸는 규칙. DB 없이도 완료와 되돌리기를 검증할 수 있도록 한곳에 둡니다. */
object BookReading {
    fun toggleFinished(book: BookEntity, now: Long): BookEntity {
        val finished = !book.isFinished
        return book.copy(
            updatedAt = now,
            status = if (finished) BookStatus.FINISHED.name else BookStatus.READING.name,
            currentPage = if (finished && book.totalPages > 0) book.totalPages else book.currentPage,
            finishedAt = if (finished) now else null
        )
    }

    /**
     * 다 읽음은 진도가 그대로여도 읽은 날입니다(사용자가 정함, 2026-10-30).
     * 다시 읽는 중은 상태만 돌립니다. 되돌렸다는 이유로 이미 남긴 독서를 지우거나 새로 세지 않습니다.
     * 서버 saveBook의 완료 전환과 같은 기준입니다.
     */
    fun becameFinished(before: BookEntity, after: BookEntity): Boolean = !before.isFinished && after.isFinished

    /** 처음 읽기 시작한 쪽과 기록의 식별자를 지켜, 같은 날 두 번 남겨도 읽은 쪽이 겹쳐 세지지 않게 합니다. */
    fun record(
        book: BookEntity,
        previousPage: Int,
        today: LocalDate,
        existing: BookReadingLogEntity?,
        now: Long
    ): BookReadingLogEntity = existing?.copy(
        updatedAt = now,
        bookSyncId = book.syncId,
        bookTitle = book.title,
        endPage = book.currentPage
    ) ?: BookReadingLogEntity(
        bookId = book.id,
        bookSyncId = book.syncId,
        bookTitle = book.title,
        readDate = today.toString(),
        startPage = previousPage.coerceAtLeast(0),
        endPage = book.currentPage,
        updatedAt = now,
        createdAt = now
    )
}
