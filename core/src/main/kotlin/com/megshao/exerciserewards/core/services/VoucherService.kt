package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.Voucher
import com.megshao.exerciserewards.core.models.VoucherOtpResult
import com.megshao.exerciserewards.core.networking.CsrfParser
import com.megshao.exerciserewards.core.networking.HTTPClienting
import com.megshao.exerciserewards.core.security.LogCategory
import com.megshao.exerciserewards.core.security.SecureLog

/**
 * 檢視加碼券服務：兌換完成（state=REDEEMED）後的簡訊 OTP 驗證 + 券碼頁解析。
 *
 * ⚠️ 合規要求「須本人帳號即時畫面抵用、不得截圖」——本 service 完全不做任何快取，
 * [fetchVoucher] 每次呼叫都會重新打一次 `/member/voucher/{uuid}/view`；呼叫端也絕不可以
 * 把解析出的 [Voucher] 存起來跨畫面重用，離開畫面就要丟棄，下次進入重新走一次
 * [sendOtp] → [verifyOtp] → [fetchVoucher]。
 *
 * 端點：
 * - 檢視頁：`GET /member/voucher/{uuid}`（含 resend/verify 兩個 form 的 `_csrf`）
 * - 發送：`POST /member/voucher/{uuid}/resend`（body `_csrf`）
 * - 驗證：`POST /member/voucher/{uuid}`（body `_csrf`,`otp`）→ 200+`.notice--error` 或 302 到 `.../view`
 * - 券碼頁：`GET /member/voucher/{uuid}/view`
 */
public class VoucherService(
    private val http: HTTPClienting,
) : VoucherServicing {

    private val log = SecureLog(LogCategory.VOUCHER)

    override suspend fun sendOtp(taskId: String) {
        val uuid = validated(taskId)
        val html = http.getHtml(voucherPath(uuid))
        val csrf = CsrfParser.extract(html)

        val result = http.postForm(
            path = resendPath(uuid),
            fields = listOf("_csrf" to csrf),
        )

        if (result.statusCode != 200 && result.statusCode != 302) {
            log.error("sendOtp returned unexpected status")
            throw AppError.UnexpectedResponse(result.statusCode)
        }
        log.info("otp resend requested")
    }

    override suspend fun verifyOtp(taskId: String, otp: String): VoucherOtpResult {
        val uuid = validated(taskId)
        val path = voucherPath(uuid)
        val html = http.getHtml(path)
        val csrf = CsrfParser.extract(html)

        val result = http.postForm(
            path = path,
            fields = listOf("_csrf" to csrf, "otp" to otp),
        )

        if (result.statusCode == 302 && result.location?.contains("/view") == true) {
            log.info("otp verified (302 -> view)")
            return VoucherOtpResult.Success
        }
        if (result.statusCode == 200) {
            val parsed = VoucherParser.parseVerifyError(result.body)
            log.info("otp verify stayed on page (200)")
            return VoucherOtpResult.WrongCode(remaining = parsed.remaining)
        }
        log.error("otp verify returned unexpected status")
        return VoucherOtpResult.Failed(message = "驗證失敗，請稍後再試")
    }

    override suspend fun fetchVoucher(taskId: String): Voucher {
        val uuid = validated(taskId)
        val html = http.getHtml(viewPath(uuid))
        val voucher = VoucherParser.parseView(html)
        log.debug { "parsed voucher with ${voucher.figures.size} figure(s)" }
        return voucher
    }

    private fun validated(taskId: String): String {
        val trimmed = taskId.trim()
        if (trimmed.isEmpty()) throw AppError.Parsing("empty taskID")
        return trimmed
    }

    private fun voucherPath(taskId: String): String = "/member/voucher/$taskId"

    private fun resendPath(taskId: String): String = voucherPath(taskId) + "/resend"

    private fun viewPath(taskId: String): String = voucherPath(taskId) + "/view"
}
