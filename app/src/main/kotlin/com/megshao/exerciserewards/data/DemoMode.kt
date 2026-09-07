package com.megshao.exerciserewards.data

import com.megshao.exerciserewards.core.models.LoginCredentials
import com.megshao.exerciserewards.core.models.Profile

/**
 * 送審用「示範模式」（Demo Mode）。
 *
 * **為什麼需要**：本 App 的登入需要真實身分證號、出生日期、手機號碼，且帳號屬於官方網站
 * `500.gov.tw`（App 不做註冊）。商店審查員拿不到這樣一組真實憑證，沒有示範模式就完全
 * 無法走完主要流程。
 *
 * **刻意不做成隱藏手勢**：示範模式的入口就是一般使用者也看得到的登入表單——只要輸入下列
 * 這組哨兵值即可進入。這組值與商店後台的示範帳號欄位同步，是**完整揭露的功能**。
 *
 * **安全性**：`A000000000` 不是合法的中華民國身分證號（檢查碼不符），真實使用者不可能誤觸。
 * 進入示範模式後整個 App 改用 Mock 服務，**不會發出任何網路請求**，個資也只留在
 * 記憶體（[InMemoryProfileStore]），絕不寫入加密儲存。
 */
public object DemoMode {
    /** 示範帳號三碼。修改這裡就要同步更新商店後台的示範帳號欄位。 */
    public const val ID_NO: String = "A000000000"
    public const val BIRTH_DATE: String = "1990-01-01"
    public const val PHONE: String = "0900000000"

    /** 示範模式下預先填好的個資（只存在記憶體）。 */
    public val profile: Profile
        get() = Profile(idNo = ID_NO, birthDate = BIRTH_DATE, phone = PHONE)

    public fun matches(credentials: LoginCredentials): Boolean =
        matches(credentials.idNo, credentials.birthDate, credentials.phone)

    public fun matches(profile: Profile): Boolean =
        matches(profile.idNo, profile.birthDate, profile.phone)

    /** 身分證號大小寫皆可、前後空白忽略——審查員手動輸入時容錯。 */
    public fun matches(idNo: String, birthDate: String, phone: String): Boolean =
        idNo.trim().equals(ID_NO, ignoreCase = true) &&
            birthDate.trim() == BIRTH_DATE &&
            phone.trim() == PHONE
}
