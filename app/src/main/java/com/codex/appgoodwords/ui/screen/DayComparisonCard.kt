package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DayComparison
import com.codex.appgoodwords.data.DayComparisons
import java.time.LocalDate

internal const val dayComparisonCardTag = "day_comparison_card"
internal const val dayComparisonOpenTag = "day_comparison_open"
internal const val dayComparisonDialogTag = "day_comparison_dialog"
internal const val dayComparisonYesterdayTag = "day_comparison_yesterday"

/**
 * 어제보다 — 오늘 지금까지와 어제 하루 전체(사용자가 정함, 2026-11-19). 홈 돌아보기 칸의 하루 점수 바로 아래입니다.
 *
 * **한 일만 짚습니다**(사용자가 고름): 오늘이 어제보다 많거나 같은 것만 줄로 보이고, 적은 것은 숫자 없이 빠집니다. 앞선 것이
 * 없으면 둘 다 한 것을 "어제처럼 오늘도 …" 한 줄로. 두 날의 수는 "두 날 나란히 보기"를 눌러야 다 보입니다 — 아침마다 지는 수를
 * 먼저 보여 주면 다그치는 카드가 됩니다. 카드를 둘지는 [DayComparison.shown]이 정합니다.
 */
@Composable
fun DayComparisonCard(
    comparison: DayComparison,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBoth by rememberSaveable { mutableStateOf(false) }
    val lines = comparison.aheadOrEven.take(DayComparisons.CARD_LINES)

    // 돌아보기 칸의 다른 카드와 같은 모서리·여백입니다.
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(dayComparisonCardTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = "어제보다",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "오늘 지금까지와 어제 하루를 견줍니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (lines.isNotEmpty()) {
                lines.forEach { row -> ComparisonLine(text = DayComparisons.line(row)) }
            } else {
                DayComparisons.sharedLine(comparison)?.let { line ->
                    Text(text = line, style = MaterialTheme.typography.bodyMedium)
                }
            }

            TextButton(
                onClick = { showBoth = true },
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag(dayComparisonOpenTag)
            ) {
                Text("두 날 나란히 보기 ›")
            }
        }
    }

    if (showBoth) {
        BothDaysDialog(
            comparison = comparison,
            onDismiss = { showBoth = false },
            onOpenYesterday = {
                showBoth = false
                onOpenDay(comparison.today.minusDays(1))
            }
        )
    }
}

@Composable
private fun ComparisonLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // 짚는 카드("요즘 이렇게 지내고 있어요")와 같은 점입니다. 칭찬의 빛깔이라 붉게 칠하지 않습니다.
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** 두 날의 수를 모두. 여기서는 적은 날도 숫자 그대로입니다 — 눌러서 보러 온 것이기 때문입니다. */
@Composable
private fun BothDaysDialog(
    comparison: DayComparison,
    onDismiss: () -> Unit,
    onOpenYesterday: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(dayComparisonDialogTag),
        title = { Text("어제와 오늘") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TableRow(first = "", yesterday = "어제", today = "오늘 지금까지", header = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                comparison.rows.forEach { row ->
                    TableRow(
                        first = row.kind.label,
                        yesterday = DayComparisons.cell(row.kind, row.yesterday),
                        today = DayComparisons.cell(row.kind, row.today)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        dismissButton = {
            TextButton(onClick = onOpenYesterday, modifier = Modifier.testTag(dayComparisonYesterdayTag)) {
                Text("어제 그날 요약")
            }
        }
    )
}

@Composable
private fun TableRow(first: String, yesterday: String, today: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = first, style = style, color = color, modifier = Modifier.weight(1f))
        Text(text = yesterday, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(text = today, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}
