package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exposure_events",
    indices = [
        Index("occurredAt"),
        Index("contentItemId", "eventType", "occurredAt"),
        Index(value = ["syncId"], unique = true)
    ]
)
data class ExposureEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // 노출 이벤트는 기록 후 바뀌지 않으므로 updatedAt 없이 식별자만 둔다.
    val syncId: String = SyncIdentity.newId(),
    val contentItemId: Long,
    val contentTitle: String,
    val contentType: ContentType,
    val eventType: ExposureEventType,
    val trigger: ExposureTrigger,
    val occurredAt: Long = System.currentTimeMillis()
)

