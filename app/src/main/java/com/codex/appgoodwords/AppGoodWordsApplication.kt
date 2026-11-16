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

    /**
     * 앱 화면이 지금 사람 앞에 떠 있는지. [MainActivity]가 앞으로 나오고 물러날 때 적습니다.
     *
     * 배경 일꾼이 알림을 띄울지 정할 때 봅니다([com.codex.appgoodwords.work.DailyCoachWorker]) —
     * 보고 있는 화면 위에 알림까지 겹치면 같은 말을 두 번 듣습니다.
     */
    @Volatile
    var mainScreenVisible: Boolean = false

    override fun onCreate() {
        super.onCreate()
        AppNotifications.createNotificationChannels(this)

        applicationScope.launch {
            container.repository.seedDefaultsIfNeeded()
            container.reminderScheduler.sync(container.settingsStore.getSettings())
        }

        // 시각을 정한 루틴의 알람(사용자가 정함, 2026-11-17). 앱이 떠 있는 동안 루틴·체크가 바뀔 때마다 다시 겁니다 —
        // 앱·위젯·알림에서 체크해도, 동기화·가져오기로 루틴이 바뀌어도 같은 자리에서 잡습니다.
        applicationScope.launch {
            container.routineAlarm.watch(container.database)
        }
    }
}

