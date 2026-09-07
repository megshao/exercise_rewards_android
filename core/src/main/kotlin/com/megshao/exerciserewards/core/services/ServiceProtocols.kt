package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.LoginCredentials
import com.megshao.exerciserewards.core.models.LoginOutcome
import com.megshao.exerciserewards.core.models.RedeemOption
import com.megshao.exerciserewards.core.models.RedeemResult
import com.megshao.exerciserewards.core.models.TaskPeriod
import com.megshao.exerciserewards.core.models.VendorIntro
import com.megshao.exerciserewards.core.models.Voucher
import com.megshao.exerciserewards.core.models.VoucherOtpResult

/** 登入服務介面（登入流程無 OTP）。 */
public interface AuthServicing {
    /** 執行 access -> login 序列。回傳分流結果。 */
    public suspend fun login(credentials: LoginCredentials): LoginOutcome

    /** 登出並清 session。 */
    public suspend fun logout()
}

/** 我的任務服務介面。 */
public interface TasksServicing {
    /** 取得 14 期任務清單（需已登入）。 */
    public suspend fun fetchTasks(): List<TaskPeriod>

    /** 取得某期已上傳截圖的端點網址（`/member/screenshot/{id}` 會 302 到 S3）。 */
    public fun screenshotUrl(taskId: String): String

    /**
     * 用已登入的 session 打 `/member/screenshot/{id}`，回傳 302 導向的 S3 presigned 圖片
     * 絕對網址（該網址自帶簽章、無需登入）。
     *
     * 實作**必須驗證這個網址**：`Location` 完全由官方站決定，屬不受信任輸入。
     * 見 `TasksService.isAllowedScreenshotImageUrl`（https + 白名單 host），
     * 以及下載它時要用的 cookie-less 專用 client。
     */
    public suspend fun screenshotImageUrl(taskId: String): String
}

/**
 * 兌換服務介面：列出某期可兌換的商家品項、送出兌換申請。
 *
 * ⚠️ [redeem] 會消耗使用者真實的兌換次數且送出後不可更換；官網送出後該期即進入
 * state=REDEEMED，要看券碼還需再走一次簡訊 OTP 驗證（見 [VoucherServicing]），因此
 * [RedeemResult] 只能 best-effort 回報表單是否送出成功。呼叫端（UI）必須先讓使用者
 * 二次確認才可呼叫 [redeem]。
 */
public interface RedeemServicing {
    /** GET 兌換頁並解析出各商家品項清單。 */
    public suspend fun options(taskId: String): List<RedeemOption>

    /** 先 GET 兌換頁取得 `_csrf`，再 POST 送出兌換表單。 */
    public suspend fun redeem(taskId: String, vendorId: String, item: String): RedeemResult

    /**
     * GET 廠商可兌換商品頁並解析出分類與品項。
     *
     * [path] 只接受 [RedeemOption.introPath]——那是 `RedeemParser` 已經驗證過、
     * 限定在 `/intro/<檔名>.html` 的 base-relative path。**不要讓呼叫端自己拼網址**：
     * 官網的規則是「靜態頁存在才長出連結」，自己拼會拼出 404。
     */
    public suspend fun vendorIntro(path: String): VendorIntro
}

/**
 * 檢視加碼券服務：兌換完成（state=REDEEMED）後，每次要看券碼都要重新走一次簡訊 OTP。
 *
 * ⚠️ 合規要求「須本人帳號即時畫面抵用、不得截圖」，因此呼叫端絕對不可以快取
 * [fetchVoucher] 的結果──每次進入畫面都要從 [sendOtp]／[verifyOtp] 重新驗證一次。
 */
public interface VoucherServicing {
    /** 先 GET 券碼頁取得 `_csrf`，再 POST `/member/voucher/{uuid}/resend` 觸發簡訊發送。 */
    public suspend fun sendOtp(taskId: String)

    /** 先 GET 券碼頁取得 `_csrf`，再 POST `/member/voucher/{uuid}`（`_csrf`,`otp`）驗證。 */
    public suspend fun verifyOtp(taskId: String, otp: String): VoucherOtpResult

    /** GET `/member/voucher/{uuid}/view` 並解析出券碼內容。僅在 [verifyOtp] 回傳成功後呼叫。 */
    public suspend fun fetchVoucher(taskId: String): Voucher
}
