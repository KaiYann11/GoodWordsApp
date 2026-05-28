package com.codex.appgoodwords.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ExposureTrigger
import com.codex.appgoodwords.data.RoutineEntity
import kotlin.random.Random

class QuoteReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as AppGoodWordsApplication
        val settings = application.container.settingsStore.getSettings()

        if (!settings.remindersEnabled) {
            return Result.success()
        }

        if (!settings.isWithinReminderWindow()) {
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
