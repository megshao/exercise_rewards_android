package com.megshao.exerciserewards.core.networking

import com.megshao.exerciserewards.core.loadFixture
import com.megshao.exerciserewards.core.models.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CsrfParserTest {

    @Test
    fun `extract returns token from access fixture`() {
        val html = loadFixture("access")

        val token = CsrfParser.extract(html)

        assertEquals("test-csrf-token-access", token)
    }

    @Test
    fun `extract returns token from login fixture`() {
        val html = loadFixture("login")

        val token = CsrfParser.extract(html)

        assertEquals("test-csrf-token-member", token)
    }

    @Test
    fun `extract throws when html is empty`() {
        assertFailsWith<AppError.CsrfNotFound> { CsrfParser.extract("") }
    }

    @Test
    fun `extract throws when value attribute is missing`() {
        assertFailsWith<AppError.CsrfNotFound> {
            CsrfParser.extract("""<input type="hidden" name="_csrf"/>""")
        }
    }

    @Test
    fun `extract works regardless of attribute order`() {
        // value 屬性在 name 之前
        val html = """<input type="hidden" value="tokenXYZ" name="_csrf"/>"""

        assertEquals("tokenXYZ", CsrfParser.extract(html))
    }
}
