package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.LoginCredentials
import com.megshao.exerciserewards.core.models.LoginOutcome
import com.megshao.exerciserewards.core.networking.HTTPFormResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthServiceTest {

    private val credentials = LoginCredentials(
        idNo = "A123456789",
        birthDate = "1990-01-01",
        phone = "0912345678",
    )

    // MARK: - success

    @Test
    fun `login returns success when login redirects to member tasks`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/access"] = csrfHtml("access-csrf")
            htmlByPath["/login"] = csrfHtml("login-csrf")
            formResultByPath["/access"] = HTTPFormResult(302, "/login", "")
            formResultByPath["/login"] = HTTPFormResult(302, "/member/tasks", "")
        }
        val sut = AuthService(http)

        val outcome = sut.login(credentials)

        assertEquals(LoginOutcome.SUCCESS, outcome)
        assertEquals(listOf("/access", "/login"), http.getPaths)
        assertEquals(listOf("/access", "/login"), http.postPaths)

        val accessFields = assertNotNull(http.postFields["/access"])
        assertTrue(accessFields.contains("_csrf" to "access-csrf"))
        assertTrue(accessFields.contains("idNo" to credentials.idNo))

        val loginFields = assertNotNull(http.postFields["/login"])
        assertTrue(loginFields.contains("_csrf" to "login-csrf"))
        assertTrue(loginFields.contains("birthDate" to credentials.birthDate))
        assertTrue(loginFields.contains("phone" to credentials.phone))
    }

    // MARK: - notRegistered

    @Test
    fun `login returns not registered when access redirects to register`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/access"] = csrfHtml("access-csrf")
            formResultByPath["/access"] = HTTPFormResult(302, "/register", "")
        }
        val sut = AuthService(http)

        assertEquals(LoginOutcome.NOT_REGISTERED, sut.login(credentials))
        // 未註冊時不應該再打 /login
        assertEquals(listOf("/access"), http.getPaths)
        assertEquals(listOf("/access"), http.postPaths)
    }

    // MARK: - invalidCredentials

    @Test
    fun `login returns invalid credentials when login redirects back to the login page`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/access"] = csrfHtml("access-csrf")
            htmlByPath["/login"] = csrfHtml("login-csrf")
            formResultByPath["/access"] = HTTPFormResult(302, "/login", "")
            formResultByPath["/login"] = HTTPFormResult(302, "/login", "")
        }
        val sut = AuthService(http)

        assertEquals(LoginOutcome.INVALID_CREDENTIALS, sut.login(credentials))
        assertEquals(listOf("/access", "/login"), http.getPaths)
        assertEquals(listOf("/access", "/login"), http.postPaths)
    }

    @Test
    fun `login returns invalid credentials when the login page stays with 200`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/access"] = csrfHtml("access-csrf")
            htmlByPath["/login"] = csrfHtml("login-csrf")
            formResultByPath["/access"] = HTTPFormResult(302, "/login", "")
            formResultByPath["/login"] = HTTPFormResult(200, null, "<html>login page</html>")
        }
        val sut = AuthService(http)

        assertEquals(LoginOutcome.INVALID_CREDENTIALS, sut.login(credentials))
    }

    // MARK: - error scenarios

    @Test
    fun `login throws when the access step returns an unexpected status`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/access"] = csrfHtml("access-csrf")
            formResultByPath["/access"] = HTTPFormResult(500, null, "")
        }
        val sut = AuthService(http)

        val error = assertFailsWith<AppError.UnexpectedResponse> { sut.login(credentials) }
        assertEquals(500, error.statusCode)
    }

    @Test
    fun `login throws csrf not found when the access html has no csrf`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/access"] = "<html>no csrf here</html>"
        }
        val sut = AuthService(http)

        assertFailsWith<AppError.CsrfNotFound> { sut.login(credentials) }
    }

    /** 每次登入都先清 session，否則過期的 JSESSIONID 會讓新的 `_csrf` 綁在死掉的 session 上。 */
    @Test
    fun `login resets the session before the handshake`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/access"] = csrfHtml("access-csrf")
            formResultByPath["/access"] = HTTPFormResult(302, "/register", "")
        }

        AuthService(http).login(credentials)

        assertEquals(1, http.resetSessionCallCount)
    }

    // MARK: - logout

    @Test
    fun `logout posts the csrf and resets the session`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/member/tasks"] = csrfHtml("tasks-csrf")
            formResultByPath["/logout"] = HTTPFormResult(302, "/", "")
        }
        val sut = AuthService(http)

        sut.logout()

        assertEquals(listOf("/member/tasks"), http.getPaths)
        assertEquals(listOf("/logout"), http.postPaths)
        assertTrue(assertNotNull(http.postFields["/logout"]).contains("_csrf" to "tasks-csrf"))
        assertEquals(1, http.resetSessionCallCount)
    }

    @Test
    fun `logout falls back to the login page when the tasks page is unavailable`() = runTest {
        val http = FakeHttpClient().apply {
            htmlByPath["/login"] = csrfHtml("login-csrf")
            formResultByPath["/logout"] = HTTPFormResult(302, "/", "")
        }
        val sut = AuthService(http)

        sut.logout()

        assertEquals(listOf("/member/tasks", "/login"), http.getPaths)
        assertTrue(assertNotNull(http.postFields["/logout"]).contains("_csrf" to "login-csrf"))
        assertEquals(1, http.resetSessionCallCount)
    }
}
