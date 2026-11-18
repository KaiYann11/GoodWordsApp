package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DayClose
import com.codex.appgoodwords.data.DayDigest
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.RoutineEntity
import java.time.LocalDate

internal const val dayCloseScreenTag = "day_close_screen"
internal const val dayCloseCardTag = "day_close_card"
internal const val dayCloseCardOpenTag = "day_close_card_open"
internal const val dayCloseDoneLineTag = "day_close_done_line"
internal const val dayCloseGratitudeTag = "day_close_gratitude"
internal const val dayCloseTomorrowTag = "day_close_tomorrow"
internal const val dayCloseFinishTag = "day_close_finish"
internal fun dayCloseMoodTag(mood: DiaryMood): String = "day_close_mood_${mood.name}"
internal fun dayCloseCheckTag(routineId: Long): String = "day_close_check_$routineId"

/**
 * 하루 마무리(사용자가 정함, 2026-11-19 — 한 장에 네 칸). 오늘 한 것을 맨 위에 두고, 지금 기분 → 했는데 못 적은 루틴 → 오늘
 * 감사한 일 한 줄 → 내일 할 일 하나를 한 화면에서 채운 뒤 [마무리] 한 번으로 끝냅니다. 네 칸 모두 건너뛸 수 있습니다.
 *
 * - **기분과 루틴은 누르는 즉시 적힙니다** — 홈의 기분 카드·루틴 탭의 수행 +1과 같은 길입니다(되돌리기 스낵바도 같습니다).
 * - **감사 한 줄과 내일 할 일은 [마무리]를 누를 때** 오늘의 감사 일기와 내일 날짜의 할 일이 됩니다([DayClose]).
 *
 * 루틴 칸은 "했는데 못 적은 것"을 묻습니다 — 남은 개수를 세지 않고, 오늘 요일의 안 적은 루틴을 체크할 자리로만 늘어놓습니다
 * (루틴 탭의 "안 한 것"과 같은 목록). 오늘 할 루틴이 없는 날에는 칸이 없습니다.
 */
@Composable
fun DayCloseScreen(
    today: LocalDate,
    digest: DayDigest,
    /** 오늘의 기분(마지막 기록). 고른 칩이 이것입니다. */
    mood: DiaryMood?,
    /** 오늘 요일의 루틴 가운데 아직 안 적은 것([com.codex.appgoodwords.data.RoutineNudgeCalculator.restOfToday]). */
    restRoutines: List<RoutineEntity>,
    /** 오늘 요일에 하는 루틴이 하나라도 있는지. 없으면 루틴 칸을 두지 않습니다. */
    hasRoutinesToday: Boolean,
    onPickMood: (DiaryMood) -> Unit,
    onCheckRoutine: (RoutineEntity) -> Unit,
    /** 감사 한 줄과 내일 할 일 하나(비어 있을 수 있음). 저장과 마무리 표시는 부르는 쪽이 합니다. */
    onFinish: (gratitude: String, tomorrowTodo: String) -> Unit,
    modifier: Modifier = Modifier,
    /** 저장하는 중. 그 사이 두 번 눌려 감사 일기·할 일이 둘씩 생기지 않게 [마무리]를 잠급니다. */
    saving: Boolean = false
) {
    var gratitude by rememberSaveable { mutableStateOf("") }
    var tomorrowTodo by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 자판이 올라와도 창은 줄지 않습니다(AGENTS). 아래쪽 칸을 쓸 때 굴려서 볼 수 있게 비킵니다.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag(dayCloseScreenTag),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = DayClose.dateLabel(today),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = DayClose.doneLine(digest)?.let { "오늘 한 것  $it" } ?: "아직 적은 것이 없습니다. 지금 남겨도 됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(dayCloseDoneLineTag)
            )
        }

        CloseSection(number = 1, title = "지금 기분") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DiaryMood.entries) { choice ->
                    FilterChip(
                        selected = mood == choice,
                        onClick = { onPickMood(choice) },
                        label = { Text("${choice.emoji} ${choice.label}") },
                        modifier = Modifier.testTag(dayCloseMoodTag(choice))
                    )
                }
            }
        }

        if (hasRoutinesToday) {
            CloseSection(number = 2, title = "했는데 못 적은 루틴이 있나요?") {
                if (restRoutines.isEmpty()) {
                    Text(
                        text = "오늘 할 루틴을 모두 적었습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    restRoutines.forEach { routine ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = routine.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = { onCheckRoutine(routine) },
                                modifier = Modifier.testTag(dayCloseCheckTag(routine.id))
                            ) {
                                Text("수행 +1")
                            }
                        }
                    }
                }
            }
        }

        CloseSection(number = if (hasRoutinesToday) 3 else 2, title = "오늘 감사한 일 한 줄") {
            OutlinedTextField(
                value = gratitude,
                onValueChange = { gratitude = it },
                placeholder = { Text("오늘의 감사 일기로 남습니다") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(dayCloseGratitudeTag)
            )
        }

        CloseSection(number = if (hasRoutinesToday) 4 else 3, title = "내일 할 일 하나") {
            OutlinedTextField(
                value = tomorrowTodo,
                onValueChange = { tomorrowTodo = it },
                placeholder = { Text("내일 날짜의 할 일로 남습니다") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(dayCloseTomorrowTag)
            )
        }

        Button(
            enabled = !saving,
            onClick = { onFinish(gratitude, tomorrowTodo) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(dayCloseFinishTag)
        ) {
            Text("마무리")
        }
    }
}

/** 번호와 물음이 붙은 칸 하나. 홈 카드와 같은 모서리·여백입니다. */
@Composable
private fun CloseSection(number: Int, title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "$number  $title",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

/**
 * 홈 "오늘" 칸 맨 아래의 마무리 카드(사용자가 고름, 2026-11-19). 마무리 시각 뒤, 그날 아직 마무리하지 않았을 때만 있습니다 —
 * 낮에는 자리를 차지하지 않고, 마무리하면 그날은 사라집니다.
 */
@Composable
fun DayCloseCard(
    doneLine: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(dayCloseCardTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "하루 마무리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = doneLine?.let { "오늘 $it" } ?: "기분과 감사 한 줄, 내일 할 일 하나로 오늘을 닫습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onOpen,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag(dayCloseCardOpenTag)
            ) {
                Text("마무리하기 ›")
            }
        }
    }
}
