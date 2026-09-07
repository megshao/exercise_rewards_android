package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.loadFixture
import com.megshao.exerciserewards.core.models.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 驗證 [VoucherParser] 對合成最小 fixture 的解析
 * （見 `src/test/resources/fixtures/voucher_view.html`／`voucher_verify_error.html`）。
 * 純函式測試，不打網路。
 */
class VoucherParserTest {

    // MARK: - parseView

    @Test
    fun `parseView extracts two figures with code 128`() {
        val voucher = VoucherParser.parseView(loadFixture("voucher_view"))

        assertEquals(2, voucher.figures.size)
        assertEquals("CODE_128", voucher.figures[0].format)
        assertEquals("00000000", voucher.figures[0].value)
        assertEquals("CODE_128", voucher.figures[1].format)
        assertEquals("AAAA0000BBBB1111", voucher.figures[1].value)
    }

    @Test
    fun `parseView extracts captions in order`() {
        val voucher = VoucherParser.parseView(loadFixture("voucher_view"))

        assertTrue(voucher.figures[0].caption.contains("商品條碼"))
        assertTrue(voucher.figures[1].caption.contains("券號條碼"))
    }

    @Test
    fun `parseView extracts vendor item and expiry`() {
        val voucher = VoucherParser.parseView(loadFixture("voucher_view"))

        assertTrue(voucher.vendorName.contains("示範超商 C"), "vendorName was: ${voucher.vendorName}")
        assertTrue(voucher.itemName.contains("超值"), "itemName was: ${voucher.itemName}")
        assertTrue(voucher.expiry.contains("115"), "expiry was: ${voucher.expiry}")
    }

    @Test
    fun `parseView extracts notices`() {
        val voucher = VoucherParser.parseView(loadFixture("voucher_view"))

        assertFalse(voucher.notices.isEmpty())
        assertTrue(voucher.notices.any { it.contains("不得以紙本列印") })
    }

    @Test
    fun `parseView throws parsing when there are no figures`() {
        assertFailsWith<AppError.Parsing> {
            VoucherParser.parseView("<html><body>沒有券碼區塊</body></html>")
        }
    }

    // MARK: - parseVerifyError

    @Test
    fun `parseVerifyError extracts the remaining count`() {
        val parsed = VoucherParser.parseVerifyError(loadFixture("voucher_verify_error"))

        assertEquals(2, parsed.remaining)
        assertTrue(parsed.message.contains("驗證碼錯誤"))
    }

    @Test
    fun `parseVerifyError falls back when the notice is missing`() {
        val parsed = VoucherParser.parseVerifyError("<html><body>沒有錯誤提示</body></html>")

        assertNull(parsed.remaining)
        assertFalse(parsed.message.isEmpty())
    }
}
