package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.Voucher
import com.megshao.exerciserewards.core.models.VoucherFigure

/**
 * 解析檢視加碼券相關頁面的 HTML。純函式、無副作用、無網路呼叫。
 *
 * 兩個入口對應官網的兩種頁面：
 * - [parseView]：正確 OTP 通過後 302 到 `/member/voucher/{uuid}/view` 的券碼頁，
 *   內含 `.voucher-banner`／`.voucher-meta`（通路／品項／兌換期限）與一或多個
 *   `.voucher-figure`（`data-format`／`data-value`／caption）。
 * - [parseVerifyError]：OTP 驗證錯誤時原地回傳的同一頁，
 *   含 `<p class="notice notice--error">驗證碼錯誤，還可以再試 N 次。</p>`。
 *
 * 容錯策略：任何欄位抓不到就給合理預設（空字串／null／略過該筆），不整頁拋錯——
 * 頁面版型微調時，App 應該退化成「資訊少一點」而不是整個功能掛掉。
 */
public object VoucherParser {

    // MARK: - Patterns
    //
    // ReDoS 防線：輸入是官方站回傳的 HTML，屬**不受信任輸入**。三條規則：
    // 1. 相鄰量詞的字元集合不得重疊。`\s*([^<]*?)\s*</p>` 是反例（`\s` ⊂ `[^<]`），
    //    iOS 端實測 800 個空白要 9 秒、1600 個要 81 秒（O(n³)）。正解 `([^<]*)</p>` + trim。
    // 2. 每個量詞都要長度上限，讓單次比對成本不隨整頁長度成長。
    // 3. 標籤屬性掃描用 `[^<>]`——屬性內不會有裸 `<`，排除它可讓「大量未閉合標籤」
    //    的攻擊在下一個 `<` 就停住（原本 `[^>]*` 會一路掃到文件尾，O(n²)）。
    // 另有一道獨立防線：OkHttpHttpClient 對 body 設 2 MB 上限。

    private val itemNameTagRegex =
        compile("""<strong\b[^<>]{0,2000}\bclass\s{0,8}=\s{0,8}["']voucher-meta__item["'][^<>]{0,2000}>([^<]{0,2000})</strong>""")
    private val channelBlockRegex =
        compile("""voucher-meta__channel[\s\S]{0,2000}?<span[^<>]{0,2000}>([^<]{0,2000})</span>""")

    // `([^<]*)` 貪婪一定停在第一個 `<`（零回溯），trim 由 firstGroup 統一處理。
    private val expiryRegex = compile("""兌換期限[：:]([^<]{0,2000})</p>""")

    private val figureSectionOpenTagRegex = compile("""<section\b[^<>]{0,2000}>""")
    private val figureSectionClassRegex =
        compile("""class\s{0,8}=\s{0,8}["'][^"'<>]{0,500}\bvoucher-figure\b[^"'<>]{0,500}["']""")
    private val figureCaptionRegex =
        compile("""<h2\b[^<>]{0,2000}\bclass\s{0,8}=\s{0,8}["']voucher-figure__caption["'][^<>]{0,2000}>([^<]{0,2000})</h2>""")
    private val figureFormatRegex = compile("""data-format\s{0,8}=\s{0,8}["']([^"']{0,2000})["']""")
    private val figureValueRegex = compile("""data-value\s{0,8}=\s{0,8}["']([^"']{0,2000})["']""")

    // 注意事項區塊的長度上限同時也是 listItemRegex 的成本上限——
    // parseNotices 只在這個已被截短的片段內找 `<li>`，不會掃整頁。
    private val noticesSectionRegex =
        compile("""<section\b[^<>]{0,2000}\bclass\s{0,8}=\s{0,8}["']voucher-notices["'][\s\S]{0,8000}?</section>""")
    private val listItemRegex = compile("""<li\b[^<>]{0,2000}>([\s\S]{0,2000}?)</li>""")
    private val tagStripRegex = compile("""<[^<>]{1,2000}>""")

    private val errorNoticeRegex =
        compile("""<p\b[^<>]{0,2000}\bclass\s{0,8}=\s{0,8}["'][^"'<>]{0,500}\bnotice--error\b[^"'<>]{0,500}["'][^<>]{0,2000}>([^<]{0,2000})</p>""")

    // `(\d+)\s*次` 對 8000 位連續數字要 4.6 秒（O(n²)：`\d+` 每次貪婪到底再逐格回溯）。
    // 剩餘次數是個位數，上限 9 位綽綽有餘，且讓每個起點的成本變成常數。
    private val remainingCountRegex = compile("""(\d{1,9})\s{0,8}次""")

    private fun compile(pattern: String): Regex =
        Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * 解析券碼頁（`/member/voucher/{uuid}/view`）。
     *
     * @throws AppError.Parsing 只在完全找不到任何 `voucher-figure` 時拋出——
     * 沒有券碼就不成一張「加碼券」，這種情況才視為解析失敗。
     */
    public fun parseView(html: String): Voucher {
        val figures = parseFigures(html)
        if (figures.isEmpty()) {
            throw AppError.Parsing("no voucher-figure found in voucher view HTML")
        }
        return Voucher(
            vendorName = firstGroup(html, channelBlockRegex) ?: "",
            itemName = firstGroup(html, itemNameTagRegex) ?: "",
            expiry = firstGroup(html, expiryRegex) ?: "",
            figures = figures,
            notices = parseNotices(html),
        )
    }

    private fun parseFigures(html: String): List<VoucherFigure> =
        splitFigureBlocks(html).mapNotNull { block ->
            val format = firstGroup(block, figureFormatRegex) ?: return@mapNotNull null
            val value = firstGroup(block, figureValueRegex) ?: return@mapNotNull null
            VoucherFigure(
                format = format,
                value = value,
                caption = firstGroup(block, figureCaptionRegex) ?: "",
            )
        }

    /**
     * 把整份 HTML 切成一支支 `<section class="voucher-figure" ...> ... </section>` 片段
     * （片段包含開頭的 `<section>` 標籤本身，方便一併抓 data-* / class 屬性）。
     */
    private fun splitFigureBlocks(html: String): List<String> {
        val blocks = mutableListOf<String>()
        for (match in figureSectionOpenTagRegex.findAll(html)) {
            val tag = match.value
            if (!figureSectionClassRegex.containsMatchIn(tag)) continue
            val blockStart = match.range.last + 1
            val blockEnd = html.indexOf("</section>", blockStart).takeIf { it >= 0 } ?: html.length
            blocks.add(tag + html.substring(blockStart, blockEnd))
        }
        return blocks
    }

    private fun parseNotices(html: String): List<String> {
        val section = noticesSectionRegex.find(html)?.value ?: return emptyList()
        return listItemRegex.findAll(section).mapNotNull { match ->
            val stripped = tagStripRegex.replace(match.groupValues[1], "")
            stripped.trim().takeIf { it.isNotEmpty() }
        }.toList()
    }

    /**
     * 解析 OTP 驗證錯誤時原地回傳的同一頁：`.notice--error` 文字與其中的剩餘次數 N。
     * 找不到 `.notice--error` 或抓不到數字時給合理預設（`remaining = null`、通用錯誤訊息）。
     */
    public fun parseVerifyError(html: String): VerifyError {
        val message = firstGroup(html, errorNoticeRegex)
            ?: return VerifyError(remaining = null, message = "驗證碼錯誤")
        val remaining = firstGroup(message, remainingCountRegex)?.toIntOrNull()
        return VerifyError(remaining = remaining, message = message)
    }

    public data class VerifyError(public val remaining: Int?, public val message: String)

    private fun firstGroup(text: String, regex: Regex): String? =
        regex.find(text)?.groupValues?.get(1)?.trim()
}
