package com.codex.appgoodwords.ui.screen

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.StatsSummary
import java.time.format.DateTimeFormatter

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
                    text = "글귀를 확인하거나 루틴을 체크하면 여기에 기록이 쌓입니다.",
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
