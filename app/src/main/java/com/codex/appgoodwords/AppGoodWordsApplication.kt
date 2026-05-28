package com.codex.appgoodwords

import android.app.Application
import com.codex.appgoodwords.data.AppContainer
import com.codex.appgoodwords.work.AppNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppGoodWordsApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppNotifications.createNotificationChannels(this)

        applicationScope.launch {
            container.repository.seedDefaultsIfNeeded()
            container.reminderScheduler.sync(container.settingsStore.getSettings())
        }
    }
}

