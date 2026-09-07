package com.megshao.exerciserewards.core.models

import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證 [TaskPeriod.canUpload]——「要不要畫『上傳運動紀錄』按鈕」的唯一依據。
 *
 * **這組測試守的是什麼**：官網對上傳窗已經關掉的期別照樣回 `NOT_UPLOADED`（→ OPEN），
 * 所以「只看 state 就決定給不給 CTA」的判斷，會對過期未上傳的期別照樣畫出按鈕，
 * 一路到使用者按下「確認上傳」才被伺服器擋。細節見 [canUpload] 的註解。
 *
 * **為什麼是單元測試而不是 UI 測試**：這個分支在 UI 層沒有辦法被公平地驗到——
 * 示範資料裡沒有「已過期但仍 OPEN」的期別，要讓 UI 測試看到這種卡片就得替示範資料加一個
 * 測試專用的注入點，而那會踩到「啟動參數只能影響畫面呈現、不可以拿來改資料來源」的界線。
 * 因此改成把決策本身搬進 core，用這裡的矩陣覆蓋——UI 那一側剩下的只是
 * 「true 給按鈕、false 給一行字」。
 */
class UploadWindowTest {

    private fun taipei(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(TaskPeriod.ACTIVITY_ZONE)
            .toInstant()

    /** 官網實際存在過的一期：`2026/09/01 ~ 2026/09/06`（活動首期只有 6 天）。 */
    private fun period(
        state: TaskState,
        start: String = "2026/09/01",
        end: String = "2026/09/06",
    ) = TaskPeriod(id = "id", index = 1, startDate = start, endDate = end, state = state)

    // MARK: - 回報的 bug

    /**
     * **回報情境**：9/1~9/6 那期在 9/7 已經關窗，但官網仍回 `NOT_UPLOADED`。
     * 舊版只看 state，於是這張卡片照樣有「上傳運動紀錄」按鈕。
     */
    @Test
    fun `open period cannot upload once its window has closed`() {
        assertFalse(period(TaskState.OPEN).canUpload(taipei(2026, 9, 7)))
    }

    /** 上界是隔天零點，所以最後一天的 23:59 仍然可以上傳——修正不能把窗提早關掉。 */
    @Test
    fun `open period can still upload until midnight of its last day`() {
        assertTrue(period(TaskState.OPEN).canUpload(taipei(2026, 9, 6, 23, 59)))
    }

    @Test
    fun `open period can upload in the middle of its window`() {
        assertTrue(period(TaskState.OPEN).canUpload(taipei(2026, 9, 3, 12)))
    }

    /** 單日期別（官網第 14 期就是 `2026/11/30 ~ 2026/11/30`）不能被算成一開始就關著的窗。 */
    @Test
    fun `single day period is uploadable for that whole day`() {
        val sut = period(TaskState.OPEN, start = "2026/11/30", end = "2026/11/30")
        assertTrue(sut.canUpload(taipei(2026, 11, 30, 23, 59)))
        assertFalse(sut.canUpload(taipei(2026, 12, 1)))
    }

    // MARK: - 只有 OPEN 能上傳

    /**
     * 其餘四個狀態不論日期都不能上傳：已上傳過的期別（審核中／可兌換／已兌換）每期限一次，
     * 尚未開始的期別則連表單都還沒開。窗還開著的時間點也一樣不行——
     * 這裡刻意用「還在窗內」的 9/3 來測，才驗得出 state 那半邊真的有在把關。
     */
    @Test
    fun `only open periods are uploadable even while the window is still open`() {
        val insideWindow = taipei(2026, 9, 3, 12)
        val others = listOf(
            TaskState.NOT_STARTED, TaskState.PENDING_REVIEW,
            TaskState.REDEEMABLE, TaskState.REDEEMED, TaskState.UNKNOWN,
        )
        for (state in others) {
            assertFalse(period(state).canUpload(insideWindow), "$state 不該可以上傳")
        }
    }

    /**
     * 新增 [TaskState] 成員時這個測試會失敗，提醒作者去決定新狀態能不能上傳，
     * 而不是讓它默默落進 `state == OPEN` 的否定側（比照 TaskStateTest 的同名守則）。
     */
    @Test
    fun `every state is classified deliberately`() {
        val uploadable = setOf(TaskState.OPEN)
        val notUploadable = setOf(
            TaskState.NOT_STARTED, TaskState.PENDING_REVIEW,
            TaskState.REDEEMABLE, TaskState.REDEEMED, TaskState.UNKNOWN,
        )

        assertTrue(uploadable.intersect(notUploadable).isEmpty())
        assertEquals(
            TaskState.entries.size,
            uploadable.size + notUploadable.size,
            "TaskState 有新成員時，請先決定它能不能上傳運動紀錄",
        )

        val insideWindow = taipei(2026, 9, 3, 12)
        uploadable.forEach { assertTrue(period(it).canUpload(insideWindow), "$it") }
        notUploadable.forEach { assertFalse(period(it).canUpload(insideWindow), "$it") }
    }

    // MARK: - 降級：只擋官網明確說已結束的

    /**
     * 日期解析不出來（官網改格式）時**不得誤擋**：按鈕留著，由伺服器判。
     * 這是刻意的不對稱——誤擋會讓還來得及的使用者整期作廢，誤放只是多一次失敗的送出。
     */
    @Test
    fun `unparseable dates keep the upload button available`() {
        for (malformed in listOf("09/01", "", "2026-09-01", "2026/13/01", "yyyy/MM/dd")) {
            assertTrue(
                period(TaskState.OPEN, start = malformed).canUpload(taipei(2030, 1, 1)),
                "起日 $malformed 不該讓上傳鈕消失",
            )
            assertTrue(
                period(TaskState.OPEN, end = malformed).canUpload(taipei(2030, 1, 1)),
                "訖日 $malformed 不該讓上傳鈕消失",
            )
        }
    }

    /**
     * 同一個方向：OPEN 但還沒到起日也照樣給上傳鈕。官網對未開始的期別回的是
     * NOT_STARTED，會走到這裡代表官網自己說可以上傳，App 端不多擋一層。
     */
    @Test
    fun `open period before its start date is still offered`() {
        assertTrue(period(TaskState.OPEN).canUpload(taipei(2026, 8, 20, 12)))
    }

    /**
     * 一律以台北時間判斷，不跟著裝置時區跑：使用者人在 UTC-8，台北時間 9/7 00:30
     * 那一刻當地才 9/6 早上，窗仍然必須算關了。
     */
    @Test
    fun `window closes on taipei time regardless of device time zone`() {
        // 台北 2026/09/07 00:30 == UTC 2026/09/06 16:30
        val justAfterClose = Instant.ofEpochSecond(1_788_712_200)
        assertEquals(taipei(2026, 9, 7, 0, 30), justAfterClose)
        assertFalse(period(TaskState.OPEN).canUpload(justAfterClose))
    }
}
