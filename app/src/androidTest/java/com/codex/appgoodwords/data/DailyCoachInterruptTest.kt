package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.net.InetAddress
import java.net.ServerSocket
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 오늘의 한마디를 묻다가 연결이 끊겼을 때.
 *
 * Android 15는 화면 밖으로 나간 앱의 통신을 끊습니다. 홈에서 묻던 중에 앱을 나가 그 사이 끊기면, 그 실패를
 * 세는 순간 하루 횟수만 줄고 "인터넷 연결과 열쇠를 확인해 주세요"라는 맞지 않는 사유가 남습니다.
 *
 * **진짜로 끊기는 연결을 씁니다.** 이 기기 안에 받기만 하고 답하지 않는 자리를 열어 두고 잠시 뒤 닫습니다 —
 * 앱이 실제로 쓰는 [AiFeedbackClient]가 그 끊김을 겪고, 물음이 몇 번 나갔는지도 그 자리가 셉니다.
 */
class DailyCoachInterruptTest {
    private lateinit var database: AppDatabase
    private lateinit var settingsStore: SettingsStore
    private lateinit var server: SilentServer
    private lateinit var coordinator: DailyCoachCoordinator
    private var handedOver = 0
    private val today = LocalDate.now()

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        settingsStore = SettingsStore(context)
        server = SilentServer(holdMillis = 1_500)
        settingsStore.updateAiFeedbackSettings(AiFeedbackSettings(provider = AiProvider.OLLAMA.name, coachEnabled = true))
        settingsStore.updateServerSyncSettings(ServerSyncSettings("https://127.0.0.1:${server.port}", ""))
        settingsStore.setDailyCoachState(DailyCoachState())
        // 오늘 한 것이 있어야 묻습니다.
        val routineId = database.routineDao().insert(RoutineEntity(title = "물 한 컵"))
        database.routineCheckDao().insert(RoutineCheckEntity(routineId = routineId, routineTitle = "물 한 컵"))

        coordinator = DailyCoachCoordinator(
            database = database,
            settingsStore = settingsStore,
            repository = AppRepository(
                database = database,
                contentItemDao = database.contentItemDao(),
                exposureEventDao = database.exposureEventDao(),
                routineDao = database.routineDao(),
                routineCheckDao = database.routineCheckDao(),
                routineMemoDao = database.routineMemoDao(),
                contentMemoDao = database.contentMemoDao(),
                linkMetadataFetcher = LinkMetadataFetcher()
            ),
            handOver = { handedOver += 1 }
        )
    }

    @After fun tearDown() = runBlocking {
        server.close()
        database.close()
        // 앱과 같은 설정을 씁니다. 뒤의 시험이 닫힌 주소로 동기화하거나 묻지 않게 비웁니다.
        settingsStore.updateServerSyncSettings(ServerSyncSettings("", ""))
        settingsStore.updateAiFeedbackSettings(AiFeedbackSettings())
        settingsStore.setDailyCoachState(DailyCoachState())
    }

    @Test fun anAskCutOffByLeavingIsNotCountedAndIsHandedOver() = runBlocking {
        val state = coordinator.refresh(interrupted = { true })

        // 물음은 실제로 나갔습니다.
        assertEquals(1, server.accepted.get())
        assertEquals(0, state.askCount)
        assertEquals(0L, state.lastAskedAt)
        assertEquals("", state.lastError)
        assertTrue(state.interruptedAsk)
        assertEquals(1, handedOver)
        // 화면이 다시 읽는 것도 같습니다.
        assertEquals(state, settingsStore.getDailyCoachState())
    }

    @Test fun theCutOffAskIsAskedAgainWithoutWaitingForTheGap() = runBlocking {
        coordinator.refresh(interrupted = { true })

        // 주기(기본 90분)를 기다리지 않고 곧바로 다시 묻습니다. 이번에는 화면에 있는 채로 끊겨 셉니다.
        val again = coordinator.refresh()

        assertEquals(2, server.accepted.get())
        assertEquals(1, again.askCount)
        assertFalse(again.interruptedAsk)
        assertTrue(again.lastError, again.lastError.contains("연결하지 못했습니다"))
        assertEquals(1, handedOver)
    }

    @Test fun aFailureWhileOnScreenIsCountedAsBefore() = runBlocking {
        val state = coordinator.refresh()

        assertEquals(1, state.askCount)
        assertTrue(state.lastError, state.lastError.contains("연결하지 못했습니다"))
        assertFalse(state.interruptedAsk)
        assertEquals(0, handedOver)
    }

    @Test fun nothingIsHandedOverWhenTheBackgroundSwitchIsOff() = runBlocking {
        // 배경에서 묻지 말라고 해 두었으면 넘기지 않습니다. 끊긴 물음은 앱으로 돌아왔을 때 홈이 묻습니다.
        settingsStore.updateAiFeedbackSettings(
            AiFeedbackSettings(provider = AiProvider.OLLAMA.name, coachEnabled = true, coachBackground = false)
        )

        val state = coordinator.refresh(interrupted = { true })

        assertTrue(state.interruptedAsk)
        assertEquals(0, state.askCount)
        assertEquals(0, handedOver)
    }

    @Test fun twoLooksAtTheSameTimeAskOnce() = runBlocking {
        // 홈과 배경 일꾼이 같은 때 들여다봐도 한 번만 나갑니다. 뒤에 온 쪽은 앞의 물음이 끝나기를 기다렸다가
        // 다시 보고, 방금 물었으니 돌아갑니다.
        listOf(async { coordinator.refresh() }, async { coordinator.refresh() }).awaitAll()

        assertEquals(1, server.accepted.get())
        assertEquals(1, settingsStore.getDailyCoachState().askCount)
    }

    @Test fun whatIsKeptWhileAskingStaysKept() = runBlocking {
        // 묻는 데 몇 분이 걸리기도 합니다. 그 사이 앞서 받은 글귀를 담았으면, 물음이 끝나도 담긴 채여야 합니다 —
        // 담기 버튼이 되살아나면 같은 글귀가 두 벌 쌓입니다.
        settingsStore.setDailyCoachState(
            DailyCoachState(
                date = today,
                tip = DailyCoachTip(message = "앞서 받은 한마디", quote = "작은 일을 소홀히 하지 않는다", createdAt = 1L)
            )
        )

        val asking = async { coordinator.refresh(force = true) }
        while (server.accepted.get() == 0) delay(20)
        coordinator.keepQuote(today)
        val state = asking.await()

        assertTrue(state.quoteKept)
        assertTrue(state.lastError, state.lastError.contains("연결하지 못했습니다"))
        assertEquals(1, database.contentItemDao().getAll().size)
    }
}

/** 받기만 하고 답하지 않다가 [holdMillis] 뒤에 끊는 자리. 물음이 몇 번 나갔는지 셉니다. */
private class SilentServer(private val holdMillis: Long) : AutoCloseable {
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val port: Int = socket.localPort
    val accepted = AtomicInteger()

    init {
        thread(isDaemon = true) {
            while (true) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                accepted.incrementAndGet()
                thread(isDaemon = true) {
                    Thread.sleep(holdMillis)
                    runCatching { client.close() }
                }
            }
        }
    }

    override fun close() = socket.close()
}
