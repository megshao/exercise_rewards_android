package com.megshao.exerciserewards

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.megshao.exerciserewards.data.DemoMode
import com.megshao.exerciserewards.data.DisclaimerConsent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 示範模式的端到端流程測試。
 *
 * **這組測試存在的理由**：Android 端的 UI 有 12 個畫面、每一頁都有「載入中／空／錯誤／
 * 正常」四種樣子，用手點一遍要十分鐘，而且點漏一頁不會有人知道。示範模式（[DemoMode]）
 * 本來就是為了「不連網也能走完整條路」而存在——正好讓這條路徑可以被自動化。
 *
 * 對應 iOS 端的 `App/UITests/ScreenshotTests.swift`。
 *
 * **刻意用示範模式而不是 mock 注入**：示範模式是產品本來就有的功能，商店審查員走的也是
 * 這條路。用它當測試入口，等於同時驗證了「審查員會看到什麼」。
 *
 * ## 這裡踩過的三個坑（改測試前先讀）
 *
 * 1. **`createAndroidComposeRule` 會在 `@Before` 之前就啟動 Activity 並組完畫面。**
 *    在 `@Before` 裡改偏好不會讓已經組好的畫面重畫，所以每個測試都要自己
 *    `scenario.recreate()`。
 * 2. **示範模式的旗標由 [AppContainer] 在 process 啟動時讀一次。** 只寫 SharedPreferences
 *    不會讓已經活著的 container 換環境——必須直接呼叫 `container.enterDemo()`。
 *    先前靠「下一個測試剛好碰到 process 重啟」才過，那是假通過。
 * 3. **placeholder 是獨立的 Text 節點，不是輸入框。** 對它 `performTextInput` 會失敗，
 *    所以欄位都掛了 `testTag`（見 `ProfileField`）。
 */
@RunWith(AndroidJUnit4::class)
class DemoFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container: AppContainer
        get() = (
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as ExerciseRewardsApplication
            ).container

    @Before
    fun resetToFirstRun() {
        // 每次都從乾淨狀態開始，才驗得到首次啟動的三頁。
        container.exitDemo()
        container.preferences.resetToFirstRun()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test
    fun walksFromWelcomeToTheMainTabsUsingTheDemoAccount() {
        // 1. 歡迎頁
        rule.onNodeWithText("Exercise Rewards").assertIsDisplayed()
        rule.onNodeWithText("開始使用").performClick()

        // 2. 免責聲明：三個區塊都要在，而且沒勾同意就不能繼續
        rule.onNodeWithText("使用前請先確認").assertIsDisplayed()
        rule.onNodeWithText("這不是官方 App").assertIsDisplayed()
        rule.onNodeWithTag("disclaimer.agree").performClick()
        rule.onNodeWithText("同意並開始使用").performClick()

        // 3. 個資表單：填示範帳號
        rule.onNodeWithText("填寫個人資料").assertIsDisplayed()
        rule.onNodeWithTag("field.身分證號").performTextInput(DemoMode.ID_NO)
        rule.onNodeWithTag("field.手機號碼").performTextInput(DemoMode.PHONE)

        // 出生日期走滾輪：預設就是 1990-01-01，也就是示範帳號要的值。
        // 這一步同時驗證滾輪的「中央那一項就是選取值」——那裡踩過坑（見 WheelPicker）。
        rule.onNodeWithTag("field.birthDate").performClick()
        rule.onNodeWithText("1990 年 1 月 1 日（民國 79 年）").assertIsDisplayed()
        rule.onNodeWithText("完成").performClick()
        rule.onNodeWithText("1990 年 1 月 1 日").assertIsDisplayed()

        rule.onNodeWithText("送出並驗證").performClick()

        // 4. 進入主畫面：示範模式橫幅 + 首頁
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("本週任務").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("示範模式 · 畫面為範例資料，未連線官方網站").assertIsDisplayed()
        rule.onNodeWithText("加碼券").assertIsDisplayed()
    }

    @Test
    fun showsTheFourteenPeriodsOnTheTasksTab() {
        enterDemoMode()

        rule.onNodeWithText("任務").performClick()
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("第 6 期").fetchSemanticsNodes().isNotEmpty()
        }

        // 當期（第 6 期）置頂高亮，狀態是「未上傳」並有上傳 CTA。
        rule.onNodeWithText("第 6 期").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("上傳運動紀錄").performScrollTo().assertIsDisplayed()

        // 已兌換的期別在下面，捲下去要看得到。
        rule.onNodeWithText("第 1 期").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun walletSeparatesRedeemableAndUsableVouchers() {
        enterDemoMode()

        rule.onNodeWithText("券夾").performClick()
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("可兌換").fetchSemanticsNodes().isNotEmpty()
        }

        // 「可兌換」有兩個節點：券夾的區塊標題（WalletScreen 的 SectionTitle）與卡片上的
        // 狀態徽章（StatusBadge）。這裡要驗的是**區塊**存在，取第一個（組合順序上標題在前）。
        rule.onAllNodesWithText("可兌換").onFirst().performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("可使用的加碼券").performScrollTo().assertIsDisplayed()

        // 標記一張為已使用 → 應該移到「已使用」區並換成「還原成未使用」
        rule.onAllNodesWithText("標記為已使用").onFirst().performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("還原成未使用").fetchSemanticsNodes().isNotEmpty()
        }
        // 同樣不要用「已使用」：它既是區塊標題也是卡片徽章（兩個節點）。這一句只在
        // 已使用的卡片裡出現，所以它證明的是**卡片真的移到已使用狀態**，比「有個叫已使用
        // 的節點」精確。（ScreenshotTest 的取景捲動踩過同一個坑，兩邊用同一個目標。）
        rule.onNodeWithText("你在 App 內標記為已使用", substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun redeemRequiresASecondConfirmation() {
        enterDemoMode()

        rule.onNodeWithText("券夾").performClick()
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("去兌換").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("去兌換").onFirst().performScrollTo().performClick()

        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("全家便利商店").fetchSemanticsNodes().isNotEmpty()
        }

        // ⚠️ 兌換會消耗真實次數且不可更換，所以列表上的「兌換」只是開確認框，不會直接送出。
        rule.onAllNodesWithText("兌換").onFirst().performClick()
        // **不要用「確認兌換」當斷言目標**：對話框的標題與確認按鈕都是這四個字，會撞到兩個
        // 節點。改用對話框內文裡唯一的那一句——而且它更精確地表達了這條測試要驗的東西：
        // 出現的是**二次確認**，不只是某個叫「確認兌換」的節點。
        rule.onNodeWithText("兌換後不可更換", substring = true).assertIsDisplayed()
        rule.onNodeWithText("取消").performClick()
        // 取消後仍停在清單，沒有送出。
        rule.onNodeWithText("全家便利商店").assertIsDisplayed()
    }

    @Test
    fun voucherAlwaysStartsFromOtpAndShowsBarcodesAfterVerifying() {
        enterDemoMode()

        rule.onNodeWithText("券夾").performClick()
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("檢視券碼").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("檢視券碼").onFirst().performScrollTo().performClick()

        // 合規要求：每次進來都要重走一次 OTP，畫面一律從這裡開始。
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("發送簡訊驗證碼").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("發送簡訊驗證碼").performClick()

        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("簡訊驗證出示券碼").fetchSemanticsNodes().isNotEmpty()
        }
        // 示範模式任何 6 碼都通過。
        rule.onNodeWithTag("field.otp").performTextInput("123456")
        rule.onAllNodesWithText("檢視券碼").onFirst().performClick()

        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("① 商品條碼").fetchSemanticsNodes().isNotEmpty()
        }
        // 兩段式券：兩段都要在（缺一不可）。
        rule.onNodeWithText("① 商品條碼").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("② 券號條碼").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("已經在門市用掉了嗎？").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profileShowsTheDemoExitAndThePrivacyControls() {
        enterDemoMode()

        rule.onNodeWithContentDescription("我的資料").performClick()
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("個資不外傳，只在登入時送給官方網站").fetchSemanticsNodes().isNotEmpty()
        }

        // 示範模式下才會出現的那一列。
        rule.onNodeWithText("離開示範模式").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("傳送匿名使用統計").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("立即清除本機資料").performScrollTo().assertIsDisplayed()

        // 示範模式的個資是預先填好的（只在記憶體，絕不落盤）。
        rule.onAllNodesWithTag("field.身分證號").fetchSemanticsNodes().isNotEmpty()
    }

    /**
     * 直接進到主畫面的示範模式（跳過首次啟動三頁，那條路由第一個測試負責）。
     *
     * `container.enterDemo()` 是必要的：只寫偏好不會讓已經活著的 container 換環境（見檔頭）。
     */
    private fun enterDemoMode() {
        container.preferences.apply {
            hasSeenWelcome = true
            disclaimerAgreedVersion = DisclaimerConsent.CURRENT_VERSION
            hasCompletedOnboarding = true
        }
        container.enterDemo()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }
}
