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
    /**
     * 어떤 항목의 이벤트인지 기기와 무관하게 가리킨다.
     * contentItemId는 기기마다 따로 증가하는 값이라, 다른 기기의 이벤트를 받으면 엉뚱한 항목을 가리킨다.
     */
    val contentItemSyncId: String = "",
    val contentTitle: String,
    val contentType: ContentType,
    val eventType: ExposureEventType,
    val trigger: ExposureTrigger,
    val occurredAt: Long = System.currentTimeMillis()
)

