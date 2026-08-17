package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 날씨와 기분은 이름 문자열로 저장합니다.
 *
 * 열거형을 그대로 저장하지 않는 이유는, 모르는 값이 들어와도 앱이 죽지 않게 하려는 것입니다.
 * 그래서 "모르는 값을 어떻게 다루는가"가 이 기능의 핵심이고, 여기서 그것을 봅니다.
 */
class DiaryTagsTest {
    @Test
    fun aKnownCodeBecomesItsOption() {
        assertEquals(DiaryWeather.RAIN, DiaryWeather.fromCode("RAIN"))
        assertEquals(DiaryMood.GREAT, DiaryMood.fromCode("GREAT"))
    }

    @Test
    fun anUnchosenValueIsNoOption() {
        assertNull(DiaryWeather.fromCode(""))
        assertNull(DiaryWeather.fromCode(null))
        assertNull(DiaryMood.fromCode("   "))
    }

    @Test
    fun anUnknownCodeIsNoOptionInsteadOfACrash() {
        // 다음 버전에서 선택지를 늘린 기기가 보내올 수 있는 값.
        assertNull(DiaryWeather.fromCode("AURORA"))
        assertNull(DiaryMood.fromCode("EXCITED"))
    }

    @Test
    fun codesAreCaseSensitiveSoTheyMatchWhatWeStore() {
        // 저장은 enum의 name으로만 합니다. 소문자를 받아 주면 두 표기가 섞여 같은 내용 합치기가 어긋납니다.
        assertNull(DiaryWeather.fromCode("rain"))
    }

    @Test
    fun aDiaryWithOnlyAMoodIsWorthSaving() {
        // 글 쓸 기운은 없어도 기분만 남기고 싶은 날이 있습니다.
        val draft = DiaryDraft(mood = DiaryMood.TIRED.name)

        assertTrue(draft.hasSomethingToSave)
    }

    @Test
    fun anEmptyDraftIsStillNotWorthSaving() {
        assertFalse(DiaryDraft().hasSomethingToSave)
    }

    @Test
    fun editingADiaryKeepsWhatWasChosen() {
        val diary = DiaryEntity(
            entryDate = "2026-08-17",
            weather = DiaryWeather.SNOW.name,
            mood = DiaryMood.GOOD.name
        )

        val draft = DiaryDraft.from(diary)

        assertEquals(DiaryWeather.SNOW, draft.weatherOption)
        assertEquals(DiaryMood.GOOD, draft.moodOption)
    }

    @Test
    fun everyOptionHasSomethingToShow() {
        // 빈 이름이 섞이면 목록에 날짜만 덩그러니 남습니다.
        DiaryWeather.entries.forEach { weather ->
            assertTrue(weather.name, weather.label.isNotBlank() && weather.emoji.isNotBlank())
        }
        DiaryMood.entries.forEach { mood ->
            assertTrue(mood.name, mood.label.isNotBlank() && mood.emoji.isNotBlank())
        }
    }
}
