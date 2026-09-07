package com.megshao.exerciserewards.data

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.LoginCredentials
import com.megshao.exerciserewards.core.models.LoginOutcome
import com.megshao.exerciserewards.core.models.RedeemOption
import com.megshao.exerciserewards.core.models.RedeemResult
import com.megshao.exerciserewards.core.models.TaskPeriod
import com.megshao.exerciserewards.core.models.TaskState
import com.megshao.exerciserewards.core.models.VendorIntro
import com.megshao.exerciserewards.core.models.VendorIntroCategory
import com.megshao.exerciserewards.core.models.Voucher
import com.megshao.exerciserewards.core.models.VoucherFigure
import com.megshao.exerciserewards.core.models.VoucherOtpResult
import com.megshao.exerciserewards.core.services.AuthServicing
import com.megshao.exerciserewards.core.services.RedeemServicing
import com.megshao.exerciserewards.core.services.TasksServicing
import com.megshao.exerciserewards.core.services.UploadResult
import com.megshao.exerciserewards.core.services.UploadServicing
import com.megshao.exerciserewards.core.services.VoucherServicing
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 示範模式（App Store／Google Play 審查用）與 Preview 的假服務。
 *
 * **絕不連外**：示範模式的橫幅上寫著「未連線官方網站」，這裡的每一支都只是 delay + 回傳
 * 記憶體裡的資料。要改這些檔案時請守住那句話。
 */

public class MockAuthService(private val delayMillis: Long = 400) : AuthServicing {
    override suspend fun login(credentials: LoginCredentials): LoginOutcome {
        delay(delayMillis)
        return LoginOutcome.SUCCESS
    }

    override suspend fun logout() {
        delay(200)
    }
}

public class MockTasksService(
    private val delayMillis: Long = 400,
    private val sample: List<TaskPeriod> = demoPeriods(),
) : TasksServicing {

    override suspend fun fetchTasks(): List<TaskPeriod> {
        delay(delayMillis)
        return sample
    }

    override fun screenshotUrl(taskId: String): String = DEMO_SCREENSHOT_URI

    /**
     * 示範模式**絕不能連外**，所以這裡回傳一個本機標記 URI；
     * `ScreenshotLoader` 對它有專門分支，會在裝置上自己畫一張示範截圖，不走網路。
     */
    override suspend fun screenshotImageUrl(taskId: String): String {
        delay(300)
        return DEMO_SCREENSHOT_URI
    }

    public companion object {
        /** 本機示範截圖的標記。**不是網址**，不會有任何連線發生。 */
        public const val DEMO_SCREENSHOT_URI: String = "demo://screenshot"

        /**
         * 示範模式與 Preview 用的完整 14 期範例資料。
         *
         * **日期是依「今天」動態算出來的**：第 6 期永遠涵蓋當下所在那一週（週一～週日，
         * 台北時間），其餘 13 期以它為基準前後各推一週。示範模式因此永遠有一個真正的當期，
         * 不會像寫死日期那樣過幾天就整份過期。
         *
         * **順序是 1→14，跟正式站一樣。** 不要為了讓當期被選中而把它排到第 0 位——
         * iOS 端就是那樣把「取第一個非 NOT_STARTED」的 bug 蓋住了整整一版
         * （見 `TaskPeriod.current` 的註解）。當期由日期決定，示範資料跟正式資料走同一條路徑。
         *
         * 狀態分佈刻意涵蓋全部 5 種狀態，讓審查員與截圖素材都看得到完整流程：
         * 已兌換 3 期（券夾有券可看）／可兌換 1 期／審核中 1 期／可上傳 1 期／尚未開始 8 期。
         *
         * 「已使用」不在這份資料裡——它不是官網的狀態，而是使用者在 App 內自己標記的
         * 本機旗標（見 [VoucherUsageStore]），因此示範模式一開始三張券都是未使用。
         *
         * `id` 比照真實後端：只有當期與已結束的期別有 UUID，尚未開始的期別為空字串
         * （空字串會讓卡片不顯示需要 UUID 的按鈕，與正式站行為一致）。
         */
        public fun demoPeriods(now: Instant = Instant.now()): List<TaskPeriod> {
            val today = LocalDate.ofInstant(now, TaskPeriod.ACTIVITY_ZONE)
            val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val currentIndex = 6

            fun weekStart(index: Int): LocalDate = thisMonday.plusWeeks((index - currentIndex).toLong())
            fun start(index: Int) = siteDate(weekStart(index))
            fun end(index: Int) = siteDate(weekStart(index).plusDays(6))

            /** 該期第 `day` 天的 `MM/dd HH:mm`——官網「上傳時間／審核時間」就是這個格式。 */
            fun stamp(index: Int, day: Int, hour: Int, minute: Int): String {
                val date = weekStart(index).plusDays(day.toLong())
                return "%02d/%02d %02d:%02d".format(date.monthValue, date.dayOfMonth, hour, minute)
            }

            val notStarted = (7..14).map { index ->
                TaskPeriod(
                    id = "",
                    index = index,
                    startDate = start(index),
                    endDate = end(index),
                    state = TaskState.NOT_STARTED,
                )
            }

            return listOf(
                // 已完成並兌換：券夾裡看得到 3 張加碼券。
                // `voucherSummary` 比照官網任務卡上的「兌換內容：通路／品項」那一行。
                TaskPeriod(
                    id = "demo-period-01", index = 1, startDate = start(1), endDate = end(1),
                    state = TaskState.REDEEMED,
                    uploadedAt = stamp(1, 2, 21, 42),
                    reviewedAt = stamp(1, 4, 10, 18) + " 通過",
                    voucherSummary = "示範超商 A／50+3元加碼券",
                ),
                TaskPeriod(
                    id = "demo-period-02", index = 2, startDate = start(2), endDate = end(2),
                    state = TaskState.REDEEMED,
                    uploadedAt = stamp(2, 2, 7, 55),
                    reviewedAt = stamp(2, 4, 14, 3) + " 通過",
                    voucherSummary = "示範超商 C／指定雞胸果昔兌換券",
                ),
                TaskPeriod(
                    id = "demo-period-03", index = 3, startDate = start(3), endDate = end(3),
                    state = TaskState.REDEEMED,
                    uploadedAt = stamp(3, 3, 20, 11),
                    reviewedAt = stamp(3, 4, 9, 26) + " 通過",
                    voucherSummary = "示範超市 D／50元加碼券",
                ),
                // 審核通過、尚未兌換：券夾「可兌換」區與任務頁的「立即兌換」按鈕都由這期驅動。
                TaskPeriod(
                    id = "demo-period-04", index = 4, startDate = start(4), endDate = end(4),
                    state = TaskState.REDEEMABLE,
                    remainingText = "剩 3 天 5 小時可兌換",
                    uploadedAt = stamp(4, 2, 19, 30),
                    reviewedAt = stamp(4, 4, 11, 47) + " 通過",
                ),
                // 已上傳、等待審核。
                TaskPeriod(
                    id = "demo-period-05", index = 5, startDate = start(5), endDate = end(5),
                    state = TaskState.PENDING_REVIEW,
                    remainingText = "已上傳 · 5 個工作日內完成審核",
                    uploadedAt = stamp(5, 2, 22, 8),
                ),
                // 本週：可上傳，倒數中。倒數字串依這一期的結束時間算，避免與日期矛盾。
                TaskPeriod(
                    id = "demo-period-06", index = 6, startDate = start(6), endDate = end(6),
                    state = TaskState.OPEN,
                    remainingText = uploadRemainingText(weekStart(6).plusDays(6), now),
                ),
            ) + notStarted
        }

        /** 官網格式 `yyyy/MM/dd`——`TaskPeriod.dateSpan` 就是照這個格式解析的。 */
        private fun siteDate(date: LocalDate): String =
            "%04d/%02d/%02d".format(date.year, date.monthValue, date.dayOfMonth)

        /** 「剩 N 天 M 小時可上傳」。上傳窗到期別最後一天結束為止，與 `hasEnded` 同界。 */
        private fun uploadRemainingText(periodEnd: LocalDate, now: Instant): String? {
            val closesAt = periodEnd.plusDays(1).atStartOfDay(TaskPeriod.ACTIVITY_ZONE).toInstant()
            if (!closesAt.isAfter(now)) return null
            val seconds = closesAt.epochSecond - now.epochSecond
            val days = seconds / 86_400
            val hours = (seconds % 86_400) / 3_600
            return if (days > 0) "剩 $days 天 $hours 小時可上傳" else "剩 $hours 小時可上傳"
        }
    }
}

public class MockRedeemService(private val delayMillis: Long = 400) : RedeemServicing {

    override suspend fun options(taskId: String): List<RedeemOption> {
        delay(delayMillis)
        return DEMO_OPTIONS
    }

    override suspend fun redeem(taskId: String, vendorId: String, item: String): RedeemResult {
        delay(delayMillis)
        return RedeemResult(submitted = true, message = "已送出兌換，請完成簡訊驗證後檢視加碼券")
    }

    /** 逐項版與列舉版各給一個範例，讓審查員兩種畫面都看得到。 */
    override suspend fun vendorIntro(path: String): VendorIntro {
        delay(delayMillis)
        return INTROS[path] ?: LIST_STYLE_INTRO
    }

    public companion object {
        public val DEMO_OPTIONS: List<RedeemOption> = listOf(
            RedeemOption("1", "全家便利商店", "50+3元加碼券", "item-demo-family", "/intro/vendor-1.html"),
            RedeemOption("2", "7-11", "50+5元加碼券", "item-demo-711", "/intro/vendor-2.html"),
            RedeemOption("3", "萊爾富", "50元加碼券", "item-demo-hilife", "/intro/vendor-3.html"),
            RedeemOption("5", "全聯", "50元加碼券", "item-demo-pxmart", "/intro/vendor-5.html"),
        )

        /** 逐項版（示範超商）：分類卡 + 品項清單。 */
        private val LIST_STYLE_INTRO = VendorIntro(
            title = "示範超商可兌換商品",
            subtitle = "點選商品分類，即可展開查看相關兌換品項。",
            categories = listOf(
                VendorIntroCategory(
                    name = "全部品項",
                    items = listOf(
                        "示範無糖綠茶", "示範礦泉水", "示範大冰拿鐵",
                        "示範綜合堅果", "示範茶葉蛋", "示範鮮豆漿",
                    ),
                    statedCount = 6,
                    isAllItems = true,
                ),
                VendorIntroCategory(name = "現煮咖啡", items = listOf("示範大冰拿鐵"), statedCount = 1),
                VendorIntroCategory(name = "無糖茶", items = listOf("示範無糖綠茶"), statedCount = 1),
                VendorIntroCategory(name = "瓶裝水類", items = listOf("示範礦泉水"), statedCount = 1),
                VendorIntroCategory(
                    name = "堅果、蛋類",
                    items = listOf("示範綜合堅果", "示範茶葉蛋"),
                    statedCount = 2,
                ),
                VendorIntroCategory(name = "豆米漿／鮮乳", items = listOf("示範鮮豆漿"), statedCount = 1),
            ),
            notices = listOf("實際可兌換品項、供應狀況及門市庫存，依各門市現場公告為準。"),
        )

        /** 列舉版（示範超市）：只有類別與舉例，沒有完整品項清單。 */
        private val TABLE_STYLE_INTRO = VendorIntro(
            title = "示範超市可兌換商品",
            subtitle = "以下為運動幣加碼活動可兌換商品類別",
            categories = listOf(
                VendorIntroCategory(name = "冷藏鮮乳", examples = "示範低脂鮮乳、示範高品質鮮乳等"),
                VendorIntroCategory(name = "豆漿／米漿／燕麥", examples = "示範無加糖鮮豆漿、示範陽光糙米漿等"),
                VendorIntroCategory(name = "常溫鮮蛋", examples = "示範洗選蛋（白）等"),
                VendorIntroCategory(name = "堅果／核仁類", examples = "示範綜合堅果、示範無調味腰果罐等"),
            ),
            notices = listOf("實際可兌換品項、供應狀況及門市庫存，依各門市現場公告為準。"),
        )

        private val INTROS = mapOf(
            "/intro/vendor-1.html" to LIST_STYLE_INTRO,
            "/intro/vendor-2.html" to LIST_STYLE_INTRO,
            "/intro/vendor-3.html" to LIST_STYLE_INTRO,
            "/intro/vendor-5.html" to TABLE_STYLE_INTRO,
        )
    }
}

/** 任何 6 碼都驗證成功；券碼比照官網萊爾富的兩段式券。 */
public class MockVoucherService(private val delayMillis: Long = 400) : VoucherServicing {

    override suspend fun sendOtp(taskId: String) {
        delay(delayMillis)
    }

    override suspend fun verifyOtp(taskId: String, otp: String): VoucherOtpResult {
        delay(delayMillis)
        return VoucherOtpResult.Success
    }

    override suspend fun fetchVoucher(taskId: String): Voucher {
        delay(delayMillis)
        return DEMO_VOUCHER
    }

    public companion object {
        public val DEMO_VOUCHER: Voucher = Voucher(
            vendorName = "示範超商 C",
            itemName = "超值商品券",
            expiry = "115年12月31日",
            figures = listOf(
                VoucherFigure("CODE_128", "00000000", "① 商品條碼"),
                VoucherFigure("CODE_128", "AAAA0000BBBB1111", "② 券號條碼"),
            ),
            notices = listOf(
                "加碼券使用期限自取得後起至115年12月31日24時止，逾期視同放棄，恕不補發、展延、折換現金或更換其他等值商品。",
                "抵用時應開啟本人帳號之有效加碼券頁面，並依合作店家現場流程完成抵用，不得以紙本列印、手機截圖、翻拍或其他非活動網站即時畫面方式抵用。",
            ),
        )
    }
}

public class MockUploadService(private val delayMillis: Long = 500) : UploadServicing {
    override suspend fun upload(taskId: String?, imageData: ByteArray, fileName: String): UploadResult {
        delay(delayMillis)
        return UploadResult(submitted = true, message = "（示範模式）已模擬送出，未實際連線官方網站。")
    }
}

/** 讓「示範模式不連外」這件事在型別上也成立：任何真的要連線的呼叫都會丟這個。 */
internal fun demoModeShouldNotConnect(): Nothing =
    throw AppError.BlockedEgress("demo-mode")
