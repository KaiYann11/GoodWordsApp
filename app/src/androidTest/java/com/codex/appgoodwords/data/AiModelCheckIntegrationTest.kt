package com.codex.appgoodwords.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * "모델 정보 받아오기"(설정 → AI)가 서버를 거쳐 CLI에서 실제 모델 이름을 알아 오는 길을 끝까지 밟습니다.
 *
 * 앱 → HTTPS → 서버 `POST /api/ai-models/resolve` → `claude`/`codex`에 칸마다 짧게 묻기 → 답한 모델 이름. 서버에 로그인해 둔
 * CLI가 실제로 답하므로 사용량이 조금 듭니다. `testServerUrl`이 없으면 건너뜁니다 — 운영 서버가 아니라 **CLI가 깔린 PC에서
 * 띄운 별도 HTTPS 테스트 서버**를 가리켜야 합니다.
 */
class AiModelCheckIntegrationTest {
    private val syncSettings = ServerSyncSettings(
        InstrumentationRegistry.getArguments().getString("testServerUrl").orEmpty(),
        InstrumentationRegistry.getArguments().getString("testServerApiKey").orEmpty()
    )

    @Before fun setUp() {
        assumeTrue("CLI가 깔린 PC의 HTTPS 테스트 서버가 필요합니다.", syncSettings.serverUrl.isNotBlank())
    }

    @Test fun claudeCliNamesItsDefaultAndEachFamilyAlias() {
        runBlocking {
            val settings = AiFeedbackSettings(provider = AiProvider.CLAUDE_CLI.name)
            // 앱이 보내는 그대로입니다 — 칸(`latest:opus`)이 아니라 별칭(`opus`)과 빈 값(CLI 기본).
            val requests = AiModels.serverPickedChoices(AiProvider.CLAUDE_CLI, settings.modelChoice)
                .map { settings.withModel(it).requestModel(emptyList()) }
            assertEquals(listOf("", "opus", "sonnet", "haiku"), requests)

            val resolved = AiFeedbackClient().resolveModels(AiProvider.CLAUDE_CLI, requests, syncSettings)

            requests.forEach { request ->
                val row = resolved[request]
                assertTrue("'$request' → $row", row != null && row.error.isEmpty() && row.model.startsWith("claude-"))
            }
            listOf("opus", "sonnet", "haiku").forEach { family ->
                assertTrue("$family → ${resolved[family]}", resolved.getValue(family).model.contains(family))
            }
        }
    }

    @Test fun codexCliNamesItsDefault() {
        runBlocking {
            val resolved = AiFeedbackClient().resolveModels(AiProvider.CODEX_CLI, listOf(""), syncSettings)

            val row = resolved[""]
            assertTrue("CLI 기본 → $row", row != null && row.error.isEmpty() && row.model.startsWith("gpt-"))
        }
    }
}
