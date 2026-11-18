package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 할 일의 반복(사용자가 정함, 2026-11-19 — 매주 · 2주마다 · 매달 · 매년, 끝낼 때 다음 회차).
 *
 * 서버 `nextRepeatDate`·`todoRepeatRule`·웹 `todoRepeatLabel`이 같은 꼴과 규칙을 씁니다. 둘이 다르면 휴대폰에서 끝낸 것과
 * 웹에서 끝낸 것의 다음 회차가 다른 날에 생깁니다.
 */
class TodoRepeatTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun theCodeRoundTripsAndUnknownTextMeansNoRepeat() {
        listOf("WEEKLY", "BIWEEKLY", "MONTHLY:1", "MONTHLY:31", "YEARLY:02-29", "YEARLY:12-25").forEach { code ->
            assertEquals(code, TodoRepeats.parse(code)?.code)
        }
        // 매일·요일마다는 루틴의 자리라 받지 않습니다. 어느 해에도 없는 날도 받지 않습니다.
        listOf("", " ", "DAILY", "MONTHLY:0", "MONTHLY:32", "MONTHLY:x", "YEARLY:02-30", "YEARLY:13-01", "YEARLY:1225").forEach { code ->
            assertNull(code, TodoRepeats.parse(code))
            assertEquals(code, "", TodoRepeats.normalize(code))
        }
    }

    @Test
    fun monthlyAndYearlyKeepTheDayTheyWereSetOn() {
        assertEquals("MONTHLY:31", TodoRepeats.of(TodoRepeatKind.MONTHLY, LocalDate.of(2027, 1, 31))?.code)
        assertEquals("YEARLY:02-29", TodoRepeats.of(TodoRepeatKind.YEARLY, LocalDate.of(2028, 2, 29))?.code)
        assertEquals("WEEKLY", TodoRepeats.of(TodoRepeatKind.WEEKLY, LocalDate.of(2027, 1, 31))?.code)
        assertNull(TodoRepeats.of(TodoRepeatKind.NONE, LocalDate.of(2027, 1, 31)))
    }

    @Test
    fun theNextRoundComesAfterTheDueDateAndNotBeforeToday() {
        val monthly31 = TodoRepeats.parse("MONTHLY:31")!!
        // 미리 끝내면 바로 다음 회차, 1월 31일의 매달은 2월에 마지막 날, 3월에 다시 31일.
        assertEquals(LocalDate.of(2027, 2, 28), TodoRepeats.nextDate(monthly31, LocalDate.of(2027, 1, 31), LocalDate.of(2027, 1, 20)))
        assertEquals(LocalDate.of(2027, 3, 31), TodoRepeats.nextDate(monthly31, LocalDate.of(2027, 2, 28), LocalDate.of(2027, 2, 28)))
        assertEquals(LocalDate.of(2028, 2, 29), TodoRepeats.nextDate(monthly31, LocalDate.of(2028, 1, 31), LocalDate.of(2028, 1, 31)))
        // 늦게 끝내면 오늘이나 그 뒤의 첫 회차입니다. 지난 회차를 다시 만들지 않습니다.
        val weekly = TodoRepeats.parse("WEEKLY")!!
        assertEquals(LocalDate.of(2026, 11, 19), TodoRepeats.nextDate(weekly, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 19)))
        assertEquals(LocalDate.of(2026, 11, 26), TodoRepeats.nextDate(weekly, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 20)))
        val biweekly = TodoRepeats.parse("BIWEEKLY")!!
        assertEquals(LocalDate.of(2026, 12, 3), TodoRepeats.nextDate(biweekly, LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19)))
        // 2월 29일의 매년은 윤년이 아닌 해에 28일입니다.
        val leap = TodoRepeats.parse("YEARLY:02-29")!!
        assertEquals(LocalDate.of(2029, 2, 28), TodoRepeats.nextDate(leap, LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 1)))
        assertEquals(LocalDate.of(2032, 2, 29), TodoRepeats.nextDate(leap, LocalDate.of(2031, 2, 28), LocalDate.of(2031, 3, 1)))
    }

    @Test
    fun theReminderKeepsItsDayOffsetAndTime() {
        // 마감 전날 21:30에 걸어 둔 알람은 다음 회차에도 전날 21:30입니다.
        val remindAt = LocalDateTime.of(2027, 1, 30, 21, 30).atZone(zone).toInstant().toEpochMilli()
        val shifted = TodoRepeats.shiftReminder(remindAt, LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), zone)
        assertEquals(LocalDateTime.of(2027, 2, 27, 21, 30).atZone(zone).toInstant().toEpochMilli(), shifted)
        assertNull(TodoRepeats.shiftReminder(null, LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), zone))
    }

    @Test
    fun theNextRoundCarriesTheTodoButIsNew() {
        val done = TodoEntity(
            id = 7, syncId = "rent", updatedAt = 1_000L, title = "월세 내기", note = "계좌", dueDate = "2027-01-31",
            remindAt = null, doneAt = 2_000L, createdAt = 500L, imageUris = listOf("content://photo/account"), repeatRule = "MONTHLY:31"
        )

        val next = TodoRepeats.nextOf(done, today = LocalDate.of(2027, 1, 31), now = 3_000L, zoneId = zone)!!

        assertEquals("2027-02-28", next.dueDate)
        assertEquals(listOf("월세 내기", "계좌", "MONTHLY:31"), listOf(next.title, next.note, next.repeatRule))
        assertEquals(listOf("content://photo/account"), next.imageUris)
        assertEquals(listOf(0L, 3_000L, 3_000L), listOf(next.id, next.createdAt, next.updatedAt))
        assertNull(next.doneAt)
        assertNull(next.repeatedAt)
        assertEquals(false, next.syncId == done.syncId)
        // 이미 다음 회차를 만들었거나, 반복하지 않거나, 날짜가 없으면 만들지 않습니다.
        assertNull(TodoRepeats.nextOf(done.copy(repeatedAt = 2_000L), LocalDate.of(2027, 1, 31), 3_000L, zone))
        assertNull(TodoRepeats.nextOf(done.copy(repeatRule = ""), LocalDate.of(2027, 1, 31), 3_000L, zone))
        assertNull(TodoRepeats.nextOf(done.copy(dueDate = ""), LocalDate.of(2027, 1, 31), 3_000L, zone))
    }

    @Test
    fun editingKeepsTheRuleUnlessTheKindOrDateChanges() {
        val february = TodoEntity(syncId = "r", title = "월세", dueDate = "2027-02-28", repeatRule = "MONTHLY:31")
        assertEquals("MONTHLY:31", TodoRepeats.ruleFor(TodoRepeatKind.MONTHLY, LocalDate.of(2027, 2, 28), february))
        assertEquals("MONTHLY:3", TodoRepeats.ruleFor(TodoRepeatKind.MONTHLY, LocalDate.of(2027, 3, 3), february))
        assertEquals("YEARLY:02-28", TodoRepeats.ruleFor(TodoRepeatKind.YEARLY, LocalDate.of(2027, 2, 28), february))
        assertEquals("", TodoRepeats.ruleFor(TodoRepeatKind.NONE, LocalDate.of(2027, 2, 28), february))
        assertEquals("", TodoRepeats.ruleFor(TodoRepeatKind.MONTHLY, null, february))
        assertEquals("WEEKLY", TodoRepeats.ruleFor(TodoRepeatKind.WEEKLY, LocalDate.of(2027, 2, 28), null))
    }

    @Test
    fun labelsAndTheNextMessageReadLikeTheMockup() {
        // 2026-11-19는 목요일입니다. 웹 todoRepeatLabel과 같은 말입니다.
        assertEquals("매주 목요일", TodoRepeats.label("WEEKLY", "2026-11-19"))
        assertEquals("2주마다 목요일", TodoRepeats.label("BIWEEKLY", "2026-11-19"))
        assertEquals("매주", TodoRepeats.label("WEEKLY", ""))
        assertEquals("매달 25일", TodoRepeats.label("MONTHLY:25", "2026-11-25"))
        assertEquals("매년 2월 29일", TodoRepeats.label("YEARLY:02-29", "2027-02-28"))
        assertNull(TodoRepeats.label("", "2026-11-19"))
        assertEquals("다음은 12월 25일(금)", TodoRepeats.nextMessage(TodoEntity(title = "월세", dueDate = "2026-12-25")))
    }
}
