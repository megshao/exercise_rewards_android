package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.LoginCredentials
import com.megshao.exerciserewards.core.models.LoginOutcome
import com.megshao.exerciserewards.core.networking.CsrfParser
import com.megshao.exerciserewards.core.networking.HTTPClienting
import com.megshao.exerciserewards.core.security.LogCategory
import com.megshao.exerciserewards.core.security.SecureLog

/**
 * 登入／登出服務。實作官方站無 OTP 的兩段式登入序列：
 * access（身分證核對）→ login（三碼核對）。
 */
public class AuthService(
    private val http: HTTPClienting,
) : AuthServicing {

    private val log = SecureLog(LogCategory.AUTH)

    override suspend fun login(credentials: LoginCredentials): LoginOutcome {
        // 明確重新登入前先清掉舊 cookie/session：持久化 cookie 若殘留過期的 JSESSIONID／
        // LBSCookie，會讓 GET /access 拿到的 _csrf 綁在死掉的舊 session 上，POST 被打回，
        // 造成登入一直失敗。清乾淨再走 access→login 握手，確保每次登入都是全新 session。
        http.resetSession()

        val accessCsrf = fetchCsrf("/access")

        val accessResult = http.postForm(
            path = "/access",
            fields = listOf(
                "_csrf" to accessCsrf,
                "idNo" to credentials.idNo,
            ),
        )

        val accessLocation = accessResult.location
        if (accessResult.statusCode != 302 || accessLocation == null) {
            log.error("access step returned unexpected status")
            throw AppError.UnexpectedResponse(accessResult.statusCode)
        }

        if (accessLocation.contains("/register")) {
            log.info("access redirected to register: idNo not registered")
            return LoginOutcome.NOT_REGISTERED
        }

        if (!accessLocation.contains("/login")) {
            log.error("access step redirected to unexpected location")
            throw AppError.UnexpectedResponse(accessResult.statusCode)
        }

        val loginCsrf = fetchCsrf("/login")

        val loginResult = http.postForm(
            path = "/login",
            fields = listOf(
                "_csrf" to loginCsrf,
                "idNo" to credentials.idNo,
                "birthDate" to credentials.birthDate,
                "phone" to credentials.phone,
            ),
        )

        val loginLocation = loginResult.location
        if (loginResult.statusCode == 302 && loginLocation != null) {
            if (loginLocation.contains("/member/tasks")) {
                log.info("login succeeded")
                return LoginOutcome.SUCCESS
            }
            if (loginLocation.contains("/login")) {
                log.info("login redirected back to login page: invalid credentials")
                return LoginOutcome.INVALID_CREDENTIALS
            }
            log.error("login step redirected to unexpected location")
            throw AppError.UnexpectedResponse(loginResult.statusCode)
        }

        if (loginResult.statusCode == 200) {
            // 停在登入頁（表單驗證失敗），三碼不符
            log.info("login stayed on login page: invalid credentials")
            return LoginOutcome.INVALID_CREDENTIALS
        }

        log.error("login step returned unexpected status")
        throw AppError.UnexpectedResponse(loginResult.statusCode)
    }

    override suspend fun logout() {
        val html = try {
            http.getHtml("/member/tasks")
        } catch (_: AppError) {
            http.getHtml("/login")
        }
        val csrf = CsrfParser.extract(html)
        http.postForm(path = "/logout", fields = listOf("_csrf" to csrf))
        http.resetSession()
        log.info("logout completed")
    }

    private suspend fun fetchCsrf(path: String): String = CsrfParser.extract(http.getHtml(path))
}
