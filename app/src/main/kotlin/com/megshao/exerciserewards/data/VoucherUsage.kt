package com.megshao.exerciserewards.data

import android.content.Context
import com.megshao.exerciserewards.core.models.TaskPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「這張加碼券我已經用掉了」的本機標記。
 *
 * ## 為什麼需要這個
 *
 * 官網**沒有**「已使用／已核銷」這個狀態。實測（2026-09-06，券已在超商用掉的帳號）：
 * `/member/tasks` 那一期仍然是 `period-state--REDEEMED`「已兌換」，仍然掛著
 * 「檢視加碼券」連結，卡片上也仍然寫著「兌換內容：…」；券碼頁（OTP 關卡）
 * 與沒用過的券**一模一樣**，沒有任何 used／expired 標記。
 *
 * 也就是說**這個狀態只能由使用者自己告訴 App**。
 *
 * ## 界線
 *
 * - **純本機狀態**：不送官網、不進遙測。券到底還能不能用，一律以現場條碼掃得過為準；
 *   這裡只是使用者自己的紀錄。
 * - **可還原**：標錯了要能改回來，因此 [setUsed] 兩個方向都支援。
 * - **算「本機資料」**：[clear] 必須被「立即清除本機資料」與示範模式切換呼叫，
 *   否則會出現「示範資料的券被標成已使用」或「清完資料還記得你用過哪張」。
 *
 * ## key 用期別 UUID 而不是期數
 *
 * 期數只有 1–14，換帳號就會撞在一起——A 帳號標記的第 3 期會直接套到 B 帳號的第 3 期上。
 * 期別 UUID 是官網給的、綁帳號，不會有這個問題。
 *
 * ## 為什麼是一個共用的 StateFlow 而不是各畫面自己讀一次
 *
 * **這裡踩過坑（iOS 端），理由留著**：標記可以在三個地方被改（券碼頁、券夾、首頁），
 * 而首頁／任務／券夾是同時活著的三個分頁。先前的寫法是每個 ViewModel 各抄一份快照，
 * 結果在券夾標記完切回任務分頁，那份快照還是舊的——連下拉重新整理都救不了，
 * 因為下拉只重抓官網資料，而「已使用」根本不在官網資料裡。
 *
 * 改成共用的 [usedIds] 之後，任何一處寫入都會讓三個分頁一起重畫，
 * 不需要任何「回來時記得重讀」的呼叫（那種呼叫漏掉一個就是同一個 bug 再來一次）。
 */
public class VoucherUsageStore(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val _usedIds = MutableStateFlow(readIds())

    public val usedIds: StateFlow<Set<String>> = _usedIds.asStateFlow()

    public fun isUsed(period: TaskPeriod): Boolean = isUsed(period.id)

    public fun isUsed(id: String): Boolean = id.isNotEmpty() && _usedIds.value.contains(id)

    /** 切換某一期的標記，回傳切換後的值（呼叫端用它送遙測）。 */
    public fun toggle(id: String): Boolean {
        val used = !isUsed(id)
        setUsed(used, id)
        return used
    }

    /** 標記／取消標記。id 為空字串（官網沒給 UUID 的期別）一律忽略。 */
    public fun setUsed(used: Boolean, id: String) {
        if (id.isEmpty()) return
        val next = _usedIds.value.toMutableSet().apply { if (used) add(id) else remove(id) }
        // 排序過再存：內容順序穩定，日後 diff／除錯時看得懂。
        prefs.edit().putStringSet(KEY_IDS, next.toSortedSet()).apply()
        _usedIds.value = next
    }

    /** 清除所有標記（「立即清除本機資料」與示範模式切換時呼叫）。 */
    public fun clear() {
        prefs.edit().remove(KEY_IDS).apply()
        _usedIds.value = emptySet()
    }

    private fun readIds(): Set<String> = prefs.getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()

    private companion object {
        const val FILE_NAME = "voucher_usage"
        const val KEY_IDS = "used_ids_v1"
    }
}
