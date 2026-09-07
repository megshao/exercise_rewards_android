package com.megshao.exerciserewards.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 本機加密的唯一實作：金鑰放 Android Keystore，內容用 AES-256-GCM。
 *
 * **為什麼不用 `androidx.security:security-crypto`（EncryptedSharedPreferences）**：
 * Google 已經把那整個函式庫標記為 deprecated。這個 App 的整個賣點就是資料安全，
 * 開場就押一個停止維護的加密相依不合理。這裡只用平台自己的 Keystore API——
 * 沒有第三方加密函式庫，任何人都讀得完這幾十行並自己驗證。
 *
 * 設計：
 * - 金鑰由 Keystore 產生與保管，**永遠不會離開 Keystore**（有 StrongBox 的機型在安全晶片內）。
 *   App 這一側只拿得到一個 handle，即使程式被反編譯也複製不走金鑰。
 * - `setRandomizedEncryptionRequired(true)`：強制每次加密都用系統產生的隨機 IV。
 *   GCM 最致命的誤用就是 IV 重複（會直接洩漏明文之間的關係），這個旗標讓「重複」
 *   在 API 層就做不到——不是靠呼叫端自律。
 * - IV 與密文一起存（`iv || ciphertext`，Base64）。IV 不是秘密，但必須原封不動地拿回來。
 * - 不要求使用者認證（`setUserAuthenticationRequired` 預設 false）：這份資料要能在
 *   冷啟動自動登入時讀到，加上一道生物辨識會讓 App 完全無法自動化——而本專案刻意
 *   完全不含生物辨識（與 iOS 端一致）。保護邊界是「裝置鎖與 App 沙盒」，
 *   不是「每次讀取都問人」。
 */
internal object KeystoreCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "exercise_rewards_data_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * 解密。**任何失敗都回 null 而不是丟例外**：金鑰被清掉（使用者重設螢幕鎖、還原備份）、
     * 資料損毀、格式改版——這些情況下正確的行為是「當作沒有存過」，讓使用者重新輸入一次，
     * 而不是讓 App 開不起來。
     */
    fun decrypt(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= IV_LENGTH) return null
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
