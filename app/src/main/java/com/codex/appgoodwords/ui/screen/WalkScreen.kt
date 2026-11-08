package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.HealthConnection
import com.codex.appgoodwords.data.StepCountEntity
import com.codex.appgoodwords.data.WalkEntity
import com.codex.appgoodwords.data.WalkPath
import com.codex.appgoodwords.data.WalkRecords
import com.codex.appgoodwords.data.Walking
import com.codex.appgoodwords.ui.theme.Sky
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

internal const val walkScreenTag = "walk_screen"
internal const val walkListRowTagPrefix = "walk_row_"
internal const val walkDeleteTag = "walk_delete"

/**
 * 걷기 화면. 홈 걷기 카드의 "지난 걷기"·"지도 보기", 하루의 걸음 "걷기", 기록 알림에서 엽니다.
 *
 * 위에서부터 오늘 걸음 수, 최근 이레의 걸음, 걷는 중이면 지금 걷는 길(아니면 시작 단추), 지난 걷기입니다.
 * 하나를 누르면 그 길을 지도에 크게 펼칩니다([WalkDetail]).
 */
@Composable
fun WalkScreen(
    walks: List<WalkEntity>,
    stepCounts: List<StepCountEntity>,
    goalSteps: Int,
    connection: HealthConnection,
    recording: Boolean,
    onConnectHealth: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenWalk: (String) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val records = WalkRecords(stepCounts = stepCounts, walks = walks, goalSteps = goalSteps)
    val stepsByDate = Walking.stepsByDate(records)
    val walkedDays = Walking.walkedDays(records, zoneId)
    val ongoing = walks.firstOrNull { !it.isFinished }
    val finished = walks.filter { it.isFinished }.sortedByDescending { it.startedAt }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(walkScreenTag),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "today") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepCountLine(todaySteps = stepsByDate[today], connection = connection, onConnectHealth = onConnectHealth)
                if (connection == HealthConnection.CONNECTED) {
                    RecentDays(today = today, stepsByDate = stepsByDate, walkedDays = walkedDays)
                    Text(
                        text = "${Walking.formatSteps(goalSteps)}을 넘었거나 걷기를 기록한 날이 걸은 날입니다. 설정 → 오늘의 걸음에서 바꿉니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "live") {
            if (recording || ongoing != null) {
                LiveWalk(walk = ongoing, onStop = onStop)
            } else {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = Sky),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(walkStartTag)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" 걷기 기록 시작")
                }
            }
        }

        if (finished.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "아직 기록한 걷기가 없습니다. 걷기 기록 시작을 누르면 걷는 동안 지나온 길이 지도에 남습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item(key = "past-title") {
                Text("지난 걷기", style = MaterialTheme.typography.titleMedium)
            }
            items(finished, key = { it.syncId }) { walk ->
                WalkRow(walk = walk, today = today, zoneId = zoneId, onOpen = { onOpenWalk(walk.syncId) })
            }
        }
    }
}

/** 최근 이레의 걸음. 걸은 날은 하늘색으로 적습니다(못 걸은 날을 붉게 세지 않습니다). */
@Composable
private fun RecentDays(today: LocalDate, stepsByDate: Map<LocalDate, Int>, walkedDays: Set<LocalDate>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (6 downTo 0).forEach { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val steps = stepsByDate[date]
            val walked = date in walkedDays
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = steps?.let { compactSteps(it) } ?: "·",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (walked) FontWeight.Bold else FontWeight.Normal,
                    color = if (walked) Sky else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** `6.2천`처럼 줄여 적습니다. 일곱 칸이 한 줄에 들어가야 합니다. */
private fun compactSteps(steps: Int): String =
    if (steps < 1_000) steps.toString() else String.format(Locale.KOREA, "%.1f천", steps / 1_000.0)

@Composable
private fun LiveWalk(walk: WalkEntity?, onStop: () -> Unit) {
    val now by produceState(System.currentTimeMillis(), walk?.startedAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val points = remember(walk?.path) { WalkPath.decode(walk?.path.orEmpty()) }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("걷는 중", style = MaterialTheme.typography.titleMedium, color = Sky, fontWeight = FontWeight.Bold)
            WalkMap(
                points = points,
                followRoute = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(label = "거리", value = Walking.formatDistance(walk?.distanceMeters ?: 0))
                Stat(label = "시간", value = Walking.formatDuration(now - (walk?.startedAt ?: now)))
            }
            Text(
                text = "화면을 꺼도 기록은 이어집니다. 걸음 수는 끝낸 뒤 Health Connect에서 그 동안을 읽어 붙입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(walkStopTag)
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  끝내기")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WalkRow(walk: WalkEntity, today: LocalDate, zoneId: ZoneId, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag(walkListRowTagPrefix + walk.syncId)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "${walkDayLabel(walk.startedAt, today, zoneId)} ${walkClock(walk.startedAt, zoneId)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = walkNumbers(walk),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

/** `2.1km · 32분 · 3,120걸음`. 걸음 수를 모르면 뺍니다. */
internal fun walkNumbers(walk: WalkEntity): String = buildList {
    add(Walking.formatDistance(walk.distanceMeters))
    add(Walking.formatDuration(walk.durationMillis))
    if (walk.steps > 0) add(Walking.formatSteps(walk.steps))
}.joinToString(" · ")

/**
 * 걷기 하나를 지도에 크게 펼칩니다. 지우기는 한 번 더 묻습니다 — 지운 길은 되살릴 수 없습니다.
 */
@Composable
fun WalkDetail(
    walk: WalkEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val points = remember(walk.path) { WalkPath.decode(walk.path) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val date = Instant.ofEpochMilli(walk.startedAt).atZone(zoneId).toLocalDate()
        Text(
            text = "${date.monthValue}월 ${date.dayOfMonth}일 ${walkClock(walk.startedAt, zoneId)}–${walkClock(walk.endedAt, zoneId)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (points.isEmpty()) {
            Text(
                text = "이 걷기에는 지나온 길이 없습니다. 위치를 잡지 못한 채 끝냈습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            WalkMap(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(label = "거리", value = Walking.formatDistance(walk.distanceMeters))
            Stat(label = "시간", value = Walking.formatDuration(walk.durationMillis))
            if (walk.steps > 0) Stat(label = "걸음", value = String.format(Locale.KOREA, "%,d", walk.steps))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { confirming = true }, modifier = Modifier.testTag(walkDeleteTag)) {
            Text("이 걷기 지우기")
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("걷기를 지울까요?") },
            text = { Text("지나온 길과 거리가 이 휴대폰과 서버에서 지워집니다. 걸음 수(Health Connect)는 그대로입니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("그대로 두기") } }
        )
    }
}
