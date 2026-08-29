package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DailyProgress
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.FeedbackKind
import com.codex.appgoodwords.data.FeedbackNote
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.MoodPracticeRow
import com.codex.appgoodwords.data.OnThisDayMemory
import com.codex.appgoodwords.data.RoutineCheckEntity
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.StatsSummary
import java.time.LocalDate
import java.time.YearMonth

internal const val feedbackCardTag = "home_feedback_card"
internal const val growthTeaserTag = "home_growth_teaser"
internal const val onThisDayCardTag = "home_on_this_day"
internal const val moodPracticeCardTag = "home_mood_practice"

/**
 * 돌아보는 자리.
 *
 * 예전에는 홈에도 글귀 목록이 있어서 보관함과 같은 일을 두 곳에서 했습니다. 읽는 일은
 * 글귀가 모여 있는 보관함으로 모으고, 홈에는 "지금 어떻게 지내고 있는지"만 남겼습니다.
 * 오늘 할 일(오늘의 걸음), 짚어 주는 문구, 그리고 통계 순서입니다. 앞에서부터 시간의 폭이
 * 넓어지도록 두어, 위에서 아래로 읽으면 오늘에서 지난 달까지 자연스럽게 이어집니다.
 */
internal const val homeListTag = "home_list"

@Composable
fun HomeScreen(
    summary: StatsSummary,
    notes: List<FeedbackNote>,
    routines: List<RoutineEntity>,
    checks: List<RoutineCheckEntity>,
    modifier: Modifier = Modifier,
    /** 오늘의 걸음. null이면 카드를 두지 않습니다. */
    dailyLoop: DailyProgress? = null,
    /** 걸음을 누르면 그 화면으로 데려갑니다. 알려만 주면 다시 찾아 들어가야 합니다. */
    onOpenStep: (DailyStep) -> Unit = {},
    /** 가장 최근에 AI가 써 준 돌아보기. 없으면 권하는 카드만 둡니다. */
    latestReport: GrowthReportEntity? = null,
    onOpenGrowth: () -> Unit = {},
    /** 지난 이맘때 남긴 것. 없으면 카드를 두지 않습니다. */
    memories: List<OnThisDayMemory> = emptyList(),
    onOpenMemory: (OnThisDayMemory) -> Unit = {},
    /** 기분별 실천. 날이 적으면 빈 목록입니다. */
    moodPractice: List<MoodPracticeRow> = emptyList(),
    /** 오늘의 기분. 찍어 둔 것이 없고 일기에도 없으면 null입니다. */
    todayMood: DiaryMood? = null,
    onPickMood: (DiaryMood) -> Unit = {},
    /** 찍어 둔 것이 있을 때만 지울 수 있습니다. */
    onClearMood: (() -> Unit)? = null,
    /** 번뜩인 것을 한 줄로 담습니다. 여기가 앱에서 가장 빨리 닿는 자리입니다. */
    onCaptureIdea: (String) -> Unit = {},
    /** 왼쪽으로 밀었을 때. 담는 칸을 띄웁니다. */
    onSwipeToCapture: () -> Unit = {}
) {
    var selectedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(homeListTag)
            // 왼쪽으로 밀면 담는 칸이 뜹니다. 세로로 굴리는 목록이라 가로 몸짓과 부딪히지 않습니다.
            // 문턱을 두는 이유는, 굴리다 손가락이 옆으로 흐르는 것만으로 뜨면 성가시기 때문입니다.
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (dragged <= -SWIPE_TO_CAPTURE_PX) onSwipeToCapture()
                    }
                ) { change, amount ->
                    dragged += amount
                    change.consume()
                }
            },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 앱을 열면 가장 먼저 보이는 자리입니다. 오늘 무엇부터 할지 여기서 정해집니다.
        dailyLoop?.let { progress ->
            item {
                DailyLoopCard(progress = progress, onOpenStep = onOpenStep)
            }
        }

        // 걸음 바로 아래입니다. 3초짜리라 손이 가장 먼저 닿는 자리에 두어야 하고,
        // 오늘 무엇을 했는지 옆에 오늘 어땠는지가 나란히 놓입니다.
        item {
            TodayMoodCard(mood = todayMood, onPick = onPickMood, onClear = onClearMood)
        }

        // 번뜩인 것은 언제 올지 모릅니다. 담으러 들어가는 사이에 날아가므로,
        // 앱을 열면 바로 보이는 자리에 한 줄 칸을 둡니다.
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("번뜩인 것", style = MaterialTheme.typography.titleMedium)
                    IdeaCaptureField(onCapture = onCaptureIdea)
                    Text(
                        text = "화면을 왼쪽으로 밀어도 이 칸이 뜹니다. " +
                            "보관함 아이디어에 담기고, 익으면 루틴이나 할 일로 옮기면 됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            FeedbackCard(notes = notes)
        }

        // 없는 날이 대부분이라 있을 때만 자리를 냅니다. 빈 카드가 매일 있으면
        // "오늘은 없음"을 매일 확인하게 됩니다.
        if (memories.isNotEmpty()) {
            item {
                OnThisDayCard(memories = memories, onOpen = onOpenMemory)
            }
        }

        item {
            GrowthTeaserCard(report = latestReport, onOpen = onOpenGrowth)
        }

        item {
            StatsCard(summary = summary)
        }

        if (moodPractice.isNotEmpty()) {
            item {
                MoodPracticeCard(rows = moodPractice)
            }
        }

        item {
            RoutineCalendarCard(
                routines = routines,
                checks = checks,
                selectedMonthText = selectedMonthText,
                selectedDateText = selectedDateText,
                onMonthChanged = { month, date ->
                    selectedMonthText = month.toString()
                    selectedDateText = date.toString()
                },
                onDateSelected = { date ->
                    selectedDateText = date.toString()
                }
            )
        }
    }
}

/**
 * 기록을 문장으로 짚어 주는 카드.
 *
 * 숫자는 아래 통계 카드가 맡습니다. 여기서는 그 숫자가 무슨 뜻인지 한 줄로 옮깁니다.
 * 못 한 것을 세지 않습니다. 짚는 말은 [FeedbackWriter]가 한 줄까지만 담아 줍니다.
 */
@Composable
private fun FeedbackCard(notes: List<FeedbackNote>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(feedbackCardTag),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "요즘 이렇게 지내고 있어요",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (notes.isEmpty()) {
                Text(
                    text = "기록이 쌓이면 여기에 짚어 드릴 말이 생깁니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                notes.forEach { note ->
                    FeedbackLine(note = note)
                }
            }
        }
    }
}

@Composable
private fun FeedbackLine(note: FeedbackNote) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 점 하나로 성격을 나눕니다. 아이콘을 붙이면 세 줄이 저마다 다른 그림이 되어 시끄럽습니다.
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(note.kind.dotColor())
        )
        Text(
            text = note.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 짚는 말도 붉게 칠하지 않습니다.
 *
 * 경고 색을 쓰면 뜸해진 루틴 하나가 화면에서 사고처럼 보입니다.
 */
@Composable
private fun FeedbackKind.dotColor(): Color = when (this) {
    FeedbackKind.STREAK -> MaterialTheme.colorScheme.primary
    FeedbackKind.PRAISE -> MaterialTheme.colorScheme.tertiary
    FeedbackKind.NUDGE -> MaterialTheme.colorScheme.outline
    FeedbackKind.INVITE -> MaterialTheme.colorScheme.secondary
}

/**
 * AI가 써 준 돌아보기로 가는 문.
 *
 * 홈에 글 전체를 펼치지 않습니다. 잘한 점 한 줄만 보이고 나머지는 그 화면에서 봅니다.
 * 홈은 훑는 자리이지 읽는 자리가 아닙니다.
 */
@Composable
private fun GrowthTeaserCard(
    report: GrowthReportEntity?,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(growthTeaserTag),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "AI 돌아보기",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = report?.strengths?.firstOrNull()
                    ?: report?.guide?.takeIf { it.isNotBlank() }
                    ?: "기록을 보고 잘한 점과 다음 걸음을 정리해 드립니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onOpen) {
                Text(if (report == null) "받아 보기" else "돌아보기 열기")
            }
        }
    }
}

/**
 * 지난 이맘때 남긴 것.
 *
 * 일기와 글귀는 쌓일수록 값이 커지는 기록인데, 지금까지 홈은 최근 이레와 이번 달만 보여
 * 주었습니다. 오래 쓴 사람에게만 생기는 되돌림을 여기에 둡니다.
 * 없는 날이 대부분이라 **아무것도 없으면 카드 자체를 두지 않습니다.** 빈 카드가 매일 자리를
 * 차지하면 "오늘은 없음"을 매일 확인하게 됩니다.
 */
@Composable
private fun OnThisDayCard(
    memories: List<OnThisDayMemory>,
    onOpen: (OnThisDayMemory) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(onThisDayCardTag),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "지난 이맘때",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            memories.forEach { memory ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(memory) },
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${memory.whenText} · ${memory.kind.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = memory.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (memory.body.isNotBlank()) {
                        Text(
                            text = memory.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 기분과 실천을 나란히 놓은 줄.
 *
 * **인과로 말하지 않습니다.** 많이 움직여서 기분이 좋았는지 그 반대인지는 이 숫자로 알 수
 * 없습니다. "이런 날엔 이만큼 움직였습니다"까지만 적습니다.
 */
@Composable
private fun MoodPracticeCard(rows: List<MoodPracticeRow>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(moodPracticeCardTag),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "기분과 실천",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "그 기분으로 적은 날, 하루에 평균 몇 번 움직였는지입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = row.mood.emoji, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = row.mood.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%.1f회".format(row.averagePerDay),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${row.dayCount}일",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 이만큼은 밀어야 담는 칸이 뜹니다. 굴리다 손가락이 흐르는 것으로는 뜨지 않게 합니다. */
private const val SWIPE_TO_CAPTURE_PX = 220f
