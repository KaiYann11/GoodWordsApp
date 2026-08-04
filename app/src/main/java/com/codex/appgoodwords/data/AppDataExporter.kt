package com.codex.appgoodwords.data

import android.content.Context
import android.net.Uri

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
        val payload = AppDataJson.toJson(
            AppDataSnapshot(
                items = items,
                events = events,
                routines = routines,
                routineChecks = routineChecks,
                routineMemos = routineMemos,
                settings = settings
            )
        )

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("내보내기 파일을 열 수 없습니다.")

        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(payload.toString(2))
        }

        return items.size + routines.size
    }
}
