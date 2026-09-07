package com.megshao.exerciserewards.core.services

/**
 * 把 HTML 屬性／文字節點裡的字元參照還原成原字元。
 *
 * **為什麼需要**：官網的商品頁把分類名放在屬性裡，例如
 * `data-category="Let&#x27;s Café"`。直接拿屬性值當畫面文字，使用者會看到
 * 「Let&#x27;s Café」這串原始碼。
 *
 * **刻意只做這一件事**：這裡不是 HTML 剖析器，也不打算變成一個。只還原五個具名實體與
 * 數值字元參照——那是官方站（Thymeleaf 預設跳脫）唯一會產生的東西。
 * 遇到不認得的 `&...;` 一律**原樣保留**，不猜、不丟例外。
 *
 * ReDoS 防線：整支函式不用正則，只做一次線性掃描，成本與輸入長度成正比。
 */
public object HtmlEntities {
    /** 官方站會產生的具名實體。 */
    private val named: Map<String, String> = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        // `&nbsp;` 出現在少數排版用的空白，還原成一般空白比留著 U+00A0 好處理。
        "nbsp" to " ",
    )

    /**
     * 數值字元參照的位數上限。`&#x1F600;` 是 6 碼，取 8 已經很寬鬆；
     * 設上限是為了讓「一個 `&#` 後面接超長數字」不會拖著整段掃描。
     */
    private const val MAX_NUMERIC_DIGITS = 8

    public fun decode(input: String): String {
        if (!input.contains('&')) return input

        val output = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val character = input[index]
            if (character != '&') {
                output.append(character)
                index += 1
                continue
            }

            // 找這個 `&` 後面最近的 `;`，且中間不能超過具名實體的最長長度。
            val afterAmpersand = index + 1
            val limit = minOf(afterAmpersand + MAX_NUMERIC_DIGITS + 3, input.length)
            val semicolon = input.indexOf(';', afterAmpersand).takeIf { it in afterAmpersand until limit }
            if (semicolon == null) {
                // 不是字元參照（例如一個單獨的 `&`）——原樣保留。
                output.append(character)
                index = afterAmpersand
                continue
            }

            val body = input.substring(afterAmpersand, semicolon)
            val decoded = decodeBody(body)
            if (decoded != null) {
                output.append(decoded)
            } else {
                // 不認得就原樣保留整段（含 `&` 與 `;`），不做任何猜測。
                output.append('&').append(body).append(';')
            }
            index = semicolon + 1
        }
        return output.toString()
    }

    /** 解析 `&` 與 `;` 之間那一段。認不得回傳 null。 */
    private fun decodeBody(body: String): String? {
        named[body.lowercase()]?.let { return it }

        if (!body.startsWith("#")) return null
        val digits = body.substring(1)
        val value: Int? = if (digits.startsWith("x") || digits.startsWith("X")) {
            val hex = digits.substring(1)
            if (hex.isEmpty() || hex.length > MAX_NUMERIC_DIGITS ||
                !hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            ) {
                null
            } else {
                hex.toIntOrNull(16)
            }
        } else {
            if (digits.isEmpty() || digits.length > MAX_NUMERIC_DIGITS || !digits.all { it in '0'..'9' }) {
                null
            } else {
                digits.toIntOrNull()
            }
        }
        val scalar = value ?: return null
        // surrogate 區段與超出 Unicode 範圍的值都不是合法的 scalar。
        if (scalar > 0x10FFFF || scalar in 0xD800..0xDFFF) return null
        return String(Character.toChars(scalar))
    }
}
