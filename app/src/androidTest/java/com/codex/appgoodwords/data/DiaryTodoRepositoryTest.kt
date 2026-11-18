package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 일기와 할 일이 저장되고 지워지는 경로를 실제 DB로 확인합니다.
 *
 * 특히 삭제 표식이 남는지를 봅니다. 표식이 없으면 다른 기기에서 지운 일기가
 * 다음 병합에 그대로 되살아납니다.
 */
class DiaryTodoRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val repository = container.repository

    private val today = LocalDate.now()

    @After
    fun tearDown() = runBlocking {
        container.database.diaryDao().clearAll()
        container.database.todoDao().clearAll()
    }

    @Test
    fun aDiaryWithOnlyAPhotoIsStillWorthSaving() = runBlocking {
        // 사진만 넣고 글은 안 쓰는 날도 있다. 본문만 보고 막으면 그 날이 통째로 사라진다.
        repository.saveDiary(
            DiaryDraft(entryDate = today, imageUris = listOf("content://photo/1"))
        )

        val saved = container.database.diaryDao().getAll().single()
        assertEquals(listOf("content://photo/1"), saved.imageUris)
    }

    @Test
    fun anEmptyDiaryIsRefused() = runBlocking {
        val failure = runCatching { repository.saveDiary(DiaryDraft(entryDate = today)) }

        assertTrue("빈 일기가 저장되었습니다.", failure.isFailure)
    }

    @Test
    fun editingADiaryKeepsItsSyncId() = runBlocking {
        repository.saveDiary(DiaryDraft(entryDate = today, body = "처음"))
        val first = container.database.diaryDao().getAll().single()

        repository.saveDiary(DiaryDraft(id = first.id, entryDate = today, body = "고친 뒤"))

        val edited = container.database.diaryDao().getAll().single()
        assertEquals("고친 뒤", edited.body)
        assertEquals("syncId가 바뀌면 다른 기기에서 새 일기로 보입니다.", first.syncId, edited.syncId)
    }

    @Test
    fun deletingADiaryLeavesAMark() = runBlocking {
        repository.saveDiary(DiaryDraft(entryDate = today, body = "지울 일기"))
        val saved = container.database.diaryDao().getAll().single()

        repository.deleteDiary(saved.id)

        val mark = container.database.deletionDao().getAll().firstOrNull { it.syncId == saved.syncId }
        assertNotNull("표식이 없으면 다음 병합에 되살아납니다.", mark)
        assertEquals(SyncEntityType.DIARY, mark?.entityType)
    }

    @Test
    fun deletingATodoLeavesAMark() = runBlocking {
        val saved = repository.saveTodo(TodoDraft(title = "지울 할 일", dueDate = today))

        repository.deleteTodo(saved.id)

        val mark = container.database.deletionDao().getAll().firstOrNull { it.syncId == saved.syncId }
        assertNotNull(mark)
        assertEquals(SyncEntityType.TODO, mark?.entityType)
    }

    @Test
    fun togglingDoneFlipsBothWays() = runBlocking {
        val saved = repository.saveTodo(TodoDraft(title = "우체국", dueDate = today))

        val done = repository.toggleTodoDone(saved.id)
        assertNotNull("완료 시각이 없습니다.", done?.todo?.doneAt)
        assertNull("반복하지 않는 할 일은 다음 회차가 없습니다.", done?.next)

        val undone = repository.toggleTodoDone(saved.id)
        assertNull("다시 누르면 되돌아와야 합니다.", undone?.todo?.doneAt)
    }

    @Test
    fun finishingARepeatingTodoMakesTheNextRoundOnce() = runBlocking {
        // 1월 31일의 매달 — 날을 들고 있어 2월에는 마지막 날, 사진·메모·반복이 따라갑니다(사용자가 정함, 2026-11-19).
        val saved = repository.saveTodo(
            TodoDraft(
                title = "월세 내기",
                note = "계좌 사진",
                dueDate = LocalDate.of(2027, 1, 31),
                imageUris = listOf("content://photo/account"),
                repeat = TodoRepeatKind.MONTHLY
            )
        )
        assertEquals("MONTHLY:31", saved.repeatRule)

        val toggle = repository.toggleTodoDone(saved.id, today = LocalDate.of(2027, 1, 30))!!
        val next = toggle.next!!
        assertEquals("2027-02-28", next.dueDate)
        assertEquals("MONTHLY:31", next.repeatRule)
        assertEquals(listOf("content://photo/account"), next.imageUris)
        assertEquals("계좌 사진", next.note)
        assertNull(next.doneAt)
        assertNotNull("다음 회차를 만든 때가 남아야 다시 끝내도 또 만들지 않습니다.", toggle.todo.repeatedAt)
        assertEquals(next, container.database.todoDao().getById(next.id))

        // 끝냄을 거뒀다 다시 끝내도 다음 회차는 하나뿐입니다.
        repository.toggleTodoDone(saved.id)
        val again = repository.toggleTodoDone(saved.id, today = LocalDate.of(2027, 1, 30))!!
        assertNull(again.next)
        assertEquals(2, container.database.todoDao().getAll().size)

        // 2월 28일 회차를 끝내면 3월에는 다시 31일입니다.
        val third = repository.toggleTodoDone(next.id, today = LocalDate.of(2027, 2, 28))!!.next!!
        assertEquals("2027-03-31", third.dueDate)
    }

    @Test
    fun undoingTheRepeatRemovesTheNextRoundWithAMark() = runBlocking {
        val saved = repository.saveTodo(TodoDraft(title = "분리수거", dueDate = today, repeat = TodoRepeatKind.BIWEEKLY))
        val toggle = repository.toggleTodoDone(saved.id)!!
        val next = toggle.next!!
        assertEquals(today.plusWeeks(2).toString(), next.dueDate)

        val restored = repository.undoTodoRepeat(saved.id, next.syncId)!!
        assertNull(restored.doneAt)
        assertNull("되돌린 뒤 다시 끝내면 다음 회차가 다시 생겨야 합니다.", restored.repeatedAt)
        assertEquals(listOf(saved.syncId), container.database.todoDao().getAll().map { it.syncId })
        // 표식이 없으면 이미 올라간 다음 회차가 서버와 웹에 남습니다.
        val mark = container.database.deletionDao().getAll().firstOrNull { it.syncId == next.syncId }
        assertEquals(SyncEntityType.TODO, mark?.entityType)

        assertNotNull(repository.toggleTodoDone(saved.id)!!.next)
    }

    @Test
    fun editingARoundKeepsItsRuleUnlessTheDateMoves() = runBlocking {
        val saved = repository.saveTodo(
            TodoDraft(title = "월세", dueDate = LocalDate.of(2027, 1, 31), repeat = TodoRepeatKind.MONTHLY)
        )
        val february = repository.toggleTodoDone(saved.id, today = LocalDate.of(2027, 1, 31))!!.next!!

        // 이름만 고치면 날(31일)을 지킵니다 — 28일로 다시 잡으면 3월부터 28일에 옵니다.
        val renamed = repository.saveTodo(TodoDraft.from(february).copy(title = "월세 이체"))
        assertEquals("MONTHLY:31", renamed.repeatRule)

        // 날짜를 옮기면 그 날짜로 다시 겁니다. 날짜를 지우면 반복도 없습니다.
        val moved = repository.saveTodo(TodoDraft.from(renamed).copy(dueDate = LocalDate.of(2027, 3, 5)))
        assertEquals("MONTHLY:5", moved.repeatRule)
        val someday = repository.saveTodo(TodoDraft.from(moved).copy(dueDate = null))
        assertEquals("", someday.repeatRule)
    }

    @Test
    fun editingATodoKeepsItsDoneMark() = runBlocking {
        val saved = repository.saveTodo(TodoDraft(title = "우체국", dueDate = today))
        repository.toggleTodoDone(saved.id)

        val edited = repository.saveTodo(TodoDraft(id = saved.id, title = "우체국 등기", dueDate = today))

        // 제목만 고쳤는데 완료가 풀리면 끝낸 일이 되살아난다.
        assertNotNull("고치면서 완료 표시를 잃었습니다.", edited.doneAt)
    }

    @Test
    fun aTodoKeepsItsPhotosThroughTheDoneMarkAndEdits() = runBlocking {
        // 빈 주소와 같은 사진 두 번은 떼고 붙인 차례대로 둡니다(메모·행복한 순간과 같게).
        val saved = repository.saveTodo(
            TodoDraft(
                title = "반품하기",
                dueDate = today,
                imageUris = listOf("content://photo/receipt", " ", "content://photo/box", "content://photo/receipt")
            )
        )
        assertEquals(listOf("content://photo/receipt", "content://photo/box"), saved.imageUris)

        // 완료를 눌러도 사진은 그대로입니다.
        repository.toggleTodoDone(saved.id)
        val done = container.database.todoDao().getById(saved.id)!!
        assertEquals(saved.imageUris, done.imageUris)

        // 고치기는 화면이 들고 있던 사진 목록 그대로 저장합니다 — 뺀 사진은 빠지고 완료 표시는 남습니다.
        val edited = repository.saveTodo(TodoDraft.from(done).copy(title = "택배 반품하기", imageUris = listOf("content://photo/box")))
        assertEquals(listOf("content://photo/box"), container.database.todoDao().getById(saved.id)?.imageUris)
        assertNotNull("고치면서 완료 표시를 잃었습니다.", edited.doneAt)
    }

    @Test
    fun onlyUnfinishedTodosWithAlarmsNeedRescheduling() = runBlocking {
        repository.saveTodo(TodoDraft(title = "알람 없음", dueDate = today))
        val withAlarm = repository.saveTodo(
            TodoDraft(title = "알람 있음", dueDate = today, remindAt = System.currentTimeMillis() + 600_000L)
        )
        val finished = repository.saveTodo(
            TodoDraft(title = "끝낸 일", dueDate = today, remindAt = System.currentTimeMillis() + 600_000L)
        )
        repository.toggleTodoDone(finished.id)

        val pending = repository.getPendingTodoReminders()

        assertEquals(listOf(withAlarm.syncId), pending.map { it.syncId })
    }
}
