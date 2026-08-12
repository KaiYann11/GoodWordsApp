package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 서버 없이도 앱이 온전히 돌아가는지, 그리고 서버 없이 두 기기를 맞출 수 있는지 봅니다.
 *
 * 서버가 없으면 아무것도 못 하는 앱이 되면 안 됩니다. 서버는 여러 기기를 쓸 때만 있으면 됩니다.
 */
class StandaloneAndFileMergeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container

    private val today = LocalDate.now()
    private lateinit var workFile: File

    @Before
    fun setUp() {
        runBlocking {
            // 서버가 없는 상태를 만든다.
            container.settingsStore.updateServerSyncSettings(ServerSyncSettings())
        }
        workFile = File(context.cacheDir, "merge-test-${System.nanoTime()}.json")
    }

    @After
    fun tearDown() {
        runBlocking {
            container.settingsStore.updateServerSyncSettings(ServerSyncSettings())
            container.database.diaryDao().clearAll()
            container.database.todoDao().clearAll()
        }
        workFile.delete()
    }

    @Test
    fun withoutAServerNothingIsScheduledAndNothingFails() = runBlocking {
        val settings = container.settingsStore.getServerSyncSettings()

        assertTrue("기본값에 서버 주소가 들어 있습니다.", settings.serverUrl.isBlank())
        assertFalse("서버도 없는데 배경 동기화가 켜져 있습니다.", settings.canAutoSync)
    }

    @Test
    fun everythingWorksLocallyWithoutAServer() = runBlocking {
        container.repository.saveDiary(DiaryDraft(entryDate = today, body = "서버 없이 쓴 일기"))
        val todo = container.repository.saveTodo(TodoDraft(title = "서버 없이 만든 할 일", dueDate = today))
        container.repository.toggleTodoDone(todo.id)

        assertEquals("서버 없이 쓴 일기", container.repository.observeDiaries().first().first().body)
        assertNotNull("완료 표시가 로컬에서 되지 않았습니다.", container.repository.getTodoById(todo.id)?.doneAt)
    }

    @Test
    fun twoDevicesCanReconcileThroughAFileWithoutAServer() = runBlocking {
        // 다른 기기가 내보낸 파일이라고 치자. 이 기기에는 없는 할 일이 들어 있다.
        val other = snapshotOf(
            todos = listOf(
                TodoEntity(
                    syncId = "other-device-todo",
                    updatedAt = System.currentTimeMillis(),
                    title = "다른 기기에서 만든 할 일",
                    dueDate = today.toString()
                )
            )
        )
        workFile.writeText(AppDataJson.toJson(other).toString(), Charsets.UTF_8)

        container.repository.saveTodo(TodoDraft(title = "이 기기에서 만든 할 일", dueDate = today))
        val before = container.syncCoordinator.currentSnapshot()

        container.appDataImporter.mergeFromFile(android.net.Uri.fromFile(workFile), before)

        val titles = container.database.todoDao().getAll().map { it.title }
        assertTrue("이 기기 할 일이 사라졌습니다: $titles", titles.contains("이 기기에서 만든 할 일"))
        assertTrue("파일 쪽 할 일이 들어오지 않았습니다: $titles", titles.contains("다른 기기에서 만든 할 일"))
    }

    @Test
    fun aFileMergeDoesNotDuplicateWhatBothSidesAlreadyHave() = runBlocking {
        container.repository.saveTodo(TodoDraft(title = "양쪽에 있는 할 일", dueDate = today))
        val mine = container.syncCoordinator.currentSnapshot()

        // 같은 내용을 다른 syncId로 들고 있는 파일. 새로 깐 기기가 흔히 이 상태다.
        val other = snapshotOf(
            todos = listOf(
                TodoEntity(
                    syncId = "different-sync-id",
                    updatedAt = System.currentTimeMillis(),
                    title = "양쪽에 있는 할 일",
                    dueDate = today.toString()
                )
            )
        )
        workFile.writeText(AppDataJson.toJson(other).toString(), Charsets.UTF_8)

        container.appDataImporter.mergeFromFile(android.net.Uri.fromFile(workFile), mine)

        val matching = container.database.todoDao().getAll().filter { it.title == "양쪽에 있는 할 일" }
        assertEquals("같은 할 일이 두 벌이 되었습니다.", 1, matching.size)
    }

    private fun snapshotOf(todos: List<TodoEntity>) = AppDataSnapshot(
        items = emptyList(),
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings(),
        todos = todos
    )
}
