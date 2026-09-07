package com.megshao.exerciserewards.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 驗證個資遮罩：確保任何進入 log／錯誤訊息前的敏感值都被遮蔽。 */
class RedactTest {

    @Test
    fun `idNo keeps head and tail masks middle`() {
        assertEquals("A12●●●●●89", Redact.idNo("A123456789"))
    }

    @Test
    fun `phone masks middle`() {
        // 09 開頭門號，保留頭 4 尾 3
        assertEquals("0912●●●678", Redact.phone("0912345678"))
    }

    @Test
    fun `email masks user keeps domain`() {
        assertEquals("m●●g@mail.com", Redact.email("ming@mail.com"))
    }

    @Test
    fun `short string is fully masked`() {
        // 長度不足以保留頭尾時，整串遮罩，不可洩漏原文
        val out = Redact.middle("AB", keepHead = 3, keepTail = 2)
        assertFalse(out.contains("A"))
        assertFalse(out.contains("B"))
        assertEquals("●●", out)
    }

    @Test
    fun `fully is never empty`() {
        assertEquals("●", Redact.fully(""))
    }
}
