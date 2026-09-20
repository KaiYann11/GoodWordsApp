package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun DiaryUnlockCard(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("일기가 잠겨 있습니다. 본문·사진과 일기가 포함된 백업·돌아보기를 보려면 확인해 주세요.")
            TextButton(onClick = onUnlock, modifier = Modifier.testTag("unlock_diary_access")) {
                Text("기기 잠금으로 확인")
            }
        }
    }
}
