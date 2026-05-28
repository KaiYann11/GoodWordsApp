package com.codex.appgoodwords.data

import java.time.LocalTime

data class ReminderSettings(
    val remindersEnabled: Boolean = true,
    val intervalMinutes: Int = 360,
    val preferredHour: Int = 9,
    val preferredMinute: Int = 0,
    val repeatEndHour: Int = 22,
    val repeatEndMinute: Int = 0,
    val categoryFilter: String = "",
    val showOnLaunch: Boolean = true,
    val lockScreenVisible: Boolean = true,
    val notificationSoundEnabled: Boolean = true,
    val dailySummaryEnabled: Boolean = true,
    val summaryHour: Int = 21,
    val summaryMinute: Int = 0
) {
    val effectiveIntervalMinutes: Int
        get() = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)

    fun isWithinReminderWindow(currentTime: LocalTime = LocalTime.now()): Boolean {
        val startMinutes = preferredHour * 60 + preferredMinute
        val endMinutes = repeatEndHour * 60 + repeatEndMinute
        val nowMinutes = currentTime.hour * 60 + currentTime.minute

        if (startMinutes == endMinutes) {
            return true
        }

        return if (startMinutes < endMinutes) {
            nowMinutes in startMinutes..endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes <= endMinutes
        }
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 15
    }
}
