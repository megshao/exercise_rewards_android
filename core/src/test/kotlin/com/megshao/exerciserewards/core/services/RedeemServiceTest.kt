package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.loadFixture
import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.networking.HTTPFormResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證 [RedeemService]：不打真實網路，全部透過 [FakeHttpClient]。 */
class RedeemServiceTest {

    private val taskId = "00000000-0000-4000-8000-000000000001"
    private val redeemPath = "/member/redeem/$taskId"

    // MARK: - options

    @Test
    fun `options fetches the redeem page and parses the fixture`() = runTest {
        val http = FakeHttpClient().apply { htmlByPath[redeemPath] = loadFixture("redeem") }
        val sut = RedeemService(http)

        val options = sut.options(taskId)

        assertEquals(listOf(redeemPath), http.getPaths)
        assertEquals(6, options.size)
        assertEquals("1", options.first().vendorId)
    }

    @Test
    fun `options throws parsing when the task id is empty`() = runTest {
        val http = FakeHttpClient()
        val sut = RedeemService(http)

        assertFailsWith<AppError.Parsing> { sut.options("  ") }
        assertTrue(http.getPaths.isEmpty(), "should not hit the network for an empty taskID")
    }

    // MARK: - redeem

    @Test
    fun `redeem fetches the csrf first then posts vendor and item`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[redeemPath] = loadFixture("redeem")
            formResultByPath[redeemPath] = HTTPFormResult(302, "/member/tasks", "")
        }
        val sut = RedeemService(http)

        val result = sut.redeem(taskId, vendorId = "1", item = "test-item-0001")

        // GET 先於 POST，且 POST 帶正確 path／fields
        assertTrue(result.submitted)
        assertFalse(result.message.isEmpty())
        assertEquals(listOf(redeemPath), http.getPaths)
        assertEquals(listOf(redeemPath), http.postPaths)

        val fields = assertNotNull(http.postFields[redeemPath])
        assertTrue(fields.contains("_csrf" to "test-csrf-token-member"))
        assertTrue(fields.contains("vendorId" to "1"))
        assertTrue(fields.contains("item" to "test-item-0001"))
    }

    @Test
    fun `redeem returns not submitted when the page stays with 200`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[redeemPath] = loadFixture("redeem")
            formResultByPath[redeemPath] = HTTPFormResult(200, null, "<html>redeem page</html>")
        }
        val sut = RedeemService(http)

        val result = sut.redeem(taskId, vendorId = "1", item = "test-item-0001")

        assertFalse(result.submitted)
        assertFalse(result.message.isEmpty())
    }

    @Test
    fun `redeem throws on an unexpected status`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[redeemPath] = loadFixture("redeem")
            formResultByPath[redeemPath] = HTTPFormResult(500, null, "")
        }
        val sut = RedeemService(http)

        val error = assertFailsWith<AppError.UnexpectedResponse> {
            sut.redeem(taskId, vendorId = "1", item = "test-item-0001")
        }
        assertEquals(500, error.statusCode)
    }

    @Test
    fun `redeem throws csrf not found when the redeem page has no csrf`() = runTest {
        val http = FakeHttpClient().apply { htmlByPath[redeemPath] = "<html>no csrf here</html>" }
        val sut = RedeemService(http)

        assertFailsWith<AppError.CsrfNotFound> {
            sut.redeem(taskId, vendorId = "1", item = "test-item-0001")
        }
        assertTrue(http.postPaths.isEmpty(), "should not POST when the csrf is missing")
    }

    // MARK: - vendorIntro 的第二道白名單

    @Test
    fun `vendorIntro rejects a path outside the intro namespace`() = runTest {
        val http = FakeHttpClient()
        val sut = RedeemService(http)

        for (path in listOf("/member/tasks", "/intro/../member/tasks", "https://evil.example/x.html")) {
            assertFailsWith<AppError.Parsing>("不該放行的 path：$path") { sut.vendorIntro(path) }
        }
        assertTrue(http.getPaths.isEmpty(), "白名單外的 path 不可以發出請求")
    }

    @Test
    fun `vendorIntro fetches and parses an allowed path`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/intro/vendor-1.html"] = loadFixture("vendor_intro_details")
        }
        val sut = RedeemService(http)

        val intro = sut.vendorIntro("/intro/vendor-1.html")

        assertEquals(listOf("/intro/vendor-1.html"), http.getPaths)
        assertEquals(4, intro.categories.size)
    }
}
