package com.codex.appgoodwords.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.widget.QuoteWidget
import kotlin.random.Random

class QuoteReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        run(canPostNotifications = AppNotifications.canPostNotifications(applicationContext))

    /** 권한 확인만 밖에서 받습니다. 테스트에서 실제 권한을 뺏으면 앱 프로세스가 죽습니다. */
    internal suspend fun run(canPostNotifications: Boolean): Result {
        val application = applicationContext as AppGoodWordsApplication
        val settings = application.container.settingsStore.getSettings()

        if (!settings.remindersEnabled) {
            return Result.success()
        }

        if (!settings.isWithinReminderWindow()) {
            return Result.success()
        }

        // 알림 권한이 없으면 알림 함수가 조용히 돌아갑니다.
        // 그런데도 노출·읽음을 기록하면 보지도 않은 글귀가 읽음으로 쌓이고, 순환까지 앞서 나갑니다.
        // 위젯은 계속 돌려야 하므로 위젯 노출로만 남깁니다.
        if (!canPostNotifications) {
            QuoteWidget.pickNextItem(application.container)
            QuoteWidget().updateAll(applicationContext)
            return Result.success()
        }

        val item = application.container.repository.getRandomContent(settings.categoryFilter)
        val routine = application.container.repository.getRandomReminderRoutine()

        when (val target = pickReminderTarget(item, routine)) {
            is ReminderTarget.Content -> {
                application.container.repository.recordContentSurfaced(
                    item = target.item,
                    trigger = ExposureTrigger.REMINDER_NOTIFICATION
                )
                application.container.repository.recordContentViewed(
                    contentItemId = target.item.id,
                    trigger = ExposureTrigger.REMINDER_NOTIFICATION
                )
                AppNotifications.showContentNotification(
                    context = applicationContext,
                    item = target.item,
                    settings = settings,
                    countedAsViewedInNotification = true
                )
                // 위젯이 알림과 같은 글귀를 보여주도록 맞춘다. 별도 스케줄 없이
                // 사용자가 정한 반복 주기대로 위젯도 함께 돌아간다.
                application.container.settingsStore.setWidgetContentId(target.item.id)
                QuoteWidget().updateAll(applicationContext)
            }

            is ReminderTarget.Routine -> {
                val todayCount = application.container.repository.getTodayRoutineCheckCount(target.routine.id)
                AppNotifications.showRoutineNotification(
                    context = applicationContext,
                    routine = target.routine,
                    todayCount = todayCount,
                    settings = settings
                )
            }

            null -> return Result.success()
        }
        return Result.success()
    }

    private fun pickReminderTarget(
        item: ContentItemEntity?,
        routine: RoutineEntity?
    ): ReminderTarget? {
        return when {
            item != null && routine != null -> {
                if (Random.nextBoolean()) {
                    ReminderTarget.Content(item)
                } else {
                    ReminderTarget.Routine(routine)
                }
            }

            item != null -> ReminderTarget.Content(item)
            routine != null -> ReminderTarget.Routine(routine)
            else -> null
        }
    }

    private sealed interface ReminderTarget {
        data class Content(val item: ContentItemEntity) : ReminderTarget
        data class Routine(val routine: RoutineEntity) : ReminderTarget
    }
}
