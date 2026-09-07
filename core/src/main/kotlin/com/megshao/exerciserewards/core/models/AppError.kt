package com.megshao.exerciserewards.core.models

/**
 * 全 Kit 共用的錯誤型別。對應 iOS 端 `AppError`（`Sources/ExerciseRewardsKit/Models/Models.swift`）。
 *
 * **為什麼是 sealed class 而不是一堆 Exception 子類散在各處**：遙測端只准送「分類」不准送
 * associated value（host 字串、parser 訊息、位元組數都不得外流），封閉階層讓那個對應表
 * 在編譯期就是窮盡的。
 */
public sealed class AppError(message: String) : Exception(message) {
    /** 連線層錯誤。`detail` 只放錯誤碼之類的非個資字串，**不放 URL**。 */
    public data class Network(val detail: String) : AppError("network error: $detail")

    /** 頁面上找不到 `_csrf` 隱藏欄位，或其值為空。 */
    public data object CsrfNotFound : AppError("csrf token not found")

    /** 官方站回了預期外的狀態碼。 */
    public data class UnexpectedResponse(val statusCode: Int) : AppError("unexpected response: $statusCode")

    /** HTML 結構與預期不符。`detail` 是給 log 看的，不進遙測。 */
    public data class Parsing(val detail: String) : AppError("parsing failed: $detail")

    public data object NotLoggedIn : AppError("not logged in")

    /** 嘗試連非白名單網域。`host` 只給 log，遙測端只送 HostClass 分類。 */
    public data class BlockedEgress(val host: String) : AppError("blocked egress: $host")

    /**
     * response body 超過 [com.megshao.exerciserewards.core.networking.OkHttpHttpClient.MAX_RESPONSE_BYTES]（2 MB）。
     *
     * 官方頁面實測都在數十 KB；超過這個量級代表對面不是我們認得的那個站
     * （官網被入侵、或裝置信任了 MITM 憑證），此時**不該把 body 交給任何 parser**。
     * [byteCount] 是實際／宣告的位元組數，只給 log 用，不進遙測。
     */
    public data class ResponseTooLarge(val byteCount: Long) : AppError("response too large: $byteCount bytes")
}
