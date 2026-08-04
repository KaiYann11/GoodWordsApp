package com.codex.appgoodwords.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AppDataSnapshot(
    val items: List<ContentItemEntity>,
    val events: List<ExposureEventEntity>,
    val routines: List<RoutineEntity>,
    val routineChecks: List<RoutineCheckEntity>,
    val routineMemos: List<RoutineMemoEntity>,
    val settings: ReminderSettings,
    /** 설정은 레코드가 아니라 한 덩어리여서 마지막으로 손댄 시각으로 승자를 정한다. */
    val settingsUpdatedAt: Long = 0L,
    val deletions: List<DeletionEntity> = emptyList()
)

object AppDataJson {
    const val schemaVersion: Int = 8

    fun toJson(snapshot: AppDataSnapshot): JSONObject = JSONObject()
        .put("appName", "오늘의 글귀")
        .put("schemaVersion", schemaVersion)
        .put("exportedAt", formatTimestamp(System.currentTimeMillis()))
        .put("itemCount", snapshot.items.size)
        .put("eventCount", snapshot.events.size)
        .put("routineCount", snapshot.routines.size)
        .put("routineCheckCount", snapshot.routineChecks.size)
        .put("routineMemoCount", snapshot.routineMemos.size)
        .put("settings", snapshot.settings.toJson())
        .put("settingsUpdatedAt", snapshot.settingsUpdatedAt)
        .put(
            "deletions",
            JSONArray().apply {
                snapshot.deletions.forEach { deletion -> put(deletion.toJson()) }
            }
        )
        .put(
            "items",
            JSONArray().apply {
                snapshot.items.forEach { item -> put(item.toJson()) }
            }
        )
        .put(
            "exposureEvents",
            JSONArray().apply {
                snapshot.events.forEach { event -> put(event.toJson()) }
            }
        )
        .put(
            "routines",
            JSONArray().apply {
                snapshot.routines.forEach { routine -> put(routine.toJson()) }
            }
        )
        .put(
            "routineChecks",
            JSONArray().apply {
                snapshot.routineChecks.forEach { check -> put(check.toJson()) }
            }
        )
        .put(
            "routineMemos",
            JSONArray().apply {
                snapshot.routineMemos.forEach { memo -> put(memo.toJson()) }
            }
        )

    fun fromJsonText(jsonText: String): AppDataSnapshot {
        val payload = JSONObject(jsonText)
        val settings = payload.optJSONObject("settings").toReminderSettings()
            .let { it.copy(intervalMinutes = it.effectiveIntervalMinutes) }
        return AppDataSnapshot(
            items = payload.optJSONArray("items").toContentItems(),
            events = payload.optJSONArray("exposureEvents").toExposureEvents(),
            routines = payload.optJSONArray("routines").toRoutines(),
            routineChecks = payload.optJSONArray("routineChecks").toRoutineChecks(),
            routineMemos = payload.optJSONArray("routineMemos").toRoutineMemos(),
            settings = settings,
            settingsUpdatedAt = payload.optLong("settingsUpdatedAt", 0L),
            deletions = payload.optJSONArray("deletions").toDeletions()
        )
    }

    private fun DeletionEntity.toJson(): JSONObject = JSONObject()
        .put("syncId", syncId)
        .put("entityType", entityType.name)
        .put("deletedAt", deletedAt)
        .put("deletedAtText", formatTimestamp(deletedAt))

    private fun JSONArray?.toDeletions(): List<DeletionEntity> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val deletion = optJSONObject(index) ?: continue
                val syncId = deletion.optString("syncId").trim()
                val entityType = SyncEntityType.fromNameOrNull(deletion.optString("entityType"))
                if (syncId.isBlank() || entityType == null) continue

                add(
                    DeletionEntity(
                        syncId = syncId,
                        entityType = entityType,
                        deletedAt = deletion.optLong("deletedAt", 0L)
                    )
                )
            }
        }
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
        .put("syncId", syncId)
        .put("updatedAt", updatedAt)
        .put("lastSurfacedAt", lastSurfacedAt ?: JSONObject.NULL)
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
        .put("syncId", syncId)
        .put("contentItemId", contentItemId)
        .put("contentTitle", contentTitle)
        .put("contentType", contentType.name)
        .put("eventType", eventType.name)
        .put("trigger", trigger.name)
        .put("occurredAt", occurredAt)
        .put("occurredAtText", formatTimestamp(occurredAt))

    private fun RoutineEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("syncId", syncId)
        .put("updatedAt", updatedAt)
        .put("title", title)
        .put("note", note)
        .put("category", category)
        .put("reminderEnabled", reminderEnabled)
        .put("createdAt", createdAt)
        .put("createdAtText", formatTimestamp(createdAt))

    private fun RoutineCheckEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("syncId", syncId)
        .put("routineId", routineId)
        .put("routineTitle", routineTitle)
        .put("checkedAt", checkedAt)
        .put("checkedAtText", formatTimestamp(checkedAt))

    private fun RoutineMemoEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("syncId", syncId)
        .put("updatedAt", updatedAt)
        .put("routineId", routineId)
        .put("routineTitle", routineTitle)
        .put("body", body)
        .put("createdAt", createdAt)
        .put("createdAtText", formatTimestamp(createdAt))

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
                        // 구버전 파일이나 구버전 서버가 준 레코드는 syncId가 없어 새로 부여한다.
                        syncId = SyncIdentity.orNew(item.optString("syncId")),
                        updatedAt = item.optLong("updatedAt", 0L)
                            .takeIf { it > 0L }
                            ?: item.optLong("createdAt", 0L),
                        lastSurfacedAt = item.optNullableLong("lastSurfacedAt"),
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
                        syncId = SyncIdentity.orNew(event.optString("syncId")),
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
                        syncId = SyncIdentity.orNew(routine.optString("syncId")),
                        updatedAt = routine.optLong("updatedAt", 0L)
                            .takeIf { it > 0L }
                            ?: routine.optLong("createdAt", 0L),
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
                        syncId = SyncIdentity.orNew(check.optString("syncId")),
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
                        syncId = SyncIdentity.orNew(memo.optString("syncId")),
                        updatedAt = memo.optLong("updatedAt", 0L)
                            .takeIf { it > 0L }
                            ?: memo.optLong("createdAt", 0L),
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

    private fun formatTimestamp(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
