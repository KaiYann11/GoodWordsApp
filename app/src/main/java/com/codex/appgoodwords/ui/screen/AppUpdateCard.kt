package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.AppUpdateState

internal const val appUpdateCheckTag = "app_update_check"
internal const val appUpdateDownloadTag = "app_update_download"
internal const val appUpdateInstallTag = "app_update_install"

/**
 * 설정의 "앱 업데이트".
 *
 * 예전에는 브라우저로 업데이트 페이지를 열어 내려받고 파일을 찾아 눌러야 했습니다. 그 길도
 * 그대로 두지만(서버 홈의 **앱 업데이트**), 앱 안에서도 세 번이면 끝나게 합니다 —
 * 확인 · 내려받기 · 설치.
 *
 * **설치는 안드로이드가 합니다.** 앱이 조용히 깔지 않습니다. 받아 둔 파일의 해시가 서버가 알려 준
 * 것과 같을 때만 설치 화면으로 넘기고([AppUpdateClient]), 그 화면에서 사용자가 정합니다.
 */
@Composable
fun AppUpdateCard(
    state: AppUpdateState,
    currentVersionName: String,
    currentVersionCode: Int,
    serverConfigured: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("앱 업데이트", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "지금 버전 $currentVersionName ($currentVersionCode)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (state) {
                is AppUpdateState.Idle -> {
                    Text(
                        text = if (serverConfigured) {
                            "서버에 새 버전이 올라와 있는지 확인합니다."
                        } else {
                            // 서버 주소가 없으면 받아 올 곳이 없습니다. 눌러 보고 나서 알게 하지 않습니다.
                            "서버 주소를 먼저 넣어야 확인할 수 있습니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is AppUpdateState.Checking -> {
                    Text("확인하는 중…", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is AppUpdateState.UpToDate -> {
                    Text(
                        text = "이미 최신입니다. 서버에 올라온 것도 ${state.versionName}입니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                is AppUpdateState.Available -> {
                    Text(
                        text = "새 버전 ${state.info.versionName} (${state.info.versionCode})이 있습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "내려받을 크기 ${state.info.sizeText}. 지금 기록은 그대로 남습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is AppUpdateState.Downloading -> {
                    Text(
                        text = "내려받는 중… ${(state.ratio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { state.ratio },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is AppUpdateState.Ready -> {
                    Text(
                        text = "${state.info.versionName} 설치 준비가 끝났습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        // 설치 화면이 무엇을 물을지 미리 알려 둡니다. 처음 보면 막히는 자리입니다.
                        text = "설치는 안드로이드가 묻습니다. 처음에는 \"이 출처의 앱 설치 허용\"을 한 번 켜 줘야 합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is AppUpdateState.Failed -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is AppUpdateState.Available -> {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.testTag(appUpdateDownloadTag)
                        ) { Text("내려받기") }
                        TextButton(onClick = onDismiss) { Text("나중에") }
                    }

                    is AppUpdateState.Ready -> {
                        Button(
                            onClick = { onInstall(state.filePath) },
                            modifier = Modifier.testTag(appUpdateInstallTag)
                        ) { Text("설치") }
                        TextButton(onClick = onDismiss) { Text("나중에") }
                    }

                    is AppUpdateState.Downloading -> Unit

                    else -> {
                        OutlinedButton(
                            onClick = onCheck,
                            enabled = serverConfigured && state !is AppUpdateState.Checking,
                            modifier = Modifier.testTag(appUpdateCheckTag)
                        ) { Text("업데이트 확인") }
                    }
                }
            }
        }
    }
}
