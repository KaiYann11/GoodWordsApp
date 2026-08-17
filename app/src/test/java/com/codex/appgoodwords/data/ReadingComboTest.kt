package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 콤보는 그날 읽은 개수로만 정해집니다.
 *
 * 예전처럼 빨리 넘길수록 오르면, 읽지 않고 쓸어 넘기는 쪽이 유리해집니다.
 * 여기서 보는 것은 "속도가 결과에 섞이지 않는가"입니다.
 */
class ReadingComboTest {
    @Test
    fun theComboIsTheDayCount() {
        assertEquals(7, ReadingCombo.of(7).count)
        assertEquals("오늘 7개", ReadingCombo.of(7).title)
    }

    @Test
    fun theFirstReadOfTheDayIsNamed() {
        val combo = ReadingCombo.of(1)

        assertEquals("오늘 첫 글귀", combo.title)
        assertFalse("1개는 이정표가 아닙니다.", combo.isMilestone)
    }

    @Test
    fun anEmptyDayInvites() {
        val combo = ReadingCombo.of(0)

        assertEquals(0, combo.count)
        assertFalse(combo.isMilestone)
    }

    @Test
    fun milestonesAreOnlyTheListedCounts() {
        for (count in 1..30) {
            assertEquals(
                "$count 에서 이정표 판정이 어긋났습니다.",
                count in ReadingCombo.MILESTONES,
                ReadingCombo.of(count).isMilestone
            )
        }
    }

    @Test
    fun theNextMilestoneCountsDown() {
        val combo = ReadingCombo.of(7)

        assertEquals(10, combo.nextMilestone)
        assertEquals("10개까지 3개 남았습니다", combo.message)
    }

    /**
     * 10을 갓 넘긴 사람에게 20까지의 막대가 0부터 시작하면, 방금 지나온 이정표가 헛되어 보입니다.
     */
    @Test
    fun theBarStartsFromTheMilestoneJustPassed() {
        assertEquals(0f, ReadingCombo.of(10).progressToNext, 0.001f)
        assertEquals(0.5f, ReadingCombo.of(15).progressToNext, 0.001f)
        assertEquals(0.9f, ReadingCombo.of(19).progressToNext, 0.001f)
    }

    @Test
    fun pastTheLastMilestoneThereIsNothingLeftToChase() {
        val combo = ReadingCombo.of(120)

        assertNull(combo.nextMilestone)
        assertEquals(1f, combo.progressToNext, 0.001f)
        assertEquals("오늘 충분히 읽었습니다", combo.message)
    }

    @Test
    fun aNegativeCountIsTreatedAsNone() {
        // 취소가 겹쳐 음수가 되어도 화면이 깨지면 안 됩니다.
        val combo = ReadingCombo.of(-3)

        assertEquals(0, combo.count)
        assertTrue(combo.progressToNext >= 0f)
    }

    @Test
    fun milestonesRiseInOrder() {
        // 순서가 어긋나면 다음 이정표를 잘못 고릅니다.
        assertEquals(ReadingCombo.MILESTONES.sorted(), ReadingCombo.MILESTONES)
        assertEquals(ReadingCombo.MILESTONES.distinct(), ReadingCombo.MILESTONES)
    }
}
