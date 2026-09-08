package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.GrowthCalendar
import com.codex.appgoodwords.data.GrowthCalendarDay
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.ReportPeriod
import java.time.LocalDate
import java.time.YearMonth

internal const val growthCalendarTag = "growth_calendar"
internal const val growthCalendarMonthTag = "growth_calendar_month"
internal const val growthCalendarPrevTag = "growth_calendar_prev"
internal const val growthCalendarNextTag = "growth_calendar_next"
internal const val growthCalendarSelectedTag = "growth_calendar_selected"

internal fun growthCalendarDayTag(date: LocalDate) = "growth_calendar_day_$date"

/**
 * 돌아보기를 달력에 펼쳐 봅니다.
 *
 * 목록만 있으면 "요즘 자주 돌아봤나", **"어느 구간을 빠뜨렸나"**를 알 수 없습니다.
 * 날짜에 놓아야 비어 있는 자리가 보이고, 비어 있는 자리가 보여야 다음에 무엇을 돌아볼지
 * 정할 수 있습니다.
 *
 * 두 가지를 다르게 칠합니다. **돌린 날**(그날 돌아보기를 만들었음)과 **다뤄진 날**(어떤
 * 돌아보기가 그날을 기간에 품었음)입니다. 지난 한 주를 오늘 돌아봤다면 오늘은 돌린 날이고
 * 지난 이레는 다뤄진 날입니다. 섞어 칠하면 둘을 가릴 수 없습니다.
 */
@Composable
fun GrowthCalendarCard(
    reports: List<GrowthReportEntity>,
    month: YearMonth,
    selectedDate: LocalDate?,
    onMonthChanged: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now()
) {
    val calendar = remember(reports, month) { GrowthCalendar.month(reports, month) }
    val currentMonth = remember(today) { YearMonth.from(today) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(growthCalendarTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onMonthChanged(month.minusMonths(1)) },
                    modifier = Modifier.testTag(growthCalendarPrevTag)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "지난달")
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag(growthCalendarMonthTag),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${month.year}년 ${month.monthValue}월",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "돌아본 ${calendar.ranDays}일 · 다룬 ${calendar.coveredDays}일 · " +
                            "빈 ${calendar.untouchedDays}일",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                IconButton(
                    onClick = { onMonthChanged(month.plusMonths(1)) },
                    // 오지 않은 달에는 돌아볼 것이 없습니다.
                    enabled = month.isBefore(currentMonth),
                    modifier = Modifier.testTag(growthCalendarNextTag)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "다음 달")
                }
            }

            Legend()

            CalendarGrid(
                days = calendar.days,
                selectedDate = selectedDate,
                today = today,
                onDateSelected = onDateSelected
            )

            selectedDate?.let { date ->
                SelectedDay(
                    date = date,
                    reports = remember(reports, date) { GrowthCalendar.reportsOn(reports, date) }
                )
            }
        }
    }
}

/**
 * 무슨 색이 무슨 뜻인지.
 *
 * 색만 칠해 두면 아는 사람만 읽습니다. 두 가지를 가려 칠하는 것이 이 달력의 뜻이라
 * 그 뜻을 적어 둡니다.
 */
@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem(color = MaterialTheme.colorScheme.primary, label = "돌아본 날")
        LegendItem(
            color = MaterialTheme.colorScheme.secondaryContainer,
            label = "다뤄진 날"
        )
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalendarGrid(
    days: List<GrowthCalendarDay>,
    selectedDate: LocalDate?,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    // 일요일부터 시작하는 줄에 맞춥니다. 첫날 앞은 빈 칸입니다.
    val firstOffset = days.firstOrNull()?.date?.dayOfWeek?.value?.rem(7) ?: 0
    val cells = List<GrowthCalendarDay?>(firstOffset) { null } + days
    val padded = cells + List((7 - cells.size % 7) % 7) { null }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        padded.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(modifier = Modifier.weight(1f).heightIn(min = 40.dp))
                    } else {
                        DayCell(
                            day = day,
                            selected = day.date == selectedDate,
                            isToday = day.date == today,
                            onClick = { onDateSelected(day.date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: GrowthCalendarDay,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 다뤄진 날은 칸을 칠하고, 돌린 날은 그 위에 점을 찍습니다. 한 칸이 둘 다일 수 있어서
    // 서로 다른 방식으로 표시해야 겹쳐도 읽힙니다.
    val container = when {
        day.covered -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(onClick = onClick)
            .testTag(growthCalendarDayTag(day.date)),
        color = container,
        shape = RoundedCornerShape(8.dp),
        border = when {
            selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            else -> null
        }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${day.date.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (day.ran) FontWeight.Bold else FontWeight.Normal,
                color = if (day.covered) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            // 돌린 날에만 점이 찍힙니다. 자리는 늘 잡아 두어 칸 높이가 들쭉날쭉하지 않게 합니다.
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        color = if (day.ran) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * 고른 날에 매인 돌아보기.
 *
 * 칸을 눌러 놓고 아무것도 안 뜨면 무엇을 누른 것인지 알 수 없습니다. 그날 돌린 것과
 * 그날을 다룬 것을 함께 보여 줍니다.
 */
@Composable
private fun SelectedDay(
    date: LocalDate,
    reports: List<GrowthReportEntity>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(growthCalendarSelectedTag),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${date.monthValue}월 ${date.dayOfMonth}일",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        if (reports.isEmpty()) {
            Text(
                text = "이날은 아직 돌아보지 않았습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            reports.forEach { report ->
                Text(
                    text = "· ${ReportPeriod.of(report.period).label} 돌아보기 " +
                        "(${report.periodStart} ~ ${report.periodEnd})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
