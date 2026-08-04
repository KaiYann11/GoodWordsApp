package com.codex.appgoodwords.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppDataJson은 파일 내보내기/가져오기와 서버 동기화가 공유하는 전송 포맷입니다.
 * 여기서 깨지면 동기화가 데이터를 조용히 잃으므로 왕복 보존과 방어적 파싱을 함께 확인합니다.
 */
class AppDataJsonTest {
    @Test
    fun roundTrip_preservesEveryField() {
        val original = fullSnapshot()

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(original).toString())

        assertEquals(original.items, restored.items)
        assertEquals(original.events, restored.events)
        assertEquals(original.routines, restored.routines)
        assertEquals(original.routineChecks, restored.routineChecks)
        assertEquals(original.routineMemos, restored.routineMemos)
        assertEquals(original.settings, restored.settings)
    }

    @Test
    fun roundTrip_keepsNullLastShownAtNull() {
        val snapshot = fullSnapshot().let { base ->
            base.copy(items = listOf(base.items.first().copy(lastShownAt = null)))
        }

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertNull(restored.items.single().lastShownAt)
    }

    @Test
    fun roundTrip_keepsKoreanTextAndControlCharacters() {
        val snapshot = fullSnapshot().let { base ->
            base.copy(
                items = listOf(
                    base.items.first().copy(
                        title = "따옴표 \" 와 역슬래시 \\ 그리고 줄바꿈",
                        body = "첫 줄\n둘째 줄\t탭",
                        tags = listOf("한글 태그", "emoji 🌱")
                    )
                )
            )
        }

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertEquals(snapshot.items.single(), restored.items.single())
    }

    @Test
    fun roundTrip_handlesEmptySnapshot() {
        val empty = AppDataSnapshot(
            items = emptyList(),
            events = emptyList(),
            routines = emptyList(),
            routineChecks = emptyList(),
            routineMemos = emptyList(),
            settings = ReminderSettings()
        )

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(empty).toString())

        assertEquals(empty, restored)
    }

    @Test
    fun toJson_writesSchemaVersionAndCounts() {
        val snapshot = fullSnapshot()

        val payload = AppDataJson.toJson(snapshot)

        assertEquals(AppDataJson.schemaVersion, payload.getInt("schemaVersion"))
        assertEquals(snapshot.items.size, payload.getInt("itemCount"))
        assertEquals(snapshot.events.size, payload.getInt("eventCount"))
        assertEquals(snapshot.routines.size, payload.getInt("routineCount"))
        assertEquals(snapshot.routineChecks.size, payload.getInt("routineCheckCount"))
        assertEquals(snapshot.routineMemos.size, payload.getInt("routineMemoCount"))
    }

    @Test
    fun fromJsonText_missingSectionsBecomeEmptyWithDefaultSettings() {
        val restored = AppDataJson.fromJsonText("""{"schemaVersion":7}""")

        assertTrue(restored.items.isEmpty())
        assertTrue(restored.events.isEmpty())
        assertTrue(restored.routines.isEmpty())
        assertTrue(restored.routineChecks.isEmpty())
        assertTrue(restored.routineMemos.isEmpty())
        assertEquals(ReminderSettings(), restored.settings)
    }

    @Test
    fun fromJsonText_unknownEnumValuesFallBackToDefaults() {
        val json = """
            {
              "items": [{"id": 1, "type": "SOMETHING_NEW", "title": "t", "body": "b"}],
              "exposureEvents": [{
                "id": 1, "contentItemId": 1, "contentTitle": "t",
                "contentType": "?", "eventType": "?", "trigger": "?", "occurredAt": 10
              }]
            }
        """.trimIndent()

        val restored = AppDataJson.fromJsonText(json)

        assertEquals(ContentType.QUOTE, restored.items.single().type)
        val event = restored.events.single()
        assertEquals(ContentType.QUOTE, event.contentType)
        assertEquals(ExposureEventType.SHOWN, event.eventType)
        assertEquals(ExposureTrigger.MANUAL_REFRESH, event.trigger)
    }

    @Test
    fun fromJsonText_dropsBlankTagEntries() {
        val json = """
            {"items": [{"id": 1, "type": "QUOTE", "title": "t", "body": "b", "tags": ["보관", "", "  "]}]}
        """.trimIndent()

        val restored = AppDataJson.fromJsonText(json)

        assertEquals(listOf("보관"), restored.items.single().tags)
    }

    @Test
    fun fromJsonText_dropsMemosWithoutRoutineOrBody() {
        val json = """
            {"routineMemos": [
              {"id": 1, "routineId": 7, "routineTitle": "r", "body": "남길 메모", "createdAt": 10},
              {"id": 2, "routineId": 0, "routineTitle": "r", "body": "루틴 없음", "createdAt": 11},
              {"id": 3, "routineId": 7, "routineTitle": "r", "body": "   ", "createdAt": 12}
            ]}
        """.trimIndent()

        val restored = AppDataJson.fromJsonText(json)

        assertEquals(listOf(1L), restored.routineMemos.map { it.id })
    }

    @Test
    fun fromJsonText_coercesIntervalBelowMinimum() {
        val json = """{"settings": {"intervalMinutes": 1}}"""

        val restored = AppDataJson.fromJsonText(json)

        assertEquals(ReminderSettings.MIN_INTERVAL_MINUTES, restored.settings.intervalMinutes)
    }

    @Test
    fun fromJsonText_acceptsPayloadProducedByServerFieldOrder() {
        // 서버는 같은 스키마를 직접 만들어 내려주므로 키 순서나 추가 키가 달라도 읽혀야 한다.
        val payload = JSONObject(AppDataJson.toJson(fullSnapshot()).toString())
            .put("appName", "오늘의 글귀")
            .put("serverOnlyField", "무시되어야 함")

        val restored = AppDataJson.fromJsonText(payload.toString())

        assertEquals(fullSnapshot().items, restored.items)
    }

    private fun fullSnapshot(): AppDataSnapshot = AppDataSnapshot(
        items = listOf(
            ContentItemEntity(
                id = 11L,
                type = ContentType.LINK,
                title = "기록하는 습관",
                body = "매일 한 문장씩 남긴다.",
                author = "작자 미상",
                sourceUrl = "https://example.com/a?b=1&c=2",
                thumbnailUrl = "https://example.com/thumb.png",
                category = "동기부여",
                tags = listOf("습관", "기록"),
                imageUris = listOf("content://images/1"),
                videoUris = listOf("content://videos/1"),
                createdAt = 1_700_000_000_000L,
                lastShownAt = 1_700_000_500_000L,
                showCount = 3,
                isFavorite = true
            )
        ),
        events = listOf(
            ExposureEventEntity(
                id = 21L,
                contentItemId = 11L,
                contentTitle = "기록하는 습관",
                contentType = ContentType.LINK,
                eventType = ExposureEventType.CONFIRMED,
                trigger = ExposureTrigger.NOTIFICATION_TAP,
                occurredAt = 1_700_000_600_000L
            )
        ),
        routines = listOf(
            RoutineEntity(
                id = 31L,
                title = "아침 스트레칭",
                note = "10분",
                category = "건강",
                reminderEnabled = false,
                createdAt = 1_700_000_700_000L
            )
        ),
        routineChecks = listOf(
            RoutineCheckEntity(
                id = 41L,
                routineId = 31L,
                routineTitle = "아침 스트레칭",
                checkedAt = 1_700_000_800_000L
            )
        ),
        routineMemos = listOf(
            RoutineMemoEntity(
                id = 51L,
                routineId = 31L,
                routineTitle = "아침 스트레칭",
                body = "허리가 한결 편해졌다.",
                createdAt = 1_700_000_900_000L
            )
        ),
        settings = ReminderSettings(
            remindersEnabled = false,
            intervalMinutes = 90,
            preferredHour = 7,
            preferredMinute = 30,
            repeatEndHour = 23,
            repeatEndMinute = 15,
            categoryFilter = "동기부여",
            showOnLaunch = false,
            lockScreenVisible = false,
            notificationSoundEnabled = false,
            dailySummaryEnabled = false,
            summaryHour = 20,
            summaryMinute = 45
        )
    )
}
