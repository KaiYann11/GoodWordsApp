package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exposure_events",
    indices = [
        Index("occurredAt"),
        Index("contentItemId", "eventType", "occurredAt")
    ]
)
data class ExposureEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentItemId: Long,
    val contentTitle: String,
    val contentType: ContentType,
    val eventType: ExposureEventType,
    val trigger: ExposureTrigger,
    val occurredAt: Long = System.currentTimeMillis()
)

