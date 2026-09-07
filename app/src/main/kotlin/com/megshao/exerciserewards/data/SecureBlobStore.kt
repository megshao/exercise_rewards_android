package com.megshao.exerciserewards.data

import android.content.Context

/**
 * 一個檔案存一團加密字串。個資與 session cookie 都走這裡（各自一個檔）。
 *
 * **為什麼是「一整團」而不是逐欄位加密**：欄位名本身也會洩漏資訊
 * （cookie 的 `domain|path|name` 就是一組可辨識的指紋）。整團加密之後，
 * 磁碟上只看得到一個 Base64 字串，連「裡面有幾筆」都看不出來。
 *
 * 落地位置是 App 私有目錄的普通 SharedPreferences，內容則永遠是密文
 * （見 [KeystoreCrypto]）。加上 manifest 關掉 `allowBackup`、
 * `data_extraction_rules.xml` 排除全部網域，等同 iOS Keychain 的
 * `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`：不進雲端、不隨裝置轉移。
 */
internal class SecureBlobStore(context: Context, fileName: String) {

    private val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    fun read(): String? = prefs.getString(KEY_BLOB, null)?.let(KeystoreCrypto::decrypt)

    fun write(value: String) {
        prefs.edit().putString(KEY_BLOB, KeystoreCrypto.encrypt(value)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_BLOB = "blob"
    }
}
