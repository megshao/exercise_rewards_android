package com.megshao.exerciserewards.core.models

import java.time.Instant

// MARK: - 「這一期現在還能不能上傳」

/**
 * 這一期現在還能不能上傳運動紀錄。
 *
 * **這裡踩過的坑**（2026-09-07 回報）：官網對「上傳窗早就關掉」的期別**照樣回
 * `NOT_UPLOADED`**（`TaskParser` 對應到 [TaskState.OPEN]），而且照樣附上「本期任務可上傳時間
 * 剩 N 小時 N 分」那串倒數——同一批實測資料裡，一張 `2026/09/01 ~ 2026/09/06` 的卡片在
 * 9/7 仍然長這樣。App 這邊「能不能上傳」只看 `state`，於是過期未上傳的期別照樣畫出
 * 「上傳運動紀錄」按鈕：使用者要一路點進上傳頁、從相簿挑好照片、按下「確認上傳」，
 * 才會被伺服器擋下來。那是**事後攔截，不是預防**，而且白白讓使用者選了一次照片。
 *
 * 也就是說「能不能上傳」是**狀態與日曆的合取**，不是 `state` 單獨回答得了的問題。
 *
 * **為什麼這條規則放在 core、而不是留在 UI 裡的一個 `if`**：它是領域規則而非排版，
 * 而且很現實——寫在 Composable 裡的分支，單元測試搆不到。搬到這裡它才有 `UploadWindowTest` 守著。
 *
 * **刻意不加「這一期已經開始了嗎」這個條件**：官網對還沒開始的期別回的是 `NOT_STARTED`，
 * 正常路徑走不到這裡；真要出現「OPEN 卻還沒到起日」那也是官網自己說可以上傳，
 * 寧可讓它送出去由伺服器判，也不要在 App 端自作主張多擋一層。
 * 這個取捨與下面那條降級規則是同一個方向：**只擋官網明確說已經結束的，不擋看不懂的。**
 *
 * 日期解析不出來時 [TaskPeriod.hasEnded] 回 false，因此這裡會回 true——官網哪天改了日期格式，
 * 行為退化成「按鈕留著、由伺服器擋」，而不是反過來把一個還開著的窗誤擋掉。
 *
 * @param now 判斷基準時間。日期一律以台北時間解讀（見 [TaskPeriod.Companion.ACTIVITY_ZONE]）。
 */
public fun TaskPeriod.canUpload(now: Instant): Boolean =
    state == TaskState.OPEN && !hasEnded(now)
