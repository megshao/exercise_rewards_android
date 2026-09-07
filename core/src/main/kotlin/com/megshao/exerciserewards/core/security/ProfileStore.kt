package com.megshao.exerciserewards.core.security

import com.megshao.exerciserewards.core.models.Profile

/**
 * 個資的落地介面。**實作一律在 `:app`**（Android Keystore + EncryptedSharedPreferences），
 * core 只握著這個介面，好讓 service 層可以在 JVM 上被測試。
 *
 * 對應 iOS 端的 `ProfileStoring` / `KeychainStore`。實作必須滿足：
 * - 金鑰由硬體支援的 Keystore 保管，明文絕不落盤；
 * - 不進雲端備份、不隨裝置轉移（見 `res/xml/data_extraction_rules.xml`）。
 */
public interface ProfileStoring {
    public fun save(profile: Profile)
    public fun load(): Profile?
    public fun clear()
}
