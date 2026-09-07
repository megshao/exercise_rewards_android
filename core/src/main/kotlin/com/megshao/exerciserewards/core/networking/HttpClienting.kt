package com.megshao.exerciserewards.core.networking

/**
 * HTTP 抽象層介面。實作須：只允許 https 到 500.gov.tw、cookie 只存在 App 私有儲存區、
 * 修正官方站 http:// 降級 redirect、絕不 log 敏感內容。
 *
 * **命名刻意沿用 iOS 端的 `HTTPClienting`**（Kotlin 通常會叫 `HttpClient`）：
 * 這個 App 兩個平台是同一份領域邏輯的兩次實作，型別名一致才能跨 repo 直接 grep 對照。
 * 同樣理由適用於 `AuthServicing` / `TasksServicing` / `RedeemServicing` / `VoucherServicing`。
 */
public interface HTTPClienting {
    /** GET 一個頁面，回傳 HTML 字串（自動處理 LBSCookie 握手）。 */
    public suspend fun getHtml(path: String): String

    /** POST 表單，回傳最終 HTTP 狀態碼與（若有）Location 標頭與 body。 */
    public suspend fun postForm(path: String, fields: List<Pair<String, String>>): HTTPFormResult

    /**
     * GET path，但**不跟隨** redirect，回傳原始、未正規化的絕對 `Location` 標頭字串
     * （例如導向 S3 presigned URL 的截圖端點）；回應非 3xx 時回傳 null。
     *
     * 與 [getHtml]／[postForm] 不同：這裡刻意不做 http→https 正規化、不轉成 base-relative
     * path，因為目的地可能是白名單外的第三方 host（S3），呼叫端只是要把這個絕對網址
     * 原樣交給 UI 顯示，本 client 並不會連線過去。
     */
    public suspend fun redirectLocation(path: String): String?

    /**
     * multipart/form-data POST（上傳檔案用）：帶若干純文字欄位與一個檔案欄位，
     * **不跟隨** redirect，回傳狀態碼＋正規化後的 Location＋body。
     */
    public suspend fun uploadMultipart(
        path: String,
        fields: List<Pair<String, String>>,
        fileField: String,
        fileName: String,
        mimeType: String,
        fileData: ByteArray,
    ): HTTPFormResult

    /** 清除所有 cookie／session（登出用）。 */
    public suspend fun resetSession()
}

public data class HTTPFormResult(
    public val statusCode: Int,
    /** 302 的 Location（已正規化為 path） */
    public val location: String?,
    public val body: String,
)

public object SiteConfig {
    public const val HOST: String = "500.gov.tw"
    public const val BASE: String = "https://500.gov.tw/registrant"

    /**
     * [BASE] 的 URL path 部分（`/registrant`），用來把 Location header 還原成的絕對 path
     * 換算回 [HTTPClienting.getHtml]／[HTTPClienting.postForm] 使用的 base-relative path。
     */
    public const val BASE_PATH_PREFIX: String = "/registrant"
}
