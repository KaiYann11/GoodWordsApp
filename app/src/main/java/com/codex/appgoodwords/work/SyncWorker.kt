package com.codex.appgoodwords.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.SyncBackupKind

/**
 * 배경에서 서버와 병합합니다.
 *
 * 병합은 어느 쪽도 통째로 지우지 않으므로 사용자가 보지 않을 때 돌려도 안전합니다.
 * 그래도 실패는 화면에 뜨지 않으므로 결과를 설정에 남겨 설정 탭에서 확인할 수 있게 합니다.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AppGoodWordsApplication).container
        val settings = container.settingsStore.getServerSyncSettings()

        // 사용자가 껐거나 주소를 지웠으면 다음 예약을 붙잡고 있을 이유가 없다.
        if (!settings.canAutoSync) {
            return Result.success()
        }

        return runCatching { container.syncCoordinator.merge(SyncBackupKind.BEFORE_AUTO_MERGE) }
            .fold(
                onSuccess = { Result.success() },
                onFailure = {
                    // 서버가 잠깐 꺼져 있을 수 있으니 몇 번은 다시 시도한다.
                    // 계속 실패하면 다음 주기를 기다린다. 실패 사유는 설정 화면에 남아 있다.
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
                }
            )
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
