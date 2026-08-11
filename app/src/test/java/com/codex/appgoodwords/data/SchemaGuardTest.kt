package com.codex.appgoodwords.data

import java.io.Closeable
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 형식이 다른 서버와는 아예 주고받지 않는지 확인합니다.
 *
 * [SyncSchemaTest]는 판단만 봅니다. 여기서는 그 판단이 실제 요청 앞에 놓여 있는지를 봅니다.
 * 응답을 받고 나서 확인하면 이미 늦기 때문입니다.
 */
class SchemaGuardTest {
    private val client = ServerSyncClient()

    @Test
    fun mergeIsRefusedWhenTheServerFormatIsOlder() {
        FakeHealthServer(schemaVersion = AppDataJson.schemaVersion - 1).use { server ->
            val failure = runCatching {
                runBlocking { client.mergeSnapshot(server.settings(), emptySnapshot()) }
            }.exceptionOrNull()

            assertNotNull("형식이 다른 서버와 병합했습니다.", failure)
            assertTrue(
                "사용자가 읽을 수 있는 사유여야 합니다: ${failure?.message}",
                failure?.message.orEmpty().contains("동기화 형식이 다릅니다")
            )
        }
    }

    @Test
    fun downloadIsRefusedToo() {
        FakeHealthServer(schemaVersion = AppDataJson.schemaVersion + 1).use { server ->
            val failure = runCatching {
                runBlocking { client.downloadSnapshot(server.settings()) }
            }.exceptionOrNull()

            assertNotNull("형식이 다른 서버에서 가져왔습니다.", failure)
        }
    }

    @Test
    fun uploadIsRefusedBeforeTheServerIsOverwritten() {
        FakeHealthServer(schemaVersion = AppDataJson.schemaVersion - 1).use { server ->
            val failure = runCatching {
                runBlocking { client.uploadSnapshot(server.settings(), emptySnapshot()) }
            }.exceptionOrNull()

            assertNotNull("형식이 다른 서버를 덮어썼습니다.", failure)
            // 확인이 요청보다 앞에 있어야 서버가 상하지 않는다.
            assertTrue("업로드가 서버에 닿았습니다.", server.paths.none { it.startsWith("PUT") })
        }
    }

    @Test
    fun aMatchingServerIsNotBlocked() {
        FakeHealthServer(schemaVersion = AppDataJson.schemaVersion).use { server ->
            val failure = runCatching {
                runBlocking { client.downloadSnapshot(server.settings()) }
            }.exceptionOrNull()

            assertNull("같은 형식인데 막혔습니다: ${failure?.message}", failure)
        }
    }

    private fun emptySnapshot() = AppDataSnapshot(
        items = emptyList(),
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings()
    )

    /**
     * 어떤 요청에도 같은 health 응답을 돌려주는 최소 서버입니다.
     * 스키마 확인이 먼저 걸리면 그 뒤 요청은 오지 않으므로, 무엇이 왔는지만 기록해 두면 됩니다.
     */
    private class FakeHealthServer(private val schemaVersion: Int) : Closeable {
        private val socket = ServerSocket(0)
        val paths = mutableListOf<String>()

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    runCatching {
                        socket.accept().use { client ->
                            val reader = client.getInputStream().bufferedReader()
                            val requestLine = reader.readLine().orEmpty()
                            synchronized(paths) { paths += requestLine }
                            // 헤더 끝까지 읽어야 상대가 응답을 기다린다.
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isBlank()) break
                            }
                            val body = """{"ok":true,"schemaVersion":$schemaVersion}"""
                            client.getOutputStream().bufferedWriter().apply {
                                write("HTTP/1.1 200 OK\r\n")
                                write("Content-Type: application/json\r\n")
                                write("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n")
                                write("Connection: close\r\n\r\n")
                                write(body)
                                flush()
                            }
                        }
                    }
                }
            }
        }

        // 127.x는 평문 http로도 동기화할 수 있는 주소다.
        fun settings() = ServerSyncSettings(serverUrl = "http://127.0.0.1:${socket.localPort}")

        override fun close() {
            socket.close()
        }
    }
}
