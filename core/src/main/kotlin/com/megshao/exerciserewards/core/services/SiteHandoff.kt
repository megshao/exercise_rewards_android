package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.networking.SiteConfig

/**
 * 官網改版時要把使用者「交接」到官網的哪一頁。對應 iOS 端的 `SiteHandoffDestination`。
 *
 * 只列 App 自己有對應畫面的三處：任務清單（首頁與任務頁共用）、某一期的可兌換清單、
 * 某一期的加碼券券碼頁。名稱與 iOS 端一致，方便跨 repo grep。
 */
public sealed interface SiteHandoffDestination {
    /** 任務清單與首頁：`/member/tasks`。 */
    public data object Tasks : SiteHandoffDestination

    /** 某一期的可兌換清單：`/member/redeem/{uuid}`。 */
    public data class Redeem(public val taskId: String) : SiteHandoffDestination

    /** 某一期的加碼券券碼頁：`/member/voucher/{uuid}`。 */
    public data class Voucher(public val taskId: String) : SiteHandoffDestination
}

/**
 * 官網改版的降級接手。對應 iOS 端的 `SiteHandoff`。
 *
 * **要解決的問題**：這支 App 完全依賴 500.gov.tw 的 HTML 結構，全靠手寫 regex 解析。
 * 官網一改版，parser 就失效——而且是**所有已安裝版本同時失效**，只能等修好、送審、上架，
 * 空窗期數天。改版當天使用者看到的卻是「請確認網路連線」，於是去重開 Wi-Fi。
 * 錯誤歸因比壞掉更傷信任。
 *
 * 這裡不修 parser、不繞過改版，只負責兩件事：判斷「這次失敗是不是官網結構對不上」
 * （[shouldHandoff]），以及組出官網對應頁面的網址讓 UI 外開系統瀏覽器（[url]）。
 *
 * **為什麼不自動登入**（已評估過）：官網登入是兩段式 POST，`_csrf` 綁在當下 JSESSIONID 上，
 * 沒有任何 URL 可以「帶著三碼打開就登入」；App 的 cookie jar 也寫不進系統瀏覽器（平台故意封死）。
 * 唯一做得到的是 in-app WebView 注入 session，但那會把加密保護的 session 洩到 WebView 的
 * 明文儲存，`logout()` 也管不到。所以只交接、不代登。
 *
 * **URL 只能從這裡出去**：UI 層不得自己拼字串，也絕不接受解析內容裡的任意字串當網址。
 */
public object SiteHandoff {

    /**
     * 期別 UUID 的准入樣式，**與 [TaskParser] 抓 UUID 的 `idRegex` 一致**（十六進位加連字號、
     * 至多 64 字元）。[SiteHandoffDestination.Redeem]／[SiteHandoffDestination.Voucher] 帶的
     * `taskId` 來自解析官網 HTML，是不受信任輸入；照 parser 的認定收，parser 抓得到的 id
     * 這裡一定放行，其餘（`/`、`?`、`#`、`%`、空白……）一律不放，就不會組出越界路徑。
     */
    private val taskIdRegex = Regex("""^[0-9a-fA-F-]{1,64}$""")

    /**
     * 組出官網對應頁面的絕對網址。一律以 [SiteConfig.BASE] 為底，所以永遠是 https 且 host 是
     * 500.gov.tw。
     *
     * `taskId` 不合 [taskIdRegex] 時**退回任務清單**而不是回 null：使用者在那個當下仍該有
     * 一條路去官網，按鈕不能因為 id 長得不對就消失；任務清單也是他到了官網之後本來就會去的地方。
     */
    public fun url(destination: SiteHandoffDestination): String = when (destination) {
        SiteHandoffDestination.Tasks -> SiteConfig.BASE + "/member/tasks"

        is SiteHandoffDestination.Redeem -> validTaskId(destination.taskId)
            ?.let { SiteConfig.BASE + "/member/redeem/" + it }
            ?: url(SiteHandoffDestination.Tasks)

        is SiteHandoffDestination.Voucher -> validTaskId(destination.taskId)
            ?.let { SiteConfig.BASE + "/member/voucher/" + it }
            ?: url(SiteHandoffDestination.Tasks)
    }

    /**
     * 這次失敗要不要走「官網可能已改版」的交接畫面。
     *
     * 只有**官網結構對不上**的兩種才算：頁面解析不出預期結構（[AppError.Parsing]），
     * 或表單裡找不到 `_csrf`（[AppError.CsrfNotFound]）。其餘刻意不算：
     * - [AppError.Network]：那個情境下「請確認網路連線」是對的，不要蓋掉。
     * - [AppError.NotLoggedIn]：已有既有的重新登入流程。
     * - [AppError.UnexpectedResponse]／[AppError.BlockedEgress]／[AppError.ResponseTooLarge]：
     *   是官網掛了、被導去別的地方、或對面不是我們認得的站，都不是「改版」，
     *   把使用者送去官網也做不了事。
     */
    public fun shouldHandoff(error: Throwable): Boolean = when (error) {
        is AppError.Parsing, is AppError.CsrfNotFound -> true
        else -> false
    }

    private fun validTaskId(taskId: String): String? = taskId.takeIf(taskIdRegex::matches)
}
