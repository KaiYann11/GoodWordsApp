package com.codex.appgoodwords.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AppDataExporter(
    private val context: Context
) {
    fun export(
        uri: Uri,
        items: List<ContentItemEntity>,
        events: List<ExposureEventEntity>,
        routines: List<RoutineEntity>,
        routineChecks: List<RoutineCheckEntity>,
        routineMemos: List<RoutineMemoEntity>,
        settings: ReminderSettings
    ): Int {
        val payload = JSONObject()
            .put("appName", "오늘의 글귀")
            .put("schemaVersion", 7)
            .put("exportedAt", formatTimestamp(System.currentTimeMillis()))
            .put("itemCount", items.size)
            .put("eventCount", events.size)
            .put("routineCount", routines.size)
            .put("routineCheckCount", routineChecks.size)
            .put("routineMemoCount", routineMemos.size)
            .put("settings", settings.toJson())
            .put(
                "items",
                JSONArray().apply {
                    items.forEach { item ->
                        put(item.toJson())
                    }
                }
            )
            .put(
                "exposureEvents",
                JSONArray().apply {
                    events.forEach { event ->
                        put(event.toJson())
                    }
                }
            )
            .put(
                "routines",
                JSONArray().apply {
                    routines.forEach { routine ->
                        put(routine.toJson())
                    }
                }
            )
            .put(
                "routineChecks",
                JSONArray().apply {
                    routineChecks.forEach { check ->
                        put(check.toJson())
                    }
                }
            )
            .put(
                "routineMemos",
                JSONArray().apply {
                    routineMemos.forEach { memo ->
                        put(memo.toJson())
                    }
                }
            )

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("내보내기 파일을 열 수 없습니다.")

        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(payload.toString(2))
        }

        return items.size + routines.size
    }

    private fun ReminderSettings.toJson(): JSONObject = JSONObject()
        .put("remindersEnabled", remindersEnabled)
        .put("intervalMinutes", intervalMinutes)
        .put("preferredHour", preferredHour)
        .put("preferredMinute", preferredMinute)
        .put("repeatEndHour", repeatEndHour)
        .put("repeatEndMinute", repeatEndMinute)
        .put("categoryFilter", categoryFilter)
        .put("showOnLaunch", showOnLaunch)
        .put("lockScreenVisible", lockScreenVisible)
        .put("notificationSoundEnabled", notificationSoundEnabled)
        .put("dailySummaryEnabled", dailySummaryEnabled)
        .put("summaryHour", summaryHour)
        .put("summaryMinute", summaryMinute)

    private fun ContentItemEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("title", title)
        .put("body", body)
        .put("author", author)
        .put("sourceUrl", sourceUrl)
        .put("thumbnailUrl", thumbnailUrl)
        .put("category", category)
        .put("tags", JSONArray(tags))
        .put("imageUris", JSONArray(imageUris))
        .put("videoUris", JSONArray(videoUris))
        .put("createdAt", createdAt)
        .put("createdAtText", formatTimestamp(createdAt))
        .put("lastShownAt", lastShownAt ?: JSONObject.NULL)
        .put("lastShownAtText", lastShownAt?.let(::formatTimestamp) ?: JSONObject.NULL)
        .put("showCount", showCount)
        .put("isFavorite", isFavorite)

    private fun ExposureEventEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("contentItemId", contentItemId)
        .put("contentTitle", contentTitle)
        .put("contentType", contentType.name)
        .put("eventType", eventType.name)
        .put("trigger", trigger.name)
        .put("occurredAt", occurredAt)
        .put("occurredAtText", formatTimestamp(occurredAt))

    private fun RoutineEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("note", note)
        .put("category", category)
        .put("reminderEnabled", reminderEnabled)
        .put("createdAt", createdAt)
        .put("createdAtText", formatTimestamp(createdAt))

    private fun RoutineCheckEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("routineId", routineId)
        .put("routineTitle", routineTitle)
        .put("checkedAt", checkedAt)
        .put("checkedAtText", formatTimestamp(checkedAt))

    private fun RoutineMemoEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("routineId", routineId)
        .put("routineTitle", routineTitle)
        .put("body", body)
        .put("createdAt", createdAt)
        .put("createdAtText", formatTimestamp(createdAt))

    private fun formatTimestamp(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
