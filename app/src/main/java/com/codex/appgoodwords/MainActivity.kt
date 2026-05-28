package com.codex.appgoodwords

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.codex.appgoodwords.ui.AppGoodWordsApp
import com.codex.appgoodwords.ui.AppViewModelFactory
import com.codex.appgoodwords.ui.MainViewModel
import com.codex.appgoodwords.work.AppNotifications

class MainActivity : ComponentActivity() {
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

    override fun onResume() {
        super.onResume()
        viewModel.refreshConfirmedToday()
        viewModel.refreshRoutineToday()
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
        if (contentId > 0L) {
            viewModel.handleOpenItemRequest(
                itemId = contentId,
                markConfirmed = markConfirmed,
                recordView = recordView
            )
        }
    }
}
