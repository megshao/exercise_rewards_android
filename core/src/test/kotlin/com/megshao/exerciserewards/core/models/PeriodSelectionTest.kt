package com.megshao.exerciserewards.core.models

import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 驗證 [TaskPeriod.Companion.current]——首頁「本週任務」與任務頁置頂高亮的唯一依據。
 *
 * iOS v1.0.0 的規則是「第一個非 NOT_STARTED 的期別」，完全不看日期，而且**沒有任何測試**
 * 覆蓋它。這個檔案就是補上那張網。
 */
class PeriodSelectionTest {

    private fun taipei(year: Int, month: Int, day: Int, hour: Int = 12): Instant =
        LocalDateTime.of(year, month, day, hour, 0)
            .atZone(TaskPeriod.ACTIVITY_ZONE)
            .toInstant()

    /** 官網實際的排法：1→14 遞增，第 1 期是 9/1~9/6（6 天的首期），其後每期週一～週日。 */
    private fun officialSchedule(states: Map<Int, TaskState> = emptyMap()): List<TaskPeriod> {
        val ranges = listOf(
            "2026/09/01" to "2026/09/06", "2026/09/07" to "2026/09/13",
            "2026/09/14" to "2026/09/20", "2026/09/21" to "2026/09/27",
            "2026/09/28" to "2026/10/04", "2026/10/05" to "2026/10/11",
        )
        return ranges.mapIndexed { offset, range ->
            TaskPeriod(
                id = "id-${offset + 1}",
                index = offset + 1,
                startDate = range.first,
                endDate = range.second,
                state = states[offset + 1] ?: TaskState.NOT_STARTED,
            )
        }
    }

    // MARK: - 回報的 bug

    /**
     * **回報情境（2026-09-07）**：第 1 期 9/1~9/6 已 REDEEMABLE、第 2 期 9/7~9/13 仍
     * NOT_STARTED。舊規則會選中第 1 期，首頁「本週任務」因此顯示已經過完的那一週。
     */
    @Test
    fun `current week advances even when the new period is still not started`() {
        val periods = officialSchedule(mapOf(1 to TaskState.REDEEMABLE))
        val current = TaskPeriod.current(periods, taipei(2026, 9, 7))
        assertEquals(2, current?.index)
        assertEquals("2026/09/07", current?.startDate)
    }

    /** 9/6 當天還在第 1 期——修正不能把當期提早翻過去。 */
    @Test
    fun `current week still points at period one on its last day`() {
        val periods = officialSchedule(mapOf(1 to TaskState.REDEEMABLE))
        assertEquals(1, TaskPeriod.current(periods, taipei(2026, 9, 6, 23))?.index)
    }

    /**
     * 舊規則真正的規模：第 1 期一旦不是 NOT_STARTED 就**永遠**被選中。
     * 逐週往前走，當期必須跟著前進，不能整季卡在第 1 期。
     */
    @Test
    fun `current week keeps advancing across the whole season`() {
        val periods = officialSchedule(
            mapOf(1 to TaskState.REDEEMED, 2 to TaskState.REDEEMED, 3 to TaskState.REDEEMABLE),
        )
        val expected = listOf(
            taipei(2026, 9, 3) to 1, taipei(2026, 9, 10) to 2, taipei(2026, 9, 17) to 3,
            taipei(2026, 9, 24) to 4, taipei(2026, 10, 1) to 5, taipei(2026, 10, 8) to 6,
        )
        for ((now, index) in expected) {
            assertEquals(index, TaskPeriod.current(periods, now)?.index, "$now 應該落在第 $index 期")
        }
    }

    /** 陣列順序不該影響結果——官網哪天改成倒序排也一樣。 */
    @Test
    fun `result does not depend on array order`() {
        val periods = officialSchedule(mapOf(1 to TaskState.REDEEMABLE)).reversed()
        assertEquals(2, TaskPeriod.current(periods, taipei(2026, 9, 7))?.index)
    }

    // MARK: - 活動邊界

    /** 活動還沒開始：顯示最早的一期，讓使用者看得到即將開始的任務。 */
    @Test
    fun `before the season starts shows the earliest period`() {
        assertEquals(1, TaskPeriod.current(officialSchedule(), taipei(2026, 8, 20))?.index)
    }

    /** 活動全部結束：停在最後一期，而不是變成空白。 */
    @Test
    fun `after the season ends shows the last period`() {
        val periods = officialSchedule(mapOf(1 to TaskState.REDEEMED))
        assertEquals(6, TaskPeriod.current(periods, taipei(2027, 1, 1))?.index)
    }

    @Test
    fun `empty input yields null`() {
        assertNull(TaskPeriod.current(emptyList(), taipei(2026, 9, 7)))
    }

    // MARK: - 官網改格式時的退路

    /** 日期全部解析不出來時退回舊的狀態啟發式，而不是讓區塊空白。 */
    @Test
    fun `falls back to the state heuristic when no date parses`() {
        val periods = listOf(
            TaskPeriod("a", 1, "09/01", "09/06", TaskState.NOT_STARTED),
            TaskPeriod("b", 2, "09/07", "09/13", TaskState.OPEN),
        )
        assertEquals(2, TaskPeriod.current(periods, taipei(2026, 9, 7))?.index)
    }

    /** 只要**有一期**日期可用就走日期路徑，不因其他期別壞掉而整組降級。 */
    @Test
    fun `partially parseable schedule still uses dates`() {
        val periods = officialSchedule(mapOf(1 to TaskState.REDEEMABLE)).toMutableList()
        periods[0] = periods[0].copy(startDate = "壞掉的日期")
        assertEquals(2, TaskPeriod.current(periods, taipei(2026, 9, 7))?.index)
    }
}
