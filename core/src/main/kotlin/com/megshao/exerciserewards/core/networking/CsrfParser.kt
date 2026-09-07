package com.megshao.exerciserewards.core.networking

import com.megshao.exerciserewards.core.models.AppError

/**
 * 從 Thymeleaf 渲染的 HTML 表單中擷取隱藏欄位 `_csrf` 的 value。
 */
public object CsrfParser {
    // 先抓出整個 <input ... name="_csrf" ...> 標籤，再從標籤內找 value 屬性，
    // 這樣不論 name/value 屬性順序為何都能正確擷取。
    //
    // ReDoS 防線：`[^>]*` 在「大量未閉合 `<input `」的惡意頁面上是 O(n²)
    // （iOS 端實測 56 KB → 6.7 秒）。改用 `[^<>]{0,2000}`：屬性內不可能有裸 `<`，
    // 排除它之後掃描會在下一個 `<` 停住，再加上長度上限，成本與整頁長度脫鉤。
    private val inputTagRegex = Regex(
        """<input\b[^<>]{0,2000}\bname\s{0,8}=\s{0,8}["']_csrf["'][^<>]{0,2000}>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val valueAttrRegex = Regex(
        """\bvalue\s{0,8}=\s{0,8}["']([^"']{0,2000})["']""",
        RegexOption.IGNORE_CASE,
    )

    /** @throws AppError.CsrfNotFound 若找不到 `_csrf` 欄位或其值為空。 */
    public fun extract(html: String): String {
        val tag = inputTagRegex.find(html)?.value ?: throw AppError.CsrfNotFound
        val token = valueAttrRegex.find(tag)?.groupValues?.get(1) ?: throw AppError.CsrfNotFound
        if (token.isEmpty()) throw AppError.CsrfNotFound
        return token
    }
}
