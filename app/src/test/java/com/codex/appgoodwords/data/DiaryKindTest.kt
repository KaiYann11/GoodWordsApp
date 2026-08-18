package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 감사·반성 일기는 물음에 답하는 일기입니다.
 *
 * 답은 물음과 자리를 맞춰 둔 목록이라, 빈칸을 잘못 다루면 답이 다른 물음에 가서 붙습니다.
 * 화면에는 아무 오류도 나지 않고 엉뚱한 말만 남으므로, 자리를 지키는지를 여기서 봅니다.
 */
class DiaryKindTest {
    @Test
    fun aKnownCodeBecomesItsKind() {
        assertEquals(DiaryKind.GRATITUDE, DiaryKind.fromCode("GRATITUDE"))
        assertEquals(DiaryKind.FREE, DiaryKind.fromCode("FREE"))
    }

    @Test
    fun anUnknownKindIsReadAsFreeInsteadOfACrash() {
        // 다음 버전에서 종류를 늘린 기기가 보내올 수 있는 값.
        assertNull(DiaryKind.fromCode("MORNING_PAGE"))
        assertEquals(DiaryKind.FREE, DiaryEntity(entryDate = "2026-08-18", kind = "MORNING_PAGE").kindOption)
    }

    @Test
    fun aFreeDiaryHasNoPrompts() {
        assertFalse(DiaryKind.FREE.isGuided)
        assertTrue(DiaryKind.GRATITUDE.isGuided)
        assertTrue(DiaryKind.REFLECTION.isGuided)
    }

    @Test
    fun everyPromptHasSomethingToRead() {
        // 빈 물음이 섞이면 무엇에 답하라는 것인지 알 수 없는 칸이 생깁니다.
        DiaryKind.entries.forEach { kind ->
            assertTrue(kind.name, kind.label.isNotBlank())
            kind.prompts.forEach { prompt -> assertTrue(kind.name, prompt.isNotBlank()) }
        }
    }

    @Test
    fun anAnswerInTheMiddleKeepsItsPlace() {
        // 가운데 빈칸을 버리면 마지막 답이 첫 물음의 답이 됩니다.
        val kept = DiaryAnswers.normalize(listOf("", "고마운 사람", ""))

        assertEquals(listOf("", "고마운 사람"), kept)
    }

    @Test
    fun trailingBlanksAreDropped() {
        assertEquals(emptyList<String>(), DiaryAnswers.normalize(listOf("", "  ", "")))
        assertEquals(listOf("커피"), DiaryAnswers.normalize(listOf(" 커피 ", "")))
    }

    @Test
    fun promptsAndAnswersStayPairedWhenOnlyTheLastIsFilled() {
        val diary = DiaryEntity(
            entryDate = "2026-08-18",
            kind = DiaryKind.GRATITUDE.name,
            answers = listOf("", "", "비 그친 하늘")
        )

        assertEquals(listOf(DiaryKind.GRATITUDE.prompts[2] to "비 그친 하늘"), diary.filledAnswers)
    }

    @Test
    fun aDiaryWithOnlyAnAnswerIsWorthSaving() {
        // 감사 일기는 본문 없이 답만 적는 날이 흔합니다.
        val draft = DiaryDraft(kind = DiaryKind.GRATITUDE.name, answers = listOf("커피"))

        assertTrue(draft.hasSomethingToSave)
    }

    @Test
    fun aDiaryWithOnlyBlankAnswersIsNotWorthSaving() {
        val draft = DiaryDraft(kind = DiaryKind.GRATITUDE.name, answers = listOf("", "  "))

        assertFalse(draft.hasSomethingToSave)
    }

    @Test
    fun aGuidedDiaryKeepsNoWeatherOrMood() {
        // 자유로 쓰다 종류를 바꾼 경우. 화면에서 칩이 사라지므로 저장도 하면 안 됩니다.
        val draft = DiaryDraft(
            weather = DiaryWeather.RAIN.name,
            mood = DiaryMood.GOOD.name,
            kind = DiaryKind.GRATITUDE.name,
            answers = listOf("커피")
        )

        assertEquals("", draft.effectiveWeather)
        assertEquals("", draft.effectiveMood)
    }

    @Test
    fun aFreeDiaryStillKeepsWeatherAndMood() {
        val draft = DiaryDraft(weather = DiaryWeather.RAIN.name, mood = DiaryMood.GOOD.name)

        assertEquals(DiaryWeather.RAIN.name, draft.effectiveWeather)
        assertEquals(DiaryMood.GOOD.name, draft.effectiveMood)
    }

    @Test
    fun switchingKindBackBringsTheChoiceReturns() {
        // 고른 값 자체는 지우지 않습니다. 잘못 눌렀을 때 되돌릴 방법이 없어집니다.
        val guided = DiaryDraft(mood = DiaryMood.GOOD.name, kind = DiaryKind.REFLECTION.name)

        val backToFree = guided.copy(kind = DiaryKind.FREE.name)

        assertEquals(DiaryMood.GOOD, backToFree.moodOption)
    }

    @Test
    fun aGuidedDiaryWithOnlyAHiddenMoodIsNotWorthSaving() {
        // 안 보이는 값으로 저장 버튼이 켜지면 아무것도 안 적은 일기가 저장됩니다.
        val draft = DiaryDraft(mood = DiaryMood.TIRED.name, kind = DiaryKind.GRATITUDE.name)

        assertFalse(draft.hasSomethingToSave)
    }

    @Test
    fun writingOneAnswerLeavesTheOthersAlone() {
        val draft = DiaryDraft(kind = DiaryKind.REFLECTION.name).withAnswer(2, "일찍 자기")

        assertEquals(listOf("", "", "일찍 자기"), draft.answers)
    }

    @Test
    fun aGuidedDiaryWithoutABodyStillHasATitleToShow() {
        // 제목도 본문도 없으면 목록에 날짜만 덩그러니 남습니다.
        val diary = DiaryEntity(
            entryDate = "2026-08-18",
            kind = DiaryKind.GRATITUDE.name,
            answers = listOf("따뜻한 커피")
        )

        assertEquals("따뜻한 커피", diary.displayTitle)
    }

    @Test
    fun editingAGuidedDiaryKeepsWhatWasWritten() {
        val diary = DiaryEntity(
            entryDate = "2026-08-18",
            kind = DiaryKind.REFLECTION.name,
            answers = listOf("일찍 일어남", "", "물 마시기")
        )

        val draft = DiaryDraft.from(diary)

        assertEquals(DiaryKind.REFLECTION, draft.kindOption)
        assertEquals(listOf("일찍 일어남", "", "물 마시기"), draft.answers)
    }
}
