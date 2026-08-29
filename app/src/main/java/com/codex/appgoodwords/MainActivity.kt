package com.codex.appgoodwords

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.codex.appgoodwords.ui.AppGoodWordsApp
import com.codex.appgoodwords.ui.AppViewModelFactory
import com.codex.appgoodwords.ui.MainViewModel
import com.codex.appgoodwords.work.AppNotifications

/**
 * [FragmentActivity]를 씁니다. 앱 잠금이 쓰는 BiometricPrompt가 그것을 요구합니다.
 * ComponentActivity의 기능은 그대로입니다(FragmentActivity가 그것을 물려받습니다).
 */
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels {
        AppViewModelFactory((application as AppGoodWordsApplication).container)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleIncomingIntent(intent)

        setContent {
            AppGoodWordsApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // 오래 비웠다 돌아오면 글귀를 새로 섞습니다. 화면을 돌린 것과 가르려고 onResume이 아니라
        // onStart에서 봅니다. 잠깐 뜨는 권한 창은 onStop까지 가지 않습니다.
        viewModel.onAppForegrounded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshConfirmedToday()
        viewModel.refreshRoutineToday()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val sharedText = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }

        val contentId = intent?.getLongExtra(AppNotifications.extraContentId, 0L) ?: 0L
        val markConfirmed = intent?.getBooleanExtra(AppNotifications.extraMarkConfirmed, false) ?: false
        val recordView = intent?.getBooleanExtra(AppNotifications.extraRecordView, true) ?: true

        viewModel.handleSharedText(sharedText)
        if (intent?.getBooleanExtra(AppNotifications.extraCaptureIdea, false) == true) {
            viewModel.handleCaptureIdeaRequest()
        }
        if (contentId > 0L) {
            viewModel.handleOpenItemRequest(
                itemId = contentId,
                markConfirmed = markConfirmed,
                recordView = recordView
            )
        }
    }
}
