package com.megshao.exerciserewards

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.megshao.exerciserewards.data.DemoMode
import com.megshao.exerciserewards.telemetry.Telemetry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 上架素材用的原始畫面截圖擷取流程（Google Play 需要至少 2 張手機直向截圖）。
 *
 * 對應 iOS 端的 `App/UITests/ScreenshotTests.swift`，**檔名刻意與那邊一致**
 * （`00-welcome`、`03-home`、`08b-vendor-intro`…，含 iOS 也跳過的 `05`），
 * 這樣兩個 repo 的 `docs/screenshots/raw/` 可以直接並排對照。
 *
 * 這支測試**只負責拍照，不做斷言式驗收**：它依序把 App 導覽到每一個要交付的畫面，
 * 等畫面穩定後用 `UiAutomation.takeScreenshot()` 拍全螢幕（含狀態列），寫成 PNG。
 * 驗收行為是 [DemoFlowTest] 的事；兩者刻意分開，因為「素材產不出來」與「功能壞了」
 * 是兩種不同的失敗，混在一起會讓人分不清該修哪個。
 *
 * 一律在**示範模式**下拍攝：全部服務換成 `Mock*Service`，不發任何網路請求，畫面上是穩定的
 * 假資料，**也不會有任何真實個資出現在素材裡**。這一點不是順帶的好處——上架素材會公開，
 * 用真帳號拍就等於把個資放上商店頁。
 *
 * ## 為什麼要開 `Telemetry.isScreenshotMode`
 *
 * 示範模式平常會在畫面頂端掛一條「示範模式 · 畫面為範例資料，未連線官方網站」的橫幅
 * （[com.megshao.exerciserewards.ui.AppNavigation]）。那條橫幅對**使用者與商店審查員**
 * 必須永遠出現——他們進示範模式時看到的就該是「這是範例資料」。
 * 但商店截圖裡不該有它，所以截圖模式只把橫幅收起來，其餘一切不變。
 * 順帶一提，截圖模式也是遙測六道閘門的第二道：拍照過程中一筆事件都不會送出。
 *
 * ## 重跑指令
 *
 * ```sh
 * ANDROID_SERIAL=<emulator-5554> ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.megshao.exerciserewards.ScreenshotTest
 * # 然後把 PNG 拉回來（Android 的測試程序寫不到 host 路徑，這點與 iOS 模擬器不同）：
 * adb pull /sdcard/Android/data/com.megshao.exercise_rewards.debug/files/screenshots docs/screenshots/raw
 * ```
 *
 * 跑之前建議先把狀態列固定成乾淨樣子（Android 版的 `simctl status_bar override`）：
 *
 * ```sh
 * adb shell settings put global sysui_demo_allowed 1
 * adb shell am broadcast -a com.android.systemui.demo -e command enter
 * adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
 * adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
 * adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
 * adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
 * ```
 *
 * 拍完的原始圖再交給美化流程套裝置框與標語（skill `aso-cosmicmeta-ss`，
 * 它的 `assets/android_frame.png` 就是給這件事用的）。
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val container: AppContainer
        get() = (
            instrumentation.targetContext.applicationContext as ExerciseRewardsApplication
            ).container

    /**
     * 落檔目錄：App 自己的 external files（不需要任何權限，解除安裝時一起消失）。
     *
     * **不能像 iOS 那樣直接寫 host 的絕對路徑**——那是 iOS 模擬器與 host 共用檔案系統的
     *特性，Android 的測試跑在裝置／模擬器裡，寫不到 host。所以拍完要 `adb pull`（見檔頭）。
     */
    private val outputDir: File by lazy {
        File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    }

    private val written = mutableListOf<String>()
    private val framingFailures = mutableListOf<String>()

    @Before
    fun enterScreenshotMode() {
        // 橫幅收起來（見檔頭），並且拍照過程中不送任何遙測。
        Telemetry.isScreenshotMode = true

        // 從乾淨狀態開始，才拍得到首次啟動的三頁。
        container.exitDemo()
        container.preferences.resetToFirstRun()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @After
    fun leaveScreenshotMode() {
        Telemetry.isScreenshotMode = false
        if (written.isEmpty()) {
            println("⚠️ 一張都沒寫出來，檢查 $outputDir 是否可寫")
        } else {
            println("📸 已寫出 ${written.size} 張截圖到 $outputDir：\n" + written.joinToString("\n"))
        }
        // **診斷一定要寫成檔案。** `println` 在 instrumented test 裡只進 logcat，
        // `am instrument -r` 不會轉送，所以印出來的警告在擷取管線裡**完全看不到**
        // （踩過：警告確實產生了，但日誌裡一個字都沒有）。寫成檔案讓 host 端讀。
        val report = File(outputDir, FRAMING_REPORT)
        if (framingFailures.isEmpty()) {
            report.delete()
        } else {
            report.writeText(framingFailures.joinToString("\n"))
            println("⚠️ 取景捲動有 ${framingFailures.size} 處失敗，已寫入 $report")
        }
    }

    @Test
    fun capturesEveryStoreScreen() {
        // ---- 首次啟動的三頁 ----
        awaitText("開始使用")
        capture("00-welcome")
        rule.onNodeWithText("開始使用").performClick()

        awaitText("使用前請先確認")
        capture("01-disclaimer")
        rule.onNodeWithTag("disclaimer.agree").performClick()
        rule.onNodeWithText("同意並開始使用").performClick()

        awaitText("填寫個人資料")
        // 示範帳號（哨兵值）。真帳號絕不能出現在上架素材裡。
        rule.onNodeWithTag("field.身分證號").performTextInput(DemoMode.ID_NO)
        rule.onNodeWithTag("field.手機號碼").performTextInput(DemoMode.PHONE)

        // **出生日期一定要走滾輪並按「完成」。**
        // 滾輪的預設值就是示範帳號要的 1990-01-01，但**不打開、不按完成就不會寫進 draft**，
        // 於是三碼對不上、登入失敗，後面 awaitText("本週任務") 會逾時 30 秒才報錯——
        // 那個錯誤看起來像「首頁壞了」，其實是這裡少一步。（第一次跑就是死在這裡。）
        rule.onNodeWithTag("field.birthDate").performClick()
        awaitText("1990 年 1 月 1 日（民國 79 年）")
        // Android 才有的一張：民國／西元雙顯示的滾輪。iOS 端用系統 DatePicker，沒有對應畫面，
        // 所以這個編號在 iOS 那邊不存在——刻意的例外，其餘檔名維持與 iOS 一致。
        capture("02b-birthdate")
        rule.onNodeWithText("完成").performClick()
        awaitText("1990 年 1 月 1 日")

        capture("02-login")
        rule.onNodeWithText("送出並驗證").performClick()

        // ---- 主畫面三個分頁 ----
        awaitText("本週任務")
        capture("03-home")

        rule.onNodeWithText("任務").performClick()
        awaitText("第 6 期")
        capture("04-tasks")
        // 捲到最舊的一期，讓「已兌換」狀態也入鏡。
        // **不要用「第 1 期」當目標**：示範資料把當期（第 6 期）置頂，第 1 期緊接在下面、
        // 本來就在第一屏可見，`performScrollTo` 對已可見的節點是 no-op，於是這一張會與
        // 04-tasks 位元組完全相同（實測踩過）。第 14 期在最底部，一定要捲。
        scrollToForFraming("第 14 期")
        capture("04b-tasks-scrolled")

        // ---- 上傳與看截圖（都從任務頁進） ----
        scrollToForFraming("第 6 期")
        rule.onNodeWithText("上傳運動紀錄").performScrollTo().performClick()
        awaitText("從相簿選擇截圖")
        // 空的上傳頁有七成畫面是空白，當商店素材說服力很低。塞一張真的運動紀錄截圖進去，
        // 拍到的是使用者實際會看到的「已選圖」狀態。
        pickDemoExerciseRecord()
        awaitText("確認上傳")
        capture("06-upload")
        pressBack()

        awaitText("第 6 期")
        rule.onAllNodesWithText("看截圖").onFirst().performScrollTo().performClick()
        // 這一頁整頁只有一張全螢幕 Image，**沒有任何 Text 節點**，
        // 所以要等 contentDescription 而不是文字（踩過：awaitText 在這裡一定逾時）。
        awaitContentDescription("上傳的運動紀錄截圖")
        capture("07-task-screenshot")
        pressBack()

        // ---- 券夾、兌換、廠商品項 ----
        awaitText("券夾")
        rule.onNodeWithText("券夾").performClick()
        awaitText("可使用的加碼券")
        capture("09-wallet")

        rule.onAllNodesWithText("去兌換").onFirst().performScrollTo().performClick()
        awaitText("全家便利商店")
        capture("08-redeem")

        rule.onAllNodesWithText("兌換品項").onFirst().performScrollTo().performClick()
        awaitText("兌換注意事項")
        capture("08b-vendor-intro")
        pressBack()

        awaitText("全家便利商店")
        pressBack()

        // ---- 券碼：OTP 關卡 → 兩段式條碼 ----
        awaitText("可使用的加碼券")
        rule.onAllNodesWithText("檢視券碼").onFirst().performScrollTo().performClick()
        awaitText("發送簡訊驗證碼")
        rule.onNodeWithText("發送簡訊驗證碼").performClick()
        awaitText("簡訊驗證出示券碼")
        // 示範模式任何 6 碼都通過。
        rule.onNodeWithTag("field.otp").performTextInput("123456")
        rule.onAllNodesWithText("檢視券碼").onFirst().performClick()
        awaitText("① 商品條碼")
        capture("10-voucher")
        pressBack()

        // ---- 券夾的「已使用」樣子 ----
        awaitText("可使用的加碼券")
        rule.onAllNodesWithText("標記為已使用").onFirst().performScrollTo().performClick()
        awaitText("還原成未使用")
        // 不要用「已使用」當目標：那個字同時是區塊標題（SectionTitle）與卡片上的狀態徽章
        // （StatusBadge），會撞到兩個節點。這一句唯一，而且剛好把已使用的卡片帶進畫面。
        scrollToForFraming("你在 App 內標記為已使用（本機紀錄顯示，使用與否以條碼能否使用為主）。")
        capture("09b-wallet-used")

        // ---- 我的資料 ----
        // **入口只在首頁的標題列**（`HomeScreen.kt` 唯一的 contentDescription = "我的資料"），
        // 券夾與任務分頁沒有那顆頭像。流程走到這裡時停在券夾，所以要先回首頁。
        rule.onNodeWithText("首頁").performClick()
        awaitText("本週任務")
        rule.onNodeWithContentDescription("我的資料").performClick()
        awaitText("個資不外傳，只在登入時送給官方網站")
        capture("11-profile")
        scrollToForFraming("傳送匿名使用統計")
        capture("12-profile-security")
    }

    // MARK: - 工具

    /**
     * 等某段文字出現。
     *
     * Mock 服務刻意帶 0.4~0.5 秒延遲（模擬真實網路），所以每一次換頁都要等；
     * 逾時給得比 [DemoFlowTest] 寬鬆，因為模擬器在冷啟動後的頭幾秒特別慢。
     */
    private fun awaitText(text: String) {
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 同上，但等的是 `contentDescription`（純圖片畫面沒有文字節點可等）。 */
    private fun awaitContentDescription(description: String) {
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * 讓上傳頁進入「已選好一張截圖」的狀態。
     *
     * 上傳頁走的是系統相片選擇器（`ActivityResultContracts.PickVisualMedia`），那是**另一個
     * 行程的 Activity**，Compose 的測試規則碰不到它。所以這裡用 espresso-intents 攔下那個
     * Intent，直接回一個指向測試資產的 `file://` URI。
     *
     * **刻意不改產品程式碼、也不在事後合成假畫面**：
     * - 不改產品碼——為了拍照在 `UploadScreen` 開一個後門，是拿產品的正確性換素材。
     * - 不合成——那樣商店上的截圖會是 App 從未真正渲染過的狀態，那是造假。
     *
     * 這條路還有一個附帶好處：`onPicked` 之後的 [ImageReencoder] 是**真的跑過**的，
     * 所以這張截圖同時證明了「重新編碼後仍然顯示得出來」。
     *
     * 資產是一張真實的步數統計截圖，EXIF 已在進版控前清除（本來也會被 ImageReencoder 去掉）。
     */
    private fun pickDemoExerciseRecord() {
        // **兩個 context 不能混。** 資產打包在**測試 APK** 裡，要用
        // `instrumentation.context`；`targetContext` 是被測 App，它的 assets 裡沒有這個檔案
        // （踩過：FileNotFoundException: demo-exercise-record.png）。
        // 檔案要寫到**被測 App** 的 cache，因為之後是它在讀這個 file:// URI。
        val testContext = instrumentation.context
        val appContext = instrumentation.targetContext
        val file = File(appContext.cacheDir, DEMO_RECORD_ASSET).apply {
            outputStream().use { out ->
                testContext.assets.open(DEMO_RECORD_ASSET).use { it.copyTo(out) }
            }
        }
        val result = Instrumentation.ActivityResult(
            android.app.Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(file)),
        )
        Intents.init()
        try {
            Intents.intending(IntentMatchers.anyIntent()).respondWith(result)
            rule.onNodeWithText("從相簿選擇截圖").performClick()
            rule.waitForIdle()
        } finally {
            // 一定要 release，否則後面每一個 Activity 啟動都會被這個 stub 攔下來。
            Intents.release()
        }
    }

    /**
     * 為了取景而捲動，**失敗不讓整個流程掛掉**。
     *
     * 這種捲動只影響「畫面上看得到什麼」，不影響畫面本身對不對。真正的導覽步驟該硬失敗，
     * 但為了把某個區塊帶進鏡頭而捲動失敗（節點剛好沒有可捲的祖先、或同一段文字撞到兩個
     * 節點）不該讓已經拍好的十幾張作廢。
     *
     * 失敗會記下來並在最後一併印出——**靜默失敗是不行的**：取景捲動沒生效時，
     * 拍出來的會是跟前一張一模一樣的圖（實測踩過），而那種重複很容易被當成已經拍好了。
     * 另一道防線在 [capture] 的重複偵測。
     */
    private fun scrollToForFraming(text: String) {
        runCatching { rule.onNodeWithText(text).performScrollTo() }
            .onFailure { framingFailures += "  取景捲動失敗（不影響畫面正確性）：$text — ${it.message?.lineSequence()?.firstOrNull()}" }
    }

    /**
     * 收起軟鍵盤。
     *
     * **這一步不是潔癖，是消除不確定性。** 填完欄位之後鍵盤要不要留著是時序決定的，
     * 實測同一個畫面在不同輪次拍出 170 KB 與 243 KB 兩種結果——差別就是半個畫面
     * 被鍵盤蓋住。商店素材每跑一次就換一個樣子是不能接受的，所以每張拍照前一律收鍵盤。
     *
     * 走 `WindowInsetsController` 而不是按返回鍵：返回鍵在鍵盤沒開時會**改成導覽上一頁**，
     * 那會把流程走歪。
     */
    private fun hideSoftKeyboard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        rule.activityRule.scenario.onActivity { activity ->
            activity.window.insetsController?.hide(android.view.WindowInsets.Type.ime())
        }
        rule.waitForIdle()
    }

    /**
     * 返回上一頁。
     *
     * **刻意不用 `Espresso.pressBack()`**：Espresso 要求 root view 具有視窗焦點，
     * 而模擬器視窗在 host 桌面上沒被聚焦時，Android 端就會回報 `has-window-focus=false`，
     * 於是它等 10 秒後丟 `RootViewWithoutFocusException`——那跟 App 沒有關係，
     * 純粹是「跑測試的人剛好點到別的視窗」。截圖流程不該因為這種事失敗。
     *
     * 走 Activity 自己的 `onBackPressedDispatcher`，跟使用者按返回鍵是同一條路，
     * 但不需要視窗焦點。
     */
    private fun pressBack() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /**
     * 拍全螢幕（含狀態列），寫成 PNG。
     *
     * 用 `UiAutomation.takeScreenshot()` 而不是 Compose 的 `captureToImage()`：後者只拍
     * composable 子樹，拍不到狀態列，而且遇到 Dialog 這種另開 window 的東西會漏。
     * 商店素材要的是「使用者眼睛看到的那一整塊」，所以用系統層級的截圖。
     */
    private fun capture(name: String) {
        hideSoftKeyboard()
        rule.waitForIdle()
        // Compose 的進場動畫與 Mock 的延遲都不在 waitForIdle 的管轄內，留一點時間讓畫面定住。
        Thread.sleep(SETTLE_MS)

        val bitmap: Bitmap? = instrumentation.uiAutomation.takeScreenshot()
        if (bitmap == null) {
            println("⚠️ $name 拍不到（takeScreenshot 回 null），跳過")
            return
        }
        val file = File(outputDir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()


        written += "  ${file.absolutePath} (${file.length()} bytes)"
    }

    private companion object {
        /** 拍照前的靜置時間。實測 500 ms 偶爾會拍到動畫中途，700 ms 穩定。 */
        const val SETTLE_MS = 700L

        /** 取景捲動失敗的報告檔名，由 `scripts/capture-screenshots.sh` 讀出來顯示。 */
        const val FRAMING_REPORT = "_framing-failures.txt"

        /** 上傳頁要用的示範運動紀錄截圖（androidTest 資產，不進正式 APK）。 */
        const val DEMO_RECORD_ASSET = "demo-exercise-record.png"
    }
}
