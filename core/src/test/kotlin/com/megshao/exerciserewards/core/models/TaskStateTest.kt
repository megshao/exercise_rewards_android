package com.megshao.exerciserewards.core.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證 [TaskState.showsUploadCountdown]。
 *
 * 這條規則存在的理由是一個實測事實：官網的 `period-remaining` 是**上傳窗**倒數
 * （「本期任務可上傳時間 剩 N 小時 N 分」），而且**對已經走完審核的期別照樣回傳**。
 * 2026-09-06 實測一張已兌換的券，卡片上仍寫著那句話。
 *
 * 因此這裡守的不是排版偏好，而是「哪些狀態下官網給的那個數字已經沒有意義」。
 */
class TaskStateTest {

    /** 審核完成之後（可兌換／已兌換）上傳早就做完了，倒數指的是一個用不到的窗。 */
    @Test
    fun `hides upload countdown after review is complete`() {
        assertFalse(TaskState.REDEEMABLE.showsUploadCountdown)
        assertFalse(TaskState.REDEEMED.showsUploadCountdown)
    }

    /** 上傳窗還可能用得到的狀態一律保留。 */
    @Test
    fun `shows upload countdown while the upload window still matters`() {
        assertTrue(TaskState.NOT_STARTED.showsUploadCountdown)
        assertTrue(TaskState.OPEN.showsUploadCountdown)
        assertTrue(TaskState.PENDING_REVIEW.showsUploadCountdown)
    }

    /** 認不出來的狀態保守處理：照顯示，不要把官網給的資訊悄悄吃掉。 */
    @Test
    fun `unknown state keeps showing whatever the site sent`() {
        assertTrue(TaskState.UNKNOWN.showsUploadCountdown)
    }

    /**
     * 新增狀態時這個測試會失敗，提醒作者去決定新狀態該落在哪一邊——
     * 而不是讓它默默沿用 when 的某個分支。
     */
    @Test
    fun `every state is classified deliberately`() {
        val hidden = setOf(TaskState.REDEEMABLE, TaskState.REDEEMED)
        val shown = setOf(TaskState.NOT_STARTED, TaskState.OPEN, TaskState.PENDING_REVIEW, TaskState.UNKNOWN)

        assertTrue(hidden.intersect(shown).isEmpty())
        assertEquals(
            TaskState.entries.size,
            hidden.size + shown.size,
            "TaskState 有新成員時，請先決定它要不要顯示上傳窗倒數",
        )
        hidden.forEach { assertFalse(it.showsUploadCountdown, "$it") }
        shown.forEach { assertTrue(it.showsUploadCountdown, "$it") }
    }
}
