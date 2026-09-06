package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DayScore
import com.codex.appgoodwords.data.ScorePoint
import com.codex.appgoodwords.data.ScoreTrend
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal const val dayScoreCardTag = "day_score_card"
internal const val dayScoreValueTag = "day_score_value"
internal const val dayScoreChangeTag = "day_score_change"
internal const val dayScoreOpenDayTag = "day_score_open_day"

internal fun dayScoreBarTag(date: LocalDate) = "day_score_bar_$date"

/**
 * 오늘 몇 점이고, 지난주보다 나아지고 있는지.
 *
 * **오늘 점수만 크게 보여 주지 않습니다.** 하루 점수는 들쭉날쭉해서 그것만 보면 좋은 날엔
 * 우쭐하고 나쁜 날엔 그만두게 됩니다. 사람이 알고 싶은 것은 "내가 나아지고 있나"이고,
 * 그건 이번 주 평균과 지난주 평균의 차이가 말해 줍니다.
 *
 * 막대를 누르면 그날의 요약이 열립니다. 점수만 보고는 왜 그 점수인지 알 수 없기 때문입니다.
 */
@Composable
fun DayScoreCard(
    todayScore: DayScore,
    trend: ScoreTrend,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(dayScoreCardTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "오늘 점수",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = scoreNote(todayScore),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${todayScore.score}",
                    modifier = Modifier.testTag(dayScoreValueTag),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            ChangeLine(trend = trend)

            ScoreBars(trend = trend, onOpenDay = onOpenDay)

            TextButton(
                onClick = { onOpenDay(LocalDate.now()) },
                modifier = Modifier.testTag(dayScoreOpenDayTag)
            ) {
                Text("그날 요약 보기")
            }
        }
    }
}

/**
 * 지난주와 견준 한 줄.
 *
 * 견줄 지난주가 없으면 아무 말도 하지 않습니다. 처음 쓰는 사람에게 "0점에서 올랐습니다"라고
 * 하면 뜻 없는 칭찬이 되고, 그런 칭찬이 쌓이면 나중의 칭찬도 믿지 않게 됩니다.
 */
@Composable
private fun ChangeLine(trend: ScoreTrend) {
    val text = when {
        !trend.hasComparison -> "이번 주 평균 ${trend.thisWeekAverage}점"
        trend.change > 0 -> "이번 주 평균 ${trend.thisWeekAverage}점 · 지난주보다 ${trend.change}점 올랐습니다"
        trend.change < 0 -> "이번 주 평균 ${trend.thisWeekAverage}점 · 지난주보다 ${-trend.change}점 낮습니다"
        else -> "이번 주 평균 ${trend.thisWeekAverage}점 · 지난주와 같습니다"
    }

    Text(
        text = text,
        modifier = Modifier.testTag(dayScoreChangeTag),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (trend.change > 0) FontWeight.Bold else FontWeight.Normal,
        color = when {
            !trend.hasComparison -> MaterialTheme.colorScheme.onSurfaceVariant
            trend.change > 0 -> MaterialTheme.colorScheme.primary
            trend.change < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

/**
 * 날짜별 막대.
 *
 * 그림 라이브러리를 새로 넣지 않고 Box 높이로 그립니다(이 앱의 다른 막대와 같은 방식).
 * 네 주를 한 화면에 욱여넣으면 하루 칸이 손가락보다 좁아져서 옆으로 넘깁니다.
 */
@Composable
private fun ScoreBars(
    trend: ScoreTrend,
    onOpenDay: (LocalDate) -> Unit
) {
    val scroll = rememberScrollState()

    // 좁은 화면에서는 네 주가 다 안 들어가 옆으로 밀립니다. 그때 왼쪽(가장 오래된 날)에
    // 멈춰 있으면 정작 오늘이 화면 밖에 있습니다. 오늘이 보이는 자리에서 시작합니다.
    LaunchedEffect(trend.points.size, scroll.maxValue) {
        scroll.scrollTo(scroll.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        trend.points.forEach { point ->
            ScoreBar(point = point, onClick = { onOpenDay(point.date) })
        }
    }
}

@Composable
private fun ScoreBar(
    point: ScorePoint,
    onClick: () -> Unit
) {
    // 0점인 날도 자리는 남깁니다. 빼 버리면 안 한 날이 사라져 흐름이 실제보다 좋아 보입니다.
    val height = MIN_SCORE_BAR + (MAX_SCORE_BAR - MIN_SCORE_BAR) * point.score / 100

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(dayScoreBarTag(point.date))
    ) {
        Box(
            modifier = Modifier
                .width(9.dp)
                .height(height.dp)
                .background(
                    color = if (point.score > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(3.dp)
                )
        )
        Text(
            text = point.date.format(scoreDayFormatter),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 점수 밑에 붙는 한 줄.
 *
 * 못한 것을 세지 않습니다. 다음 한 걸음만 짚어 주는 것이 이 앱의 방식이고([DailyProgress]),
 * 점수도 같아야 합니다. 숫자로 다그치려고 두는 것이 아닙니다.
 */
private fun scoreNote(score: DayScore): String = when {
    score.score >= 100 -> "가득 찬 하루입니다."
    score.score >= DayScore.STEP_POINTS -> "오늘 걸음을 다 밟았습니다."
    score.isEmpty -> "아직 오늘이 비어 있습니다."
    else -> "${score.deedCount}가지를 했습니다."
}

private const val MIN_SCORE_BAR = 3
private const val MAX_SCORE_BAR = 52

private val scoreDayFormatter = DateTimeFormatter.ofPattern("d")
