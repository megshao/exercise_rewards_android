package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.VendorIntro
import com.megshao.exerciserewards.core.models.VendorIntroCategory
import com.megshao.exerciserewards.core.models.VendorIntroLayout

/**
 * 解析廠商可兌換商品頁（`/intro/vendor-{id}.html`）。純函式、無副作用、無網路呼叫。
 *
 * 這幾頁是官網的**靜態頁**，不需要登入也拿得到，內容只有商品名稱，沒有任何個資。
 *
 * ## 兩種版型（都得吃）
 *
 * **A. 逐項列出**（全家／7-11／萊爾富）：
 * ```html
 * <details class="category-card all-items" data-category="全部品項">
 *   <summary class="category-trigger">
 *     <span class="category-title">全部品項<span class="item-count">308 項</span></span>
 *   </summary>
 *   <div class="category-content">
 *     <ul><li data-name="ＦＭＣ不知春茶">ＦＭＣ不知春茶</li>…</ul>
 *   </div>
 * </details>
 * ```
 *
 * **B. 只給類別與舉例**（全聯／萬家福・樂家康）：
 * ```html
 * <table>
 *   <thead><tr><th>類別名稱</th><th>商品名稱（列舉）</th></tr></thead>
 *   <tbody><tr><td>冷藏鮮乳</td><td>光泉低脂鮮乳、林鳳營高品質鮮乳等</td></tr>…</tbody>
 * </table>
 * ```
 *
 * 版型 A 有就用 A，沒有才找 B——不是「猜哪一種」，而是「A 的標記存在與否」這個確定的訊號。
 *
 * **兩種都對不上時不丟例外，改退到純文字**（`<li>`／`<p>` 的可見文字兜成單一分類），
 * 並在 [VendorIntro.layout] 標成 [VendorIntroLayout.UNRECOGNISED]。理由：這一頁是純資訊，
 * 讓使用者看到「這家大概能換什麼」比看到一個錯誤畫面有用。
 * 代價是失敗不再自動變成例外，所以那個 layout 就是要求呼叫端自己回報的契約。
 * 真的連一個字都撈不到才丟 [AppError.Parsing]。
 *
 * ## ReDoS 防線
 *
 * 與 [TaskParser]／[RedeemParser] 同一套規矩（相鄰量詞字元集合不重疊、量詞一律有上限、
 * 標籤屬性用 `[^<>]`）。分類卡的切割用字串搜尋而非正則，因為 `<details>` 可以巢狀，
 * 用 `.*?</details>` 在惡意輸入上會退化。
 */
public object VendorIntroParser {
    // MARK: - Patterns

    private val titleRegex = compile("""<h1\b[^<>]{0,400}>([^<]{0,300})<""")
    private val heroSubtitleRegex = compile("""<div class="hero-copy">.{0,2000}?<p\b[^<>]{0,200}>([^<]{0,500})<""")
    private val categoryAttrRegex = compile("""data-category\s{0,8}=\s{0,8}["']([^"']{0,300})["']""")
    private val detailsOpenTagRegex = compile("""<details\b[^<>]{0,600}>""")
    private val itemCountRegex = compile("""item-count["'][^<>]{0,200}>([^<]{0,60})<""")
    private val itemNameRegex = compile("""<li\b[^<>]{0,400}\bdata-name\s{0,8}=\s{0,8}["']([^"']{0,500})["']""")
    private val tableBodyRegex = compile("""<tbody\b[^<>]{0,200}>""")
    private val cellRegex = compile("""<td\b[^<>]{0,200}>([^<]{0,2000})<""")
    private val noticeRegex =
        compile("""<aside\b[^<>]{0,200}\bclass\s{0,8}=\s{0,8}["'][^"']{0,120}notice[^"']{0,120}["'][^<>]{0,200}>""")
    private val mainOpenTagRegex = compile("""<main\b[^<>]{0,400}>""")

    /** 退路用：任何 `<li>` 的可見文字（不要求 `data-name`）。 */
    private val plainListItemRegex = compile("""<li\b[^<>]{0,400}>([^<]{0,500})<""")
    private val paragraphRegex = compile("""<p\b[^<>]{0,200}>([^<]{0,2000})<""")

    private fun compile(pattern: String): Regex =
        Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * 解析整頁 HTML。
     *
     * @throws AppError.Parsing 只在**連純文字都撈不到**時（頁面根本不是商品頁）。
     */
    public fun parse(html: String): VendorIntro {
        var layout = VendorIntroLayout.ITEM_LIST
        var categories = parseDetailCategories(html)

        if (categories.isEmpty()) {
            categories = parseTableCategories(html)
            layout = VendorIntroLayout.CATEGORY_TABLE
        }
        if (categories.isEmpty()) {
            categories = parseFallbackCategories(html)
            layout = VendorIntroLayout.UNRECOGNISED
        }

        if (categories.isEmpty()) {
            throw AppError.Parsing("no category-card, table row or item text found in vendor intro HTML")
        }

        return VendorIntro(
            title = text(html, titleRegex) ?: "可兌換商品",
            subtitle = text(html, heroSubtitleRegex),
            categories = categories,
            notices = parseNotices(html),
            layout = layout,
        )
    }

    // MARK: - 兩種版型都對不上時的最小可用結果

    /**
     * 把頁面裡的 `<li>`（沒有就退 `<p>`）可見文字兜成單一分類。
     *
     * 刻意**不猜分類結構**：既然版型認不出來，就不要假裝知道哪個是類別、哪個是品項。
     * 只給一個「商品資訊」分類把撈到的文字逐條列出，讓使用者至少看得到內容，
     * 同時由 `layout == UNRECOGNISED` 通知工程師該更新解析器了。
     *
     * 排除頁尾注意事項與導覽文字：`<aside class="notice">` 那一段另外解析，
     * 這裡只掃 `<main>`（沒有 `<main>` 才退回整頁）。
     */
    private fun parseFallbackCategories(html: String): List<VendorIntroCategory> {
        val scope = mainOpenTagRegex.find(html)?.let { match ->
            val tail = html.substring(match.range.last + 1)
            val close = tail.indexOf("</main>")
            if (close >= 0) tail.substring(0, close) else tail
        } ?: html

        // 注意事項在 `<aside class="notice">` 裡，會被 parseNotices 另外撈走，
        // 這裡先切掉避免同一段文字出現兩次。
        val body = noticeRegex.find(scope)?.let { scope.substring(0, it.range.first) } ?: scope

        var items = allGroups(body, plainListItemRegex)
        if (items.isEmpty()) {
            items = allGroups(body, paragraphRegex)
        }

        val cleaned = items
            .map { HtmlEntities.decode(it).trim() }
            .filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return emptyList()

        return listOf(
            VendorIntroCategory(name = "商品資訊", items = cleaned, statedCount = null, isAllItems = true),
        )
    }

    // MARK: - 版型 A：<details> 分類卡

    private fun parseDetailCategories(html: String): List<VendorIntroCategory> =
        splitDetailBlocks(html).mapNotNull { block ->
            val rawName = categoryAttrRegex.find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            val openTag = detailsOpenTagRegex.find(block)?.value ?: ""
            VendorIntroCategory(
                name = HtmlEntities.decode(rawName),
                items = allGroups(block, itemNameRegex).map(HtmlEntities::decode),
                examples = null,
                statedCount = statedCount(block),
                // 官網用 `class="category-card all-items"` 標出彙總卡。
                isAllItems = openTag.contains("all-items"),
            )
        }

    /**
     * 把整份 HTML 依 `<details` 切成一張一張分類卡（到下一個 `<details` 或文件尾）。
     *
     * 官網目前不巢狀 `<details>`，用「下一個開頭」當邊界因此等同用 `</details>`；
     * 但真的巢狀時這個切法只會讓某一卡多含一些內容，不會像正則那樣爆炸。
     */
    private fun splitDetailBlocks(html: String): List<String> {
        val marker = "<details"
        val blocks = mutableListOf<String>()
        var searchStart = 0
        while (true) {
            val blockStart = html.indexOf(marker, searchStart)
            if (blockStart < 0) break
            val nextSearchStart = blockStart + marker.length
            val blockEnd = html.indexOf(marker, nextSearchStart).takeIf { it >= 0 } ?: html.length
            blocks.add(html.substring(blockStart, blockEnd))
            searchStart = nextSearchStart
        }
        return blocks
    }

    /**
     * 從「308 項」取出 308。抓不到或不是數字回傳 null——**不拿 `items.size` 頂替**，
     * 官網數字與解析結果不一致時要看得出來。
     */
    private fun statedCount(block: String): Int? {
        val raw = itemCountRegex.find(block)?.groupValues?.get(1) ?: return null
        val digits = raw.filter { it in '0'..'9' }
        return digits.toIntOrNull()
    }

    // MARK: - 版型 B：類別／舉例 表格

    private fun parseTableCategories(html: String): List<VendorIntroCategory> {
        // 只看 <tbody> 之後的內容，避開表頭那一列（`<th>` 本來就不會被 `<td>` 樣式抓到，
        // 這一步是為了在頁面有多張表時仍從資料列開始）。
        val tbodyTag = tableBodyRegex.find(html) ?: return emptyList()
        val body = html.substring(tbodyTag.range.last + 1)

        val categories = mutableListOf<VendorIntroCategory>()
        for (row in body.split("<tr")) {
            val cells = allGroups(row, cellRegex).map { HtmlEntities.decode(it).trim() }
            if (cells.size < 2 || cells[0].isEmpty()) continue
            categories.add(
                VendorIntroCategory(
                    name = cells[0],
                    items = emptyList(),
                    examples = cells[1].ifEmpty { null },
                    statedCount = null,
                    isAllItems = false,
                ),
            )
        }
        return categories
    }

    // MARK: - 注意事項

    private fun parseNotices(html: String): List<String> {
        val asideTag = noticeRegex.find(html) ?: return emptyList()
        val tail = html.substring(asideTag.range.last + 1)
        val close = tail.indexOf("</aside>")
        val block = if (close >= 0) tail.substring(0, close) else tail
        return allGroups(block, paragraphRegex)
            .map { HtmlEntities.decode(it).trim() }
            .filter { it.isNotEmpty() }
    }

    // MARK: - Regex helpers

    /** 取第一個 capture group，還原字元參照、去頭尾空白；空字串視同沒有。 */
    private fun text(html: String, regex: Regex): String? =
        regex.find(html)?.groupValues?.get(1)
            ?.let { HtmlEntities.decode(it).trim() }
            ?.takeIf { it.isNotEmpty() }

    private fun allGroups(text: String, regex: Regex): List<String> =
        regex.findAll(text).map { it.groupValues[1] }.toList()
}
