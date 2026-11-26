package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.CompareCard
import com.codex.appgoodwords.data.CompareColumn
import com.codex.appgoodwords.data.CompareLine
import com.codex.appgoodwords.data.CompareSection
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.DayCompareDetails
import com.codex.appgoodwords.data.DayPair
import com.codex.appgoodwords.ui.theme.Indigo
import com.codex.appgoodwords.ui.theme.IndigoSoft
import com.codex.appgoodwords.ui.theme.Ink
import com.codex.appgoodwords.ui.theme.InkMuted
import com.codex.appgoodwords.ui.theme.Surface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val dayCompareScreenTag = "day_compare_screen"
internal const val dayCompareEmptyTag = "day_compare_empty"
internal const val dayCompareYesterdayTag = "day_compare_yesterday"

internal fun dayCompareCardTag(section: CompareSection): String = "day_compare_card_" + section.name.lowercase()

/**
 * 어제와 오늘(사용자가 고름, 2026-11-27 — 항목 카드 두 칸). 홈 글귀 아래 띠나 돌아보기 칸의 어제보다 카드에서 엽니다.
 *
 * 칸마다 카드 하나, 왼쪽이 **어제 하루**, 오른쪽이 **오늘 지금까지**이고 자세한 것(루틴 이름·읽은 글귀·기분과 시각·걸음과
 * 걷기·끝낸 할 일·일기·읽은 책)이 늘 펼쳐져 있습니다. 앞선 칸에는 머리글 옆에 빛 칩("+1")이 붙고, 적은 칸은 수만 나란히
 * 둡니다 — 눌러서 보러 온 화면이라 숫자를 감추지 않습니다. 기분은 견주지 않고 두 날의 흐름을 나란히 둡니다.
 * 칸이 차례로 떠오르고 큰 수가 0에서 차오릅니다(은은한 빛과 깊이).
 */
@Composable
fun DayCompareScreen(
    pair: DayPair?,
    diaryLocked: Boolean,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val cards = remember(pair, diaryLocked) { pair?.let { DayCompareDetails.cards(it, diaryLocked) }.orEmpty() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(dayCompareScreenTag),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") { CompareHeader(pair = pair) }

        // 두 날을 아직 못 읽었을 때(화면을 막 열었거나 자정을 막 넘김)는 비었다고 말하지 않습니다. 곧 채워집니다.
        if (pair != null && cards.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "어제도 오늘도 아직 적은 것이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .testTag(dayCompareEmptyTag)
                )
            }
        }

        itemsIndexed(cards, key = { _, card -> card.section.name }) { index, card ->
            CompareCardView(card = card, index = index + 1)
        }

        if (pair != null) {
            item(key = "yesterday") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = { onOpenDay(pair.yesterday.score.date) },
                        modifier = Modifier.testTag(dayCompareYesterdayTag)
                    ) {
                        Text("어제 그날 요약 ›")
                    }
                }
            }
        }
    }
}

private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

/** 맨 위 띠. 글귀 카드와 같은 흐르는 빛 위에 두 날을 적습니다. */
@Composable
private fun CompareHeader(pair: DayPair?) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .riseIn(index = 0, shadowColor = Indigo.copy(alpha = 0.6f), shape = shape)
            .clip(shape)
            .aurora(base = Brush.verticalGradient(listOf(IndigoSoft, Surface)))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        if (pair == null) {
            Text(text = "어제와 오늘", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DayLabel(title = "어제", date = pair.yesterday.score.date, note = "하루", modifier = Modifier.weight(1f))
                Text(
                    text = "→",
                    style = MaterialTheme.typography.titleLarge,
                    color = Indigo.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                DayLabel(
                    title = "오늘",
                    date = pair.today.score.date,
                    note = "지금까지",
                    emphasized = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DayLabel(title: String, date: LocalDate, note: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) Indigo else InkMuted
        )
        Text(text = date.format(dayFormat), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text(text = note, style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

/** 칸의 빛깔은 홈 하루의 걸음과 같습니다. 기분만 걸음이 아니라 따로 둡니다. */
private fun sectionColors(section: CompareSection): StepColors = when (section) {
    CompareSection.ROUTINE -> stepColors(DailyStep.ROUTINE)
    CompareSection.QUOTE -> stepColors(DailyStep.QUOTE)
    CompareSection.MOOD -> StepColors(accent = Color(0xFFEC4899), soft = Color(0xFFFCE7F3))
    CompareSection.STEPS -> stepColors(DailyStep.WALK)
    CompareSection.TODO -> stepColors(DailyStep.TODO)
    CompareSection.DIARY -> stepColors(DailyStep.DIARY)
    CompareSection.READING -> stepColors(DailyStep.READING)
}

@Composable
private fun CompareCardView(card: CompareCard, index: Int) {
    val colors = sectionColors(card.section)
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .riseIn(index = index, shadowColor = colors.accent.copy(alpha = 0.55f), shape = shape)
            .testTag(dayCompareCardTag(card.section)),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowDot(color = colors.accent)
                Text(text = card.section.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = card.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (card.gain.isNotEmpty()) {
                    GainChip(text = card.gain, colors = colors)
                }
            }
            val divider = MaterialTheme.colorScheme.outlineVariant
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 두 칸을 가르는 가는 세로줄. 칸의 키에 맞춰 그립니다.
                    .drawBehind {
                        val x = size.width / 2f
                        drawLine(color = divider, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    }
            ) {
                CompareColumnView(
                    label = "어제",
                    column = card.yesterday,
                    emptyText = "없음",
                    accent = colors.accent,
                    today = false,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )
                CompareColumnView(
                    label = "오늘",
                    column = card.today,
                    emptyText = "아직 없음",
                    accent = colors.accent,
                    today = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
            }
        }
    }
}

/** 칸 이름 앞의 빛나는 점. */
@Composable
private fun GlowDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .drawBehind {
                val glow = size.minDimension * 1.4f
                drawCircle(brush = Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), radius = glow), radius = glow)
                drawCircle(color = color, radius = size.minDimension / 2f)
            }
    )
}

/** 오늘이 앞선 만큼. 그 칸의 빛깔로 옅게 빛납니다. */
@Composable
private fun GainChip(text: String, colors: StepColors) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = colors.accent,
        modifier = Modifier
            .softShadow(colors.accent.copy(alpha = 0.5f), shape, elevation = 6.dp)
            .clip(shape)
            .background(colors.soft)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun CompareColumnView(
    label: String,
    column: CompareColumn,
    emptyText: String,
    accent: Color,
    today: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            color = if (today) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (column.isEmpty) {
            Text(text = emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            if (column.headline.isNotEmpty()) {
                CountUpHeadline(text = column.headline, color = if (today) accent else MaterialTheme.colorScheme.onSurface)
            }
            column.lines.forEach { line -> CompareLineRow(line = line) }
        }
    }
}

/** "6,240걸음"의 수가 0에서 차오릅니다. 수로 시작하지 않는 글은 그대로 적습니다. */
@Composable
private fun CountUpHeadline(text: String, color: Color) {
    val match = remember(text) { Regex("^([0-9,]+)(.*)$").find(text) }
    val target = match?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
    val style = MaterialTheme.typography.titleLarge
    if (match == null || target == null) {
        Text(text = text, style = style, fontWeight = FontWeight.Bold, color = color)
        return
    }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val shown by animateIntAsState(
        targetValue = if (started) target else 0,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "compareCount"
    )
    Text(
        text = String.format(Locale.KOREA, "%,d", shown) + match.groupValues[2],
        style = style,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun CompareLineRow(line: CompareLine) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Text(text = line.lead, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(20.dp))
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (line.trail.isNotEmpty()) {
            Text(
                text = line.trail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
