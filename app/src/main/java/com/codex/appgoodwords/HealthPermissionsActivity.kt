package com.codex.appgoodwords

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme

/**
 * Health Connect의 권한 창에서 "개인정보처리방침"을 누르면 뜨는 화면입니다.
 *
 * Health Connect는 이 화면이 없는 앱에는 권한 창을 아예 띄우지 않습니다. 무엇을 왜 읽는지, 어디로 가는지만 적습니다.
 */
class HealthPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppGoodWordsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("걸음 수를 읽는 까닭", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "오늘의 글귀는 Health Connect에서 하루 걸음 수만 읽습니다. 쓰거나 고치지 않습니다.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "읽은 걸음 수는 홈의 걷기 카드와 하루 점수의 '걷기'에 씁니다. 정한 걸음 수를 넘은 날을 걸은 날로 셉니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "하루치 걸음 수는 이 휴대폰과, 설정에서 이어 둔 내 서버에만 남습니다. AI에는 숫자만 가고, 걷기 기록의 위치·경로는 " +
                                "보내지 않습니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "권한은 언제든 휴대폰 설정 → Health Connect에서 거둘 수 있습니다. 거두면 그때부터 걸음 수를 읽지 않습니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { finish() }) { Text("닫기") }
                    }
                }
            }
        }
    }
}
