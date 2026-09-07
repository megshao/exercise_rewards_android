package com.megshao.exerciserewards.core.models

import java.time.Instant

// MARK: - 「本週是哪一期」

/**
 * 從 14 期裡挑出「當期」——**依日曆，不依狀態**。
 *
 * **這裡踩過的坑**（iOS v1.0.0 的 bug，2026-09-07 回報）：舊版的規則是
 * 「第一個非 [TaskState.NOT_STARTED] 的期別」。官網回的卡片是 1→14 遞增順序，第 1 期一旦走到
 * 可兌換／已兌換就永遠不再是 NOT_STARTED，於是它永遠是「第一個符合的」——首頁「本週任務」
 * 與任務頁的置頂高亮會整整 14 期黏在第 1 期上，一次都不前進。
 * 當時的示範資料刻意把當期排在陣列第 0 位，剛好把這個 bug 遮住，截圖測試也驗不出來。
 *
 * 現在的規則，由上而下：
 * 1. [now] 落在哪一期的日期區間內，就是哪一期；
 * 2. 活動還沒開始 → 最早的那一期（讓使用者看得到即將開始的任務）；
 * 3. 活動已全部結束 → 最後結束的那一期；
 * 4. **所有**期別的日期都解析不出來（官網改了格式）→ 退回舊的狀態啟發式。
 *
 * 第 4 條刻意保留：官網的 markup 不是契約，日期壞掉時「猜錯一期」仍遠好過讓整個區塊空白。
 *
 * @param periods 官網回傳的期別清單，順序不拘。
 * @param now 判斷基準時間。
 */
public fun TaskPeriod.Companion.current(periods: List<TaskPeriod>, now: Instant): TaskPeriod? {
    val dated = periods.mapNotNull { period -> period.dateSpan?.let { period to it } }
    if (dated.isEmpty()) return stateOnlyFallback(periods)

    dated.firstOrNull { (_, span) -> now in span }?.let { return it.first }
    // 活動尚未開始：取最早開始的那一期。
    dated.filter { (_, span) -> now < span.start }
        .minByOrNull { (_, span) -> span.start }
        ?.let { return it.first }
    // 活動已全部結束：停在最後結束的那一期。
    return dated.maxByOrNull { (_, span) -> span.end }?.first
}

/**
 * 日期完全不可用時的退路：第一個非 [TaskState.NOT_STARTED] 的期別，找不到就用 index 最大的那期。
 *
 * 這就是 iOS v1.0.0 的舊規則。**只給 [current] 在日期解析全滅時呼叫**，
 * 不要當成正常路徑——它本身就是上面那個 bug 的來源。
 */
internal fun TaskPeriod.Companion.stateOnlyFallback(periods: List<TaskPeriod>): TaskPeriod? =
    periods.firstOrNull { it.state != TaskState.NOT_STARTED } ?: periods.maxByOrNull { it.index }
