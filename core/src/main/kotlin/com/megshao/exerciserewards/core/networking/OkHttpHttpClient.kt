package com.megshao.exerciserewards.core.networking

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.security.LogCategory
import com.megshao.exerciserewards.core.security.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * [HTTPClienting] 的預設實作，基於 OkHttp。對應 iOS 端的 `URLSessionHTTPClient`。
 *
 * 安全設計重點（與 iOS 端逐條對齊）：
 * - 只允許連到 [SiteConfig.HOST]（`500.gov.tw`）及其子網域，其餘一律丟出
 *   [AppError.BlockedEgress]（見 [isAllowedHost]）。
 * - Cookie 儲存交給注入的 [ClearableCookieJar]：`:app` 給持久化版本（登入 session 可跨
 *   App 重啟續用），測試給 [InMemoryCookieJar]。[resetSession]（登出）把它清空。
 * - **不自動跟隨 redirect**（`followRedirects(false)`），由本類別自行讀取 3xx 狀態碼與
 *   `Location` header、正規化後決定下一步。這同時修正官方站「redirect 的 Location 是
 *   http://」造成 Secure cookie 掉失的問題（正規化後只保留 path，實際請求一律用 https
 *   重新組 URL），也讓 LBSCookie 的 `?_cookie_check=1` 握手可以透過一般的 redirect 迴圈
 *   自然完成（cookie jar 是同一個，握手拿到的 cookie 會自動帶進下一跳）。
 * - **Response body 大小上限 2 MB**（[MAX_RESPONSE_BYTES]）：超過就丟
 *   [AppError.ResponseTooLarge]，body 不解碼、不交給任何 parser。這是所有 HTML parser
 *   共用的止血點，見該常數的說明。
 * - 絕不記錄 cookie／body／表單欄位；需要時只記錄 path（不含 query）與狀態碼。
 */
public class OkHttpHttpClient(
    private val cookieJar: ClearableCookieJar = InMemoryCookieJar(),
) : HTTPClienting {

    private val log = SecureLog(LogCategory.NETWORK)

    private val client: OkHttpClient = OkHttpClient.Builder()
        // 自己讀 3xx，理由見類別註解。
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    // MARK: - HTTPClienting

    override suspend fun getHtml(path: String): String {
        var currentUrl = buildUrl(path)
        var hop = 0
        while (true) {
            hop += 1
            if (hop > MAX_REDIRECTS) throw AppError.UnexpectedResponse(-1)

            val raw = send(buildRequest(currentUrl, "GET"))
            if (raw.statusCode in 300..399) {
                val location = raw.location ?: throw AppError.UnexpectedResponse(raw.statusCode)
                currentUrl = buildUrl(normalizeLocation(location))
                continue
            }
            if (raw.statusCode != 200) throw AppError.UnexpectedResponse(raw.statusCode)
            return raw.body
        }
    }

    override suspend fun postForm(path: String, fields: List<Pair<String, String>>): HTTPFormResult {
        val url = buildUrl(path)
        val body = encodeForm(fields).toByteArray(Charsets.UTF_8)
        val request = buildRequest(
            url = url,
            method = "POST",
            body = body,
            contentType = "application/x-www-form-urlencoded; charset=utf-8",
        )
        val raw = send(request)
        return HTTPFormResult(
            statusCode = raw.statusCode,
            location = raw.location?.let { normalizeLocation(it) },
            body = raw.body,
        )
    }

    override suspend fun uploadMultipart(
        path: String,
        fields: List<Pair<String, String>>,
        fileField: String,
        fileName: String,
        mimeType: String,
        fileData: ByteArray,
    ): HTTPFormResult {
        val url = buildUrl(path)
        val boundary = "----HuihanBoundary${UUID.randomUUID()}"
        val body = encodeMultipart(fields, fileField, fileName, mimeType, fileData, boundary)
        val request = buildRequest(
            url = url,
            method = "POST",
            body = body,
            contentType = "multipart/form-data; boundary=$boundary",
        )
        val raw = send(request)
        return HTTPFormResult(
            statusCode = raw.statusCode,
            location = raw.location?.let { normalizeLocation(it) },
            body = raw.body,
        )
    }

    override suspend fun redirectLocation(path: String): String? {
        val raw = send(buildRequest(buildUrl(path), "GET"))
        if (raw.statusCode !in 300..399) return null
        // 刻意不呼叫 normalizeLocation：目的地可能是白名單外的 S3 host，
        // 這裡只原樣回傳 Location header，交給呼叫端當純字串使用。
        return raw.location
    }

    override suspend fun resetSession() {
        cookieJar.clear()
    }

    // MARK: - Sending

    private data class RawResponse(
        val statusCode: Int,
        val location: String?,
        val body: String,
    )

    /**
     * 送出單一請求，**不跟隨 redirect**（交給呼叫端讀 3xx + Location 自行處理）。
     * 連線層錯誤（斷網、逾時、TLS…）一律包成 [AppError.Network]，避免原生例外逸出到 UI
     * 顯示成「未知錯誤」；暫時性錯誤自動重試一次。
     */
    private suspend fun send(request: Request): RawResponse = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            if (!isRetryable(error)) {
                // 不記錄 URL/query，只記錄例外型別（非個資）。
                log.error("network error (${error.javaClass.simpleName})")
                throw AppError.Network(error.javaClass.simpleName)
            }
            delay(RETRY_DELAY_MILLIS)
            try {
                client.newCall(request).execute()
            } catch (retryError: IOException) {
                log.error("network error (${retryError.javaClass.simpleName}) after retry")
                throw AppError.Network(retryError.javaClass.simpleName)
            }
        }

        response.use { readCapped(it) }
    }

    /**
     * 讀取回應，套用 [MAX_RESPONSE_BYTES] 上限。
     *
     * 先看站方宣告的 Content-Length，再看實收位元組數（有些回應不帶 Content-Length，
     * 或宣告的跟實際的不符，兩邊都要擋）。任一超標就直接丟錯，
     * **body 不會被解碼成字串、更不會交給任何 parser**。
     *
     * 實收這一路刻意讀到 `上限 + 1` 就停手（而不是像 iOS 那樣整包收完再量）：
     * 已經確定要丟錯的東西沒有理由先吃進記憶體。代價是超標時報不出精確位元組數，
     * 所以宣告值存在時優先報宣告值——那個數字只給 log，不進遙測。
     */
    private fun readCapped(response: Response): RawResponse {
        val declared = response.header("Content-Length")?.toLongOrNull()
        if (declared != null && declared > MAX_RESPONSE_BYTES) {
            log.error("response body too large (declared $declared bytes)")
            throw AppError.ResponseTooLarge(declared)
        }
        val (bytes, exceeded) = readAtMost(response.body.byteStream(), MAX_RESPONSE_BYTES)
        if (exceeded) {
            log.error("response body too large (over $MAX_RESPONSE_BYTES bytes)")
            throw AppError.ResponseTooLarge(declared ?: (MAX_RESPONSE_BYTES + 1))
        }
        log.debug { "${response.request.method} ${response.request.url.encodedPath} -> ${response.code}" }
        return RawResponse(
            statusCode = response.code,
            location = response.header("Location"),
            body = String(bytes, Charsets.UTF_8),
        )
    }

    public companion object {
        /** 保護用的 redirect 迴圈上限，避免正規化邏輯出錯造成無窮迴圈。 */
        internal const val MAX_REDIRECTS: Int = 5

        internal const val RETRY_DELAY_MILLIS: Long = 500

        /**
         * response body 的大小上限（2 MB）。
         *
         * **這是所有 parser 共用的止血點。** 每個 parser 都是用正規表示式吃官方站回傳的
         * HTML，而那是不受信任的輸入；只要有任何一條樣式在對抗輸入下退化成超線性，
         * 一頁惡意 HTML 就能把整個 IO dispatcher 卡住。逐條修 regex 是必要的，但擋不住
         * 之後新加的 parser；把輸入長度先夾住，才是對「未來的自己」有效的防線。
         *
         * 2 MB 的依據：官方頁面實測都在數十 KB（fixture 最大 4 KB），留兩個數量級的餘裕。
         * 超過就代表對面不是我們認得的那個站。
         */
        public const val MAX_RESPONSE_BYTES: Long = 2L * 1024 * 1024

        private const val USER_AGENT: String =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0.0.0 Mobile Safari/537.36"
        private const val ACCEPT_HEADER: String =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

        /**
         * body 超過 [MAX_RESPONSE_BYTES] 就丟 [AppError.ResponseTooLarge]。
         * 抽成純函式方便單元測試（不必真的下載 2 MB）。
         */
        public fun validateBodySize(byteCount: Long) {
            if (byteCount > MAX_RESPONSE_BYTES) throw AppError.ResponseTooLarge(byteCount)
        }

        /** 讀最多 [max] 個位元組；回傳讀到的內容與「是否已超過上限」。 */
        internal fun readAtMost(stream: InputStream, max: Long): Pair<ByteArray, Boolean> {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0L
            stream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > max) return ByteArray(0) to true
                    out.write(buffer, 0, read)
                }
            }
            return out.toByteArray() to false
        }

        /** 暫時性連線錯誤（值得重試一次）：逾時、連線中斷、找不到主機、TLS 交握失敗等。 */
        public fun isRetryable(error: IOException): Boolean = when (error) {
            is SocketTimeoutException, is ConnectException, is UnknownHostException,
            is SSLException, is SocketException,
            -> true

            else -> false
        }

        /** 是否為白名單網域：[SiteConfig.HOST] 本身或其子網域。 */
        public fun isAllowedHost(host: String): Boolean {
            val candidate = host.lowercase()
            val root = SiteConfig.HOST.lowercase()
            return candidate == root || candidate.endsWith(".$root")
        }

        /**
         * 把 redirect 的 `Location` header 正規化成 [HTTPClienting.getHtml]／
         * [HTTPClienting.postForm] 慣用的 base-relative path（例如 `/login`、
         * `/access?_cookie_check=1`）。
         *
         * - 若帶有 host 且不在白名單內，丟出 [AppError.BlockedEgress]。
         * - 一律捨棄 scheme（不論站方回傳 http 或 https），實際請求永遠用 https 重組，
         *   藉此修正官方站 redirect 降級為 http 造成 Secure cookie 掉失的問題。
         */
        public fun normalizeLocation(location: String): String {
            val uri = try {
                URI(location)
            } catch (_: URISyntaxException) {
                throw AppError.Parsing("invalid Location header")
            }
            uri.host?.let { host ->
                if (!isAllowedHost(host)) throw AppError.BlockedEgress(host)
            }

            // 用 rawPath / rawQuery（保留 percent-encoding）：解碼後再重組會把
            // 原本編碼過的字元變成另一個網址。
            var path = uri.rawPath.orEmpty()
            if (path.isEmpty()) path = "/"
            uri.rawQuery?.takeIf { it.isNotEmpty() }?.let { path += "?$it" }

            val prefix = SiteConfig.BASE_PATH_PREFIX
            if (prefix.isNotEmpty() && path.startsWith(prefix)) {
                val stripped = path.removePrefix(prefix)
                return stripped.ifEmpty { "/" }
            }
            return if (path.startsWith("/")) path else "/$path"
        }

        /** 用 base-relative path（以 `/` 開頭）組出完整 https URL，並驗證網域白名單。 */
        public fun buildUrl(basePath: String): HttpUrl {
            if (!basePath.startsWith("/")) throw AppError.Parsing("path must start with /")
            val url = (SiteConfig.BASE + basePath).toHttpUrlOrNull()
                ?: throw AppError.Parsing("invalid path")
            if (url.scheme != "https" || !isAllowedHost(url.host)) {
                throw AppError.BlockedEgress(url.host)
            }
            return url
        }

        internal fun buildRequest(
            url: HttpUrl,
            method: String,
            body: ByteArray? = null,
            contentType: String? = null,
        ): Request {
            if (url.scheme != "https" || !isAllowedHost(url.host)) {
                throw AppError.BlockedEgress(url.host)
            }
            val requestBody = body?.toRequestBody(contentType?.toMediaType())
            return Request.Builder()
                .url(url)
                .method(method, requestBody)
                .header("User-Agent", USER_AGENT)
                .header("Accept", ACCEPT_HEADER)
                .apply { contentType?.let { header("Content-Type", it) } }
                .build()
        }

        /**
         * `application/x-www-form-urlencoded` 編碼（空白轉 `+`，其餘保留字元皆 percent-encode）。
         *
         * [URLEncoder] 的安全字元集正好是英數 + `-._*`、空白轉 `+`，與 iOS 端手寫的那組一致。
         */
        public fun encodeForm(fields: List<Pair<String, String>>): String =
            fields.joinToString("&") { (name, value) ->
                "${URLEncoder.encode(name, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }

        /** 組 multipart/form-data 內容：純文字欄位 + 一個檔案欄位。 */
        public fun encodeMultipart(
            fields: List<Pair<String, String>>,
            fileField: String,
            fileName: String,
            mimeType: String,
            fileData: ByteArray,
            boundary: String,
        ): ByteArray {
            val out = ByteArrayOutputStream()
            fun append(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
            val dashBoundary = "--$boundary\r\n"
            for ((name, value) in fields) {
                append(dashBoundary)
                append("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                append("$value\r\n")
            }
            append(dashBoundary)
            append("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\n")
            append("Content-Type: $mimeType\r\n\r\n")
            out.write(fileData)
            append("\r\n")
            append("--$boundary--\r\n")
            return out.toByteArray()
        }
    }
}
