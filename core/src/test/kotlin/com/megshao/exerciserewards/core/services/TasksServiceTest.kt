package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證 [TasksService.screenshotImageUrl]：用已登入的 client 打 `/member/screenshot/{id}`，
 * 取 302 導向的絕對 Location（S3 presigned URL）。不打真實網路，全部透過 [FakeHttpClient]。
 */
class TasksServiceTest {

    private val taskId = "00000000-0000-4000-8000-000000000001"
    private val screenshotPath = "/member/screenshot/$taskId"

    @Test
    fun `screenshotImageUrl returns the redirect location verbatim`() = runTest {
        val s3Url = "https://example-bucket.s3.ap-northeast-1.amazonaws.com/uploads/photo123.jpg?X-Amz-Signature=abc123"
        val http = FakeHttpClient().apply { redirectLocationByPath[screenshotPath] = s3Url }
        val sut = TasksService(http)

        val url = sut.screenshotImageUrl(taskId)

        assertEquals(listOf(screenshotPath), http.redirectPaths)
        assertEquals(s3Url, url)
    }

    @Test
    fun `screenshotImageUrl throws when there is no redirect`() = runTest {
        // 未程式化任何 redirectLocation，等同真實情境的「非 3xx」。
        val http = FakeHttpClient()
        val sut = TasksService(http)

        val error = assertFailsWith<AppError.UnexpectedResponse> { sut.screenshotImageUrl(taskId) }
        assertEquals(0, error.statusCode)
    }

    @Test
    fun `screenshotImageUrl throws parsing for an empty task id`() = runTest {
        val http = FakeHttpClient()
        val sut = TasksService(http)

        assertFailsWith<AppError.Parsing> { sut.screenshotImageUrl("  ") }
        assertTrue(http.redirectPaths.isEmpty(), "should not hit the network for an empty taskID")
    }

    // MARK: - 302 Location 的網域驗證
    //
    // `Location` 完全由官方站決定，是不受信任輸入。官網被入侵、或裝置信任了 MITM 憑證時
    // 它可以是任何東西，而這個 URL 會被直接交給圖片載入器。

    @Test
    fun `screenshotImageUrl blocks a third party host`() = runTest {
        // 把使用者的 IP／UA／開啟時間送給第三方的典型 payload。
        val http = FakeHttpClient().apply {
            redirectLocationByPath[screenshotPath] = "https://attacker.example/1x1.png"
        }
        val sut = TasksService(http)

        val error = assertFailsWith<AppError.BlockedEgress> { sut.screenshotImageUrl(taskId) }
        assertEquals("attacker.example", error.host)
    }

    @Test
    fun `screenshotImageUrl blocks plain http`() = runTest {
        // https 以外一律擋（明文載圖會洩漏截圖內容）。
        val http = FakeHttpClient().apply {
            redirectLocationByPath[screenshotPath] = "http://bucket.s3.amazonaws.com/a.jpg"
        }
        val sut = TasksService(http)

        val error = assertFailsWith<AppError.BlockedEgress> { sut.screenshotImageUrl(taskId) }
        assertEquals("bucket.s3.amazonaws.com", error.host)
    }

    @Test
    fun `screenshotImageUrl allows the official site host`() = runTest {
        // 官方站本身仍在白名單內（下載端用 cookie-less client，所以同源 GET 不夾帶登入 cookie）。
        val location = "https://500.gov.tw/registrant/member/screenshot/$taskId/raw"
        val http = FakeHttpClient().apply { redirectLocationByPath[screenshotPath] = location }
        val sut = TasksService(http)

        assertEquals(location, sut.screenshotImageUrl(taskId))
    }

    @Test
    fun `isAllowedScreenshotImageUrl rejects the suffix spoof`() {
        // "amazonaws.com" 只是這個惡意網域的前綴，真正的 host 是 evil.com 的子網域。
        assertFalse(TasksService.isAllowedScreenshotImageUrl("https://amazonaws.com.evil.com/a.jpg"))
        assertTrue(TasksService.isAllowedScreenshotImageUrl("https://bucket.s3.ap-northeast-1.amazonaws.com/a.jpg"))
    }

    @Test
    fun `fetchTasks parses the tasks page`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/member/tasks"] = com.megshao.exerciserewards.core.loadFixture("member_tasks")
        }

        assertEquals(14, TasksService(http).fetchTasks().size)
    }
}
