package com.megshao.exerciserewards.data

import android.content.Context
import com.megshao.exerciserewards.core.models.TaskPeriod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 任務清單的本地快取 + 節流。
 * - 本地優先：畫面先顯示上次抓到的任務，避免每次都等網路。
 * - 節流：距離上次成功更新未滿 [MIN_INTERVAL_MILLIS]（60 秒）不再發 request。
 *
 * 快取只存任務狀態（期數／日期／狀態／倒數），**不含個資**。
 * 期別 UUID 屬於官方站識別碼，本來就已經在這份資料裡（`TaskPeriod.id`），
 * 這不算新增暴露面；但它一樣不准進遙測。
 */
public class TasksCache(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    public fun load(): List<TaskPeriod>? {
        val raw = prefs.getString(KEY_PERIODS, null) ?: return null
        // 解碼失敗代表 App 更新後 `TaskPeriod` 結構變了，快取全體失效——
        // 這是回歸訊號，但**不附任何快取內容**（雖然不含個資，但沒必要）。
        val periods = runCatching { json.decodeFromString<List<CachedPeriod>>(raw) }.getOrNull()
            ?: return null
        return periods.map { it.toDomain() }.ifEmpty { null }
    }

    public fun save(periods: List<TaskPeriod>) {
        val payload = json.encodeToString(periods.map(CachedPeriod::from))
        prefs.edit()
            .putString(KEY_PERIODS, payload)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    public fun lastUpdated(): Long? = prefs.getLong(KEY_UPDATED_AT, 0).takeIf { it > 0 }

    /** 距離上次更新是否已超過節流間隔（沒有紀錄時視為可更新）。 */
    public fun canRefresh(now: Long = System.currentTimeMillis()): Boolean {
        val last = lastUpdated() ?: return true
        return now - last >= MIN_INTERVAL_MILLIS
    }

    /** 清除快取（登出／清資料／進出示範模式時用）。 */
    public fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * 快取的序列化形狀。
     *
     * **刻意不直接 `@Serializable` 在 `TaskPeriod` 上**：那是 core 的領域型別，
     * 它的欄位形狀不該被「本機快取的檔案格式」綁住。中間隔一層，core 改欄位時
     * 這裡自己決定要不要跟、以及舊快取要怎麼處理。
     */
    @Serializable
    private data class CachedPeriod(
        val id: String,
        val index: Int,
        val startDate: String,
        val endDate: String,
        val state: String,
        val remainingText: String? = null,
        val uploadedAt: String? = null,
        val reviewedAt: String? = null,
        val voucherSummary: String? = null,
    ) {
        fun toDomain(): TaskPeriod = TaskPeriod(
            id = id,
            index = index,
            startDate = startDate,
            endDate = endDate,
            state = runCatching { enumValueOf<com.megshao.exerciserewards.core.models.TaskState>(state) }
                .getOrDefault(com.megshao.exerciserewards.core.models.TaskState.UNKNOWN),
            remainingText = remainingText,
            uploadedAt = uploadedAt,
            reviewedAt = reviewedAt,
            voucherSummary = voucherSummary,
        )

        companion object {
            fun from(period: TaskPeriod): CachedPeriod = CachedPeriod(
                id = period.id,
                index = period.index,
                startDate = period.startDate,
                endDate = period.endDate,
                state = period.state.name,
                remainingText = period.remainingText,
                uploadedAt = period.uploadedAt,
                reviewedAt = period.reviewedAt,
                voucherSummary = period.voucherSummary,
            )
        }
    }

    public companion object {
        public const val MIN_INTERVAL_MILLIS: Long = 60_000

        private const val FILE_NAME = "tasks_cache"
        private const val KEY_PERIODS = "periods_v1"
        private const val KEY_UPDATED_AT = "updated_at_v1"
    }
}
