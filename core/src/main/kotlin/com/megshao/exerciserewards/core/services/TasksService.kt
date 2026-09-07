package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.TaskPeriod
import com.megshao.exerciserewards.core.networking.HTTPClienting
import com.megshao.exerciserewards.core.networking.OkHttpHttpClient
import com.megshao.exerciserewards.core.networking.SiteConfig
import com.megshao.exerciserewards.core.security.LogCategory
import com.megshao.exerciserewards.core.security.SecureLog
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 「我的任務」服務：取任務清單、組截圖圖片 URL。 */
public class TasksService(
    private val http: HTTPClienting,
) : TasksServicing {

    private val log = SecureLog(LogCategory.TASKS)

    override suspend fun fetchTasks(): List<TaskPeriod> {
        val html = http.getHtml("/member/tasks")
        val tasks = TaskParser.parse(html)
        log.debug { "fetched ${tasks.size} task periods" }
        return tasks
    }

    override fun screenshotUrl(taskId: String): String {
        if (taskId.isBlank()) throw AppError.Parsing("empty taskID")
        return SiteConfig.BASE + "/member/screenshot/" + taskId
    }

    override suspend fun screenshotImageUrl(taskId: String): String {
        if (taskId.isBlank()) throw AppError.Parsing("empty taskID")
        val location = http.redirectLocation("/member/screenshot/$taskId")
        if (location == null) {
            log.error("screenshot endpoint did not redirect")
            throw AppError.UnexpectedResponse(0)
        }
        if (!isAllowedScreenshotImageUrl(location)) {
            // host 只進 AppError（遙測端只取分類，不送 host 字串本身）。
            log.error("screenshot redirect pointed at a host outside the image allowlist")
            val host = location.toHttpUrlOrNull()?.host ?: "unknown"
            throw AppError.BlockedEgress(host)
        }
        log.debug { "resolved screenshot redirect location" }
        return location
    }

    public companion object {
        /**
         * 官方站存放截圖的圖片網域（實測為 S3，網域由**官方站**決定，不是本 App 決定）。
         * 這是 500.gov.tw 白名單之外唯一被允許的目的地。
         */
        private const val IMAGE_HOST_SUFFIX = "amazonaws.com"

        /**
         * 截圖圖片網址的准入條件。
         *
         * 這個 URL 完全來自官方站回傳的 302 `Location`，屬**不受信任輸入**：官網被入侵、
         * 或使用者裝置信任了 MITM 憑證（本專案刻意不做 certificate pinning）時，`Location`
         * 可以是任何東西。原樣交給圖片載入器的話至少有兩種後果：
         * - `https://attacker.example/1x1.png`：把使用者的 IP、UA、開啟時間送給第三方。
         * - `https://500.gov.tw/registrant/<任一 GET 端點>`：以同源身分觸發一次 GET。
         *
         * 因此這裡把目的地夾成「https + （官方站白名單 或 官方圖片儲存網域）」。
         * 這是**縱深防禦的第一層**；第二層是下載圖片時要用一個 cookie-less、不落盤的
         * 專用 client。
         */
        public fun isAllowedScreenshotImageUrl(rawUrl: String): Boolean {
            val url = rawUrl.toHttpUrlOrNull() ?: return false
            if (url.scheme.lowercase() != "https") return false
            val host = url.host.lowercase()
            if (OkHttpHttpClient.isAllowedHost(host)) return true
            return host == IMAGE_HOST_SUFFIX || host.endsWith(".$IMAGE_HOST_SUFFIX")
        }
    }
}
