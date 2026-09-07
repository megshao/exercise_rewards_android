package com.megshao.exerciserewards.data

import android.content.Context
import com.megshao.exerciserewards.core.networking.ClearableCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 跨 App 重啟續用的 cookie jar，整團以 AES-256-GCM 加密後落地（見 [SecureBlobStore]）。
 *
 * **為什麼要持久化**：官方站的登入 session 就是 cookie（JSESSIONID／LBSCookie）。
 * 不持久化的話每次冷啟動都要使用者重新輸入三碼，而那正是這個 App 想解決的問題。
 *
 * **為什麼要加密**：cookie 在這裡等同帳號憑證，明文躺在 App 私有目錄，
 * 在 root 過的裝置或備份萃取下就是可以直接拿去用的 session。
 * 保護層級與個資本身一致，不因為它「只是 cookie」而降級。
 *
 * 每筆存成「來源網址 + 空白 + Set-Cookie 字串」：`Cookie.parse` 需要一個 URL 才能還原
 * host-only 與 path 的語意，只存 `toString()` 的話 host-only 那半邊會掉。
 */
public class EncryptedCookieJar(
    context: Context,
) : ClearableCookieJar {

    private val store = SecureBlobStore(context, FILE_NAME)

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val merged = load().associateBy(::key).toMutableMap()
        for (cookie in cookies) {
            merged[key(cookie)] = cookie
        }
        persist(merged.values)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val stored = load()
        val alive = stored.filter { it.expiresAt > now }
        // 順手把過期的清掉，別讓死掉的 session 留著誤導下一次請求。
        if (alive.size != stored.size) persist(alive)
        return alive.filter { it.matches(url) }
    }

    /** 登出。**必須真的清空**，這是「登出」在這個 App 唯一的意思。 */
    @Synchronized
    override fun clear() {
        store.clear()
    }

    private fun load(): List<Cookie> {
        val blob = store.read() ?: return emptyList()
        return blob.lineSequence().mapNotNull(::parseLine).toList()
    }

    private fun persist(cookies: Collection<Cookie>) {
        store.write(cookies.joinToString("\n", transform = ::formatLine))
    }

    private fun formatLine(cookie: Cookie): String =
        "https://${cookie.domain}${cookie.path}$SEPARATOR$cookie"

    private fun parseLine(line: String): Cookie? {
        val separator = line.indexOf(SEPARATOR)
        if (separator < 0) return null
        val url = line.substring(0, separator).toHttpUrlOrNull() ?: return null
        return Cookie.parse(url, line.substring(separator + 1))
    }

    private fun key(cookie: Cookie): String = "${cookie.domain}|${cookie.path}|${cookie.name}"

    private companion object {
        const val FILE_NAME = "session.enc"
        const val SEPARATOR = " "
    }
}
