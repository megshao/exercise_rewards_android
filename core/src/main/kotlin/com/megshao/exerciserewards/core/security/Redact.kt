package com.megshao.exerciserewards.core.security

/**
 * 敏感字串遮罩工具。所有可能寫入 log／錯誤訊息的個資都要先過這裡。
 */
public object Redact {

    /** 保留頭尾各 keep 碼，中間以 ● 遮罩。 */
    public fun middle(s: String, keepHead: Int = 3, keepTail: Int = 2): String {
        val chars = s.toList()
        if (chars.size <= keepHead + keepTail) {
            return "●".repeat(maxOf(chars.size, 1))
        }
        val head = chars.take(keepHead).joinToString("")
        val tail = chars.takeLast(keepTail).joinToString("")
        return head + "●".repeat(chars.size - keepHead - keepTail) + tail
    }

    public fun idNo(s: String): String = middle(s, keepHead = 3, keepTail = 2)

    public fun phone(s: String): String = middle(s, keepHead = 4, keepTail = 3)

    public fun email(s: String): String {
        val at = s.indexOf('@')
        if (at < 0) return middle(s)
        val user = s.substring(0, at)
        val domain = s.substring(at)
        return middle(user, keepHead = 1, keepTail = 1) + domain
    }

    public fun fully(s: String): String = "●".repeat(maxOf(s.length, 1))

    // MARK: - 敏感樣式偵測

    /**
     * 「這串字看起來像個資嗎？」的偵測器。
     *
     * **為什麼需要**：上面那組遮罩函式是「呼叫端已經知道這是個資」才會用到的工具，靠的是自律。
     * 但遙測（Analytics/Crashlytics）與 log 的參數常常是層層轉手來的字串，呼叫端未必知道
     * 裡面夾了什麼。這組偵測器讓出口端（`Telemetry`、[SecureLog]）可以在送出前自己檢查一次，
     * **把「不小心夾帶個資」從自律問題變成機制問題**。
     *
     * **刻意寧可誤判**：樣式故意放寬（例如任何 10 碼以上連續數字都算可疑）。遙測參數本來就
     * 只該是有限集合的短字串與數字，誤判的代價是「少一個事件」，漏判的代價是「個資外流」。
     */
    public enum class SensitiveKind(public val raw: String) {
        /** 中華民國身分證號／居留證號（1 英文字母 + 9 碼數字） */
        TAIWAN_ID("taiwanID"),

        /** 台灣手機門號（09xxxxxxxx、+8869xxxxxxxx） */
        PHONE("phone"),

        /** 電子郵件位址 */
        EMAIL("email"),

        /** 日期（生日；YYYY-MM-DD 或 YYYY/M/D） */
        BIRTH_DATE("birthDate"),

        /** UUID（裝置／安裝識別碼、券的內部 id） */
        UUID("uuid"),

        /** 10 碼以上連續數字（券碼、健保卡號、序號、身分證去掉字母後的樣子） */
        LONG_DIGIT_SEQUENCE("longDigitSequence"),

        /**
         * 超過 [MAX_SCAN_LENGTH] 而未實際掃描的輸入。
         * 不代表「確定有個資」，而是「無法確認、依 fail-safe 一律當成有」。
         */
        OVERLONG("overlong"),
    }

    /**
     * 已編譯的樣式表。順序即 [scrub] 的套用順序。
     *
     * 每一條的長度上限都不是效能潔癖，而是 ReDoS 防線——見各條註解。
     */
    private val patterns: List<Pair<SensitiveKind, Regex>> = listOf(
        // 刻意比「合法身分證號」更寬：任何「1 個英文字母 + 9 碼數字」都算命中。
        // 檢查碼錯的、居留證號、以及示範模式的哨兵值 A000000000 都會被擋下來。
        SensitiveKind.TAIWAN_ID to Regex("""(?<![A-Za-z0-9])[A-Za-z]\d{9}(?![A-Za-z0-9])"""),
        SensitiveKind.PHONE to Regex("""(?<!\d)(?:\+?886[-\s]?|0)9\d{2}[-\s]?\d{3}[-\s]?\d{3}(?!\d)"""),
        // 兩段都用 RFC 5321 的實際長度上限收斂（local part ≤64、domain ≤255），
        // **不是**為了嚴格驗證 email，而是避免無界量詞造成回溯爆炸：
        // 開放的 `+` 遇到一長串不含 `@` 的字元時，會吃下整段再逐字元回溯，
        // 對 20 萬字元的輸入就是 O(n²)，實測會讓整個呼叫卡死（ReDoS）。
        SensitiveKind.EMAIL to Regex("""[A-Za-z0-9._%+\-]{1,64}@[A-Za-z0-9.\-]{1,255}\.[A-Za-z]{2,24}"""),
        SensitiveKind.BIRTH_DATE to Regex("""(?<!\d)(?:18|19|20)\d{2}[-/.]\d{1,2}[-/.]\d{1,2}(?!\d)"""),
        SensitiveKind.UUID to Regex(
            """(?<![0-9a-f])[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![0-9a-f])""",
            RegexOption.IGNORE_CASE,
        ),
        SensitiveKind.LONG_DIGIT_SEQUENCE to Regex("""(?<!\d)\d{10,}(?!\d)"""),
    )

    /**
     * 掃描長度上限。超過就不掃了——理由不是效能潔癖，而是這些樣式含有量詞，
     * 掃描成本隨長度成長；而合法輸入根本不會這麼長（遙測參數上限 100 字元，
     * 錯誤訊息也不該是幾百 KB）。真的遇到超長字串，代表呼叫端出了別的問題。
     */
    public const val MAX_SCAN_LENGTH: Int = 4096

    /**
     * 回傳這段字串命中的所有敏感樣式（沒命中就是空集合）。
     *
     * **超長輸入一律保守地視為命中**：這個函式是遙測出口的守門員，
     * 「不確定」必須倒向「擋下來」——漏判是個資外洩，誤判只是丟掉一個事件。
     */
    public fun sensitiveKinds(text: String): Set<SensitiveKind> {
        if (text.isEmpty()) return emptySet()
        if (text.length > MAX_SCAN_LENGTH) return setOf(SensitiveKind.OVERLONG)
        return patterns.filter { (_, regex) -> regex.containsMatchIn(text) }
            .map { (kind, _) -> kind }
            .toSet()
    }

    /** 便利判斷：這段字串裡有沒有看起來像個資的東西。 */
    public fun containsSensitive(text: String): Boolean = sensitiveKinds(text).isNotEmpty()

    /**
     * 把字串裡命中樣式的片段就地遮成 `[已遮蔽:種類]`，其餘原文保留。
     * 用於錯誤訊息這種「必須保留上下文才有除錯價值」的場合。
     */
    public fun scrub(text: String): String {
        if (text.isEmpty()) return text
        // 超長輸入先截斷再遮蔽。截掉的部分沒被掃過，所以不能留著——
        // 保留前段是為了維持除錯價值，丟掉後段是因為無法保證它不含個資。
        if (text.length > MAX_SCAN_LENGTH) {
            return scrub(text.take(MAX_SCAN_LENGTH)) + "…[已截斷:超過 $MAX_SCAN_LENGTH 字元]"
        }
        return patterns.fold(text) { acc, (kind, regex) ->
            regex.replace(acc) { "[已遮蔽:${kind.raw}]" }
        }
    }
}
