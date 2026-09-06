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

    /**
     * 화면보다 오래 사는 일에 씁니다.
     *
     * 공유로 바로 담는 [ShareTargetActivity]는 곧바로 사라지므로, 화면에 매인 코루틴에 걸면
     * 저장이 도중에 끊깁니다.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppNotifications.createNotificationChannels(this)

        applicationScope.launch {
            container.repository.seedDefaultsIfNeeded()
            container.reminderScheduler.sync(container.settingsStore.getSettings())
        }
    }
}

