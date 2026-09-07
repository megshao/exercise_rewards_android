package com.megshao.exerciserewards.core.models

import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 驗證 [TaskPeriod] 的日期區間解析。
 *
 * 這一整組能力在 iOS v1.0.0 之前不存在：`startDate`／`endDate` 是 `String`，全 App 沒有一處
 * 讀過它們，導致「本週是哪一期」與「上傳窗還開著嗎」兩件事都只能靠狀態猜。
 */
class TaskPeriodDatesTest {

    /** 以台北時間組出一個時刻，測試才不會受跑測試那台機器的時區影響。 */
    private fun taipei(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(TaskPeriod.ACTIVITY_ZONE)
            .toInstant()

    private fun period(start: String, end: String, state: TaskState = TaskState.OPEN) =
        TaskPeriod(id = "id", index = 1, startDate = start, endDate = end, state = state)

    // MARK: - 區間邊界

    @Test
    fun `span starts at midnight of the start date`() {
        assertEquals(taipei(2026, 9, 1), period("2026/09/01", "2026/09/06").dateSpan?.start)
    }

    /** `endDate` 是**包含**當天的，所以上界要落在隔天零點。 */
    @Test
    fun `span ends at midnight after the end date`() {
        assertEquals(taipei(2026, 9, 7), period("2026/09/01", "2026/09/06").dateSpan?.end)
    }

    // MARK: - isCurrent / hasEnded

    @Test
    fun `last day before midnight is still current`() {
        val sut = period("2026/09/01", "2026/09/06")
        assertTrue(sut.isCurrent(taipei(2026, 9, 6, 23, 59)))
        assertFalse(sut.hasEnded(taipei(2026, 9, 6, 23, 59)))
    }

    /** 這就是回報的情境：9/7 一到，9/1~9/6 那期必須算結束。 */
    @Test
    fun `period has ended the day after its end date`() {
        val sut = period("2026/09/01", "2026/09/06")
        assertFalse(sut.isCurrent(taipei(2026, 9, 7)))
        assertTrue(sut.hasEnded(taipei(2026, 9, 7)))
    }

    @Test
    fun `first day is current from midnight`() {
        assertTrue(period("2026/09/07", "2026/09/13").isCurrent(taipei(2026, 9, 7)))
    }

    @Test
    fun `before the start it is neither current nor ended`() {
        val sut = period("2026/09/07", "2026/09/13")
        assertFalse(sut.isCurrent(taipei(2026, 9, 6, 23, 59)))
        assertFalse(sut.hasEnded(taipei(2026, 9, 6, 23, 59)))
    }

    /** 單日期別（官網第 14 期就是 `2026/11/30 ~ 2026/11/30`）不能被算成空區間。 */
    @Test
    fun `single day period covers that whole day`() {
        val sut = period("2026/11/30", "2026/11/30")
        assertTrue(sut.isCurrent(taipei(2026, 11, 30, 23, 59)))
        assertTrue(sut.hasEnded(taipei(2026, 12, 1)))
    }

    // MARK: - 解析失敗一律降級，不得誤擋

    /** 官網若改格式，`dateSpan` 要回 null，而不是硬湊出一個錯的區間。 */
    @Test
    fun `unparseable dates yield no span`() {
        val malformed = listOf(
            "09/01", "", "2026-09-01", "2026/09", "2026/09/01/02",
            "yyyy/MM/dd", "2026/13/01", "2026/02/30", "202/09/01",
        )
        for (value in malformed) {
            assertNull(period(value, "2026/09/06").dateSpan, "起日 $value 不該解析成功")
            assertNull(period("2026/09/01", value).dateSpan, "訖日 $value 不該解析成功")
        }
    }

    /**
     * 日期不可用時 `hasEnded` 必須是 false——寧可留著上傳鈕讓伺服器擋，
     * 也不要因為官網換了格式就把還開著的窗誤判成已結束。
     */
    @Test
    fun `unparseable dates never report ended`() {
        val sut = period("09/01", "09/06")
        assertFalse(sut.hasEnded(taipei(2030, 1, 1)))
        assertFalse(sut.isCurrent(taipei(2030, 1, 1)))
    }

    /** 起訖顛倒視為無效，否則會得到一個負長度的區間。 */
    @Test
    fun `reversed range is rejected`() {
        assertNull(period("2026/09/06", "2026/09/01").dateSpan)
    }

    /**
     * 日期一律以台北時間解讀，不跟著裝置時區跑：使用者在 UTC-8 的地方打開 App，
     * 台北時間 9/7 00:30 仍然屬於 9/7 那一期。
     */
    @Test
    fun `dates are interpreted in taipei regardless of device time zone`() {
        // 台北 9/7 00:00 == UTC 9/6 16:00
        assertEquals(
            Instant.ofEpochSecond(1_788_710_400),
            period("2026/09/07", "2026/09/13").dateSpan?.start,
        )
    }
}
