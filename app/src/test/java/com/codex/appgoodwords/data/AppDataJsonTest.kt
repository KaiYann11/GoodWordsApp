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
    fun roundTrip_preservesSyncFieldsAndDeletions() {
        val snapshot = fullSnapshot().copy(
            settingsUpdatedAt = 1_700_001_000_000L,
            deletions = listOf(
                DeletionEntity("gone-1", SyncEntityType.CONTENT_ITEM, 1_700_001_100_000L),
                DeletionEntity("gone-2", SyncEntityType.ROUTINE_MEMO, 1_700_001_200_000L)
            )
        )

        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertEquals("item-sync-1", restored.items.single().syncId)
        assertEquals(1_700_000_400_000L, restored.items.single().updatedAt)
        assertEquals(1_700_000_450_000L, restored.items.single().lastSurfacedAt)
        assertEquals("event-sync-1", restored.events.single().syncId)
        assertEquals("routine-sync-1", restored.routines.single().syncId)
        assertEquals("check-sync-1", restored.routineChecks.single().syncId)
        assertEquals("memo-sync-1", restored.routineMemos.single().syncId)
        assertEquals(snapshot.settingsUpdatedAt, restored.settingsUpdatedAt)
        assertEquals(snapshot.deletions, restored.deletions)
    }

    @Test
    fun fromJsonText_legacyRecordsGetSyncIdsAndFallBackToCreatedAt() {
        // 구버전 내보내기 파일에는 syncId와 updatedAt이 없다.
        val json = """
            {"items": [{"id": 1, "type": "QUOTE", "title": "t", "body": "b", "createdAt": 1700000000000}]}
        """.trimIndent()

        val restored = AppDataJson.fromJsonText(json)

        val item = restored.items.single()
        assertTrue("syncId가 새로 부여되어야 합니다.", item.syncId.isNotBlank())
        assertEquals(1_700_000_000_000L, item.updatedAt)
        assertNull(item.lastSurfacedAt)
    }

    @Test
    fun fromJsonText_skipsDeletionsMissingIdOrType() {
        val json = """
            {"deletions": [
              {"syncId": "ok", "entityType": "CONTENT_ITEM", "deletedAt": 10},
              {"syncId": "", "entityType": "CONTENT_ITEM", "deletedAt": 11},
              {"syncId": "bad-type", "entityType": "WHAT_IS_THIS", "deletedAt": 12}
            ]}
        """.trimIndent()

        val restored = AppDataJson.fromJsonText(json)

        assertEquals(listOf("ok"), restored.deletions.map { it.syncId })
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
                // syncId/updatedAt 기본값은 랜덤·현재시각이라 픽스처에서는 고정해야 비교가 성립한다.
                syncId = "item-sync-1",
                updatedAt = 1_700_000_400_000L,
                lastSurfacedAt = 1_700_000_450_000L,
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
                syncId = "event-sync-1",
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
                syncId = "routine-sync-1",
                updatedAt = 1_700_000_750_000L,
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
                syncId = "check-sync-1",
                routineId = 31L,
                routineTitle = "아침 스트레칭",
                checkedAt = 1_700_000_800_000L
            )
        ),
        routineMemos = listOf(
            RoutineMemoEntity(
                id = 51L,
                syncId = "memo-sync-1",
                updatedAt = 1_700_000_950_000L,
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
