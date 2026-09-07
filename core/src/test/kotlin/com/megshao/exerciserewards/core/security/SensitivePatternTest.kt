package com.megshao.exerciserewards.core.security

import com.megshao.exerciserewards.core.assertCpuBudget
import com.megshao.exerciserewards.core.security.Redact.SensitiveKind
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證 [Redact.sensitiveKinds]／[Redact.containsSensitive]／[Redact.scrub]：
 * 這是遙測出口的最後一道防線，漏判 = 個資外流，誤判 = 合法事件被靜靜丟掉。
 */
class SensitivePatternTest {

    // MARK: - 共用斷言

    private fun assertHits(text: String, kind: SensitiveKind) {
        val kinds = Redact.sensitiveKinds(text)
        assertTrue(kinds.contains(kind), "「$text」應命中 $kind，實際：$kinds")
        assertTrue(Redact.containsSensitive(text), "「$text」containsSensitive 應為 true")
    }

    private fun assertClean(text: String) {
        val kinds = Redact.sensitiveKinds(text)
        assertTrue(kinds.isEmpty(), "「$text」不該命中任何樣式，實際：$kinds")
        assertFalse(Redact.containsSensitive(text), "「$text」containsSensitive 應為 false")
        assertEquals(text, Redact.scrub(text), "「$text」scrub 後應原封不動")
    }

    // MARK: - A. 必須命中：身分證號

    @Test
    fun `taiwan id standard`() = assertHits("A123456789", SensitiveKind.TAIWAN_ID)

    @Test
    fun `taiwan id with a lowercase letter`() = assertHits("a123456789", SensitiveKind.TAIWAN_ID)

    @Test
    fun `taiwan id loose formats still hit`() {
        // 刻意比合法身分證更寬：檢查碼錯的、新式居留證、示範模式哨兵值都要擋
        assertHits("Z999999999", SensitiveKind.TAIWAN_ID)
        assertHits("A800000014", SensitiveKind.TAIWAN_ID)
        assertHits("A000000000", SensitiveKind.TAIWAN_ID)
    }

    @Test
    fun `taiwan id embedded in english text`() =
        assertHits("login failed for A123456789", SensitiveKind.TAIWAN_ID)

    @Test
    fun `taiwan id embedded in chinese text`() {
        assertHits("身分證字號A123456789查無資料", SensitiveKind.TAIWAN_ID)
        assertHits("id=A123456789&pwd=", SensitiveKind.TAIWAN_ID)
    }

    @Test
    fun `taiwan id does not also count as long digits`() {
        // 9 碼數字不足 10 碼，只該命中 taiwanID
        assertEquals(setOf(SensitiveKind.TAIWAN_ID), Redact.sensitiveKinds("A123456789"))
    }

    // MARK: - A. 必須命中：手機

    @Test
    fun `phone standard`() {
        assertHits("0912345678", SensitiveKind.PHONE)
        assertHits("0987654321", SensitiveKind.PHONE)
    }

    @Test
    fun `phone with dashes`() {
        assertHits("0912-345-678", SensitiveKind.PHONE)
        assertHits("0912-345678", SensitiveKind.PHONE)
    }

    @Test
    fun `phone with spaces`() {
        // 台灣常見的空白分隔寫法，一樣不可漏
        assertHits("0912 345 678", SensitiveKind.PHONE)
        assertHits("+886 912 345 678", SensitiveKind.PHONE)
    }

    @Test
    fun `phone in international format`() {
        assertHits("+886912345678", SensitiveKind.PHONE)
        assertHits("+886-912-345-678", SensitiveKind.PHONE)
        assertHits("886912345678", SensitiveKind.PHONE)
    }

    @Test
    fun `phone embedded in text`() {
        assertHits("sms sent to 0912345678 failed", SensitiveKind.PHONE)
        assertHits("手機 0912345678 已被註冊", SensitiveKind.PHONE)
        assertHits("phone=0912345678,retry=3", SensitiveKind.PHONE)
    }

    @Test
    fun `phone also hits the long digit sequence`() {
        // 10 碼純數字同時符合兩個樣式；兩個都回報有助於除錯
        assertEquals(
            setOf(SensitiveKind.PHONE, SensitiveKind.LONG_DIGIT_SEQUENCE),
            Redact.sensitiveKinds("0912345678"),
        )
    }

    // MARK: - A. 必須命中：Email

    @Test
    fun `email standard`() = assertHits("ming@mail.com", SensitiveKind.EMAIL)

    @Test
    fun `email case variants`() {
        assertHits("Ming@Mail.COM", SensitiveKind.EMAIL)
        assertHits("MING.CHEN@EXAMPLE.ORG", SensitiveKind.EMAIL)
    }

    @Test
    fun `email with a plus tag and subdomain`() {
        assertHits("ming.chen+tag@mail.example.co", SensitiveKind.EMAIL)
        assertHits("a_b-c%d@sub.domain.tw", SensitiveKind.EMAIL)
    }

    @Test
    fun `email embedded in text`() {
        assertHits("account ming@mail.com already exists", SensitiveKind.EMAIL)
        assertHits("帳號ming@mail.com已存在", SensitiveKind.EMAIL)
    }

    // MARK: - A. 必須命中：生日

    @Test
    fun `birth date iso`() {
        assertHits("1990-01-01", SensitiveKind.BIRTH_DATE)
        assertHits("2005-12-31", SensitiveKind.BIRTH_DATE)
    }

    @Test
    fun `birth date slash variants`() {
        assertHits("1990/01/01", SensitiveKind.BIRTH_DATE)
        assertHits("1990/1/1", SensitiveKind.BIRTH_DATE)
        assertHits("1990/1/31", SensitiveKind.BIRTH_DATE)
    }

    @Test
    fun `birth date dot separator`() = assertHits("1990.01.01", SensitiveKind.BIRTH_DATE)

    @Test
    fun `birth date century boundaries`() {
        assertHits("1899-12-31", SensitiveKind.BIRTH_DATE)
        assertHits("2024-01-01", SensitiveKind.BIRTH_DATE)
    }

    @Test
    fun `birth date embedded in text`() {
        assertHits("birthday 1990-01-01 mismatch", SensitiveKind.BIRTH_DATE)
        assertHits("生日1990/1/1不符", SensitiveKind.BIRTH_DATE)
    }

    // MARK: - A. 必須命中：UUID

    @Test
    fun `uuid lowercase`() = assertHits("550e8400-e29b-41d4-a716-446655440000", SensitiveKind.UUID)

    @Test
    fun `uuid uppercase and mixed`() {
        assertHits("550E8400-E29B-41D4-A716-446655440000", SensitiveKind.UUID)
        assertHits("550e8400-E29B-41d4-A716-446655440000", SensitiveKind.UUID)
    }

    @Test
    fun `uuid from the platform generator`() = assertHits(UUID.randomUUID().toString(), SensitiveKind.UUID)

    @Test
    fun `uuid embedded in text`() {
        assertHits("device 550e8400-e29b-41d4-a716-446655440000 registered", SensitiveKind.UUID)
        assertHits("券 550e8400-e29b-41d4-a716-446655440000 已核銷", SensitiveKind.UUID)
    }

    // MARK: - A. 必須命中：長數字串

    @Test
    fun `long digit sequence exactly ten`() = assertHits("1234567890", SensitiveKind.LONG_DIGIT_SEQUENCE)

    @Test
    fun `long digit sequence longer`() {
        assertHits("12345678901234", SensitiveKind.LONG_DIGIT_SEQUENCE)
        assertHits("000012345678", SensitiveKind.LONG_DIGIT_SEQUENCE) // 健保卡號樣式
        assertHits("1725580800000", SensitiveKind.LONG_DIGIT_SEQUENCE) // 毫秒 timestamp
    }

    @Test
    fun `long digit sequence embedded in text`() {
        assertHits("voucher 1234567890123 redeemed", SensitiveKind.LONG_DIGIT_SEQUENCE)
        assertHits("券碼1234567890已使用", SensitiveKind.LONG_DIGIT_SEQUENCE)
    }

    @Test
    fun `phone glued to more digits falls back to long digits`() {
        // 手機樣式因為後面還有數字而不命中，但長數字串必須接住
        val kinds = Redact.sensitiveKinds("09123456789012")
        assertTrue(kinds.contains(SensitiveKind.LONG_DIGIT_SEQUENCE))
        assertFalse(kinds.isEmpty())
    }

    // MARK: - A. 多個敏感值同時出現

    @Test
    fun `multiple kinds in one string`() {
        val kinds = Redact.sensitiveKinds("A123456789 0912345678 ming@mail.com 1990-01-01")
        assertTrue(
            kinds.containsAll(
                listOf(
                    SensitiveKind.TAIWAN_ID, SensitiveKind.PHONE,
                    SensitiveKind.EMAIL, SensitiveKind.BIRTH_DATE,
                ),
            ),
        )
    }

    @Test
    fun `all kinds in one string`() {
        val text = "id A123456789 tel 0912-345-678 mail ming@mail.com dob 1990/1/1 " +
            "dev 550e8400-e29b-41d4-a716-446655440000 card 000012345678"
        // OVERLONG 是「超過上限、沒掃」的短路結果，定義上不會與實際命中的樣式共存，
        // 所以這裡比對的是「所有可被掃描出來的種類」。
        val scannable = SensitiveKind.entries.toSet() - SensitiveKind.OVERLONG
        assertEquals(scannable, Redact.sensitiveKinds(text))
    }

    @Test
    fun `the same kind twice still reports once`() {
        assertEquals(
            setOf(SensitiveKind.PHONE, SensitiveKind.LONG_DIGIT_SEQUENCE),
            Redact.sensitiveKinds("0912345678 / 0987654321"),
        )
    }

    // MARK: - B. 不可誤判

    @Test
    fun `legitimate strings are not flagged`() {
        LEGITIMATE_STRINGS.forEach(::assertClean)
    }

    @Test
    fun `screen names are not flagged`() {
        listOf("home", "tasks", "health", "wallet", "profile", "redeem", "voucher", "upload", "onboarding")
            .forEach(::assertClean)
    }

    @Test
    fun `version strings are not flagged`() {
        assertClean("v1.0.0")
        assertClean("1.0.0")
        assertClean("12.18.0")
    }

    @Test
    fun `period and status values are not flagged`() {
        listOf("period_3", "NOT_STARTED", "NOT_UPLOADED", "UNDER_REVIEW", "REDEEMABLE", "REDEEMED")
            .forEach(::assertClean)
    }

    @Test
    fun `ui date range text is not flagged`() {
        // 沒有年份的區間文字不是生日
        assertClean("10/06 ~ 10/12")
        assertClean("剩 2 天 7 小時可上傳")
    }

    @Test
    fun `error categories are not flagged`() {
        listOf("invalid_credentials", "not_registered", "blocked_egress", "parsing").forEach(::assertClean)
    }

    @Test
    fun `stringified numbers are not flagged`() {
        assertClean("8000")
        assertClean("14")
        assertClean("121")
    }

    @Test
    fun `bundle id and event names are not flagged`() {
        assertClean("com.megshao.exerciserewards")
        assertClean("app_launched")
        assertClean("screen_view")
    }

    @Test
    fun `nine digits is below the long digit threshold`() {
        // 釘住門檻：9 碼不算長數字串（否則 8 碼步數／百分比等字串化參數都會中）
        assertClean("123456789")
    }

    @Test
    fun `realistic composite parameters are not flagged`() {
        assertClean("period_3 REDEEMABLE 121")
        assertClean("screen=home version=1.0.0 build=42")
        assertClean("upload failed: blocked_egress (attempt 2/3)")
    }

    @Test
    fun `the empty string is not flagged`() {
        assertEquals(emptySet(), Redact.sensitiveKinds(""))
        assertFalse(Redact.containsSensitive(""))
    }

    // MARK: - C. scrub 行為

    @Test
    fun `scrub of the empty string returns empty`() = assertEquals("", Redact.scrub(""))

    @Test
    fun `scrub replaces the whole phone with a marker`() =
        assertEquals("[已遮蔽:phone]", Redact.scrub("0912345678"))

    @Test
    fun `scrub is irreversible and keeps no head or tail`() {
        // 與 Redact.phone/idNo 不同：遙測用的 scrub 不可保留任何頭尾字元
        val phone = Redact.scrub("0912345678")
        assertFalse(phone.startsWith("0912"))
        assertFalse(phone.endsWith("678"))
        assertFalse(phone.any { it.isDigit() }, "scrub 後不該殘留任何數字：$phone")

        val id = Redact.scrub("A123456789")
        assertFalse(id.contains("A12"))
        assertFalse(id.contains("89"))
        assertFalse(id.any { it.isDigit() })

        val email = Redact.scrub("ming@mail.com")
        assertFalse(email.contains("ming"))
        assertFalse(email.contains("mail.com"))
        assertFalse(email.contains("@"))

        val dob = Redact.scrub("1990-01-01")
        assertFalse(dob.contains("1990"))
        assertFalse(dob.any { it.isDigit() })

        val uuid = Redact.scrub("550e8400-e29b-41d4-a716-446655440000")
        assertFalse(uuid.contains("550e8400"))
        assertFalse(uuid.contains("446655440000"))
    }

    @Test
    fun `scrub output is itself clean`() {
        val samples = listOf(
            "A123456789", "0912-345-678", "+886912345678", "ming@mail.com", "1990/1/1",
            "550e8400-e29b-41d4-a716-446655440000", "12345678901234",
            "login failed for A123456789 at 1990-01-01 via 0912345678",
        )
        for (sample in samples) {
            val out = Redact.scrub(sample)
            assertFalse(Redact.containsSensitive(out), "scrub 後仍命中：$out")
        }
    }

    @Test
    fun `scrub preserves the surrounding context`() {
        assertEquals(
            "login failed for [已遮蔽:taiwanID] (attempt 3)",
            Redact.scrub("login failed for A123456789 (attempt 3)"),
        )
    }

    @Test
    fun `scrub preserves chinese context`() {
        assertEquals(
            "使用者 [已遮蔽:taiwanID] 於 [已遮蔽:birthDate] 登入失敗",
            Redact.scrub("使用者 A123456789 於 1990/01/01 登入失敗"),
        )
    }

    @Test
    fun `scrub handles emoji`() {
        assertEquals("🎉 [已遮蔽:phone] 🎉 完成", Redact.scrub("🎉 0912345678 🎉 完成"))
        assertEquals("👨‍👩‍👧 [已遮蔽:email]", Redact.scrub("👨‍👩‍👧 ming@mail.com"))
    }

    @Test
    fun `scrub replaces every occurrence`() {
        val out = Redact.scrub("0912345678 和 0987654321 都失敗")
        assertEquals("[已遮蔽:phone] 和 [已遮蔽:phone] 都失敗", out)
        assertEquals(2, out.split("[已遮蔽:phone]").size - 1)
    }

    @Test
    fun `scrub handles multiple kinds in one string`() {
        assertEquals(
            "id=[已遮蔽:taiwanID] tel=[已遮蔽:phone] mail=[已遮蔽:email] dob=[已遮蔽:birthDate]",
            Redact.scrub("id=A123456789 tel=0912345678 mail=ming@mail.com dob=1990-01-01"),
        )
    }

    @Test
    fun `scrub leaves legitimate strings untouched`() {
        LEGITIMATE_STRINGS.forEach { assertEquals(it, Redact.scrub(it)) }
    }

    /**
     * 超長輸入必須「很快回來」而不是「算得出正確答案」。
     *
     * 這一條在 iOS 端原本會無限期卡住：`.email` 樣式的 `[A-Za-z0-9._%+\-]+@` 遇到一長串
     * 不含 `@` 的字元時會吃下整段再逐字元回溯（ReDoS）。修法是兩層——樣式改成
     * 有界量詞，以及在掃描前先擋掉超過 [Redact.MAX_SCAN_LENGTH] 的輸入。
     *
     * 量的是 CPU 時間不是牆上時間，理由見 CpuBudget.kt。
     */
    @Test
    fun `scrub of a very long string returns promptly`() {
        val padding = "x".repeat(200_000)
        val text = padding + " 0912345678 " + padding

        val out = assertCpuBudget(1.0, "Redact.scrub 超長輸入", rounds = 2) { Redact.scrub(text) }

        // 只保留掃描過的前段，其餘截斷——沒掃過的內容不能留在輸出裡。
        assertTrue(out.endsWith("…[已截斷:超過 ${Redact.MAX_SCAN_LENGTH} 字元]"))
        assertTrue(out.length < Redact.MAX_SCAN_LENGTH + 64)
    }

    /** 超長輸入在守門用途上一律當成「有敏感資料」——漏判是外洩，誤判只是丟事件。 */
    @Test
    fun `overlong input is treated as sensitive`() {
        val text = "x".repeat(Redact.MAX_SCAN_LENGTH + 1)
        assertEquals(setOf(SensitiveKind.OVERLONG), Redact.sensitiveKinds(text))
        assertTrue(Redact.containsSensitive(text))
    }

    /** 剛好在上限內的字串仍然要正常掃描，不可被誤判成 overlong。 */
    @Test
    fun `at the scan limit input is still scanned normally`() {
        val filler = "x".repeat(Redact.MAX_SCAN_LENGTH - 10)
        assertEquals(emptySet(), Redact.sensitiveKinds(filler))

        val withPhone = "x".repeat(Redact.MAX_SCAN_LENGTH - 11) + "0912345678"
        assertEquals(Redact.MAX_SCAN_LENGTH - 1, withPhone.length)
        // 10 碼手機同時命中 phone 與 longDigitSequence，兩者都是刻意的寬鬆樣式。
        assertEquals(
            setOf(SensitiveKind.PHONE, SensitiveKind.LONG_DIGIT_SEQUENCE),
            Redact.sensitiveKinds(withPhone),
        )
    }

    @Test
    fun `scrub of a long string with many sensitive values`() {
        // 控制在掃描上限內，驗證「一段合理長度的訊息裡每一筆都被遮到」。
        val line = "A123456789 0912345678 ming@mail.com 1990-01-01\n"
        // 遮蔽標記比被遮的原文長，所以輸出會膨脹。重複次數要讓**輸出**也留在上限內，
        // 否則下面對輸出做 containsSensitive 會得到 OVERLONG 而不是「乾淨」。
        val repeats = 50
        val text = line.repeat(repeats)
        assertTrue(text.length <= Redact.MAX_SCAN_LENGTH)

        val out = Redact.scrub(text)
        assertTrue(out.length <= Redact.MAX_SCAN_LENGTH, "輸出膨脹後仍須在上限內")
        assertFalse(Redact.containsSensitive(out))
        assertEquals(repeats, out.split("[已遮蔽:taiwanID]").size - 1)
    }

    @Test
    fun `scrub marker names match the kind raw value`() {
        SensitiveKind.entries.forEach { assertFalse(it.raw.isEmpty()) }
        assertEquals("[已遮蔽:${SensitiveKind.TAIWAN_ID.raw}]", Redact.scrub("A123456789"))
    }

    // MARK: - D. 既有遮罩函式回歸

    @Test
    fun `existing mask functions are unchanged`() {
        assertEquals("A12●●●●●89", Redact.idNo("A123456789"))
        assertEquals("0912●●●678", Redact.phone("0912345678"))
        assertEquals("m●●g@mail.com", Redact.email("ming@mail.com"))
        assertEquals("●●●", Redact.fully("abc"))
        assertEquals("AB●●●FG", Redact.middle("ABCDEFG", keepHead = 2, keepTail = 2))
    }

    @Test
    fun `mask and scrub serve different purposes`() {
        // UI 遮罩保留頭尾（可辨識），遙測 scrub 完全不可還原
        assertTrue(Redact.phone("0912345678").startsWith("0912"))
        assertFalse(Redact.scrub("0912345678").startsWith("0912"))
        // 遮罩後的字串（含 ●）也不該被偵測器誤判
        assertFalse(Redact.containsSensitive(Redact.idNo("A123456789")))
        assertFalse(Redact.containsSensitive(Redact.phone("0912345678")))
    }

    private companion object {
        /** App 裡真實會送進遙測的合法字串。任何一個被誤判都代表事件被靜靜丟掉。 */
        val LEGITIMATE_STRINGS = listOf(
            // 畫面名（ScreenName 封閉列舉）
            "home", "tasks", "health", "wallet", "profile", "redeem", "voucher", "upload", "onboarding",
            // 版本號
            "v1.0.0", "1.0.0", "12.18.0", "1.0.0 (42)",
            // 期數與狀態
            "period_3", "period_14", "NOT_STARTED", "NOT_UPLOADED", "UNDER_REVIEW", "REDEEMABLE", "REDEEMED",
            // UI 上真的存在的日期區間／倒數文字
            "10/06 ~ 10/12", "剩 2 天 7 小時可上傳", "12/30 ~ 01/05",
            // 錯誤分類
            "invalid_credentials", "not_registered", "blocked_egress", "parsing", "network", "timeout",
            // 數字型參數的字串化
            "8000", "14", "121", "0", "-1", "100", "999999999",
            // 識別字串
            "com.megshao.exerciserewards", "app_launched", "screen_view", "non_fatal_error",
            "health_auth_granted", "onboarding_completed", "telemetry_preference_changed",
            "true", "false", "Android 16", "Pixel 9 Pro",
        )
    }
}
