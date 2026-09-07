package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.loadFixture
import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.TaskPeriod
import com.megshao.exerciserewards.core.models.TaskState
import com.megshao.exerciserewards.core.models.current
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 驗證 [TaskParser] 對 `/member/tasks` 頁面的解析：
 * 14 期、第 1 期 redeemable 且有 uuid，其餘 notStarted。
 */
class TaskParserTest {

    @Test
    fun `parse returns fourteen periods`() {
        assertEquals(14, TaskParser.parse(loadFixture("member_tasks")).size)
    }

    @Test
    fun `first period is redeemable with a non empty id`() {
        val first = TaskParser.parse(loadFixture("member_tasks")).first()

        assertEquals(1, first.index)
        assertEquals(TaskState.REDEEMABLE, first.state)
        assertFalse(first.id.isEmpty())
        assertEquals("00000000-0000-4000-8000-000000000001", first.id)
        assertEquals("2026/09/01", first.startDate)
        assertEquals("2026/09/06", first.endDate)
        assertEquals("剩 1 天 22 小時", first.remainingText)
        assertEquals("2026/09/03 21:42", first.uploadedAt)
        assertEquals("2026/09/04 20:10", first.reviewedAt)
    }

    @Test
    fun `remaining periods are not started with empty id`() {
        val rest = TaskParser.parse(loadFixture("member_tasks")).drop(1)

        assertEquals(13, rest.size)
        for (period in rest) {
            assertEquals(TaskState.NOT_STARTED, period.state, "period ${period.index} should be notStarted")
            assertTrue(period.id.isEmpty(), "period ${period.index} should have no uuid")
        }
    }

    @Test
    fun `period indices are sequential`() {
        assertEquals((1..14).toList(), TaskParser.parse(loadFixture("member_tasks")).map { it.index })
    }

    @Test
    fun `parse throws when no period card is found`() {
        val error = assertFailsWith<AppError.Parsing> {
            TaskParser.parse("<html><body><p>no cards here</p></body></html>")
        }
        assertEquals("no period-card found in /member/tasks HTML", error.detail)
    }

    @Test
    fun `NOT_UPLOADED maps to open`() {
        // 當期尚未上傳 = period-state--NOT_UPLOADED，應對到 OPEN（可上傳）。
        val html = """
            <ul class="period-list">
            <li class="period-card period-card--current">
            <span class="period-no">第 1 期</span>
            <span class="period-range">2026/09/01 ~ 2026/09/06</span>
            <span class="period-remaining">剩 1 天 12 小時</span>
            <p class="period-state period-state--NOT_UPLOADED"><span>尚未上傳</span></p>
            <div class="period-actions"><a href="/registrant/member/upload" class="btn btn--primary">上傳運動紀錄</a></div>
            </li>
            </ul>
        """.trimIndent()

        val periods = TaskParser.parse(html)
        assertEquals(1, periods.size)
        assertEquals(TaskState.OPEN, periods[0].state)
        assertEquals(1, periods[0].index)
    }

    @Test
    fun `UNDER_REVIEW maps to pending review`() {
        // 上傳後待審核的 state class 是 UNDER_REVIEW，應對到 PENDING_REVIEW。
        val html = """
            <ul class="period-list">
            <li class="period-card">
            <span class="period-no">第 1 期</span>
            <span class="period-range">2026/09/01 ~ 2026/09/06</span>
            <p class="period-state period-state--UNDER_REVIEW"><span>待審核</span></p>
            </li>
            </ul>
        """.trimIndent()

        assertEquals(TaskState.PENDING_REVIEW, TaskParser.parse(html).first().state)
    }

    // MARK: - 已兌換的期別（兌換內容 + voucher 連結的 UUID）

    @Test
    fun `parses the voucher summary from a redeemed card`() {
        val first = TaskParser.parse(REDEEMED_CARD_HTML).first()

        assertEquals(TaskState.REDEEMED, first.state)
        assertEquals("示範超商 C／測試品項 C2 超值券", first.voucherSummary)
    }

    /**
     * 已兌換的卡片只剩 `/member/voucher/{uuid}` 連結。idPattern 若沒認這個路徑，
     * 已兌換那期的 id 會是空字串，券夾與首頁就點不進券碼頁。
     */
    @Test
    fun `parses the id from the voucher link on a redeemed card`() {
        assertEquals(
            "00000000-0000-4000-8000-000000000009",
            TaskParser.parse(REDEEMED_CARD_HTML).first().id,
        )
    }

    /** 沒有「兌換內容」那一行的期別（未兌換、尚未開始）一律是 null，不可以拿別的欄位頂替。 */
    @Test
    fun `voucher summary is null when the card has no voucher line`() {
        assertTrue(TaskParser.parse(loadFixture("member_tasks")).all { it.voucherSummary == null })
    }

    /** 官網文字會被 Thymeleaf 跳脫；沒還原的話畫面上會看到原始碼。 */
    @Test
    fun `decodes entities in the voucher summary`() {
        val html = """
            <ul><li class="period-card">
              <span class="period-no">第 1 期</span>
              <p class="period-state period-state--REDEEMED"><span>已兌換</span></p>
              <p class="period-voucher"><span>兌換內容：全家便利商店／50&#43;3元加碼券 &amp; 贈品</span></p>
            </li></ul>
        """.trimIndent()

        assertEquals("全家便利商店／50+3元加碼券 & 贈品", TaskParser.parse(html).first().voucherSummary)
    }

    // MARK: - 端到端回歸：真實頁面 + 真實日期

    /**
     * **回報的 bug（2026-09-07）**：拿官網真的回的那份 HTML，走完整條路徑
     * （解析 → 挑當期），9/7 當天必須選到第 2 期（9/7~9/13），而不是已經過完的第 1 期。
     *
     * 這張 fixture 正是踩雷的形狀：卡片 1→14 遞增，第 1 期已 REDEEMABLE、
     * 第 2 期仍 NOT_STARTED。舊的「取第一個非 notStarted」在這裡必然選錯。
     */
    @Test
    fun `current period on the real page advances on the seventh`() {
        val periods = TaskParser.parse(loadFixture("member_tasks"))
        val sept7 = LocalDateTime.of(2026, 9, 7, 9, 0).atZone(TaskPeriod.ACTIVITY_ZONE).toInstant()

        val current = TaskPeriod.current(periods, sept7)

        assertEquals(2, current?.index)
        assertEquals("2026/09/07", current?.startDate)
        assertEquals("2026/09/13", current?.endDate)
    }

    /** 同一份頁面：第 1 期在 9/7 必須被判定為已結束，上傳 CTA 才會收起來。 */
    @Test
    fun `first period on the real page has ended on the seventh`() {
        val periods = TaskParser.parse(loadFixture("member_tasks"))
        val sept7 = LocalDateTime.of(2026, 9, 7, 9, 0).atZone(TaskPeriod.ACTIVITY_ZONE).toInstant()

        assertTrue(periods[0].hasEnded(sept7))
        assertFalse(periods[1].hasEnded(sept7))
    }

    /**
     * 官網每一期的日期都必須解析得出來。這條在官網改日期格式時會第一個亮紅燈，
     * 提醒我們當期判斷已經默默退回舊的狀態啟發式了。
     */
    @Test
    fun `every period on the real page has a parseable date span`() {
        for (period in TaskParser.parse(loadFixture("member_tasks"))) {
            assertNotNull(
                period.dateSpan,
                "第 ${period.index} 期的日期 ${period.startDate} ~ ${period.endDate} 解析失敗",
            )
        }
    }

    private companion object {
        /**
         * 已兌換的卡片在官網長這樣：狀態是 REDEEMED、動作區只剩 voucher／screenshot 兩個連結
         * （沒有 redeem 連結了），卡片最後有一行「兌換內容：通路／品項」。
         */
        val REDEEMED_CARD_HTML = """
            <ul class="period-list">
              <li class="period-card period-card--current">
                <div class="period-head">
                  <span class="period-no">第 1 期</span>
                  <span class="period-range">2026/09/01 ~ 2026/09/06</span>
                  <span class="period-remaining">本期任務可上傳時間 剩 3 小時 20 分</span>
                </div>
                <p class="period-state period-state--REDEEMED"><span>已兌換</span></p>
                <p class="period-detail"><span>上傳時間：2026/09/03 21:42</span></p>
                <div class="period-actions">
                  <a href="/registrant/member/voucher/00000000-0000-4000-8000-000000000009" class="btn btn--primary">檢視加碼券</a>
                  <a href="/registrant/member/screenshot/00000000-0000-4000-8000-000000000009" class="btn btn--secondary">檢視我的截圖</a>
                </div>
                <p class="period-voucher"><span>兌換內容：示範超商 C／測試品項 C2 超值券</span></p>
              </li>
            </ul>
        """.trimIndent()
    }
}
