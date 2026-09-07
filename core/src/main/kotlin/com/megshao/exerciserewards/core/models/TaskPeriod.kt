package com.megshao.exerciserewards.core.models

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 一期任務涵蓋的時間區間。半開區間 `[start, end)`。
 *
 * **上界刻意是開的**：官網的 `endDate` 是「包含當天」，所以 [end] 取的是隔天零點；
 * 用閉區間會讓隔天零點整同時屬於前後兩期。
 */
public data class DateSpan(public val start: Instant, public val end: Instant) {
    public operator fun contains(instant: Instant): Boolean = instant >= start && instant < end
}

/** 我的任務中的一期。 */
public data class TaskPeriod(
    /** 後端 UUID */
    public val id: String,
    /** 第 N 期 */
    public val index: Int,
    /** yyyy/MM/dd */
    public val startDate: String,
    public val endDate: String,
    public val state: TaskState,
    /** 剩 1 天 22 小時 */
    public val remainingText: String? = null,
    public val uploadedAt: String? = null,
    public val reviewedAt: String? = null,
    /**
     * 已兌換的期別，官網會在卡片上寫「兌換內容：萊爾富／指定雞胸果昔兌換券」。
     * 這裡存的是冒號後面那一段（通路／品項），只有 `state == REDEEMED` 時才會有。
     *
     * **這是官網原文，屬不受信任輸入**：只能顯示在畫面上，絕不可進遙測。
     */
    public val voucherSummary: String? = null,
) {
    public companion object {
        /**
         * 活動所在時區。
         *
         * 官網的期別日期只有 `yyyy/MM/dd`、**不帶時區**，語意是台北當地的那一天。
         * 若跟著裝置時區跑，出國的使用者會在跨日前後看到錯誤的當期。
         */
        public val ACTIVITY_ZONE: ZoneId = ZoneId.of("Asia/Taipei")

        /**
         * 把官網的 `yyyy/MM/dd` 解成當天零點（台北時間）。
         *
         * **刻意只認純數字、不用 DateTimeFormatter**：formatter 會吃裝置的 locale 與行事曆
         * 設定，使用者若把系統切成民國曆，`yyyy` 會被當成民國年解析（2026 → 西元 3937）。
         *
         * `2026/02/30` 這種不存在的日期由 [LocalDate.of] 直接丟 [DateTimeException]
         * （**不會**像 iOS 的 `Calendar` 那樣自動進位成 3/2），所以這裡不需要另外做回頭核對。
         */
        internal fun startOfDay(siteDate: String): Instant? {
            val parts = siteDate.split("/")
            if (parts.size != 3 || parts[0].length != 4) return null
            if (parts.any { part -> part.isEmpty() || !part.all { it in '0'..'9' } }) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val day = parts[2].toIntOrNull() ?: return null
            return try {
                LocalDate.of(year, month, day).atStartOfDay(ACTIVITY_ZONE).toInstant()
            } catch (_: DateTimeException) {
                null
            }
        }
    }

    /**
     * 這一期涵蓋的時間區間：[startDate] 當天零點起，到 [endDate] **當天結束**為止。
     *
     * 任一端解析失敗就回傳 null。官網的 markup 不是契約，
     * 呼叫端**必須**有一條不看日期的退路。
     */
    public val dateSpan: DateSpan?
        get() {
            val start = startOfDay(startDate) ?: return null
            val lastDay = startOfDay(endDate) ?: return null
            val end = lastDay.plus(java.time.Duration.ofDays(1))
            return if (start < end) DateSpan(start, end) else null
        }

    /** [now] 是否落在這一期之內（含起訖日當天）。日期解析不出來時回傳 false。 */
    public fun isCurrent(now: Instant): Boolean = dateSpan?.contains(now) == true

    /**
     * 這一期是否已經整個過完（[endDate] 當天已結束）。
     *
     * 日期解析不出來時回傳 false——寧可讓一個已過期的上傳鈕留著（送出時伺服器仍會擋），
     * 也不要因為官網改了日期格式就把還開著的窗誤擋掉。
     */
    public fun hasEnded(now: Instant): Boolean {
        val span = dateSpan ?: return false
        return now >= span.end
    }
}
