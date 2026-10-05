package com.codex.appgoodwords.data

import java.time.LocalDate

/**
 * 하루 치 진행.
 *
 * 쪽수와 비율을 함께 둡니다. "어제 120쪽, 오늘 177쪽"만으로는 많이 읽은 날인지 알 수 없고,
 * 비율만으로는 어디까지 왔는지 손에 잡히지 않습니다.
 */
data class ReadingDayProgress(
    val date: LocalDate,
    /** 그날 읽기 시작한 쪽. */
    val startPage: Int,
    /** 그날 마지막으로 닿은 쪽. */
    val endPage: Int,
    /** 그날 읽은 쪽수. 되돌려 적은 날은 0입니다([BookReadingLogEntity.pagesRead]). */
    val pagesRead: Int,
    /**
     * 그날 끝에서의 진행률(0~1). **전체 쪽수를 모르면 null입니다.**
     *
     * 0을 돌려주면 화면이 "0%"라고 단정합니다. 모르는 것과 안 읽은 것은 다릅니다
     * ([BookEntity.progress]와 같은 규칙).
     */
    val ratio: Float?,
    /** 그날 오른 만큼의 비율. 전체 쪽수를 모르면 null입니다. */
    val ratioGain: Float?
)

/**
 * 책 하나의 날짜별 진행.
 *
 * 책에는 마지막 쪽만 남아서([BookReadingLogEntity]), 어제와 오늘을 견주려면 날짜별 기록을
 * 다시 세는 수밖에 없습니다. 연속 날수·점수와 같은 방식입니다 — 저장하지 않고 그때그때 셉니다.
 */
data class BookProgressReport(
    /** 최근 날이 맨 위입니다. 오늘 어디까지 왔는지가 가장 궁금한 것입니다. */
    val days: List<ReadingDayProgress>,
    /** 읽은 날수. 같은 날 여러 번 적어도 하루입니다. */
    val readingDays: Int,
    /** 기록에 남은 만큼 더한 쪽수. */
    val totalPagesRead: Int
) {
    val isEmpty: Boolean
        get() = days.isEmpty()

    /**
     * 읽은 날 하루에 평균 몇 쪽.
     *
     * **안 읽은 날은 나누는 수에 넣지 않습니다.** 넣으면 바빠서 며칠 쉰 사람의 평균이 바닥으로
     * 떨어져, 읽은 날 꾸준히 읽었다는 사실이 숫자에서 사라집니다.
     */
    val averagePagesPerDay: Int
        get() = if (readingDays == 0) 0 else Math.round(totalPagesRead.toFloat() / readingDays)
}

/**
 * 날짜별 독서 진행을 셈합니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object BookProgressCalculator {

    fun build(book: BookEntity, logs: List<BookReadingLogEntity>): BookProgressReport {
        // 책을 가리키는 값은 syncId입니다. 숫자 id는 기기마다 달라 다른 책의 기록이 섞입니다.
        // 다만 아직 한 번도 동기화하지 않은 기기에는 숫자 id만 맞는 기록이 있을 수 있어 함께 봅니다.
        val mine = logs.filter { log ->
            if (log.bookSyncId.isNotBlank() && book.syncId.isNotBlank()) log.bookSyncId == book.syncId
            else log.bookId == book.id
        }

        // 하루에 책 하나당 한 줄이지만, 두 기기의 기록이 합쳐지기 전에는 같은 날이 둘일 수 있습니다.
        // 나중에 적은 쪽을 그날의 것으로 봅니다(SyncDeduplicator가 고르는 기준과 같습니다).
        val byDate = mutableMapOf<LocalDate, BookReadingLogEntity>()
        for (log in mine) {
            val date = parseDate(log.readDate) ?: continue
            val current = byDate[date]
            if (current == null || log.updatedAt > current.updatedAt) byDate[date] = log
        }

        val days = byDate.entries
            .sortedByDescending { it.key }
            .map { (date, log) ->
                ReadingDayProgress(
                    date = date,
                    startPage = log.startPage.coerceAtLeast(0),
                    endPage = log.endPage.coerceAtLeast(0),
                    pagesRead = log.pagesRead,
                    ratio = book.ratioAt(log.endPage),
                    ratioGain = book.ratioOf(log.pagesRead)
                )
            }

        return BookProgressReport(
            days = days,
            readingDays = days.size,
            totalPagesRead = days.sumOf { it.pagesRead }
        )
    }

    /** 그 쪽까지 읽었을 때의 진행률. 전체 쪽수를 모르면 null입니다. */
    private fun BookEntity.ratioAt(page: Int): Float? {
        if (totalPages <= 0) return null
        return (page.toFloat() / totalPages).coerceIn(0f, 1f)
    }

    /** 그만큼 읽은 것이 전체의 몇 할인지. 전체 쪽수를 모르면 null입니다. */
    private fun BookEntity.ratioOf(pages: Int): Float? {
        if (totalPages <= 0) return null
        return (pages.toFloat() / totalPages).coerceIn(0f, 1f)
    }

    /** 형식이 어긋난 날짜는 버립니다. 지금 날짜로 바꿔 두면 안 읽은 날이 읽은 날이 됩니다. */
    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
}
