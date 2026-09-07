package com.megshao.exerciserewards.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.megshao.exerciserewards.BuildConfig
import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.TaskPeriod
import com.megshao.exerciserewards.core.models.current
import java.time.Instant
import com.megshao.exerciserewards.core.security.LogCategory
import com.megshao.exerciserewards.core.security.Redact
import com.megshao.exerciserewards.core.security.SecureLog

// MARK: - 這個檔案存在的理由
//
// **為什麼要有一層外殼**：Firebase 的 `logEvent(name, Bundle)` 收的是任意鍵值——任何字串都
// 塞得進去，塞錯了也不會有人告訴你，而且送出去就收不回來。本 App 的整個賣點是
// 「不蒐集、不外傳個資」，所以遙測這條路必須是**唯一且受管制的出口**，比照 SecureLog 的
// 設計哲學：呼叫端不能直接碰 SDK，只能透過這裡的型別安全 API。
// 全 App 禁止 import FirebaseAnalytics / FirebaseCrashlytics，只有這個檔案可以。
//
// 事件清單、參數、觸發時機與「刻意不埋」的判斷依據，就是本檔案的工廠方法本身：
// 不能送的東西，這裡在型別上就構造不出來，不必仰賴另一份文件說明。
//
// **哪些資料絕對不准進來**（新增事件前請逐條對照）：
//   1. 個資：身分證號、出生日期、手機號碼、姓名、email、健保卡號——這三欄是 App 唯一收集的
//      個資，只在登入當下直送 500.gov.tw，任何形式（原文、雜湊、截斷、拼接）都不得進入遙測。
//   2. 官方站識別碼：登入 session／cookie／CSRF token、期別 UUID、vendorId／itemId、
//      S3 presigned URL 與其 host。
//   3. 券碼：兌換碼、券序號、條碼內容、OTP、以及任何可以拿去核銷的字串。
//   4. 自由文字：使用者輸入的任何內容、**官網回傳的任何原文**（錯誤訊息、券的注意事項、
//      剩餘時間文字、商家名／品項名）。官網文字是不可信輸入，可能回顯遮罩後的手機號或日期，
//      一律先分類成封閉列舉再送（見 TelemetryValues.kt）。
//   5. 使用者的照片：檔案大小、尺寸、原始格式、壓縮迭代次數都算「關於 User Content 的
//      測量值」，一律不送。上傳只送二元結果與活動週次。
//
// **為什麼閘門擋不住第 4、5 條、需要你自己守**：gate 的敏感樣式掃描只掃字串。
// `Flag(true)` 與 `Count(1)` 是掃不到的——所以「不埋」必須發生在寫下工廠方法的那一刻，
// 而不是指望送出前被攔下來。

/**
 * 遙測參數的值。**只有這三種**：封閉列舉轉出來的短碼、登記過值域的整數、布林。
 * 沒有「任意字串」這個選項。
 */
public sealed interface AnalyticsValue {
    public data class Code(val value: String) : AnalyticsValue
    public data class Count(val value: Int) : AnalyticsValue
    public data class Flag(val value: Boolean) : AnalyticsValue
}

/** 一個可被送出的事件。名稱與參數都由工廠方法產生，呼叫端拼不出別的。 */
public class AnalyticsEvent internal constructor(
    public val name: String,
    public val parameters: Map<String, AnalyticsValue>,
) {
    public companion object {
        private fun code(value: String) = AnalyticsValue.Code(value)
        private fun count(value: Int) = AnalyticsValue.Count(value)
        private fun flag(value: Boolean) = AnalyticsValue.Flag(value)

        // MARK: 畫面與導覽

        public fun screenViewed(screen: ScreenName): AnalyticsEvent =
            AnalyticsEvent("screen_view", mapOf("screen_name" to code(screen.raw)))

        /**
         * 導覽漏斗第一步。**刻意綁在個資表單出現、而不是歡迎頁的「開始使用」**——
         * 歡迎頁排在免責聲明之前，那時遙測還沒初始化，埋在那裡送不出去，漏斗第一步就永遠是 0。
         */
        public fun tutorialBegin(): AnalyticsEvent = AnalyticsEvent("tutorial_begin", emptyMap())

        public fun tutorialComplete(): AnalyticsEvent = AnalyticsEvent("tutorial_complete", emptyMap())

        /** 同意免責聲明。Firebase 被初始化之後的第一個事件。 */
        public fun consentGranted(): AnalyticsEvent = AnalyticsEvent("consent_granted", emptyMap())

        public fun telemetryPreferenceChanged(enabled: Boolean): AnalyticsEvent =
            AnalyticsEvent("telemetry_preference_changed", mapOf("enabled" to flag(enabled)))

        /** 只送「哪個欄位格式不對」。 */
        public fun onboardingValidationFailed(field: OnboardingField): AnalyticsEvent =
            AnalyticsEvent("onboarding_validation_failed", mapOf("field" to code(field.raw)))

        /** 未註冊 → 導向官網。無參數：開啟的是固定 URL，不含任何使用者輸入。 */
        public fun registerRedirect(): AnalyticsEvent = AnalyticsEvent("register_redirect", emptyMap())

        // MARK: 登入

        /** 只有 trigger 與耗時，沒有 cookie、沒有 CSRF、沒有三碼。 */
        public fun login(trigger: LoginTrigger, durationMs: Int?): AnalyticsEvent =
            AnalyticsEvent("login", buildMap {
                put("method", code("gov_500_form"))
                put("trigger", code(trigger.raw))
                durationMs?.let { put("duration_ms", count(Telemetry.clampDuration(it))) }
            })

        public fun loginFailed(trigger: LoginTrigger, reason: FailReason, durationMs: Int?): AnalyticsEvent =
            AnalyticsEvent("login_failed", buildMap {
                put("trigger", code(trigger.raw))
                put("reason", code(reason.raw))
                durationMs?.let { put("duration_ms", count(Telemetry.clampDuration(it))) }
            })

        // MARK: 任務

        /**
         * 抓任務成功。`period_count`（14）與 `current_period`（1–14）是活動的形狀，
         * 不是個人資料；期別 UUID 一律不送。
         */
        public fun tasksFetched(
            source: TasksSource,
            periodCount: Int,
            currentPeriod: Int?,
            hadCache: Boolean,
            durationMs: Int?,
        ): AnalyticsEvent = AnalyticsEvent("tasks_fetched", buildMap {
            put("source", code(source.raw))
            put("period_count", count(periodCount))
            currentPeriod?.let { put("current_period", count(it)) }
            put("had_cache", flag(hadCache))
            durationMs?.let { put("duration_ms", count(Telemetry.clampDuration(it))) }
        })

        public fun tasksFetchFailed(
            source: TasksSource,
            reason: FailReason,
            hadCache: Boolean,
            durationMs: Int?,
        ): AnalyticsEvent = AnalyticsEvent("tasks_fetch_failed", buildMap {
            put("source", code(source.raw))
            put("reason", code(reason.raw))
            put("had_cache", flag(hadCache))
            durationMs?.let { put("duration_ms", count(Telemetry.clampDuration(it))) }
        })

        // MARK: 上傳

        /**
         * 選圖結果。**只送二元結果。** 刻意不送的東西：檔案大小、圖片尺寸、原始格式
         * （HEIC/JPEG）、壓縮迭代次數（可反推檔案大小）、相片識別碼、EXIF。
         * 使用者的照片是 User Content，關於它的任何測量值都不該離開裝置。
         */
        public fun uploadPick(outcome: UploadPickOutcome): AnalyticsEvent =
            AnalyticsEvent("upload_pick", mapOf("outcome" to code(outcome.raw)))

        /** 只有活動週次。沒有任何檔案資訊。 */
        public fun uploadSubmit(periodIndex: Int?): AnalyticsEvent =
            AnalyticsEvent("upload_submit", buildMap {
                periodIndex?.let { put("period_index", count(it)) }
            })

        /** 官網 `.notice--error` 的原文只留在畫面上，這裡送的是分類。 */
        public fun uploadResult(outcome: UploadOutcome, periodIndex: Int?, durationMs: Int?): AnalyticsEvent =
            AnalyticsEvent("upload_result", buildMap {
                put("outcome", code(outcome.raw))
                periodIndex?.let { put("period_index", count(it)) }
                durationMs?.let { put("duration_ms", count(Telemetry.clampDuration(it))) }
            })

        /**
         * 看截圖的結果。**圖片網址、它的 host 與查詢字串一律不送**——簽章網址內含 bucket 名
         * 與簽章，是官方站識別碼。只送四選一的結果分類。
         */
        public fun screenshotView(outcome: ScreenshotOutcome): AnalyticsEvent =
            AnalyticsEvent("screenshot_view", mapOf("outcome" to code(outcome.raw)))

        // MARK: 兌換

        /** `option_count` 是官網目錄大小（全體使用者一樣），不是個人資料。 */
        public fun redeemOptions(outcome: ListOutcome, reason: FailReason?, optionCount: Int): AnalyticsEvent =
            AnalyticsEvent("redeem_options", buildMap {
                put("outcome", code(outcome.raw))
                reason?.let { put("reason", code(it.raw)) }
                put("option_count", count(optionCount))
            })

        public fun redeemSelect(vendor: Vendor): AnalyticsEvent =
            AnalyticsEvent("redeem_select", mapOf("vendor" to code(vendor.raw)))

        public fun redeemCancel(vendor: Vendor): AnalyticsEvent =
            AnalyticsEvent("redeem_cancel", mapOf("vendor" to code(vendor.raw)))

        public fun redeemSubmit(vendor: Vendor, periodIndex: Int?): AnalyticsEvent =
            AnalyticsEvent("redeem_submit", buildMap {
                put("vendor", code(vendor.raw))
                periodIndex?.let { put("period_index", count(it)) }
            })

        /** `RedeemResult.message` 即使是 App 自己的靜態文案也不送，維持「無字串」原則。 */
        public fun redeemResult(outcome: RedeemOutcome, vendor: Vendor, durationMs: Int?): AnalyticsEvent =
            AnalyticsEvent("redeem_result", buildMap {
                put("outcome", code(outcome.raw))
                put("vendor", code(vendor.raw))
                durationMs?.let { put("duration_ms", count(Telemetry.clampDuration(it))) }
            })

        // MARK: 加碼券

        public fun voucherOpen(source: VoucherSource): AnalyticsEvent =
            AnalyticsEvent("voucher_open", mapOf("source" to code(source.raw)))

        /**
         * `isResend` 分辨「第一次發」與「重新發送」。
         * 發送對象的手機門號在官網 session 裡，App 從頭到尾沒碰過，自然也送不出去。
         */
        public fun voucherOtpSend(outcome: OtpSendOutcome, reason: FailReason?, isResend: Boolean): AnalyticsEvent =
            AnalyticsEvent("voucher_otp_send", buildMap {
                put("outcome", code(outcome.raw))
                reason?.let { put("reason", code(it.raw)) }
                put("is_resend", flag(isResend))
            })

        /** OTP 本身與官網的錯誤原文都不送；`remaining` 是 0–3 的次數。 */
        public fun voucherOtpVerify(outcome: OtpVerifyOutcome, remaining: Int?): AnalyticsEvent =
            AnalyticsEvent("voucher_otp_verify", buildMap {
                put("outcome", code(outcome.raw))
                remaining?.let { put("remaining_attempts", count(it)) }
            })

        /**
         * `figure_count` 與 `format` 描述的是**券種結構**（萊爾富是兩段式），不是券碼本身。
         * `VoucherFigure.value`（券碼）、expiry、vendorName、itemName、notices 一律不送
         * ——那些是可以拿去核銷的東西與官網文字。
         */
        public fun voucherReveal(
            outcome: VoucherRevealOutcome,
            figureCount: Int,
            format: BarcodeFormat,
        ): AnalyticsEvent = AnalyticsEvent("voucher_reveal", mapOf(
            "outcome" to code(outcome.raw),
            "figure_count" to count(figureCount),
            "format" to code(format.raw),
        ))

        /**
         * 條碼畫不出來。**券碼絕不送。** 出現未知 format＝官網換了新券種，
         * 是最直接的改版訊號。
         */
        public fun barcodeRenderFailed(format: BarcodeFormat): AnalyticsEvent =
            AnalyticsEvent("barcode_render_failed", mapOf("format" to code(format.raw)))

        /** 只送方向，不帶期別 UUID／期數／兌換內容。 */
        public fun voucherMarkUsed(used: Boolean): AnalyticsEvent =
            AnalyticsEvent("voucher_mark_used", mapOf("used" to flag(used)))

        // MARK: 我的資料

        /** 只有成功／失敗。三欄個資永遠不進遙測。 */
        public fun profileSave(outcome: SimpleOutcome): AnalyticsEvent =
            AnalyticsEvent("profile_save", mapOf("outcome" to code(outcome.raw)))

        public fun localDataClear(): AnalyticsEvent = AnalyticsEvent("local_data_clear", emptyMap())
    }
}

/**
 * 全 App 唯一的 Firebase 出口。
 *
 * **初始化綁在免責聲明的同意之後**：[configure] 只能由同意流程與「已同意過」的啟動路徑
 * 呼叫（見 `ExerciseRewardsApplication`）。同意之前一個 byte 都不會送給 Google——
 * `AndroidManifest.xml` 也把 `FirebaseInitProvider` 拆掉了，否則那個 ContentProvider 會在
 * `Application.onCreate` 之前就把 Firebase 初始化掉。
 */
public object Telemetry {

    private val log = SecureLog(LogCategory.SECURITY)

    @Volatile
    private var configured: Boolean = false

    /** 使用者偏好。同意之後**預設開啟**，可隨時關掉。 */
    @Volatile
    public var isUserEnabled: Boolean = true
        private set

    /** 示範／送審模式。審查員可能自己把遙測打開，仍然一個字都不能送。 */
    @Volatile
    public var isDemoMode: Boolean = false

    /**
     * 截圖模式：自動化跑出來的事件不該進正式資料。
     *
     * **只能由儀器測試在啟動時設定**（instrumented test 直接寫這個旗標），
     * 不可以改成讀 SharedPreferences 之類使用者寫得進去的地方，也**不可以**拿來改資料來源、
     * 跳過驗證或解鎖任何功能——它只能影響「畫面上要不要畫示範橫幅」。
     */
    @Volatile
    public var isScreenshotMode: Boolean = false

    /**
     * 整數參數的值域白名單。
     *
     * 樣式掃描掃不到整數（`Count(1)` 與一個十位數的券碼在型別上沒有差別），
     * 所以整數改用「每個鍵各自的窄值域」把關。**表上沒有的鍵一律擋。**
     */
    private val allowedIntRanges: Map<String, IntRange> = mapOf(
        "period_count" to 0..14,
        "period_index" to 1..14,
        "current_period" to 1..14,
        "option_count" to 0..100,
        "figure_count" to 0..8,
        "remaining_attempts" to 0..3,
        "duration_ms" to 0..MAX_DURATION_MS,
        "status" to -1..599,
        "os_status" to -100_000..100_000,
        "options" to 0..100,
    )

    /** 耗時上限（10 分鐘）。超過一律夾住——那不是有意義的量測值，只是使用者把 App 放著。 */
    public const val MAX_DURATION_MS: Int = 600_000

    public fun clampDuration(millis: Int): Int = millis.coerceIn(0, MAX_DURATION_MS)

    /** 從 `System.nanoTime()` 的起點算出毫秒。 */
    public fun elapsedMs(startNanos: Long): Int =
        clampDuration(((System.nanoTime() - startNanos) / 1_000_000).toInt())

    /**
     * 初始化 Firebase。**只在使用者同意免責聲明之後呼叫。**
     *
     * 沒有 `google-services.json` 時 [FirebaseApp.initializeApp] 回 null，此時全程 no-op
     * 且不 crash——任何人 clone 這個 repo 都能直接 build 出可執行的 App（見 README）。
     */
    public fun configure(context: Context, userEnabled: Boolean) {
        isUserEnabled = userEnabled
        val app = FirebaseApp.initializeApp(context)
        if (app == null) {
            log.info("Firebase 設定檔不存在，遙測全程 no-op")
            return
        }
        configured = true
        applyCollectionFlags(context, userEnabled && !isDemoMode)
        log.info("Firebase 已初始化（使用者偏好：$userEnabled）")
    }

    /** 使用者在「安全與隱私」切換遙測開關。 */
    public fun setUserEnabled(context: Context, enabled: Boolean) {
        isUserEnabled = enabled
        if (!configured) return
        applyCollectionFlags(context, enabled && !isDemoMode)
        // 開啟時送一筆（同意後的第一個事件）；**關閉時不送任何事件**
        // ——使用者剛說不要，再送一筆等於沒聽到。
        if (enabled) {
            logEvent(context, AnalyticsEvent.telemetryPreferenceChanged(true))
        } else {
            // **關掉不只是停止收集，還要把已經收集但還沒上傳的東西丟掉。**
            //
            // 只呼叫 setAnalyticsCollectionEnabled(false) 的話，佇列裡等著上傳的事件
            // 與硬碟上等著上傳的當機報告仍然會在**下一次**符合條件時送出去——使用者按
            // 下開關的意思是「不要送」，不是「從現在起不要再收集，但之前收的照送」。
            //
            // resetAnalyticsData() 一併重置裝置上的 app instance id，所以關掉之後
            // 再打開，Google 端看到的是一支新的裝置，接不回關掉之前那條軌跡。
            // 這是 iOS 端關閉開關時的行為（Analytics.resetAnalyticsData() +
            // Crashlytics.deleteUnsentReports()），兩個平台的承諾必須一致，
            // 否則 privacy 頁上那句話會有一邊是假的。
            //
            // 兩個都包 runCatching：這是「使用者要求關閉」的路徑，
            // 絕不能因為清理失敗而讓關閉本身失敗。
            runCatching { FirebaseAnalytics.getInstance(context).resetAnalyticsData() }
            runCatching { FirebaseCrashlytics.getInstance().deleteUnsentReports() }
            log.info("遙測已關閉，已收集但未上傳的資料一併清除")
        }
    }

    /** 進出示範模式：SDK 層也一起關掉，不只靠 [gate]。 */
    public fun applyDemoMode(context: Context) {
        if (!configured) return
        applyCollectionFlags(context, isUserEnabled && !isDemoMode)
    }

    /** 「立即清除本機資料」：偏好回到預設並立刻停止收集。 */
    public fun resetPreference(context: Context) {
        isUserEnabled = AppPreferencesDefaults.TELEMETRY_ENABLED
        if (!configured) return
        applyCollectionFlags(context, false)
        // 清掉已收集但還沒上傳的資料，以及裝置上的 app instance id。
        runCatching { FirebaseAnalytics.getInstance(context).resetAnalyticsData() }
        runCatching { FirebaseCrashlytics.getInstance().deleteUnsentReports() }
        log.info("遙測偏好已重設，已收集的資料一併清除")
    }

    private fun applyCollectionFlags(context: Context, enabled: Boolean) {
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
    }

    // MARK: 隱私閘門

    /** 被擋下來的原因。null 代表通過。 */
    private sealed interface Block {
        val reason: String

        /** 是不是「程式寫錯了」而不是「預期中的正常狀態」。 */
        val isProgrammerError: Boolean

        data object NotConfigured : Block {
            override val reason = "Firebase 未初始化"
            override val isProgrammerError = false
        }

        data object DemoMode : Block {
            override val reason = "示範模式"
            override val isProgrammerError = false
        }

        data object ScreenshotMode : Block {
            override val reason = "截圖模式"
            override val isProgrammerError = false
        }

        data object UserDisabled : Block {
            override val reason = "使用者已關閉遙測"
            override val isProgrammerError = false
        }

        // `field` 不能當屬性名：在 accessor 裡它是指向 backing field 的保留識別字。
        data class Sensitive(val fieldName: String, val kinds: Set<Redact.SensitiveKind>) : Block {
            override val reason: String
                get() = "欄位 $fieldName 命中敏感樣式 [${kinds.map { it.raw }.sorted().joinToString(",")}]"
            override val isProgrammerError = true
        }

        data class OutOfRange(val fieldName: String, val value: Int) : Block {
            override val reason: String
                get() = allowedIntRanges[fieldName]
                    ?.let { "欄位 $fieldName 的整數 $value 超出允許值域 $it" }
                    ?: "欄位 $fieldName 是未登記的整數參數（值 $value）"
            override val isProgrammerError = true
        }
    }

    /**
     * 送出前的六道閘門：示範模式 → 截圖模式 → 使用者關閉 → 未初始化 →
     * 參數命中 [Redact] 敏感樣式 → 整數超出登記值域。
     * 這是**機制**而不是自律：呼叫端沒有繞過它的路。
     *
     * 順序有意義：**先問「該不該送」，再問「送得出去嗎」**。把 configured 放最前面的話，
     * 沒有 google-services.json 的開發／CI 環境會讓每一筆都回報「Firebase 未初始化」，
     * 於是「同意閘門到底有沒有在擋」就永遠驗不到。
     *
     * 示範模式排在使用者偏好前面：審查員可能自己把開關打開，仍然一個字都不能送。
     */
    private fun gate(eventName: String, parameters: Map<String, AnalyticsValue>): Block? {
        if (isDemoMode) return Block.DemoMode
        if (isScreenshotMode) return Block.ScreenshotMode
        if (!isUserEnabled) return Block.UserDisabled
        if (!configured) return Block.NotConfigured

        // 事件名本身也掃一次。名稱來自工廠方法，理論上不可能中；中了代表有人改壞了。
        Redact.sensitiveKinds(eventName).takeIf { it.isNotEmpty() }?.let {
            return Block.Sensitive("event_name", it)
        }

        for ((key, value) in parameters) {
            when (value) {
                is AnalyticsValue.Code -> Redact.sensitiveKinds(value.value)
                    .takeIf { it.isNotEmpty() }
                    ?.let { return Block.Sensitive(key, it) }

                is AnalyticsValue.Count -> {
                    val range = allowedIntRanges[key]
                    if (range == null || value.value !in range) {
                        return Block.OutOfRange(key, value.value)
                    }
                }

                is AnalyticsValue.Flag -> Unit
            }
            Redact.sensitiveKinds(key).takeIf { it.isNotEmpty() }?.let {
                return Block.Sensitive("key:$key", it)
            }
        }
        return null
    }

    /**
     * 處理被擋下來的事件。
     *
     * **敏感樣式／值域違規在 debug build 直接 crash**：這種情況一定是程式寫錯了
     * （某個個資或識別碼被接到遙測參數上），要在開發期就爆出來，而不是在正式版靜靜地被丟掉
     * ——靜靜丟掉會讓下一個人以為「反正閘門會擋」而放心亂接。
     * 其他原因（未初始化／示範模式／截圖模式／使用者關閉）是**預期中的正常狀態**。
     */
    private fun handle(block: Block, eventName: String) {
        if (block.isProgrammerError) {
            log.error("遙測事件遭攔截：$eventName — ${block.reason}")
            check(!BuildConfig.DEBUG) {
                "遙測事件 $eventName 夾帶疑似個資或識別碼（${block.reason}）。" +
                    "請改成封閉列舉，或到 allowedIntRanges 為這個鍵登記一個明確的窄值域；" +
                    "不要為了讓事件送得出去而放寬 Redact 的樣式。"
            }
        } else {
            log.debug { "遙測事件未送出：$eventName — ${block.reason}" }
        }
    }

    // MARK: 送出

    /** 送出一個事件。被閘門擋下時什麼事都不會發生（debug build 下敏感樣式會直接 crash）。 */
    public fun logEvent(context: Context, event: AnalyticsEvent) {
        gate(event.name, event.parameters)?.let {
            handle(it, event.name)
            return
        }
        val bundle = Bundle().apply {
            for ((key, value) in event.parameters) {
                when (value) {
                    is AnalyticsValue.Code -> putString(key, value.value)
                    is AnalyticsValue.Count -> putLong(key, value.value.toLong())
                    is AnalyticsValue.Flag -> putLong(key, if (value.value) 1L else 0L)
                }
            }
        }
        FirebaseAnalytics.getInstance(context).logEvent(event.name, bundle)

        // Breadcrumb：內容與 Analytics 參數完全相同（同一套型別限制）。
        // **不橋接 SecureLog**——它的 debug 行印過 request path（含期別 UUID）。
        val crumb = event.parameters.entries
            .sortedBy { it.key }
            .joinToString(" ") { (key, value) -> "$key=${breadcrumbText(value)}" }
        FirebaseCrashlytics.getInstance().log(if (crumb.isEmpty()) event.name else "${event.name} $crumb")
    }

    /** 送畫面瀏覽，並同步更新 Crashlytics 的 `screen` 鍵。 */
    public fun screenAppeared(context: Context, screen: ScreenName) {
        setCrashKey(context, "screen", AnalyticsValue.Code(screen.raw))
        logEvent(context, AnalyticsEvent.screenViewed(screen))
    }

    /** 設定當機報告的自訂鍵。走同一道閘門。 */
    public fun setCrashKey(context: Context, name: String, value: AnalyticsValue) {
        gate(name, mapOf(name to value))?.let {
            handle(it, "crash_key:$name")
            return
        }
        val crashlytics = FirebaseCrashlytics.getInstance()
        when (value) {
            is AnalyticsValue.Code -> crashlytics.setCustomKey(name, value.value)
            is AnalyticsValue.Count -> crashlytics.setCustomKey(name, value.value)
            is AnalyticsValue.Flag -> crashlytics.setCustomKey(name, value.value)
        }
    }

    // MARK: 非致命錯誤

    /** 每個 (issue, endpoint) 組合每個 App session 只回報一次，避免下拉刷新洗成幾千筆。 */
    private val reportedIssues = mutableSetOf<String>()

    /**
     * 記錄一個非致命錯誤。
     *
     * **刻意不接受 `Throwable`**：例外的 message 常常夾帶伺服器回傳的原文或完整 URL，
     * 而 Crashlytics 的 `recordException` 會把它整包送走。這裡只收**已經分類完成**的
     * [TelemetryIssue] 與封閉列舉／整數，呼叫端沒有「把原始 error 丟進來」這條路。
     */
    public fun recordNonFatal(
        context: Context,
        issue: TelemetryIssue,
        endpoint: Endpoint? = null,
        status: Int? = null,
        extras: Map<String, AnalyticsValue> = emptyMap(),
    ) {
        val key = "${issue.raw}|${endpoint?.raw ?: "-"}"
        synchronized(reportedIssues) {
            if (!reportedIssues.add(key)) return
        }
        val parameters = buildMap {
            put("issue", AnalyticsValue.Code(issue.raw))
            endpoint?.let { put("endpoint", AnalyticsValue.Code(it.raw)) }
            status?.let { put("status", AnalyticsValue.Count(it)) }
            putAll(extras)
        }
        gate("non_fatal_${issue.raw}", parameters)?.let {
            handle(it, "non_fatal:${issue.raw}")
            return
        }
        val crashlytics = FirebaseCrashlytics.getInstance()
        parameters.forEach { (name, value) ->
            when (value) {
                is AnalyticsValue.Code -> crashlytics.setCustomKey("nf_$name", value.value)
                is AnalyticsValue.Count -> crashlytics.setCustomKey("nf_$name", value.value)
                is AnalyticsValue.Flag -> crashlytics.setCustomKey("nf_$name", value.value)
            }
        }
        // 例外物件由我們自己建，訊息只含分類字串——不夾帶原始 error 的任何內容。
        crashlytics.recordException(TelemetryNonFatal(issue, endpoint, status))
    }

    /** 非致命錯誤的載體。**訊息只由封閉列舉組成。** */
    private class TelemetryNonFatal(
        issue: TelemetryIssue,
        endpoint: Endpoint?,
        status: Int?,
    ) : Exception(
        buildString {
            append(issue.raw)
            endpoint?.let { append(" endpoint=").append(it.raw) }
            status?.let { append(" status=").append(it) }
        },
    )

    // MARK: 分類

    /**
     * 把任意錯誤分類成 [FailReason]。**只取型別，不取內容。**
     *
     * 這支刻意收 `Throwable` 而不是讓呼叫端自己挑分類：呼叫端手上握著的是原始例外，
     * 唯一安全的路徑是交給這裡轉成封閉列舉，而不是自己抽字串出來。
     */
    public fun classify(error: Throwable): FailReason = when (error) {
        is AppError.Network -> FailReason.NETWORK
        is AppError.CsrfNotFound -> FailReason.CSRF_MISSING
        is AppError.UnexpectedResponse ->
            if (error.statusCode == -1) FailReason.REDIRECT_LOOP else FailReason.SITE_STATUS
        is AppError.Parsing -> FailReason.SITE_PARSE
        is AppError.BlockedEgress -> FailReason.BLOCKED_EGRESS
        is AppError.ResponseTooLarge -> FailReason.RESPONSE_TOO_LARGE
        is AppError.NotLoggedIn -> FailReason.INVALID_CREDENTIALS
        else -> FailReason.UNKNOWN
    }

    /**
     * 分類錯誤並在該回報時回報非致命錯誤。回傳分類供呼叫端送 Analytics。
     *
     * `sessionProbable` 為 true 時，解析失敗被歸類為「很可能只是 session 過期」，
     * **不送非致命錯誤**——不然每個使用者每天冷啟動都會產生一筆假的「官網改版」警報。
     */
    public fun reportFailure(
        context: Context,
        error: Throwable,
        endpoint: Endpoint,
        sessionProbable: Boolean = false,
    ): FailReason {
        val reason = classify(error)
        when (reason) {
            FailReason.SITE_PARSE -> {
                if (sessionProbable) return FailReason.SESSION_PROBABLE
                recordNonFatal(context, TelemetryIssue.TASKS_PARSE, endpoint)
            }

            FailReason.SITE_STATUS -> {
                val status = (error as? AppError.UnexpectedResponse)?.statusCode
                recordNonFatal(context, TelemetryIssue.TASKS_STATUS, endpoint, status)
            }

            else -> Unit
        }
        return reason
    }

    private fun breadcrumbText(value: AnalyticsValue): String = when (value) {
        is AnalyticsValue.Code -> value.value
        is AnalyticsValue.Count -> value.value.toString()
        is AnalyticsValue.Flag -> value.value.toString()
    }

    /** 從一份期別清單算出「當期是第幾期」，只給遙測用。 */
    public fun currentPeriodIndex(periods: List<TaskPeriod>): Int? =
        TaskPeriod.current(periods, Instant.now())?.index
}

/** 避免 telemetry 反向依賴 data 層，只為了讀一個預設值。 */
internal object AppPreferencesDefaults {
    const val TELEMETRY_ENABLED = true
}
