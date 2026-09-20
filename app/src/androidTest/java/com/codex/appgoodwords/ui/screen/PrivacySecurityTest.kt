package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.AttachmentKind
import com.codex.appgoodwords.data.AttachmentShot
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.DiaryEntity
import com.codex.appgoodwords.data.SearchKind
import com.codex.appgoodwords.ui.AppGoodWordsApp
import com.codex.appgoodwords.ui.AppViewModelFactory
import com.codex.appgoodwords.ui.MainViewModel
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 실물 휴대폰 대신 가짜 일기를 넣은 에뮬레이터에서 잠금 우회를 확인합니다. */
class PrivacySecurityTest {
    @get:Rule val compose = createComposeRule()
    private val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AppGoodWordsApplication).container
    private val stores = mutableListOf<ViewModelStore>()
    private val diaryIds = mutableListOf<Long>()
    private val privateBody = "SECURITY_CANARY private diary body"

    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { stores.forEach { it.clear() } }
        runBlocking {
            diaryIds.forEach { container.database.diaryDao().deleteById(it) }
            container.settingsStore.setAppLockEnabled(false)
            container.settingsStore.setDiaryLockEnabled(false)
        }
    }

    @Test fun appSearchDoesNotExposeALockedDiary() {
        val vm = openApp()
        compose.waitUntil(15_000) { vm.lockState.value == AppLockState.OPEN && vm.diaryLockEnabled.value && vm.diaries.value.any { it.body == privateBody } }
        compose.onNodeWithContentDescription("검색").performClick()
        compose.onNodeWithTag(searchInputTag).performTextInput("SECURITY_CANARY")
        compose.onNodeWithText(privateBody).assertDoesNotExist()
        compose.onNodeWithTag("unlock_diary_access").assertExists()
        // 인증할 Activity가 없는 테스트 환경에서는 실패한 채로 잠금을 유지해야 합니다.
        compose.onNodeWithTag("unlock_diary_access").performClick()
        compose.onNodeWithText(privateBody).assertDoesNotExist()
    }

    @Test fun searchOnlyShowsPrivateResultsAfterSuccessfulAuthentication() {
        val locked = mutableStateOf(true)
        var approved = false
        var requests = 0
        compose.setContent {
            SearchScreen(
                items = listOf(ContentItemEntity(id = 1, type = ContentType.QUOTE, title = "public", body = "SECURITY_CANARY public quote")),
                diaries = listOf(DiaryEntity(id = 2, entryDate = "2026-09-13", body = privateBody)),
                todos = emptyList(), books = emptyList(), routines = emptyList(), onOpenHit = {},
                diaryLocked = locked.value,
                onUnlockDiaries = { requests++; if (approved) locked.value = false }
            )
        }
        compose.onNodeWithTag(searchInputTag).performTextInput("SECURITY_CANARY")
        compose.onNodeWithText("SECURITY_CANARY public quote").assertExists()
        compose.onNodeWithText(privateBody).assertDoesNotExist()
        compose.onNodeWithTag("unlock_diary_access").performClick()
        compose.onNodeWithText(privateBody).assertDoesNotExist()
        approved = true
        compose.onNodeWithTag("unlock_diary_access").performClick()
        compose.onNodeWithText(privateBody).assertExists()
        assertEquals(2, requests)
        compose.runOnIdle { locked.value = true }
        compose.onNodeWithText(privateBody).assertDoesNotExist()
    }

    @Test fun galleryHidesDiaryAttachmentsUntilAuthentication() {
        val locked = mutableStateOf(true)
        compose.setContent {
            AttachmentGalleryScreen(
                shots = listOf(
                    AttachmentShot("content://synthetic/private", AttachmentKind.IMAGE, SearchKind.DIARY, 1, LocalDate.of(2026, 5, 1), "private"),
                    AttachmentShot("content://synthetic/public", AttachmentKind.IMAGE, SearchKind.QUOTE, 2, LocalDate.of(2025, 4, 1), "public")
                ),
                onOpenSource = {}, diaryLocked = locked.value,
                onUnlockDiaries = { locked.value = false }
            )
        }
        compose.onNodeWithText("2026년 5월 · 1장").assertDoesNotExist()
        compose.onNodeWithText("2025년 4월 · 1장").assertExists()
        compose.onNodeWithTag("unlock_diary_access").performClick()
        compose.onNodeWithText("2026년 5월 · 1장").assertExists()
        compose.runOnIdle { locked.value = true }
        compose.onNodeWithText("2026년 5월 · 1장").assertDoesNotExist()
    }

    @Test fun anExpandedDiaryIsHiddenWhenTheAccessIsRevoked() {
        val locked = mutableStateOf(true)
        compose.setContent {
            DiaryScreen(
                diaries = listOf(DiaryEntity(id = 9, entryDate = "2026-09-13", title = "private title", body = privateBody)),
                today = LocalDate.now(), onSaveDiary = {}, onDeleteDiary = {}, locked = locked.value,
                onRequestUnlock = { accepted -> locked.value = false; accepted() }
            )
        }
        compose.onNodeWithTag(diaryCardTag(9)).performClick()
        compose.onNodeWithText(privateBody).assertExists()
        compose.runOnIdle { locked.value = true }
        compose.onNodeWithText(privateBody).assertDoesNotExist()
        compose.onNodeWithText("private title").assertDoesNotExist()
    }

    @Test fun settingsCannotDisableTheDiaryLockOrExportBeforeAuthentication() {
        val vm = openApp()
        compose.waitUntil(15_000) { vm.lockState.value == AppLockState.OPEN && vm.diaryLockEnabled.value }
        compose.onNodeWithContentDescription("설정").performClick()
        compose.onNodeWithTag("unlock_diary_access").assertExists()
        compose.onNodeWithTag(diaryLockSwitchTag).assertDoesNotExist()
        compose.onNodeWithText("데이터 내보내기").assertDoesNotExist()
    }

    @Test fun daySummaryIsRemovedWhenTheAppRelocks() {
        val vm = openApp()
        compose.waitUntil(15_000) { vm.lockState.value == AppLockState.OPEN && vm.diaries.value.any { it.body == privateBody } }
        compose.runOnIdle { vm.openDay(LocalDate.now()); vm.setAppLockEnabled(true) }
        compose.waitUntil(10_000) { vm.appLockEnabled.value && vm.openedDayDigest.value?.diaryTitles?.isNotEmpty() == true }
        compose.onNodeWithTag(daySummaryDialogTag).assertIsDisplayed()
        compose.onNodeWithText("Security review diary", substring = true).assertDoesNotExist()
        compose.runOnIdle { vm.onAppBackgrounded() }
        Thread.sleep(60_100)
        compose.runOnIdle { vm.onAppForegrounded() }
        compose.waitUntil(10_000) { vm.lockState.value == AppLockState.LOCKED }
        compose.onNodeWithTag(daySummaryDialogTag).assertDoesNotExist()
        compose.onNodeWithTag(appLockUnlockButtonTag).assertExists()
    }

    private fun openApp(): MainViewModel {
        runBlocking {
            container.settingsStore.setAppLockEnabled(false)
            container.settingsStore.setDiaryLockEnabled(true)
            diaryIds += container.database.diaryDao().insert(DiaryEntity(entryDate = LocalDate.now().toString(), title = "Security review diary", body = privateBody))
        }
        lateinit var vm: MainViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val store = ViewModelStore().also { stores += it }
            vm = ViewModelProvider(store, AppViewModelFactory(container))[MainViewModel::class.java]
        }
        compose.setContent { AppGoodWordsApp(vm) }
        return vm
    }
}
