package com.codex.appgoodwords.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 기기에 있는 첨부가 서버로 올라가고, 다시 받아올 수 있는지 봅니다.
 *
 * 이 단계가 없으면 다른 기기와 웹에서는 사진을 열 방법이 없습니다.
 * 서버가 떠 있지 않으면 건너뜁니다.
 * 실행 방법: `node server/app_good_words_server.mjs --host 0.0.0.0 --port 8765`
 */
class AttachmentSyncIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val settings = ServerSyncSettings(serverUrl = "http://10.0.2.2:8765", apiKey = "")

    // 1x1 PNG. 진짜 바이트여야 서버가 짓는 해시가 의미 있습니다.
    private val png: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    )

    private lateinit var photo: File

    @Before
    fun requireServer() {
        val reachable = runCatching { runBlocking { ServerSyncClient().testConnection(settings) } }.isSuccess
        assumeTrue("서버가 떠 있지 않아 건너뜁니다.", reachable)

        photo = File(context.cacheDir, "attachment-test-${System.nanoTime()}.png")
        photo.writeBytes(png)
    }

    @After
    fun tearDown() {
        runBlocking { container.database.diaryDao().clearAll() }
        if (::photo.isInitialized) photo.delete()
    }

    @Test
    fun aDeviceAttachmentBecomesAServerAddress() = runBlocking {
        val localUri = Uri.fromFile(photo).toString()
        container.database.diaryDao().insert(
            DiaryEntity(
                syncId = "attach-${SyncIdentity.newId()}",
                entryDate = "2026-08-17",
                body = "사진을 붙인 일기",
                imageUris = listOf(localUri)
            )
        )

        val result = container.attachmentUploader.uploadPending(settings)

        assertEquals("첨부를 올리지 못했습니다.", 1, result.uploaded)
        val stored = container.database.diaryDao().getAll().single().imageUris.single()
        assertTrue("주소가 서버 것으로 바뀌지 않았습니다: $stored", AttachmentUris.isServerAttachment(stored))
    }

    @Test
    fun theUploadedFileComesBackUnchanged() = runBlocking {
        container.database.diaryDao().insert(
            DiaryEntity(
                syncId = "attach-${SyncIdentity.newId()}",
                entryDate = "2026-08-17",
                imageUris = listOf(Uri.fromFile(photo).toString())
            )
        )
        container.attachmentUploader.uploadPending(settings)

        val stored = container.database.diaryDao().getAll().single().imageUris.single()
        val url = AttachmentUris.toHttpUrl(settings.serverUrl, stored)!!

        assertArrayEquals("받아온 파일이 올린 것과 다릅니다.", png, download(url))
    }

    @Test
    fun alreadyUploadedAttachmentsAreNotSentAgain() = runBlocking {
        container.database.diaryDao().insert(
            DiaryEntity(
                syncId = "attach-${SyncIdentity.newId()}",
                entryDate = "2026-08-17",
                imageUris = listOf(Uri.fromFile(photo).toString())
            )
        )
        container.attachmentUploader.uploadPending(settings)

        // 동기화마다 다시 올리면 데이터도 배터리도 낭비되고, updatedAt이 계속 올라가
        // 다른 기기의 수정을 매번 밀어냅니다.
        val second = container.attachmentUploader.uploadPending(settings)

        assertEquals(0, second.uploaded)
    }

    @Test
    fun aMissingFileDoesNotStopTheRest() = runBlocking {
        val gone = File(context.cacheDir, "already-deleted-${System.nanoTime()}.png")
        container.database.diaryDao().insert(
            DiaryEntity(
                syncId = "attach-${SyncIdentity.newId()}",
                entryDate = "2026-08-17",
                imageUris = listOf(Uri.fromFile(gone).toString(), Uri.fromFile(photo).toString())
            )
        )

        val result = container.attachmentUploader.uploadPending(settings)

        // 사진 한 장 때문에 글까지 동기화되지 않으면 잃는 것이 더 큽니다.
        assertEquals(1, result.uploaded)
        assertEquals(1, result.failed)
        val stored = container.database.diaryDao().getAll().single().imageUris
        assertTrue("살아 있는 사진이 안 올라갔습니다.", stored.any(AttachmentUris::isServerAttachment))
        assertTrue("못 연 사진의 주소가 사라졌습니다.", stored.any(AttachmentUris::isLocal))
    }

    @Test
    fun withoutAServerNothingIsUploaded() = runBlocking {
        container.database.diaryDao().insert(
            DiaryEntity(
                syncId = "attach-${SyncIdentity.newId()}",
                entryDate = "2026-08-17",
                imageUris = listOf(Uri.fromFile(photo).toString())
            )
        )

        val result = container.attachmentUploader.uploadPending(ServerSyncSettings())

        assertEquals(0, result.uploaded)
        assertEquals(0, result.failed)
    }

    private fun download(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            if (settings.apiKey.isNotBlank()) setRequestProperty("X-API-Key", settings.apiKey)
        }
        return try {
            assertEquals(200, connection.responseCode)
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
