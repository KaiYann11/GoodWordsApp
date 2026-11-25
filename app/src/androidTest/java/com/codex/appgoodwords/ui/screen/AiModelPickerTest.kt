package com.codex.appgoodwords.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.AiModelInfo
import com.codex.appgoodwords.data.AiModelList
import com.codex.appgoodwords.data.AiModelRefreshState
import com.codex.appgoodwords.data.AiModels
import com.codex.appgoodwords.data.AiProvider
import com.codex.appgoodwords.data.ReminderSettings
import com.codex.appgoodwords.data.ServerSyncSettings
import com.codex.appgoodwords.data.SyncStatus
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 설정 → AI의 모델 칸.
 *
 * 모델이 앱에 박혀 있어 새 모델이 나올 때마다 앱을 다시 게시해야 했습니다. 이제 계열 칸("Sonnet 최신")을 고르고, 이름은
 * 받아 온 목록에서 고르거나 적습니다(사용자가 고름, 2026-11-25).
 */
class AiModelPickerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var settings by mutableStateOf(AiFeedbackSettings())
    private var refreshState by mutableStateOf(AiModelRefreshState())
    private val refreshes = mutableListOf<Pair<AiProvider, Boolean>>()

    private val claudeList = AiModelList(
        models = listOf(
            AiModelInfo("claude-fable-5-2", "Claude Fable 5.2"),
            AiModelInfo("claude-opus-5-5", "Claude Opus 5.5"),
            AiModelInfo("claude-sonnet-5-5", "Claude Sonnet 5.5"),
            AiModelInfo("claude-haiku-5-5", "Claude Haiku 5.5")
        ),
        fetchedAt = System.currentTimeMillis()
    )

    @Test
    fun claudeOffersTheLatestOfEachFamilyAndSaysWhichModelThatIs() {
        show(AiFeedbackSettings().withProvider(AiProvider.ANTHROPIC), mapOf(AiProvider.ANTHROPIC to claudeList))

        // 칸을 열면 목록이 묵었는지 보러 갑니다(받은 지 얼마 안 됐으면 저장소가 그대로 둡니다).
        compose.waitForIdle()
        assertTrue(refreshes.toString(), AiProvider.ANTHROPIC to false in refreshes)
        // 기본은 Opus 최신입니다.
        chip(AiModels.latest("opus")).performScrollTo().assertIsSelected()
        listOf("sonnet", "haiku", "fable").forEach { chip(AiModels.latest(it)).assertIsNotSelected() }
        compose.onNodeWithTag(aiModelNowTag).assertTextContains("claude-opus-5-5", substring = true)

        chip(AiModels.latest("sonnet")).performClick()

        assertEquals(AiModels.latest("sonnet"), settings.model)
        compose.onNodeWithTag(aiModelNowTag)
            .assertTextContains("지금 답하는 모델: claude-sonnet-5-5", substring = true)
            .assertTextContains("Sonnet 다음 세대가 나오면 저절로 바뀝니다", substring = true)
    }

    @Test
    fun aNamePickedFromTheFetchedListIsSavedAsItIs() {
        show(AiFeedbackSettings().withProvider(AiProvider.ANTHROPIC), mapOf(AiProvider.ANTHROPIC to claudeList))

        compose.onNodeWithTag(aiModelOtherTag).performScrollTo().performClick()
        compose.onNodeWithTag(aiModelDialogSourceTag).assertTextContains("Claude 목록", substring = true)
        compose.onNodeWithTag(aiModelRowTag("claude-fable-5-2")).performClick()
        compose.onNodeWithTag(aiModelDialogFieldTag).assertTextContains("claude-fable-5-2")
        compose.onNodeWithTag(aiModelDialogConfirmTag).performClick()

        assertEquals("claude-fable-5-2", settings.model)
        // 계열 칸이 아니라 이름 칸이 하나 더 생겨 골라져 있습니다.
        chip("claude-fable-5-2").performScrollTo().assertIsSelected()
        chip(AiModels.latest("fable")).assertIsNotSelected()
        compose.onNodeWithTag(aiModelNowTag).assertTextContains("새 모델이 나와도 바뀌지 않습니다", substring = true)
    }

    @Test
    fun claudeCliTakesAliasesAndATypedName() {
        show(AiFeedbackSettings().withProvider(AiProvider.CLAUDE_CLI), emptyMap())

        chip("").performScrollTo().assertIsSelected()
        listOf("opus", "sonnet", "haiku").forEach { chip(AiModels.latest(it)).assertIsDisplayed() }
        // 구독에서 막혀 있던 Fable은 칸에 없습니다. 켜 두었으면 적어서 고릅니다.
        chip(AiModels.latest("fable")).assertDoesNotExist()

        compose.onNodeWithTag(aiModelOtherTag).performClick()
        // Claude CLI에는 받을 목록이 없어 새로 받기도 없습니다.
        compose.onNodeWithTag(aiModelDialogRefreshTag).assertDoesNotExist()
        compose.onNodeWithTag(aiModelDialogFieldTag).performTextInput("fable")
        compose.onNodeWithTag(aiModelDialogConfirmTag).performClick()

        assertEquals("fable", settings.model)
        assertEquals("fable", settings.requestModel(emptyList()))
    }

    @Test
    fun theRefreshButtonAsksForAFreshListAndAFailureIsShown() {
        show(AiFeedbackSettings().withProvider(AiProvider.OLLAMA), emptyMap())

        compose.onNodeWithTag(aiModelOtherTag).performScrollTo().performClick()
        compose.onNodeWithText("아직 목록을 받지 못해 앱에 든 이름입니다.").assertIsDisplayed()
        compose.onNodeWithTag(aiModelDialogRefreshTag).performClick()

        assertTrue(refreshes.toString(), AiProvider.OLLAMA to true in refreshes)

        refreshState = AiModelRefreshState(errors = mapOf(AiProvider.OLLAMA to "Ollama에 연결하지 못했습니다."))
        compose.onNodeWithText("목록을 받지 못했습니다: Ollama에 연결하지 못했습니다.").assertIsDisplayed()
        // 이름을 적지 않으면 고를 수 없습니다.
        compose.onNodeWithTag(aiModelDialogConfirmTag).performClick()
        assertEquals("", settings.model)
    }

    private fun chip(choice: String) = compose.onNodeWithTag(aiModelChoiceTag(choice))

    /** 실제 앱처럼 설정을 밖에서 들고 고친 것을 다시 넘깁니다. */
    private fun show(initial: AiFeedbackSettings, lists: Map<AiProvider, AiModelList>) {
        settings = initial
        compose.setContent {
            AppGoodWordsTheme {
                SettingsScreen(
                    settings = ReminderSettings(),
                    serverSyncSettings = ServerSyncSettings(),
                    syncStatus = SyncStatus(),
                    categories = emptyList(),
                    syncBackups = emptyList(),
                    syncBackupDirectory = "",
                    onSettingsChanged = {},
                    onServerSyncSettingsChanged = {},
                    onSendTestNotification = {},
                    onResetViewCounts = {},
                    onExportRequested = {},
                    onImportRequested = {},
                    onTestServerConnection = {},
                    onSyncWithServer = {},
                    onUploadToServer = {},
                    onDownloadFromServer = {},
                    onRestoreBackup = {},
                    onDeleteCategory = {},
                    aiFeedbackSettings = settings,
                    onAiFeedbackSettingsChanged = { settings = it },
                    aiModelLists = lists,
                    aiModelRefresh = refreshState,
                    onRefreshAiModels = { provider, force -> refreshes += provider to force },
                    section = SettingsSection.AI,
                    onSectionChange = {}
                )
            }
        }
    }
}
