package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.RoutineStatsCalculator
import com.codex.appgoodwords.data.RoutineStatsReport
import java.time.DayOfWeek
import java.time.LocalDate

internal const val routineStatsListTag = "routine_stats_list"

/** 며칠째인지. 숫자가 굴러 올라가므로 글자만으로 짚기 어렵습니다. */
internal const val routineStatsStreakTag = "routine_stats_streak"

/** 요일 막대 하나. 숫자만으로는 어느 칸인지 짚기 어렵습니다. */
internal fun routineStatsWeekdayTag(day: DayOfWeek) = "routine_stats_weekday_${day.name}"

/** 달력과 막대가 같은 말을 쓰도록 요일 이름은 여기 한 곳에서 정합니다. */
internal fun weekdayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.SUNDAY -> "일"
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
}

/** 최근 몇 주를 격자로 보여 줄지. 넉 주면 요일의 버릇이 눈에 들어오고 한 화면에 들어갑니다. */
private const val RECENT_WEEK_COUNT = 4

/**
 * 루틴 하나를 지금까지 어떻게 밟아 왔는지.
 *
 * 루틴 카드에는 "오늘 몇 회"만 있습니다. 그것만으로는 **며칠째 이어 오고 있는지**와
 * **어느 요일에 잘 밟는지**가 어디에도 보이지 않습니다. 쌓아 온 것이 보이지 않으면
 * 하루 거른 날 그만두게 됩니다.
 *
 * 숫자는 저장하지 않고 체크 기록에서 그때그때 셉니다([RoutineStatsCalculator]).
 * 체크는 처음부터 날짜와 함께 쌓여 왔으므로, 지난 기록까지 거슬러 그대로 나옵니다.
 *
 * **못 한 날을 세지 않습니다.** 빠진 요일을 짚어 주거나 달성률을 매기면, 돌아보는 자리가
 * 성적표가 됩니다. 여기서 말하는 것은 한 일뿐입니다.
 */
@Composable
fun RoutineStatsScreen(
    routine: RoutineEntity,
    report: RoutineStatsReport,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(routineStatsListTag),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StreakCard(routine = routine, report = report, today = today) }

        if (report.isEmpty) {
            item {
                EmptyCard(
                    title = "아직 밟은 기록이 없습니다.",
                    body = "루틴 목록에서 수행 +1을 누르면 그날부터 쌓입니다. " +
                        "며칠째 이어 오는지와 어느 요일에 많이 밟는지가 여기에 보입니다."
                )
            }
        } else {
            item { WeekdayCard(report = report) }
            item { RecentWeeksCard(report = report, today = today) }
        }
    }
}

/** 맨 위. 지금 며칠째인지가 이 화면에서 가장 먼저 읽혀야 할 숫자입니다. */
@Composable
private fun StreakCard(routine: RoutineEntity, report: RoutineStatsReport, today: LocalDate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(routine.title, style = MaterialTheme.typography.titleMedium)
            if (routine.category.isNotBlank()) {
                Text(
                    text = routine.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (report.currentStreakDays > 0) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 오르는 것이 보람인 숫자라 굴립니다. 목록 개수에는 쓰지 않습니다.
                    CountUpNumber(
                        value = report.currentStreakDays,
                        modifier = Modifier.testTag(routineStatsStreakTag),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        suffix = "일"
                    )
                    Text(
                        text = "연속",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            } else if (!report.isEmpty) {
                // 0일 연속이라고 적지 않습니다. 끊긴 날수를 들이미는 것은 다그치는 일입니다.
                Text(
                    text = "오늘 밟으면 다시 1일째가 됩니다.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!report.isEmpty) {
                Text(
                    text = "최고 ${report.bestStreakDays}일 연속 · 실천한 날 ${report.doneDays}일 · " +
                        "모두 ${report.totalChecks}회",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = buildString {
                        report.firstDoneDate?.let { append("처음 ${dateLabel(it, today)}") }
                        report.lastDoneDate?.let { append(" · 마지막 ${dateLabel(it, today)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 요일별로 며칠 밟았는지. "무슨 요일에 많이 하나"가 이 카드의 물음입니다. */
@Composable
private fun WeekdayCard(report: RoutineStatsReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("요일별", style = MaterialTheme.typography.titleMedium)

            val busiest = report.busiestWeekdayDays
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                report.weekdays.forEach { weekday ->
                    val height = if (busiest <= 0) {
                        MIN_WEEKDAY_BAR
                    } else {
                        MIN_WEEKDAY_BAR + (MAX_WEEKDAY_BAR - MIN_WEEKDAY_BAR) * weekday.days / busiest
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.testTag(routineStatsWeekdayTag(weekday.dayOfWeek))
                    ) {
                        Text(
                            // 0은 적지 않습니다. 일곱 칸에 0이 늘어서면 안 한 것만 눈에 들어옵니다.
                            text = if (weekday.days > 0) "${weekday.days}일" else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(height.dp)
                                .background(
                                    color = if (weekday.days > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                        Text(
                            text = weekdayLabel(weekday.dayOfWeek),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = report.topWeekday?.let { day ->
                    "${weekdayLabel(day)}요일에 가장 많이 밟았습니다."
                // 으뜸이 둘 이상이거나 아직 한 번씩뿐이면 어느 요일이라 말하지 않습니다.
                } ?: "아직 요일이 갈리지 않았습니다. 조금 더 쌓이면 여기에 보입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 최근 넉 주. 칠해진 칸이 밟은 날입니다. 이어진 줄이 보이면 하루 더 밟게 됩니다. */
@Composable
private fun RecentWeeksCard(report: RoutineStatsReport, today: LocalDate) {
    // 이번 주의 남은 날까지 한 줄에 담아야 요일 칸이 세로로 줄을 맞춥니다.
    val endOfWeek = today.plusDays((6 - RoutineStatsCalculator.WEEK_ORDER.indexOf(today.dayOfWeek)).toLong())
    val start = endOfWeek.minusDays((RECENT_WEEK_COUNT * 7 - 1).toLong())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("최근 ${RECENT_WEEK_COUNT}주", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoutineStatsCalculator.WEEK_ORDER.forEach { day ->
                    Text(
                        text = weekdayLabel(day),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            (0 until RECENT_WEEK_COUNT).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 7).forEach { column ->
                        val date = start.plusDays((week * 7 + column).toLong())
                        RecentDayCell(
                            done = date in report.doneDates,
                            // 아직 오지 않은 날은 빈칸입니다. 옅게라도 칠하면 못 한 날처럼 보이고,
                            // 빈칸으로 두면 마지막으로 보이는 칸이 곧 오늘이라 따로 짚을 것도 없습니다.
                            future = date.isAfter(today),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(
                text = "칠해진 칸이 밟은 날입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentDayCell(
    done: Boolean,
    future: Boolean,
    modifier: Modifier = Modifier
) {
    val color = when {
        // 카드와 같은 색이라 칸이 아예 없는 것처럼 보입니다.
        future -> MaterialTheme.colorScheme.surface
        done -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(color = color, shape = RoundedCornerShape(6.dp))
    )
}

private const val MIN_WEEKDAY_BAR = 4
private const val MAX_WEEKDAY_BAR = 56

/** 오늘·어제는 날짜 대신 그렇게 부릅니다. "10월 15일"보다 "어제"가 먼저 읽힙니다. */
internal fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "오늘"
    today.minusDays(1) -> "어제"
    else -> "${date.monthValue}월 ${date.dayOfMonth}일"
}
