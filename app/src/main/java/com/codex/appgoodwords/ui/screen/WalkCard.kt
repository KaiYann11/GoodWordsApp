package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.HealthConnection
import com.codex.appgoodwords.data.WalkEntity
import com.codex.appgoodwords.data.Walking
import com.codex.appgoodwords.ui.theme.Sky
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

internal const val walkCardTag = "walk_card"
internal const val walkStartTag = "walk_start"
internal const val walkStopTag = "walk_stop"
internal const val walkConnectTag = "walk_connect"
internal const val walkOpenTag = "walk_open"

/**
 * 홈 오늘 칸의 걷기 카드(사용자가 고름, 2026-11-07 — 오늘 칸, 걸음 카드 아래).
 *
 * 오늘 걸음 수와 걷기 기록 시작을 한자리에 둡니다. **모자란 걸음을 세지 않습니다** — 목표까지 남은 수나 비어 있는 막대를
 * 보이지 않고, 걸은 만큼과 걸은 날인지만 보입니다.
 */
@Composable
fun WalkCard(
    todaySteps: Int?,
    walkedToday: Boolean,
    connection: HealthConnection,
    /** 다 걸은 것 중 가장 최근 것. */
    latestWalk: WalkEntity?,
    /** 지금 걷는 중인 것. 기록하는 동안만 있습니다. */
    ongoingWalk: WalkEntity?,
    recording: Boolean,
    onConnectHealth: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenWalks: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(walkCardTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = Sky)
                Text("걷기", style = MaterialTheme.typography.titleMedium)
                if (walkedToday) {
                    Text("걸은 날", style = MaterialTheme.typography.labelMedium, color = Sky, fontWeight = FontWeight.Bold)
                }
            }

            StepCountLine(todaySteps = todaySteps, connection = connection, onConnectHealth = onConnectHealth)

            if (recording) {
                val now by produceState(System.currentTimeMillis(), ongoingWalk?.startedAt) {
                    while (true) {
                        value = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                val started = ongoingWalk?.startedAt ?: now
                Text(
                    text = "걷는 중 · ${Walking.formatDistance(ongoingWalk?.distanceMeters ?: 0)} · " +
                        Walking.formatDuration(now - started),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStop, modifier = Modifier.testTag(walkStopTag)) {
                        Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  끝내기")
                    }
                    OutlinedButton(onClick = onOpenWalks, modifier = Modifier.testTag(walkOpenTag)) { Text("지도 보기") }
                }
            } else {
                latestWalk?.let { walk ->
                    Text(
                        text = "${walkDayLabel(walk.startedAt)} ${walkClock(walk.startedAt)} · " +
                            "${Walking.formatDistance(walk.distanceMeters)} · ${Walking.formatDuration(walk.durationMillis)}  ›",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onOpenWalks)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Sky),
                        modifier = Modifier.testTag(walkStartTag)
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" 걷기 기록 시작")
                    }
                    OutlinedButton(onClick = onOpenWalks, modifier = Modifier.testTag(walkOpenTag)) { Text("지난 걷기") }
                }
            }
        }
    }
}

/** 오늘 걸음 수 줄. Health Connect 상태에 따라 수 대신 할 일을 알려 줍니다. */
@Composable
internal fun StepCountLine(todaySteps: Int?, connection: HealthConnection, onConnectHealth: () -> Unit) {
    when (connection) {
        HealthConnection.CONNECTED -> Text(
            text = todaySteps?.let { "오늘 ${Walking.formatSteps(it)}" } ?: "오늘 걸음은 아직 들어오지 않았어요",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        HealthConnection.NOT_CONNECTED -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "걸음 수는 Health Connect에서 가져옵니다. 삼성 헬스가 센 걸음이 그대로 보입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onConnectHealth, modifier = Modifier.testTag(walkConnectTag)) { Text("걸음 수 가져오기") }
        }
        HealthConnection.NEEDS_UPDATE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Health Connect를 깔거나 업데이트하면 걸음 수를 볼 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onConnectHealth, modifier = Modifier.testTag(walkConnectTag)) { Text("Health Connect 열기") }
        }
        HealthConnection.UNAVAILABLE -> Text(
            text = "이 휴대폰에서는 걸음 수를 읽을 수 없습니다. 걷기 기록은 쓸 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HealthConnection.UNKNOWN -> Unit
    }
}

/** `오늘`·`어제`·`11월 5일`. */
internal fun walkDayLabel(startedAt: Long, today: LocalDate = LocalDate.now(), zoneId: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(startedAt).atZone(zoneId).toLocalDate()
    return when (date) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> "${date.monthValue}월 ${date.dayOfMonth}일"
    }
}

internal fun walkClock(at: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(at).atZone(zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))
