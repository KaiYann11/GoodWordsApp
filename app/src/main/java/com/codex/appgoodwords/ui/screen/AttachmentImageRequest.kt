package com.codex.appgoodwords.ui.screen

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.AttachmentUris

@Composable
internal fun attachmentImageLoader(): ImageLoader =
    (LocalContext.current.applicationContext as AppGoodWordsApplication).container.attachmentImageLoader

/** 접속 키는 검증한 서버 첨부에만 붙입니다. 외부 주소를 기기 파일로 열지 않습니다. */
internal fun attachmentImageRequest(context: Context, uri: String, serverUrl: String, apiKey: String): ImageRequest? {
    val serverTarget = runCatching { AttachmentUris.toHttpUrl(serverUrl, uri) }.getOrNull()
    val target = serverTarget ?: uri.takeIf(AttachmentUris::isLocal) ?: return null
    return ImageRequest.Builder(context)
        .data(target)
        .apply { if (serverTarget != null && apiKey.isNotBlank()) addHeader("X-API-Key", apiKey.trim()) }
        .crossfade(true)
        .build()
}
