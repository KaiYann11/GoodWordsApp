package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.BookEntity
import com.codex.appgoodwords.data.BookProgressReport
import com.codex.appgoodwords.data.ReadingDayProgress
import java.time.LocalDate

internal const val bookProgressListTag = "book_progress_list"

internal fun bookProgressDayTag(date: LocalDate) = "book_progress_day_$date"

/**
 * 날짜별 독서 진행.
 *
 * 책 카드에는 "지금 몇 쪽"만 있습니다. 그것만으로는 **어제보다 얼마나 나아갔는지**를 알 수 없어,
 * 꾸준히 읽고 있다는 사실이 어디에도 보이지 않았습니다. 여기서는 하루씩 끊어 보여 줍니다.
 *
 * 숫자는 저장하지 않고 기록에서 그때그때 셉니다([BookProgressCalculator]). 연속 날수·점수와
 * 같은 이유입니다 — 저장해 두면 나중에 기록을 고쳤을 때 둘이 어긋납니다.
 */
@Composable
fun BookProgressScreen(
    book: BookEntity,
    report: BookProgressReport,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(bookProgressListTag),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ProgressSummaryCard(book = book, report = report) }

        if (report.isEmpty) {
            item {
                EmptyCard(
                    title = "아직 날짜별 기록이 없습니다.",
                    // 지난 독서는 되살릴 수 없습니다. 책에 마지막 쪽만 남아 있기 때문입니다.
                    body = "책 목록에서 진도 기록을 누르면 그날부터 쌓입니다. 지난 기록은 책에 마지막 쪽만 남아 있어 되짚을 수 없습니다."
                )
            }
        }

        items(report.days, key = { it.date.toString() }) { day ->
            ReadingDayRow(day = day, today = today)
        }
    }
}

/** 맨 위의 요약. 지금 어디까지 왔는지와, 읽은 날 기준으로 얼마나 읽는지. */
@Composable
private fun ProgressSummaryCard(book: BookEntity, report: BookProgressReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = book.displayAuthor,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${book.currentPage}쪽",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                book.progress?.let { ratio ->
                    Text(
                        text = "${percent(ratio)}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    // 전체 쪽수를 모르면 "/ 몇 쪽"을 말하지 않습니다.
                    text = if (book.totalPages > 0) "/ ${book.totalPages}쪽" else "전체 쪽수 모름",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            book.progress?.let { ratio ->
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
            }

            if (!report.isEmpty) {
                Text(
                    // 안 읽은 날은 나누는 수에 넣지 않습니다. 쉰 날이 평균을 깎으면 꾸준함이 숫자에서 사라집니다.
                    text = "읽은 날 ${report.readingDays}일 · 모두 ${report.totalPagesRead}쪽 · " +
                        "읽은 날 하루 평균 ${report.averagePagesPerDay}쪽",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 하루 한 줄. 어디서 어디까지, 몇 쪽, 그날 끝에서 몇 퍼센트. */
@Composable
private fun ReadingDayRow(day: ReadingDayProgress, today: LocalDate) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(bookProgressDayTag(day.date)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = dayLabel(day.date, today),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    // 그날 닿은 자리. 요약 카드의 "지금 몇 쪽"과 글이 같아 보이지만 뜻이 다릅니다.
                    text = "${day.endPage}쪽까지",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                day.ratio?.let { ratio ->
                    Text(
                        text = "(${percent(ratio)}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = buildString {
                    append("${day.startPage} → ${day.endPage}쪽")
                    // 되돌려 적은 날은 "0쪽 읽음"이 아니라 되돌아갔다고 말해야 덜 이상합니다.
                    if (day.pagesRead > 0) append(" · ${day.pagesRead}쪽 읽음") else append(" · 앞으로 돌아갔습니다")
                    day.ratioGain?.let { gain -> if (day.pagesRead > 0) append(" (+${percent(gain)}%)") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            day.ratio?.let { ratio ->
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * 오늘·어제는 날짜 대신 그렇게 부릅니다.
 *
 * "10월 6일"보다 "오늘"이 먼저 읽힙니다. 사용자가 궁금한 것은 날짜가 아니라 어제와 오늘의 차이입니다.
 */
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "오늘"
    today.minusDays(1) -> "어제"
    else -> "${date.monthValue}월 ${date.dayOfMonth}일"
}

/** 소수점은 버립니다. 42.7%는 43%보다 42%가 덜 부풀려 보입니다. */
private fun percent(ratio: Float): Int = (ratio * 100).toInt()
