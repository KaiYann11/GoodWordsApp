package com.codex.appgoodwords.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.codex.appgoodwords.work.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject

data class AppImportResult(
    val itemCount: Int,
    val eventCount: Int,
    val routineCount: Int,
    val routineCheckCount: Int,
    val routineMemoCount: Int
)

class AppDataImporter(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler
) {
    suspend fun import(uri: Uri): AppImportResult {
        val jsonText = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("가져올 파일을 열 수 없습니다.")

        val payload = JSONObject(jsonText)
        val items = payload.optJSONArray("items").toContentItems()
        val events = payload.optJSONArray("exposureEvents").toExposureEvents()
        val routines = payload.optJSONArray("routines").toRoutines()
        val routineChecks = payload.optJSONArray("routineChecks").toRoutineChecks()
        val routineMemos = payload.optJSONArray("routineMemos").toRoutineMemos()
        val settings = payload.optJSONObject("settings").toReminderSettings()
            .copy(intervalMinutes = payload.optJSONObject("settings").toReminderSettings().effectiveIntervalMinutes)

        database.withTransaction {
            database.routineMemoDao().clearAll()
            database.routineCheckDao().clearAll()
            database.routineDao().clearAll()
            database.exposureEventDao().clearAll()
            database.contentItemDao().clearAll()

            if (items.isNotEmpty()) {
                database.contentItemDao().insertAll(items)
            }

            if (events.isNotEmpty()) {
                database.exposureEventDao().insertAll(events)
            }

            if (routines.isNotEmpty()) {
                database.routineDao().insertAll(routines)
            }

            if (routineChecks.isNotEmpty()) {
                database.routineCheckDao().insertAll(routineChecks)
            }

            if (routineMemos.isNotEmpty()) {
                database.routineMemoDao().insertAll(routineMemos)
            }
        }

        settingsStore.updateSettings(settings)
        reminderScheduler.sync(settings)

        return AppImportResult(
            itemCount = items.size,
            eventCount = events.size,
            routineCount = routines.size,
            routineCheckCount = routineChecks.size,
            routineMemoCount = routineMemos.size
        )
    }

    private fun JSONObject?.toReminderSettings(): ReminderSettings {
        if (this == null) return ReminderSettings()
        return ReminderSettings(
            remindersEnabled = optBoolean("remindersEnabled", true),
            intervalMinutes = optInt("intervalMinutes", ReminderSettings.MIN_INTERVAL_MINUTES),
            preferredHour = optInt("preferredHour", 9),
            preferredMinute = optInt("preferredMinute", 0),
            repeatEndHour = optInt("repeatEndHour", 22),
            repeatEndMinute = optInt("repeatEndMinute", 0),
            categoryFilter = optString("categoryFilter"),
            showOnLaunch = optBoolean("showOnLaunch", true),
            lockScreenVisible = optBoolean("lockScreenVisible", true),
            notificationSoundEnabled = optBoolean("notificationSoundEnabled", true),
            dailySummaryEnabled = optBoolean("dailySummaryEnabled", true),
            summaryHour = optInt("summaryHour", 21),
            summaryMinute = optInt("summaryMinute", 0)
        )
    }

    private fun JSONArray?.toContentItems(): List<ContentItemEntity> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    ContentItemEntity(
                        id = item.optLong("id", 0L),
                        type = item.optString("type").toEnumOrDefault(ContentType.QUOTE),
                        title = item.optString("title"),
                        body = item.optString("body"),
                        author = item.optString("author"),
                        sourceUrl = item.optString("sourceUrl"),
                        thumbnailUrl = item.optString("thumbnailUrl"),
                        category = item.optString("category"),
                        tags = item.optJSONArray("tags").toStringList(),
                        imageUris = item.optJSONArray("imageUris").toStringList(),
                        videoUris = item.optJSONArray("videoUris").toStringList(),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        lastShownAt = item.optNullableLong("lastShownAt"),
                        showCount = item.optInt("showCount", 0),
                        isFavorite = item.optBoolean("isFavorite", false)
                    )
                )
            }
        }
    }

    private fun JSONArray?.toExposureEvents(): List<ExposureEventEntity> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val event = optJSONObject(index) ?: continue
                add(
                    ExposureEventEntity(
                        id = event.optLong("id", 0L),
                        contentItemId = event.optLong("contentItemId", 0L),
                        contentTitle = event.optString("contentTitle"),
                        contentType = event.optString("contentType").toEnumOrDefault(ContentType.QUOTE),
                        eventType = event.optString("eventType").toEnumOrDefault(ExposureEventType.SHOWN),
                        trigger = event.optString("trigger").toEnumOrDefault(ExposureTrigger.MANUAL_REFRESH),
                        occurredAt = event.optLong("occurredAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun JSONArray?.toRoutines(): List<RoutineEntity> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val routine = optJSONObject(index) ?: continue
                add(
                    RoutineEntity(
                        id = routine.optLong("id", 0L),
                        title = routine.optString("title"),
                        note = routine.optString("note"),
                        category = routine.optString("category"),
                        reminderEnabled = routine.optBoolean("reminderEnabled", true),
                        createdAt = routine.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun JSONArray?.toRoutineChecks(): List<RoutineCheckEntity> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val check = optJSONObject(index) ?: continue
                add(
                    RoutineCheckEntity(
                        id = check.optLong("id", 0L),
                        routineId = check.optLong("routineId", 0L),
                        routineTitle = check.optString("routineTitle"),
                        checkedAt = check.optLong("checkedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun JSONArray?.toRoutineMemos(): List<RoutineMemoEntity> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val memo = optJSONObject(index) ?: continue
                val routineId = memo.optLong("routineId", 0L)
                val body = memo.optString("body").trim()
                if (routineId <= 0L || body.isBlank()) continue

                add(
                    RoutineMemoEntity(
                        id = memo.optLong("id", 0L),
                        routineId = routineId,
                        routineTitle = memo.optString("routineTitle"),
                        body = body,
                        createdAt = memo.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index)
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        return if (isNull(key)) null else optLong(key)
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T {
        return runCatching { enumValueOf<T>(this) }.getOrDefault(default)
    }
}
