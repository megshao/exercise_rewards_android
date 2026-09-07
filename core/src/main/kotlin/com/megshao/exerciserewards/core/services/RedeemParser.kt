package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.RedeemOption

/**
 * 解析 `/member/redeem/{uuid}` 頁面的 HTML，取出各商家可兌換品項。純函式、無副作用、無網路呼叫。
 *
 * 每個品項是一列 `<li class="item-row">`，列內有兩個東西：
 * - `<a class="… item-row__intro" href="/registrant/intro/vendor-{id}.html">兌換品項</a>`
 * - `<form action="/registrant/member/redeem/{uuid}" method="post" class="item-row__form"
 *   data-vendor-name="全家便利商店" data-item-name="50+3元加碼券">`，
 *   內含 hidden input `vendorId`、`item` 與 `_csrf`。
 *
 * 2026-08-27 起一家商店可以有多個品項，因此以「品項列」而非「商家」為單位解析。
 *
 * **為什麼切「列」而不是切「表單」**（這裡改過一次，理由留著）：介紹頁連結在官網的 DOM 上是
 * `<form>` 的**前一個兄弟節點**（按鈕排在「兌換」左側）。原本從 `<form>` 開始切塊的做法
 * 看不到它，只好把邊界往外推到 `<li class="item-row">`。找不到任何 `item-row` 時會退回
 * 舊的切表單邏輯——那樣仍解析得到品項，只是沒有介紹頁連結。
 */
public object RedeemParser {
    // ReDoS 防線：`[^>]*` 在「大量未閉合 `<form `／`<input `」的惡意頁面上是
    // O(n²)（iOS 端實測 48 KB → 1.9 秒）。屬性內不可能有裸 `<`，改用 `[^<>]{0,2000}` 後
    // 掃描會在下一個 `<` 停住，成本與整頁長度脫鉤。
    private val formOpenTagRegex = compile("""<form\b[^<>]{0,2000}>""")
    private val itemRowFormClassRegex = compile("""class\s{0,8}=\s{0,8}["']item-row__form["']""")
    private val vendorNameAttrRegex = compile("""data-vendor-name\s{0,8}=\s{0,8}["']([^"']{0,2000})["']""")
    private val itemNameAttrRegex = compile("""data-item-name\s{0,8}=\s{0,8}["']([^"']{0,2000})["']""")

    /**
     * 「兌換品項」連結。官網的 class 是 `btn btn--primary btn--small item-row__intro`，
     * 且屬性跨行，所以用 `[^<>]` 跨過中間的空白與其他屬性。
     */
    private val introAnchorRegex =
        compile("""<a\b[^<>]{0,2000}\bclass\s{0,8}=\s{0,8}["'][^"']{0,200}item-row__intro[^"']{0,200}["'][^<>]{0,2000}>""")
    private val hrefAttrRegex = compile("""\bhref\s{0,8}=\s{0,8}["']([^"']{0,2000})["']""")
    private val valueAttrRegex = compile("""\bvalue\s{0,8}=\s{0,8}["']([^"']{0,2000})["']""")

    /**
     * 官方站的 context path。`href` 是站根絕對路徑（`/registrant/intro/…`），
     * 而 `HTTPClienting.getHtml` 收的是 base-relative path，所以要把它剝掉。
     */
    private const val CONTEXT_PATH_PREFIX = "/registrant"

    /** 介紹頁 path 的白名單樣式。見 [introPath] 說明為什麼是白名單。 */
    private val allowedIntroPathRegex = Regex("""^/intro/[A-Za-z0-9._-]{1,64}\.html$""")

    /**
     * 品項列的開頭標籤。**刻意不比對完整字串** `<li class="item-row"`——見 [splitRowBlocks]。
     */
    private val itemRowOpenTagRegex =
        compile("""<li\b[^<>]{0,400}\bclass\s{0,8}=\s{0,8}["'][^"']{0,300}\bitem-row\b[^"']{0,300}["'][^<>]{0,400}>""")

    private fun compile(pattern: String): Regex =
        Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * 解析整頁 HTML，回傳依出現順序排列的兌換品項清單。
     *
     * @throws AppError.Parsing 當頁面內完全找不到任何 `item-row__form` 表單時。
     */
    public fun parse(html: String): List<RedeemOption> {
        val rows = splitRowBlocks(html)
        val blocks = rows.ifEmpty { splitFormBlocks(html) }
        if (blocks.isEmpty()) {
            throw AppError.Parsing("no item-row__form found in redeem HTML")
        }
        return blocks.mapNotNull(::parseBlock)
    }

    /**
     * 把整份 HTML 依品項列的 `<li>` 開頭切成一列一列。切法與 [TaskParser] 相同：
     * 每一塊從標記開始，到下一個標記／`</ul>`／文件尾為止。
     *
     * 只保留**真的含有 `item-row__form`** 的塊，這樣 [parse] 才能用「切得到列嗎」
     * 決定要不要退回舊路徑，而不會被一列不含表單的 `item-row` 誤導。
     *
     * **為什麼用正則而不是比對 `<li class="item-row"` 這個完整字串**（這裡改過一次）：
     * 完整字串比對把 class 屬性的寫法也當成契約的一部分。官網只要改成
     * `class="item-row item-row--featured"`、調換 class 順序、或在 `<li>` 上多加一個屬性，
     * 切列就會全部失敗 → [parse] 退回切表單的路徑 → **品項照樣解析成功、兌換照樣可用，
     * 但每一列的 `introPath` 都變成 null，「兌換品項」按鈕靜默消失**，而且不會丟出任何錯誤。
     * 那種失敗只有使用者回報才會被發現，所以邊界改成「class 裡有 `item-row` 這個 token」。
     */
    private fun splitRowBlocks(html: String): List<String> {
        val starts = itemRowOpenTagRegex.findAll(html).map { it.range.first }.toList()
        if (starts.isEmpty()) return emptyList()

        val blocks = mutableListOf<String>()
        for ((offset, blockStart) in starts.withIndex()) {
            val blockEnd = when {
                offset + 1 < starts.size -> starts[offset + 1]
                else -> html.indexOf("</ul>", blockStart).takeIf { it >= 0 } ?: html.length
            }
            val block = html.substring(blockStart, blockEnd)
            if (itemRowFormClassRegex.containsMatchIn(block)) {
                blocks.add(block)
            }
        }
        return blocks
    }

    /**
     * 把整份 HTML 切成一支支 `<form class="item-row__form" ...> ... </form>` 片段
     * （片段包含開頭的 `<form>` 標籤本身，方便一併從中取出 data-* 屬性）。
     */
    private fun splitFormBlocks(html: String): List<String> {
        val blocks = mutableListOf<String>()
        for (match in formOpenTagRegex.findAll(html)) {
            val tag = match.value
            if (!itemRowFormClassRegex.containsMatchIn(tag)) continue
            val blockStart = match.range.last + 1
            val blockEnd = html.indexOf("</form>", blockStart).takeIf { it >= 0 } ?: html.length
            blocks.add(tag + html.substring(blockStart, blockEnd))
        }
        return blocks
    }

    /**
     * 解析單一片段（一列 `item-row`，或退回模式下的單支表單）。
     * 任一必要欄位抓不到就回傳 null（略過該筆，不整頁失敗）。
     */
    private fun parseBlock(block: String): RedeemOption? {
        val vendorName = vendorNameAttrRegex.find(block)?.groupValues?.get(1) ?: return null
        val itemName = itemNameAttrRegex.find(block)?.groupValues?.get(1) ?: return null
        val vendorId = hiddenInputValue(block, "vendorId") ?: return null
        val itemId = hiddenInputValue(block, "item") ?: return null
        return RedeemOption(
            vendorId = vendorId,
            vendorName = HtmlEntities.decode(vendorName),
            itemName = HtmlEntities.decode(itemName),
            itemId = itemId,
            introPath = introPath(block),
        )
    }

    /**
     * 取出該列「兌換品項」連結的 base-relative path。
     *
     * **這個 href 是不受信任輸入**（官方站改版、被入侵、被 MITM 都可能塞進別的東西），
     * 而它的用途是「拿去餵給 HTTP client 發請求」，所以這裡採白名單而非黑名單：
     * 剝掉 context path 之後必須完全長得像 `/intro/<檔名>.html` 才收，其餘一律回 null
     * （畫面上就是不顯示這顆按鈕）。絕對網址、`..`、query 通通擋在外面。
     */
    private fun introPath(block: String): String? {
        val anchorTag = introAnchorRegex.find(block)?.value ?: return null
        val rawHref = hrefAttrRegex.find(anchorTag)?.groupValues?.get(1) ?: return null
        var path = HtmlEntities.decode(rawHref).trim()
        if (path.startsWith("$CONTEXT_PATH_PREFIX/")) {
            path = path.removePrefix(CONTEXT_PATH_PREFIX)
        }
        if (!allowedIntroPathRegex.containsMatchIn(path) || path.contains("..")) return null
        return path
    }

    /** 從片段中找出 `<input ... name="X" ... value="Y">` 的 Y（不論屬性順序）。 */
    private fun hiddenInputValue(block: String, name: String): String? {
        val escapedName = Regex.escape(name)
        val inputRegex = Regex(
            """<input\b[^<>]{0,2000}\bname\s{0,8}=\s{0,8}["']$escapedName["'][^<>]{0,2000}>""",
            RegexOption.IGNORE_CASE,
        )
        val tag = inputRegex.find(block)?.value ?: return null
        return valueAttrRegex.find(tag)?.groupValues?.get(1)
    }
}
