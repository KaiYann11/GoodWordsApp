package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 개념 하나를 서버의 CLI에 물어 설명을 받아 붙이는 길을 끝까지 밟습니다.
 *
 * 앱 → HTTPS → 서버 `/api/growth-feedback` → `claude`/`codex` → 답 해석 → 메모 저장.
 * 서버에 로그인해 둔 CLI가 실제로 답하므로 값이 조금 듭니다. `testServerUrl`이 없으면 건너뜁니다 —
 * 운영 서버가 아니라 **CLI가 깔린 PC에서 띄운 별도 HTTPS 테스트 서버**를 가리켜야 합니다.
 */
class ConceptAiIntegrationTest {
    private val syncSettings = ServerSyncSettings(
        InstrumentationRegistry.getArguments().getString("testServerUrl").orEmpty(),
        InstrumentationRegistry.getArguments().getString("testServerApiKey").orEmpty()
    )
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before fun setUp() {
        assumeTrue("CLI가 깔린 PC의 HTTPS 테스트 서버가 필요합니다.", syncSettings.serverUrl.isNotBlank())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = AppRepository(
            database = database,
            contentItemDao = database.contentItemDao(),
            exposureEventDao = database.exposureEventDao(),
            routineDao = database.routineDao(),
            routineCheckDao = database.routineCheckDao(),
            routineMemoDao = database.routineMemoDao(),
            contentMemoDao = database.contentMemoDao(),
            linkMetadataFetcher = LinkMetadataFetcher(),
            deletionDao = database.deletionDao()
        )
    }

    @After fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test fun claudeCliExplainsADirectlyWrittenConceptAndTheAnswerIsSavedShort() = runBlocking {
        explainWith(AiFeedbackSettings(provider = AiProvider.CLAUDE_CLI.name, model = "claude-haiku-4-5"))
    }

    @Test fun codexCliWithItsDefaultModelExplainsAConcept() = runBlocking {
        // 모델을 비워 보내는 길입니다. 서버가 `--model` 없이 돌려야 합니다.
        explainWith(AiFeedbackSettings(provider = AiProvider.CODEX_CLI.name, model = AiProvider.CLI_DEFAULT_MODEL))
    }

    private suspend fun explainWith(aiSettings: AiFeedbackSettings) {
        // 임시저장소를 거치지 않고 개념 탭에 바로 적은 것입니다.
        val id = database.contentItemDao().insert(
            ContentItemEntity(type = ContentType.CONCEPT, title = "인지 부하", body = "인지 부하")
        )
        val item = database.contentItemDao().getById(id)!!

        val rawAnswer = AiFeedbackClient().ask(
            system = ConceptExplanation.systemPrompt(),
            user = ConceptExplanation.userPrompt(item),
            model = aiSettings.effectiveModel,
            aiSettings = aiSettings,
            syncSettings = syncSettings
        )
        val explanation = ConceptExplanation.parse(rawAnswer)
        val concept = repository.saveConceptExplanation(id, explanation)

        assertEquals(ContentType.CONCEPT, concept.type)
        val memo = database.contentMemoDao().getByContentItemId(id).single()
        val stored = ConceptExplanation.fromMemo(memo.body)!!
        assertTrue(stored, stored.contains("한눈에 보기"))
        // 장황하면 카드가 접힙니다. 물음에 짧게 달라 했고, 넘겨 와도 잘라 둡니다.
        assertTrue("너무 깁니다(${stored.length}자): $stored", stored.length <= 1_201)
        assertTrue("빈 갈래는 이름표만 남기지 않습니다: $stored", !stored.contains("\n\n예시\n\n"))
    }
}
