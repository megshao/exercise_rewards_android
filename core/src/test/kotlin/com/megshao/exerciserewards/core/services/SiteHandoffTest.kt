package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.loadFixture
import com.megshao.exerciserewards.core.models.AppError
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證 [SiteHandoff]：官網改版時交接到官網的網址，與「這次失敗要不要走交接畫面」的分流。
 *
 * **這組測試守的是什麼**：`taskId` 來自解析官網 HTML，是不受信任輸入，而它會被拼進一個
 * 要交給系統瀏覽器打開的網址。這裡確認怪東西一律退回任務清單、不會組出越界路徑，
 * 而 parser 真的抓得到的 id 一定放行——兩邊都要對，否則要嘛開出奇怪的網址、要嘛按鈕失效。
 */
class SiteHandoffTest {

    private val taskId = "00000000-0000-4000-8000-000000000001"
    private val tasksUrl = "https://500.gov.tw/registrant/member/tasks"

    // MARK: - 三種 destination

    @Test
    fun `tasks destination points at the member tasks page`() {
        assertEquals(tasksUrl, SiteHandoff.url(SiteHandoffDestination.Tasks))
    }

    @Test
    fun `redeem destination carries the task id`() {
        assertEquals(
            "https://500.gov.tw/registrant/member/redeem/$taskId",
            SiteHandoff.url(SiteHandoffDestination.Redeem(taskId)),
        )
    }

    @Test
    fun `voucher destination carries the task id`() {
        assertEquals(
            "https://500.gov.tw/registrant/member/voucher/$taskId",
            SiteHandoff.url(SiteHandoffDestination.Voucher(taskId)),
        )
    }

    // MARK: - 永遠是 https 到 500.gov.tw

    @Test
    fun `every destination is https on the official host`() {
        val destinations = listOf(
            SiteHandoffDestination.Tasks,
            SiteHandoffDestination.Redeem(taskId),
            SiteHandoffDestination.Voucher(taskId),
            // 不合法的 id 退回任務清單後也要滿足同一條件。
            SiteHandoffDestination.Redeem("../../admin"),
            SiteHandoffDestination.Voucher(""),
        )
        for (destination in destinations) {
            val url = SiteHandoff.url(destination).toHttpUrl()
            assertEquals("https", url.scheme, "scheme of $destination")
            assertEquals("500.gov.tw", url.host, "host of $destination")
            assertEquals("/registrant", "/" + url.pathSegments.first(), "context path of $destination")
        }
    }

    // MARK: - 不受信任的 taskId 一律退回任務清單

    @Test
    fun `blank task id falls back to tasks`() {
        assertEquals(tasksUrl, SiteHandoff.url(SiteHandoffDestination.Redeem("")))
        assertEquals(tasksUrl, SiteHandoff.url(SiteHandoffDestination.Voucher("   ")))
    }

    @Test
    fun `path traversal falls back to tasks and never escapes the member path`() {
        val hostile = listOf("../", "..", "../../access", "$taskId/../../access", "..%2f..%2fadmin")
        for (id in hostile) {
            val url = SiteHandoff.url(SiteHandoffDestination.Redeem(id))
            assertEquals(tasksUrl, url, "id=$id")
            assertFalse(url.toHttpUrl().pathSegments.any { it == ".." }, "id=$id")
        }
    }

    @Test
    fun `query fragment and whitespace fall back to tasks`() {
        val hostile = listOf("$taskId?x=1", "$taskId#frag", "$taskId 1", "\t$taskId", "$taskId\n")
        for (id in hostile) {
            assertEquals(tasksUrl, SiteHandoff.url(SiteHandoffDestination.Voucher(id)), "id=$id")
        }
    }

    @Test
    fun `percent encoded traversal falls back to tasks`() {
        // 官網會先解碼再走路徑，所以 `%2e%2e` 就是 `..`；連 `%` 都不放行，這一類整個擋在外面。
        assertEquals(tasksUrl, SiteHandoff.url(SiteHandoffDestination.Redeem("%2e%2e")))
        assertEquals(tasksUrl, SiteHandoff.url(SiteHandoffDestination.Redeem("%2e%2e%2f%2e%2e")))
    }

    @Test
    fun `over long task id falls back to tasks`() {
        val sixtyFour = "a".repeat(64)
        val sixtyFive = "a".repeat(65)
        assertTrue(SiteHandoff.url(SiteHandoffDestination.Redeem(sixtyFour)).endsWith("/member/redeem/$sixtyFour"))
        assertEquals(tasksUrl, SiteHandoff.url(SiteHandoffDestination.Redeem(sixtyFive)))
    }

    @Test
    fun `non hex characters fall back to tasks`() {
        // 與 TaskParser 的 `idRegex` 一致：只收十六進位與連字號。
        for (id in listOf("javascript:alert(1)", "xyz", "0000-0000_0000", "Ａ１２３")) {
            assertEquals(tasksUrl, SiteHandoff.url(SiteHandoffDestination.Voucher(id)), "id=$id")
        }
    }

    // MARK: - 合法 uuid 原樣放進路徑

    @Test
    fun `valid uuid is placed verbatim without double encoding`() {
        val url = SiteHandoff.url(SiteHandoffDestination.Redeem(taskId))
        assertTrue(url.endsWith("/member/redeem/$taskId"))
        assertFalse(url.contains("%"), "uuid must not be percent-encoded")
        assertEquals(taskId, url.toHttpUrl().pathSegments.last())
    }

    @Test
    fun `upper case hex is accepted like the parser does`() {
        val upper = taskId.uppercase()
        assertTrue(SiteHandoff.url(SiteHandoffDestination.Voucher(upper)).endsWith("/member/voucher/$upper"))
    }

    /**
     * 兩邊的認定必須一致：parser 從官網頁面抓得到的 id，交接時一定要放行——
     * 否則官網改版當天，剛好是最需要這顆按鈕的人會被退回任務清單重找一次。
     */
    @Test
    fun `every task id the parser extracts is accepted`() {
        val ids = TaskParser.parse(loadFixture("member_tasks")).map { it.id }.filter { it.isNotEmpty() }
        assertTrue(ids.isNotEmpty(), "fixture should contain at least one period with a uuid")
        for (id in ids) {
            assertTrue(SiteHandoff.url(SiteHandoffDestination.Redeem(id)).endsWith("/member/redeem/$id"), "id=$id")
        }
    }

    // MARK: - 觸發條件分流

    @Test
    fun `parsing and csrf failures hand off to the site`() {
        assertTrue(SiteHandoff.shouldHandoff(AppError.Parsing("no period-card found")))
        assertTrue(SiteHandoff.shouldHandoff(AppError.CsrfNotFound))
    }

    @Test
    fun `other failures keep their existing handling`() {
        // 網路錯誤那句「請確認網路連線」是對的；未登入有既有的重新登入流程；其餘都不是「改版」。
        assertFalse(SiteHandoff.shouldHandoff(AppError.Network("timeout")))
        assertFalse(SiteHandoff.shouldHandoff(AppError.UnexpectedResponse(500)))
        assertFalse(SiteHandoff.shouldHandoff(AppError.NotLoggedIn))
        assertFalse(SiteHandoff.shouldHandoff(AppError.BlockedEgress("attacker.example")))
        assertFalse(SiteHandoff.shouldHandoff(AppError.ResponseTooLarge(3_000_000)))
        assertFalse(SiteHandoff.shouldHandoff(IllegalStateException("unrelated")))
    }
}
