package com.megshao.exerciserewards.data

import android.content.Context

/**
 * 非敏感的 App 偏好：導覽進度、免責聲明同意版本、遙測開關、示範模式。
 *
 * **刻意用普通的 SharedPreferences**：這裡一個欄位都不是個資，用加密儲存只會讓
 * 「哪些東西真的需要加密」這件事變模糊。個資與 session 走
 * [KeystoreProfileStore]／[EncryptedCookieJar]。
 */
public class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * 是否看過歡迎頁。**與免責聲明的同意分開存**：同意紀錄有版本號（改條款要重新同意），
     * 而歡迎頁看過就算看過，不該因為條款改版又跳一次。
     */
    public var hasSeenWelcome: Boolean
        get() = prefs.getBoolean(KEY_WELCOME, false)
        set(value) = prefs.edit().putBoolean(KEY_WELCOME, value).apply()

    /**
     * 已同意的免責聲明版本；0 代表從未同意。
     *
     * **用版本號而非布林**，是為了日後修改聲明內容時能讓舊使用者重新同意
     * （把 [DisclaimerConsent.CURRENT_VERSION] 加一即可）。
     *
     * **Firebase 的初始化綁在這個數字上**（見 `ExerciseRewardsApplication`）：
     * 它小於當前版本時，App 不會有任何一個 byte 送給 Google。
     */
    public var disclaimerAgreedVersion: Int
        get() = prefs.getInt(KEY_DISCLAIMER_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_DISCLAIMER_VERSION, value).apply()

    /** 同意當下的時間戳（epoch millis）。純粹本機除錯與顯示用。 */
    public var disclaimerAgreedAt: Long
        get() = prefs.getLong(KEY_DISCLAIMER_AT, 0)
        set(value) = prefs.edit().putLong(KEY_DISCLAIMER_AT, value).apply()

    public var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /**
     * 遙測開關。**同意之後預設開啟**——這不是 opt-in，是
     * 「先告知 → 主動同意 → 預設開啟 → 隨時可關」（措辭與 README／隱私權政策一致）。
     */
    public var isTelemetryEnabled: Boolean
        get() = prefs.getBoolean(KEY_TELEMETRY, TELEMETRY_DEFAULT)
        set(value) = prefs.edit().putBoolean(KEY_TELEMETRY, value).apply()

    /** 示範／送審模式：不打真實網路、不送任何遙測。 */
    public var isDemoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO, value).apply()

    /** 「立即清除本機資料」：回到初次設定的狀態。 */
    public fun resetToFirstRun() {
        prefs.edit()
            .remove(KEY_WELCOME)
            .remove(KEY_DISCLAIMER_VERSION)
            .remove(KEY_DISCLAIMER_AT)
            .remove(KEY_ONBOARDING)
            .remove(KEY_TELEMETRY)
            .apply()
    }

    public companion object {
        public const val TELEMETRY_DEFAULT: Boolean = true

        private const val FILE_NAME = "app_prefs"
        private const val KEY_WELCOME = "hasSeenWelcome"
        private const val KEY_DISCLAIMER_VERSION = "disclaimerAgreedVersion"
        private const val KEY_DISCLAIMER_AT = "disclaimerAgreedAt"
        private const val KEY_ONBOARDING = "hasCompletedOnboarding"
        private const val KEY_TELEMETRY = "telemetryEnabled"
        private const val KEY_DEMO = "demoModeEnabled"
    }
}

/**
 * 免責聲明的同意紀錄。
 *
 * **這份紀錄能證明什麼、不能證明什麼**（在依賴它之前請先讀完）：
 *
 * 能證明的：這支裝置上的 App 知道使用者同意過哪一個版本、什麼時候同意的。
 * 用途是流程控制——聲明改版時讓使用者重新同意，以及在「立即清除本機資料」時一併重置。
 *
 * **不能**當作法律意義上的舉證：紀錄只存在使用者自己的裝置，開發者拿不到；
 * root 過的裝置上使用者也能自行竄改。要做到「開發者手上有一份可舉證的同意紀錄」，
 * 必須有伺服器接收、而且要能對應到特定使用者——那等於推翻本 App
 * 「沒有任何自建後端、開發者收不到你的任何資料」的核心設計。這裡刻意選擇保住後者。
 */
public object DisclaimerConsent {
    /**
     * 同意的版本號。日後若修改聲明內容且變動涉及使用者權益，把這個數字加一，
     * 已同意過舊版的使用者就會再看到一次。
     */
    public const val CURRENT_VERSION: Int = 1
}
