package com.megshao.exerciserewards.core.networking

import com.megshao.exerciserewards.core.models.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 只測 [OkHttpHttpClient] 抽出來的純函式（不發網路請求）：
 * 網域白名單判斷、redirect Location 正規化、表單編碼。
 */
class OkHttpHttpClientPureLogicTest {

    // MARK: isAllowedHost

    @Test
    fun `isAllowedHost accepts the exact root domain`() {
        assertTrue(OkHttpHttpClient.isAllowedHost("500.gov.tw"))
    }

    @Test
    fun `isAllowedHost accepts a subdomain`() {
        assertTrue(OkHttpHttpClient.isAllowedHost("cdn.500.gov.tw"))
    }

    @Test
    fun `isAllowedHost is case insensitive`() {
        assertTrue(OkHttpHttpClient.isAllowedHost("500.GOV.TW"))
    }

    @Test
    fun `isAllowedHost rejects an unrelated domain`() {
        assertFalse(OkHttpHttpClient.isAllowedHost("evil.com"))
    }

    @Test
    fun `isAllowedHost rejects the suffix spoof trick`() {
        // "500.gov.tw" 只是這個惡意網域的前綴，真正的 host 是 evil.com 的子網域
        assertFalse(OkHttpHttpClient.isAllowedHost("500.gov.tw.evil.com"))
    }

    // MARK: normalizeLocation

    @Test
    fun `normalizeLocation downgrades http and extracts the path`() {
        // scheme 被捨棄（一律用 https 重組請求），只留下 base-relative path
        assertEquals("/login", OkHttpHttpClient.normalizeLocation("http://500.gov.tw/registrant/login"))
    }

    @Test
    fun `normalizeLocation keeps https and query`() {
        assertEquals(
            "/access?_cookie_check=1",
            OkHttpHttpClient.normalizeLocation("https://500.gov.tw/registrant/access?_cookie_check=1"),
        )
    }

    @Test
    fun `normalizeLocation handles a relative location without host`() {
        assertEquals("/member/tasks", OkHttpHttpClient.normalizeLocation("/registrant/member/tasks"))
    }

    @Test
    fun `normalizeLocation throws blocked egress for a foreign host`() {
        assertFailsWith<AppError.BlockedEgress> {
            OkHttpHttpClient.normalizeLocation("http://evil.com/phish")
        }
    }

    // MARK: buildUrl

    @Test
    fun `buildUrl composes base and path`() {
        assertEquals(
            "https://500.gov.tw/registrant/access",
            OkHttpHttpClient.buildUrl("/access").toString(),
        )
    }

    @Test
    fun `buildUrl throws when the path is missing its leading slash`() {
        assertFailsWith<AppError.Parsing> { OkHttpHttpClient.buildUrl("access") }
    }

    // MARK: encodeForm

    @Test
    fun `encodeForm escapes spaces and reserved characters`() {
        val fields = listOf("idNo" to "A123456789", "note" to "a b&c")

        assertEquals("idNo=A123456789&note=a+b%26c", OkHttpHttpClient.encodeForm(fields))
    }

    // MARK: validateBodySize

    @Test
    fun `validateBodySize accepts a body at the limit and rejects one over it`() {
        OkHttpHttpClient.validateBodySize(OkHttpHttpClient.MAX_RESPONSE_BYTES)
        assertFailsWith<AppError.ResponseTooLarge> {
            OkHttpHttpClient.validateBodySize(OkHttpHttpClient.MAX_RESPONSE_BYTES + 1)
        }
    }
}
