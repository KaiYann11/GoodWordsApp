package com.codex.appgoodwords

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.codex.appgoodwords.data.SharedText
import kotlinx.coroutines.launch

/**
 * 다른 앱의 공유에서 이 앱을 고르면, **앱을 열지 않고 바로 담습니다.**
 *
 * 예전에는 담는 화면이 떴고 사용자가 저장을 한 번 더 눌러야 했습니다. 유튜브를 보다가
 * 하나 담으려던 것뿐인데 앱이 통째로 열려서, 보던 자리로 돌아가려면 뒤로 가기를 눌러야 했습니다.
 * 여기서는 화면 없이 담고 토스트만 띄웁니다. 유튜브에 그대로 있는 것이 "자동"의 핵심입니다.
 *
 * **저장은 앱 수명에 걸어 둡니다.** 이 화면은 곧바로 사라지므로, 화면에 매인 코루틴에 걸면
 * 저장이 도중에 끊깁니다.
 *
 * 고칠 것이 있으면 담긴 뒤에 앱에서 엽니다. 담는 화면을 다시 거치게 하려면
 * `AndroidManifest.xml`의 SEND 거르개를 [MainActivity] 쪽으로 되돌리면 됩니다.
 */
class ShareTargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val draft = if (intent?.action == Intent.ACTION_SEND) {
            SharedText.toDraft(
                text = intent.getStringExtra(Intent.EXTRA_TEXT),
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            )
        } else {
            null
        }

        if (draft == null) {
            toast("담을 것이 없습니다.")
            finish()
            return
        }

        val application = application as AppGoodWordsApplication
        application.applicationScope.launch {
            val message = runCatching { application.container.repository.saveSharedContent(draft) }
                .fold(
                    onSuccess = { saved ->
                        val name = saved.title.ifBlank { "새 게시글" }
                        if (saved.alreadyKept) "이미 담아 둔 것입니다: $name" else "보관함에 담았습니다: $name"
                    },
                    onFailure = { failure -> failure.message ?: "담지 못했습니다." }
                )
            toast(message)
        }

        // 저장을 기다리지 않고 물러납니다. 공유한 앱으로 곧바로 돌아가야 합니다.
        finish()
    }

    /**
     * 화면이 사라진 뒤에도 떠야 하므로 applicationContext로 띄웁니다.
     * 액티비티로 띄우면 그 액티비티와 함께 사라질 수 있습니다.
     */
    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
