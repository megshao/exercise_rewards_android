package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.assertCpuBudget
import com.megshao.exerciserewards.core.networking.HTTPFormResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證 [UploadService]：不打真實網路，全部透過 [FakeHttpClient]。 */
class UploadServiceTest {

    private val uploadPath = "/member/upload"
    private val page = """
        <form method="post" enctype="multipart/form-data">
        <input type="hidden" name="_csrf" value="upload-csrf">
        <input type="file" name="screenshot" accept="image/jpeg,image/png">
        </form>
    """.trimIndent()

    @Test
    fun `upload posts the csrf and the screenshot field`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[uploadPath] = page
            uploadResultByPath[uploadPath] = HTTPFormResult(302, "/member/tasks", "")
        }

        val result = UploadService(http).upload("task-1", byteArrayOf(1, 2, 3), "screenshot.jpg")

        assertTrue(result.submitted)
        assertNull(result.failure)
        assertEquals(listOf(uploadPath), http.getPaths)
        assertEquals(listOf(uploadPath), http.uploadPaths)
        // 欄位名是官網契約的一部分，寫錯就整個上傳失效（實測值，見 memory 的 R1）。
        assertEquals(listOf("screenshot"), http.uploadFileFields)
    }

    /**
     * 上傳頁沒有 file 欄位＝當期不在可上傳狀態或已上傳過。**這是常態，不是錯誤**，
     * 所以不能丟例外，要回一個看得懂的訊息。
     */
    @Test
    fun `upload reports window closed when the page has no file field`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[uploadPath] = "<p>本期已上傳</p>"
        }

        val result = UploadService(http).upload(null, byteArrayOf(1), "screenshot.jpg")

        assertFalse(result.submitted)
        assertEquals(UploadFailure.WindowClosed, result.failure)
        assertTrue(http.uploadPaths.isEmpty(), "沒有表單就不該送出任何東西")
    }

    @Test
    fun `upload reports csrf missing`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[uploadPath] = """<input type="file" name="screenshot">"""
        }

        val result = UploadService(http).upload(null, byteArrayOf(1), "screenshot.jpg")

        assertEquals(UploadFailure.CsrfMissing, result.failure)
        assertTrue(http.uploadPaths.isEmpty())
    }

    /** 停在 200：讀官網的 `.notice--error` 當訊息（那是原文，只准顯示不准進遙測）。 */
    @Test
    fun `upload surfaces the site error notice on 200`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[uploadPath] = page
            uploadResultByPath[uploadPath] = HTTPFormResult(
                200,
                null,
                """<p class="notice notice--error">截圖日期不在本期範圍內</p>""",
            )
        }

        val result = UploadService(http).upload(null, byteArrayOf(1), "screenshot.jpg")

        assertFalse(result.submitted)
        assertEquals(UploadFailure.SiteRejected, result.failure)
        assertEquals("截圖日期不在本期範圍內", result.message)
    }

    @Test
    fun `upload classifies an unexpected status`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath[uploadPath] = page
            uploadResultByPath[uploadPath] = HTTPFormResult(503, null, "")
        }

        val result = UploadService(http).upload(null, byteArrayOf(1), "screenshot.jpg")

        val failure = assertIs<UploadFailure.HttpError>(result.failure)
        assertEquals(503, failure.status)
    }

    @Test
    fun `errorNotice returns null when there is no notice`() {
        assertNull(UploadService.errorNotice("<p>一切正常</p>"))
    }

    /**
     * `errorNotice` 吃的是官網 HTML（不受信任輸入）。舊寫法的 `[^>]*` 在「大量未閉合標籤」
     * 下會一路掃到文件尾（O(n²)）；這條釘住線性成本，與其他 parser 的 ReDoS 回歸同一套規矩。
     */
    @Test
    fun `errorNotice survives many unclosed tags`() {
        val html = "notice--error" + "<span ".repeat(4 * 1024 / 6)

        assertCpuBudget(0.1, "UploadService.errorNotice（4 KB 對抗輸入）") {
            UploadService.errorNotice(html)
        }
    }
}
