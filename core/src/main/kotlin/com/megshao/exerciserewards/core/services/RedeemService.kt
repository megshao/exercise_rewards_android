package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.RedeemOption
import com.megshao.exerciserewards.core.models.RedeemResult
import com.megshao.exerciserewards.core.models.VendorIntro
import com.megshao.exerciserewards.core.networking.CsrfParser
import com.megshao.exerciserewards.core.networking.HTTPClienting
import com.megshao.exerciserewards.core.security.LogCategory
import com.megshao.exerciserewards.core.security.SecureLog

/**
 * 兌換服務：GET 兌換頁解析商家品項清單、POST 送出兌換申請。
 *
 * ⚠️ [redeem] 會實際消耗使用者的兌換次數且送出後不可更換。官網送出表單後該期即進入
 * state=REDEEMED；要看券碼還需再走一次簡訊 OTP 驗證，該流程由 [VoucherService] 負責，
 * 這裡只能 best-effort 判斷兌換表單是否送出成功，回一段友善訊息。
 * 呼叫端（UI）必須先讓使用者二次確認過警語，才可以呼叫這支方法。
 */
public class RedeemService(
    private val http: HTTPClienting,
) : RedeemServicing {

    private val log = SecureLog(LogCategory.REDEEM)

    override suspend fun options(taskId: String): List<RedeemOption> {
        if (taskId.isBlank()) throw AppError.Parsing("empty taskID")
        val html = http.getHtml(redeemPath(taskId))
        val options = RedeemParser.parse(html)
        log.debug { "parsed ${options.size} redeem options" }
        return options
    }

    override suspend fun redeem(taskId: String, vendorId: String, item: String): RedeemResult {
        if (taskId.isBlank()) throw AppError.Parsing("empty taskID")
        val path = redeemPath(taskId)
        val html = http.getHtml(path)
        val csrf = CsrfParser.extract(html)

        val result = http.postForm(
            path = path,
            fields = listOf(
                "_csrf" to csrf,
                "vendorId" to vendorId,
                "item" to item,
            ),
        )

        // 官網送出兌換表單後該期即進入 state=REDEEMED；要看券碼還需再走一次簡訊 OTP 驗證，
        // 由 VoucherServicing 接手，這裡只回報表單是否成功送出。
        if (result.statusCode == 302) {
            log.info("redeem form submitted (302)")
            return RedeemResult(submitted = true, message = "已送出兌換，請完成簡訊驗證後檢視加碼券")
        }
        if (result.statusCode == 200) {
            // 停留在兌換頁：可能是表單驗證失敗，或該品項已被兌完／狀態已變化。
            log.info("redeem form stayed on page (200)")
            return RedeemResult(submitted = false, message = "兌換未成功，請重新整理頁面確認任務與品項狀態")
        }
        log.error("redeem form returned unexpected status")
        throw AppError.UnexpectedResponse(result.statusCode)
    }

    override suspend fun vendorIntro(path: String): VendorIntro {
        // 第二道白名單。第一道在 RedeemParser 的 introPath——那裡擋的是官網頁面上的 href，
        // 這裡擋的是「呼叫端傳了別的東西進來」。兩道都便宜，而這支方法會真的發請求。
        if (!ALLOWED_INTRO_PATH.containsMatchIn(path)) {
            throw AppError.Parsing("vendor intro path not allowed")
        }
        val html = http.getHtml(path)
        val intro = VendorIntroParser.parse(html)
        log.debug { "parsed vendor intro with ${intro.categories.size} categories" }
        return intro
    }

    private fun redeemPath(taskId: String): String = "/member/redeem/$taskId"

    private companion object {
        private val ALLOWED_INTRO_PATH = Regex("""^/intro/[A-Za-z0-9._-]{1,64}\.html$""")
    }
}
