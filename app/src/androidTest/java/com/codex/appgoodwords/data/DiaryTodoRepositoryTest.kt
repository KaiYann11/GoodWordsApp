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
        assertNotNull("완료 시각이 없습니다.", done?.doneAt)

        val undone = repository.toggleTodoDone(saved.id)
        assertNull("다시 누르면 되돌아와야 합니다.", undone?.doneAt)
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
