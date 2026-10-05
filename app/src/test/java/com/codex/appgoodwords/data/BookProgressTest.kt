package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 날짜별 독서 진행.
 *
 * "어제는 몇 쪽, 오늘은 몇 쪽"을 말하려면 책이 아니라 기록을 봐야 합니다. 책에는 마지막 쪽만
 * 남기 때문입니다. 여기서 특히 보는 것은 **없는 것을 0으로 단정하지 않는지**입니다 —
 * 전체 쪽수를 모르는 책에 "0%"라고 적으면 안 읽은 책처럼 보입니다.
 */
class BookProgressTest {
    private val book = BookEntity(
        id = 1,
        syncId = "book-1",
        title = "트렌드 코리아 2026",
        totalPages = 400,
        currentPage = 177
    )

    private fun log(
        date: String,
        start: Int,
        end: Int,
        syncId: String = "log-$date",
        bookSyncId: String = "book-1",
        updatedAt: Long = 1_000L
    ) = BookReadingLogEntity(
        syncId = syncId,
        updatedAt = updatedAt,
        bookId = 1,
        bookSyncId = bookSyncId,
        bookTitle = book.title,
        readDate = date,
        startPage = start,
        endPage = end
    )

    @Test fun eachDayKeepsItsRangeAndShare() {
        val report = BookProgressCalculator.build(
            book,
            listOf(
                log("2026-10-04", 60, 98),
                log("2026-10-05", 98, 120),
                log("2026-10-06", 120, 177)
            )
        )

        // 최근 날이 맨 위입니다. 오늘 어디까지 왔는지가 가장 궁금한 것입니다.
        assertEquals(
            listOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 4)),
            report.days.map { it.date }
        )

        val today = report.days.first()
        assertEquals(120, today.startPage)
        assertEquals(177, today.endPage)
        assertEquals(57, today.pagesRead)
        assertEquals(177f / 400f, today.ratio!!, 0.0001f)
        assertEquals(57f / 400f, today.ratioGain!!, 0.0001f)

        assertEquals(3, report.readingDays)
        assertEquals(117, report.totalPagesRead)
        assertEquals(39, report.averagePagesPerDay)
    }

    @Test fun aBookWithoutATotalNeverClaimsAPercentage() {
        val unknown = book.copy(totalPages = 0)

        val report = BookProgressCalculator.build(unknown, listOf(log("2026-10-06", 120, 177)))

        // 모르는 것과 안 읽은 것은 다릅니다. 0%라고 적으면 안 읽은 책처럼 보입니다.
        assertNull(report.days.single().ratio)
        assertNull(report.days.single().ratioGain)
        // 쪽수는 전체를 몰라도 말할 수 있습니다.
        assertEquals(57, report.days.single().pagesRead)
    }

    @Test fun readingBackwardsDoesNotSubtractFromTheTotal() {
        // 잘못 적었거나 앞으로 돌아가 다시 읽는 날이 있습니다.
        val report = BookProgressCalculator.build(
            book,
            listOf(log("2026-10-05", 120, 177), log("2026-10-06", 177, 150))
        )

        assertEquals(0, report.days.first().pagesRead)
        assertEquals("되돌려 적은 날이 합계를 깎았습니다.", 57, report.totalPagesRead)
        // 그래도 그날 닿은 자리는 그대로 보여 줍니다. 돌아가 읽은 것도 사실입니다.
        assertEquals(150, report.days.first().endPage)
    }

    @Test fun twoDevicesWritingTheSameDayCountAsOneDay() {
        val report = BookProgressCalculator.build(
            book,
            listOf(
                log("2026-10-06", 120, 150, syncId = "log-a", updatedAt = 1_000L),
                log("2026-10-06", 120, 177, syncId = "log-b", updatedAt = 2_000L)
            )
        )

        // 두 줄을 다 세면 그날 읽은 쪽수가 겹쳐 세집니다.
        assertEquals(1, report.readingDays)
        // 나중에 적은 쪽이 그날의 것입니다(SyncDeduplicator가 고르는 기준과 같습니다).
        assertEquals(177, report.days.single().endPage)
    }

    @Test fun anotherBooksDaysDoNotLeakIn() {
        val report = BookProgressCalculator.build(
            book,
            listOf(
                log("2026-10-06", 120, 177),
                log("2026-10-06", 10, 40, syncId = "other", bookSyncId = "book-2")
            )
        )

        assertEquals(1, report.readingDays)
        assertEquals(57, report.totalPagesRead)
    }

    @Test fun abrokenDateIsDroppedInsteadOfBecomingToday() {
        val report = BookProgressCalculator.build(
            book,
            listOf(log("2026-10-06", 120, 177), log("", 0, 30, syncId = "broken"))
        )

        assertEquals(1, report.readingDays)
    }

    @Test fun aBookWithNoRecordsYetSaysSoInsteadOfGuessing() {
        val report = BookProgressCalculator.build(book, emptyList())

        assertTrue(report.isEmpty)
        assertEquals(0, report.readingDays)
        // 나누는 수가 0일 때 평균을 묻습니다. 여기서 터지면 책을 열자마자 앱이 죽습니다.
        assertEquals(0, report.averagePagesPerDay)
    }

    @Test fun daysOffDoNotDragTheAverageDown() {
        // 열흘 중 이틀만 읽었어도 "읽은 날 하루 평균"은 그 이틀로 셉니다.
        val report = BookProgressCalculator.build(
            book,
            listOf(log("2026-09-26", 0, 40), log("2026-10-06", 40, 100))
        )

        assertEquals(2, report.readingDays)
        assertEquals(50, report.averagePagesPerDay)
    }
}
