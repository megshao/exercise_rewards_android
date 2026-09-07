package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.loadFixture
import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.VoucherOtpResult
import com.megshao.exerciserewards.core.networking.HTTPFormResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證 [VoucherService]：不打真實網路，全部透過 [FakeHttpClient]。 */
class VoucherServiceTest {

    private val taskId = "00000000-0000-4000-8000-000000000001"
    private val voucherPath = "/member/voucher/$taskId"
    private val resendPath = "$voucherPath/resend"
    private val viewPath = "$voucherPath/view"

    private fun voucherPageHtml(csrf: String): String = """
        <form class="voucher-otp-resend-form" action="$voucherPath/resend" method="post">
        <input name="_csrf" value="$csrf"></form>
        <form class="voucher-otp-verify-form" action="$voucherPath" method="post">
        <input name="_csrf" value="$csrf"><input name="otp"></form>
    """.trimIndent()

    // MARK: - sendOtp

    @Test
    fun `sendOtp fetches the csrf first then posts to the resend path`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[voucherPath] = voucherPageHtml("resend-csrf")
            formResultByPath[resendPath] = HTTPFormResult(200, null, "")
        }
        val sut = VoucherService(http)

        sut.sendOtp(taskId)

        assertEquals(listOf(voucherPath), http.getPaths)
        assertEquals(listOf(resendPath), http.postPaths)
        assertTrue(assertNotNull(http.postFields[resendPath]).contains("_csrf" to "resend-csrf"))
    }

    @Test
    fun `sendOtp throws csrf not found when the page has no csrf`() = runTest {
        val http = FakeHttpClient().apply { htmlByPath[voucherPath] = "<html>no csrf here</html>" }
        val sut = VoucherService(http)

        assertFailsWith<AppError.CsrfNotFound> { sut.sendOtp(taskId) }
        assertTrue(http.postPaths.isEmpty(), "should not POST when the csrf is missing")
    }

    @Test
    fun `sendOtp throws parsing when the task id is empty`() = runTest {
        val http = FakeHttpClient()
        val sut = VoucherService(http)

        assertFailsWith<AppError.Parsing> { sut.sendOtp("   ") }
        assertTrue(http.getPaths.isEmpty(), "should not hit the network for an empty taskID")
    }

    // MARK: - verifyOtp

    @Test
    fun `verifyOtp returns success on a 302 to the view path`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[voucherPath] = voucherPageHtml("verify-csrf")
            formResultByPath[voucherPath] = HTTPFormResult(302, "$voucherPath/view", "")
        }
        val sut = VoucherService(http)

        val result = sut.verifyOtp(taskId, otp = "123456")

        assertEquals(VoucherOtpResult.Success, result)
        assertEquals(listOf(voucherPath), http.getPaths)
        assertEquals(listOf(voucherPath), http.postPaths)
        val fields = assertNotNull(http.postFields[voucherPath])
        assertTrue(fields.contains("_csrf" to "verify-csrf"))
        assertTrue(fields.contains("otp" to "123456"))
    }

    @Test
    fun `verifyOtp returns wrong code with the remaining count on 200`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[voucherPath] = voucherPageHtml("verify-csrf")
            formResultByPath[voucherPath] = HTTPFormResult(200, null, loadFixture("voucher_verify_error"))
        }
        val sut = VoucherService(http)

        assertEquals(VoucherOtpResult.WrongCode(remaining = 2), sut.verifyOtp(taskId, otp = "000000"))
    }

    @Test
    fun `verifyOtp returns failed on an unexpected status`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[voucherPath] = voucherPageHtml("verify-csrf")
            formResultByPath[voucherPath] = HTTPFormResult(500, null, "")
        }
        val sut = VoucherService(http)

        assertIs<VoucherOtpResult.Failed>(sut.verifyOtp(taskId, otp = "000000"))
    }

    @Test
    fun `verifyOtp throws parsing when the task id is empty`() = runTest {
        val sut = VoucherService(FakeHttpClient())

        assertFailsWith<AppError.Parsing> { sut.verifyOtp("", otp = "123456") }
    }

    // MARK: - fetchVoucher

    @Test
    fun `fetchVoucher gets the view path and parses the fixture`() = runTest {
        val http = FakeHttpClient().apply { htmlByPath[viewPath] = loadFixture("voucher_view") }
        val sut = VoucherService(http)

        val voucher = sut.fetchVoucher(taskId)

        assertEquals(listOf(viewPath), http.getPaths)
        assertEquals(2, voucher.figures.size)
        assertTrue(voucher.vendorName.contains("示範超商 C"))
    }

    @Test
    fun `fetchVoucher throws parsing when the task id is empty`() = runTest {
        val http = FakeHttpClient()
        val sut = VoucherService(http)

        assertFailsWith<AppError.Parsing> { sut.fetchVoucher("  ") }
        assertTrue(http.getPaths.isEmpty(), "should not hit the network for an empty taskID")
    }
}
