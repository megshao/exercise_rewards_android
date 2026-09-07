package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.networking.CsrfParser
import com.megshao.exerciserewards.core.networking.HTTPClienting
import com.megshao.exerciserewards.core.security.LogCategory
import com.megshao.exerciserewards.core.security.SecureLog

/**
 * 上傳沒成功的原因分類。
 *
 * **存在的理由是遙測**：[UploadResult.message] 有可能是官網 `.notice--error` 的**原文**
 * （可能回顯日期、檔名等），絕對不可以被送到第三方。這個階層是同一件事的封閉分類版本，
 * 讓遙測有東西可送，而 `message` 只留給畫面顯示。
 */
public sealed interface UploadFailure {
    /** 頁面沒有 file 欄位＝當期不在可上傳狀態或本期已上傳過。**這是常態，不是錯誤。** */
    public data object WindowClosed : UploadFailure

    /** 上傳頁抓不到 `_csrf`。 */
    public data object CsrfMissing : UploadFailure

    /** 官網收下了但退回（停在 200 上傳頁，可能附 `.notice--error`）。 */
    public data object SiteRejected : UploadFailure

    /** 非 200／302 的回應。 */
    public data class HttpError(public val status: Int) : UploadFailure
}

/** 上傳運動紀錄截圖的結果。 */
public data class UploadResult(
    public val submitted: Boolean,
    public val message: String,
    /** 失敗原因分類（成功時為 null）。只給遙測用，不影響畫面。 */
    public val failure: UploadFailure? = null,
)

/**
 * 上傳運動紀錄服務介面：把使用者從相簿自選的截圖送給官網
 * `multipart POST /member/upload`。
 *
 * 隱私注意：[imageData] 只會是使用者主動從相簿選取的截圖，**絕不是** App 自己產生的內容
 * ——送出去的一律是使用者親自挑選的那一張圖檔（而且已在 App 端重新編碼、去掉 EXIF/GPS）。
 */
public interface UploadServicing {
    /**
     * @param taskId 目前所在期別的 id（僅供 UI 顯示用；後端 `/member/upload` 會自動綁
     *   「當前可上傳期」，不需帶 UUID）。
     * @param imageData 使用者從相簿選取、已重新編碼過的截圖二進位內容。
     * @param fileName 送出時使用的檔名。
     */
    public suspend fun upload(taskId: String?, imageData: ByteArray, fileName: String): UploadResult
}

/**
 * 真正的上傳實作（對應官網的上傳流程）：
 * 1. `GET /member/upload` 取 `_csrf` 並確認頁面確有 file 欄位（`name="screenshot"`）。
 *    若當期不在可上傳狀態（非 NOT_UPLOADED、或已上傳過）則頁面沒有表單，回 submitted=false。
 * 2. `multipart POST /member/upload`，file 欄位名 **`screenshot`**，帶 `_csrf`。
 * 3. 成功後端回 302 → /member/tasks；停在 200 頁視為失敗（讀 `.notice--error` 或給通用訊息）。
 *
 * 與其他 service 共用同一個已登入的 [HTTPClienting]（session cookie 一路帶著）。
 */
public class UploadService(
    private val http: HTTPClienting,
) : UploadServicing {

    private val log = SecureLog(LogCategory.TASKS)

    override suspend fun upload(taskId: String?, imageData: ByteArray, fileName: String): UploadResult {
        // 1. 取上傳頁 → _csrf + 確認有 file 欄位
        val html = http.getHtml("/member/upload")
        if (!html.contains("name=\"screenshot\"") && !html.contains("type=\"file\"")) {
            log.info("upload page has no file field: window closed or already uploaded")
            return UploadResult(
                submitted = false,
                message = "目前不在可上傳期間，或本期已上傳過（每期限一次）。",
                failure = UploadFailure.WindowClosed,
            )
        }
        val csrf = try {
            CsrfParser.extract(html)
        } catch (_: AppError.CsrfNotFound) {
            return UploadResult(
                submitted = false,
                message = "無法取得上傳授權，請重新登入後再試。",
                failure = UploadFailure.CsrfMissing,
            )
        }

        val mime = if (fileName.lowercase().endsWith(".png")) "image/png" else "image/jpeg"

        // 2. multipart POST
        val result = http.uploadMultipart(
            path = "/member/upload",
            fields = listOf("_csrf" to csrf),
            fileField = "screenshot",
            fileName = fileName,
            mimeType = mime,
            fileData = imageData,
        )

        // 3. 判讀結果
        if (result.statusCode == 302 && result.location?.contains("/member/tasks") == true) {
            log.info("upload submitted (302)")
            return UploadResult(submitted = true, message = "已送出，審查約需 5 個工作日。")
        }
        if (result.statusCode == 200) {
            // 停在上傳頁：可能是格式／大小／日期不符，官方頁會有錯誤訊息。
            // ⚠️ 這段是官網原文，只准顯示在畫面上，**不可以進遙測**。
            val notice = errorNotice(result.body)
                ?: "上傳未通過檢查，請確認截圖為原始畫面、含當週日期與運動數據後再試。"
            log.info("upload stayed on page (200)")
            return UploadResult(submitted = false, message = notice, failure = UploadFailure.SiteRejected)
        }
        log.error("upload returned unexpected status")
        return UploadResult(
            submitted = false,
            message = "上傳失敗（回應碼 ${result.statusCode}），請稍後再試。",
            failure = UploadFailure.HttpError(result.statusCode),
        )
    }

    public companion object {
        /**
         * 從回應頁抓 `.notice--error` 文字（若有）。
         *
         * 這裡吃的也是官方站回傳的 HTML（不受信任輸入）。`[^>]*` 在「大量未閉合標籤」下是
         * O(n²)——`[^>]` 會一路掃到文件尾。屬性內不可能有裸 `<`，改用 `[^<>]{0,2000}`
         * 之後掃描在下一個 `<` 就停住，且成本與整頁長度脫鉤。
         * 同理 `\s*([^<]+)` 的兩個量詞字元集合重疊（`\s` ⊂ `[^<]`），合併成一個 `[^<]{0,2000}`。
         * 另有 HTTP client 的 2 MB body 上限當共用止血點。
         */
        public fun errorNotice(html: String): String? {
            val matched = NOTICE_ERROR.find(html)?.value ?: return null
            // 取最後一個 `>` 之後的可見文字
            val gt = matched.lastIndexOf('>')
            if (gt < 0) return null
            return matched.substring(gt + 1).trim().takeIf { it.isNotEmpty() }
        }

        private val NOTICE_ERROR = Regex("""notice--error[^<>]{0,2000}>[^<]{0,2000}""")
    }
}
