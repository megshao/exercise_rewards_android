package com.megshao.exerciserewards.core.networking

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 可被清空的 cookie jar。**cookie 就是登入 session 本身**，所以這個介面存在的意義是
 * 讓「登出」有一條明確、可測試的清除路徑（對應 iOS 的 `URLSession.reset`）。
 *
 * core 只提供 [InMemoryCookieJar]；跨 App 重啟續用的持久化版本在 `:app`
 * （`EncryptedCookieJar`，落在 EncryptedSharedPreferences，金鑰由 Android Keystore 保管）。
 */
public interface ClearableCookieJar : CookieJar {
    public fun clear()
}

/**
 * 純記憶體 cookie jar。行程結束就沒了——測試與「一次性 session」情境用。
 */
public class InMemoryCookieJar : ClearableCookieJar {
    private val store = LinkedHashMap<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            store[key(cookie)] = cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val expired = store.filterValues { it.expiresAt <= now }.keys
        expired.forEach(store::remove)
        return store.values.filter { it.matches(url) }
    }

    @Synchronized
    override fun clear() {
        store.clear()
    }

    private fun key(cookie: Cookie): String = "${cookie.domain}|${cookie.path}|${cookie.name}"
}
