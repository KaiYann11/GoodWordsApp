package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.MoodTrend
import com.codex.appgoodwords.data.StatsSummary
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dayLabelFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@Composable
fun StatsCard(
    summary: StatsSummary,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("돌아보기", style = MaterialTheme.typography.titleMedium)

            if (!summary.hasActivity) {
                Text(
                    text = "글귀를 확인하거나 루틴을 체크하고, 일기·할 일·독서를 남기면 여기에 기록이 쌓입니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile(
                    label = "연속",
                    value = "${summary.currentStreakDays}일",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "최고 연속",
                    value = "${summary.bestStreakDays}일",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "실천한 날",
                    value = "${summary.activeDays}일",
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "확인한 글귀 ${summary.confirmedTotal}회 · 루틴 체크 ${summary.routineCheckTotal}회",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("최근 7일", style = MaterialTheme.typography.titleSmall)
            WeeklyBars(summary)

            // 아직 안 쓰는 기능의 빈 칸은 두지 않습니다. 기록이 생기면 그때 나타납니다.
            if (summary.reading.hasBooks) {
                Text("독서", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatTile(
                        label = "읽는 중",
                        value = "${summary.reading.readingCount}권",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "올해 완독",
                        value = "${summary.reading.finishedThisYear}권",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "읽은 쪽",
                        value = "${summary.reading.pagesRead}쪽",
                        modifier = Modifier.weight(1f)
                    )
                }
                if (summary.reading.quotesFromBooks > 0) {
                    Text(
                        text = "책에서 뽑은 글귀 ${summary.reading.quotesFromBooks}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (summary.diary.hasDiaries) {
                Text("일기", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "이번 달 ${summary.diary.daysThisMonth}일 · 전체 ${summary.diary.totalCount}편",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (summary.diary.topMoods.isNotEmpty()) {
                    Text(
                        text = summary.diary.topMoods.take(3).joinToString("  ") { entry ->
                            "${entry.mood.emoji} ${entry.mood.label} ${entry.count}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 점 하나뿐이면 그리지 않습니다. 오르내림이 없는 그래프는 자리만 차지합니다.
                summary.diary.moodTrend?.takeIf { it.hasShape }?.let { trend ->
                    Text("기분 흐름", style = MaterialTheme.typography.titleSmall)
                    MoodTrendChart(trend)
                }
            }

            if (summary.todo.hasTodos) {
                Text("할 일", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = buildString {
                        append("끝낸 일 ${summary.todo.doneCount}개 · 남은 일 ${summary.todo.openCount}개")
                        // 비율은 할 일이 하나라도 있을 때만 뜻이 있습니다.
                        summary.todo.doneRatio?.let { append(" · ${(it * 100).toInt()}%") }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (summary.todo.overdueCount > 0) {
                    Text(
                        text = "지난 일 ${summary.todo.overdueCount}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (summary.topCategories.isNotEmpty()) {
                Text("많이 본 카테고리", style = MaterialTheme.typography.titleSmall)
                summary.topCategories.take(3).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = entry.category, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${entry.count}회",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 막대 그래프를 그릴 라이브러리를 새로 넣지 않도록 Box 높이로 표현한다. */
@Composable
private fun WeeklyBars(summary: StatsSummary) {
    val maxTotal = summary.recentDays.maxOfOrNull { it.total } ?: 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        summary.recentDays.forEach { day ->
            val barHeight = if (maxTotal <= 0) {
                MIN_BAR_HEIGHT
            } else {
                MIN_BAR_HEIGHT + (MAX_BAR_HEIGHT - MIN_BAR_HEIGHT) * day.total / maxTotal
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (day.total > 0) day.total.toString() else "",
                    style = MaterialTheme.typography.labelSmall
                )
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(barHeight.dp)
                        .background(
                            color = if (day.total > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Text(
                    text = day.date.format(dayLabelFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val MIN_BAR_HEIGHT = 4
private const val MAX_BAR_HEIGHT = 56

/** 기분 한 줄의 높이. 왼쪽 이모지가 눌리지 않을 만큼입니다. */
private val moodRowHeight = 15.dp

/** 점 반지름. 좁은 칸에서도 서로 붙어 보이지 않을 만큼만 키웁니다. */
private const val MOOD_DOT_RADIUS = 3.5f

/**
 * 기분 추이.
 *
 * 세로는 기분이고 위가 좋은 쪽입니다([DiaryMood.rank]). 왼쪽 이모지가 눈금이라 점만 봐도
 * 어떤 기분이었는지 읽힙니다. 가로는 날짜입니다.
 *
 * **일기를 안 쓴 날은 점이 없습니다.** 안 쓴 날을 "보통"으로 채우면 그래프가 실제보다 평평해집니다.
 * 대신 점 사이를 잇는 선으로 흐름을 보여 주고, 하루라도 건너뛴 구간은 점선으로 그립니다.
 * 실선으로 이으면 안 쓴 날도 그 사이 어딘가였다고 말하는 셈이 됩니다.
 *
 * 그림 라이브러리를 새로 넣지 않고 Canvas로 그립니다. 이 카드의 막대 그래프와 같은 이유입니다.
 */
@Composable
private fun MoodTrendChart(trend: MoodTrend) {
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.surfaceVariant
    val chartHeight = moodRowHeight * DiaryMood.rankCount

    Row(modifier = Modifier.fillMaxWidth()) {
        Column {
            DiaryMood.entries.forEach { mood ->
                Box(
                    modifier = Modifier.height(moodRowHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(text = mood.emoji, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp)
                .height(chartHeight)
        ) {
            val rowHeight = size.height / DiaryMood.rankCount
            val dotRadius = MOOD_DOT_RADIUS.dp.toPx()
            fun y(mood: DiaryMood) = rowHeight * (mood.rank + 0.5f)
            // 첫날과 마지막 날의 점이 반만 그려지지 않도록 반지름만큼 안으로 들여 놓습니다.
            fun x(column: Int): Float {
                if (trend.dayCount <= 1) return size.width / 2f
                val usable = (size.width - dotRadius * 2).coerceAtLeast(0f)
                return dotRadius + usable * column / (trend.dayCount - 1)
            }

            // 보통 자리에 눈금 하나. 이 선 위인지 아래인지가 한눈에 보입니다.
            val neutralY = y(DiaryMood.NEUTRAL)
            drawLine(
                color = guideColor,
                start = Offset(0f, neutralY),
                end = Offset(size.width, neutralY),
                strokeWidth = 1.dp.toPx()
            )

            for (index in 1 until trend.points.size) {
                val previous = trend.points[index - 1]
                val next = trend.points[index]
                val skipped = ChronoUnit.DAYS.between(previous.date, next.date) > 1
                drawLine(
                    color = lineColor.copy(alpha = if (skipped) 0.35f else 0.7f),
                    start = Offset(x(trend.columnOf(previous)), y(previous.mood)),
                    end = Offset(x(trend.columnOf(next)), y(next.mood)),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    // 건너뛴 날이 있으면 점선. 실선은 그 사이도 이랬다는 말이 됩니다.
                    pathEffect = if (skipped) {
                        PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                    } else {
                        null
                    }
                )
            }

            trend.points.forEach { point ->
                drawCircle(
                    color = lineColor,
                    radius = MOOD_DOT_RADIUS.dp.toPx(),
                    center = Offset(x(trend.columnOf(point)), y(point.mood))
                )
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // 날짜를 열마다 적으면 2주 치가 겹칩니다. 양 끝만 있으면 어느 구간인지 압니다.
        Text(
            text = trend.from.format(dayLabelFormatter),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = trend.to.format(dayLabelFormatter),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
