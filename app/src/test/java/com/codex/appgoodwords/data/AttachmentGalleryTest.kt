package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 사진 모아 보기에 할 일의 사진도 들어옵니다(사용자가 정함, 2026-11-15).
 *
 * 누르면 그 할 일로 데려가야 하므로 어디에 붙은 사진인지(할 일과 그 번호)를 함께 들고 있어야 합니다.
 */
class AttachmentGalleryTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun aTodoPhotoComesWithItsTodoAndDueDay() {
        val shots = AttachmentGallery.collect(
            diaries = emptyList(),
            items = emptyList(),
            todos = listOf(
                TodoEntity(id = 4, syncId = "t1", title = "반품하기", dueDate = "2026-11-16", imageUris = listOf("content://photo/receipt"))
            ),
            zoneId = zone
        )

        val shot = shots.single()
        assertEquals(SearchKind.TODO, shot.source)
        assertEquals(4L, shot.sourceId)
        assertEquals(LocalDate.of(2026, 11, 16), shot.date)
        assertEquals("반품하기", shot.label)
        assertEquals(AttachmentKind.IMAGE, shot.kind)
    }

    @Test
    fun aSomedayTodoPhotoSitsOnTheDayItWasMade() {
        // 날짜 없는 할 일("언젠가")은 놓을 날이 없습니다. 만든 날에 둡니다.
        val made = LocalDate.of(2026, 11, 10).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val shots = AttachmentGallery.collect(
            diaries = emptyList(),
            items = emptyList(),
            todos = listOf(
                TodoEntity(id = 5, syncId = "t2", title = "선반 고치기", dueDate = "", createdAt = made, imageUris = listOf("content://photo/shelf"))
            ),
            zoneId = zone
        )

        assertEquals(LocalDate.of(2026, 11, 10), shots.single().date)
    }

    @Test
    fun aTodoWithoutPhotosAddsNothing() {
        val shots = AttachmentGallery.collect(
            diaries = emptyList(),
            items = emptyList(),
            todos = listOf(TodoEntity(id = 6, syncId = "t3", title = "물 사기", dueDate = "2026-11-15")),
            zoneId = zone
        )

        assertEquals(emptyList<AttachmentShot>(), shots)
    }
}
