package com.codex.appgoodwords.ui.screen

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal const val appLockUnlockButtonTag = "app_lock_unlock"

/**
 * 앱 잠금.
 *
 * **비밀번호를 따로 만들지 않습니다.** 기기에 이미 있는 잠금(지문·PIN·패턴)을 빌려 씁니다.
 * 우리가 비밀번호를 보관하면 그것을 지키는 일이 새로 생기고, 잊었을 때 되찾아 줄 방법도 없습니다.
 */
object AppLock {
    private val authenticators: Int
        get() = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * 이 기기가 잠금을 걸 수 있는지.
     *
     * 기기에 잠금이 없으면 앱만 잠가 봐야 지킬 수 있는 것이 없습니다. 켤 수 없다고 말해 줍니다.
     */
    fun canLock(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * 잠금을 풀어 달라고 묻습니다.
     *
     * 취소나 실패는 [onFailed]로 옵니다. **거기서 앱을 닫지 않습니다.** 다시 시도할 자리를
     * 남겨 두지 않으면 사용자가 자기 기록에서 잠깁니다.
     */
    fun authenticate(
        activity: FragmentActivity,
        onUnlocked: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailed(errString.toString())
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("오늘의 글귀 잠금")
                .setSubtitle("기기 잠금으로 확인합니다.")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
}

/**
 * 잠긴 동안 대신 보이는 화면.
 *
 * 열자마자 한 번 물어보고, 취소했으면 다시 물어볼 버튼을 남깁니다.
 */
@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var message by remember { mutableStateOf("") }
    var asked by remember { mutableStateOf(false) }

    fun ask() {
        val host = activity ?: return
        message = ""
        AppLock.authenticate(
            activity = host,
            onUnlocked = onUnlocked,
            onFailed = { reason -> message = reason }
        )
    }

    // 열자마자 한 번 묻습니다. 버튼을 한 번 더 누르게 하면 매번 두 번 누르는 셈입니다.
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            ask()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "잠겨 있습니다",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "기기 잠금으로 확인하면 열립니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = { ask() },
            modifier = Modifier.testTag(appLockUnlockButtonTag)
        ) {
            Text("잠금 풀기")
        }
    }
}

/**
 * 잠금 화면을 띄울지 정하는 세 가지 상태.
 *
 * 설정을 읽는 동안([CHECKING])에는 아무것도 그리지 않습니다. 잠금을 켠 사람에게 내용이
 * 한순간 비치지도 않고, 켜지 않은 대다수가 잠금 화면 깜빡임을 보지도 않습니다.
 */
enum class AppLockState {
    CHECKING,
    LOCKED,
    OPEN
}
