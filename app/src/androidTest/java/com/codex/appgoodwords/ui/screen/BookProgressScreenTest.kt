package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.codex.appgoodwords.data.BookEntity
import com.codex.appgoodwords.data.BookProgressCalculator
import com.codex.appgoodwords.data.BookReadingLogEntity
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * 날짜별 독서 진행 화면.
 *
 * 사용자가 묻는 것은 "어제는 몇 쪽, 오늘은 몇 쪽"입니다. 그래서 **오늘과 어제가 날짜가 아니라
 * 그 말로 보이는지**와, 전체 쪽수를 모르는 책에 퍼센트를 지어내지 않는지를 봅니다.
 */
class BookProgressScreenTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 6)

    private val book = BookEntity(
        id = 1,
        syncId = "book-1",
        title = "트렌드 코리아 2026",
        author = "김난도",
        totalPages = 400,
        currentPage = 177
    )

    private fun log(date: String, start: Int, end: Int) = BookReadingLogEntity(
        syncId = "log-$date",
        bookId = 1,
        bookSyncId = "book-1",
        bookTitle = book.title,
        readDate = date,
        startPage = start,
        endPage = end
    )

    @Test fun yesterdayAndTodayAreNamedNotDated() {
        val logs = listOf(log("2026-10-04", 60, 98), log("2026-10-05", 98, 120), log("2026-10-06", 120, 177))
        compose.setContent {
            AppGoodWordsTheme {
                BookProgressScreen(
                    book = book,
                    report = BookProgressCalculator.build(book, logs),
                    today = today
                )
            }
        }

        // "10월 6일"보다 "오늘"이 먼저 읽힙니다.
        compose.onNodeWithText("오늘").assertIsDisplayed()
        compose.onNodeWithText("어제").assertIsDisplayed()
        compose.onNodeWithText("10월 4일").assertIsDisplayed()

        // 그날 닿은 쪽과 그 자리의 퍼센트가 함께 보여야 "얼마나 나아갔나"가 잡힙니다.
        compose.onNodeWithText("177쪽까지").assertIsDisplayed()
        compose.onNodeWithText("(44%)").assertIsDisplayed()
        compose.onNodeWithText("120 → 177쪽 · 57쪽 읽음 (+14%)").assertIsDisplayed()
        compose.onNodeWithText("98 → 120쪽 · 22쪽 읽음 (+5%)").assertIsDisplayed()
    }

    @Test fun theSummaryCountsOnlyTheDaysActuallyRead() {
        val logs = listOf(log("2026-09-26", 0, 40), log("2026-10-06", 40, 100))
        compose.setContent {
            AppGoodWordsTheme {
                BookProgressScreen(
                    book = book,
                    report = BookProgressCalculator.build(book, logs),
                    today = today
                )
            }
        }

        // 쉰 날을 나누는 수에 넣으면 꾸준히 읽었다는 사실이 숫자에서 사라집니다.
        compose.onNodeWithText("읽은 날 2일 · 모두 100쪽 · 읽은 날 하루 평균 50쪽").assertIsDisplayed()
    }

    @Test fun aBookWithoutATotalShowsPagesButNoPercentage() {
        val unknown = book.copy(totalPages = 0)
        compose.setContent {
            AppGoodWordsTheme {
                BookProgressScreen(
                    book = unknown,
                    report = BookProgressCalculator.build(unknown, listOf(log("2026-10-06", 120, 177))),
                    today = today
                )
            }
        }

        compose.onNodeWithText("120 → 177쪽 · 57쪽 읽음").assertIsDisplayed()
        compose.onNodeWithText("전체 쪽수 모름").assertIsDisplayed()
        // 모르는 것과 안 읽은 것은 다릅니다. 0%라고 적으면 안 읽은 책처럼 보입니다.
        compose.onNodeWithText("(0%)", substring = true).assertDoesNotExist()
    }

    @Test fun aBookWithNoRecordsExplainsWhereTheyComeFrom() {
        compose.setContent {
            AppGoodWordsTheme {
                BookProgressScreen(
                    book = book,
                    report = BookProgressCalculator.build(book, emptyList()),
                    today = today
                )
            }
        }

        compose.onNodeWithText("아직 날짜별 기록이 없습니다.").assertIsDisplayed()
        compose.onNodeWithText("진도 기록을 누르면 그날부터 쌓입니다", substring = true).assertIsDisplayed()
        // 기록이 없어도 지금 어디까지 왔는지는 책이 알고 있습니다.
        compose.onNodeWithText("177쪽").assertIsDisplayed()
    }

    @Test fun aDayThatWentBackwardsSaysSoInsteadOfZeroPages() {
        compose.setContent {
            AppGoodWordsTheme {
                BookProgressScreen(
                    book = book,
                    report = BookProgressCalculator.build(book, listOf(log("2026-10-06", 177, 150))),
                    today = today
                )
            }
        }

        compose.onNodeWithText("177 → 150쪽 · 앞으로 돌아갔습니다").assertIsDisplayed()
    }
}
