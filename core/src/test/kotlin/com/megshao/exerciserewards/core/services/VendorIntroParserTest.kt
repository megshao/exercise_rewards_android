package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.loadFixture
import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.VendorIntroLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 驗證 [VendorIntroParser] 對廠商可兌換商品頁的解析。
 *
 * 官網這幾頁有兩種版型，兩個 fixture 各對應一種（皆為合成測試資料，非官方頁面複製）：
 * - `vendor_intro_details.html`：`<details data-category>` 分類卡 + `<li data-name>` 逐項清單
 * - `vendor_intro_table.html`：`類別名稱 / 商品名稱（列舉）` 表格
 */
class VendorIntroParserTest {

    // MARK: - 版型 A：逐項列出

    @Test
    fun `details layout parses every category`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_details"))

        assertEquals(4, intro.categories.size)
        assertEquals(
            listOf("全部品項", "Let's Café", "瓶裝水類", "堅果 & 蛋類"),
            intro.categories.map { it.name },
        )
    }

    @Test
    fun `details layout parses title and subtitle`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_details"))

        assertEquals("示範超商 A可兌換商品", intro.title)
        assertEquals("點選商品分類，即可展開查看相關兌換品項。", intro.subtitle)
    }

    /**
     * 分類名稱在官網是放在屬性裡的跳脫字串（`Let&#x27;s Caf&#233;`），
     * 沒有還原的話畫面上會直接看到原始碼。
     */
    @Test
    fun `details layout decodes html entities in the category name`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_details"))

        assertTrue(intro.categories.any { it.name == "Let's Café" })
        assertTrue(intro.categories.any { it.name == "堅果 & 蛋類" })
        assertFalse(intro.categories.any { it.name.contains("&#") })
    }

    @Test
    fun `details layout parses items in each category`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_details"))
        val all = assertNotNull(intro.categories.firstOrNull { it.isAllItems })
        val coffee = assertNotNull(intro.categories.firstOrNull { it.name == "Let's Café" })

        assertEquals(5, all.items.size)
        assertEquals("測試無糖茶", all.items.first())
        assertEquals(listOf("測試大冰拿鐵"), coffee.items)
    }

    /** 「全部品項」那張卡是官網用 `class="… all-items"` 標出來的彙總卡。 */
    @Test
    fun `details layout marks only the all items card`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_details"))

        assertEquals(1, intro.categories.count { it.isAllItems })
        assertEquals(true, intro.categories.firstOrNull()?.isAllItems)
    }

    /**
     * `statedCount` 是官網自己標的數字，不是 `items.size` 的複製品——
     * 官網沒標時要是 null，這樣兩者兜不攏時才看得出來。
     */
    @Test
    fun `details layout keeps stated count separate from parsed items`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_details"))
        val all = assertNotNull(intro.categories.firstOrNull { it.isAllItems })
        val nuts = assertNotNull(intro.categories.firstOrNull { it.name == "堅果 & 蛋類" })

        assertEquals(5, all.statedCount)
        assertNull(nuts.statedCount, "官網沒標「N 項」時不可以用 items.size 頂替")
        assertEquals(2, nuts.items.size)
    }

    @Test
    fun `details layout has no examples`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_details"))

        assertTrue(intro.categories.all { it.examples == null })
    }

    @Test
    fun `parses notices`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_details"))

        assertEquals(2, intro.notices.size)
        assertEquals("實際可兌換品項、供應狀況及門市庫存，依各門市現場公告為準。", intro.notices.first())
    }

    // MARK: - 版型 B：類別／舉例 表格

    @Test
    fun `table layout parses every row`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_table"))

        assertEquals(4, intro.categories.size)
        assertEquals(
            listOf("冷藏鮮乳", "豆漿/米漿/燕麥", "常溫鮮蛋", "堅果 & 核仁類"),
            intro.categories.map { it.name },
        )
    }

    /**
     * 表格版的第二欄是「列舉」不是完整清單，因此進 `examples` 而不是 `items`——
     * 放進 `items` 會讓畫面看起來像「這就是全部品項」。
     */
    @Test
    fun `table layout puts examples in examples not items`() {
        val milk = assertNotNull(VendorIntroParser.parse(loadFixture("vendor_intro_table")).categories.firstOrNull())

        assertEquals("測試低脂鮮乳、測試高品質鮮乳等", milk.examples)
        assertTrue(milk.items.isEmpty())
        assertNull(milk.statedCount)
        assertFalse(milk.isAllItems)
    }

    @Test
    fun `table layout skips the header row`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_table"))

        assertFalse(intro.categories.any { it.name == "類別名稱" })
    }

    @Test
    fun `table layout decodes entities`() {
        val intro = VendorIntroParser.parse(loadFixture("vendor_intro_table"))

        assertTrue(intro.categories.any { it.name == "堅果 & 核仁類" })
    }

    // MARK: - 失敗路徑

    /**
     * **契約改過一次，理由留著**：兩種版型都不在時，原本是丟 [AppError.Parsing]
     * 讓上層報警。現在改成退到純文字仍給出內容（見下方 UNRECOGNISED 的測試），
     * 因為這一頁是純資訊，讓使用者看到「大概能換什麼」比看到錯誤畫面有用。
     * 報警的責任因此轉移到 `VendorIntro.layout`——呼叫端看到 UNRECOGNISED 要自己回報。
     *
     * 只有**連一段可讀文字都撈不到**時才還是丟例外：那種頁面根本不是商品頁
     * （被導去登入頁、拿到錯誤頁），硬湊一個空清單給使用者看沒有意義。
     */
    @Test
    fun `throws parsing when there is nothing to show at all`() {
        val html = "<html><body><h1>某頁</h1><div><span>沒有清單也沒有段落</span></div></body></html>"

        assertFailsWith<AppError.Parsing> { VendorIntroParser.parse(html) }
    }

    @Test
    fun `falls back to the default title when the heading is missing`() {
        val html = """<main><table><tbody><tr><td>類別</td><td>舉例</td></tr></tbody></table></main>"""

        val intro = VendorIntroParser.parse(html)

        assertEquals("可兌換商品", intro.title)
        assertNull(intro.subtitle)
        assertTrue(intro.notices.isEmpty())
    }

    // MARK: - 認不出版型時的最小可用結果

    /**
     * 兩種已知版型都對不上時**不丟例外**，退到純文字仍給出內容，
     * 並把 layout 標成 UNRECOGNISED 讓呼叫端發警報。
     */
    @Test
    fun `falls back to plain text when the layout is unrecognised`() {
        val html = """
            <main>
              <h1>示範超商 Z可兌換商品</h1>
              <section class="catalog">
                <ul class="new-markup">
                  <li><span>示範品項 A</span>示範品項 A</li>
                  <li>示範品項 B</li>
                  <li>示範品項 C</li>
                </ul>
              </section>
              <aside class="notice"><h2>兌換注意事項</h2><p>依門市現場公告為準。</p></aside>
            </main>
        """.trimIndent()

        val intro = VendorIntroParser.parse(html)

        assertEquals(VendorIntroLayout.UNRECOGNISED, intro.layout)
        assertEquals("示範超商 Z可兌換商品", intro.title)
        assertEquals(1, intro.categories.size)
        assertEquals("商品資訊", intro.categories.first().name)
        assertTrue(intro.categories.first().items.contains("示範品項 B"))
    }

    /** 退路不可以把頁尾注意事項也當成品項——那段由 `notices` 另外解析。 */
    @Test
    fun `fallback excludes notice text`() {
        val html = """
            <main>
              <h1>示範超商 Z</h1>
              <ul><li>示範品項 A</li></ul>
              <aside class="notice"><p>依門市現場公告為準。</p></aside>
            </main>
        """.trimIndent()

        val intro = VendorIntroParser.parse(html)

        assertEquals(listOf("依門市現場公告為準。"), intro.notices)
        assertFalse(intro.categories.first().items.contains("依門市現場公告為準。"))
    }

    /** 沒有 `<li>` 時退一步用 `<p>`。 */
    @Test
    fun `fallback uses paragraphs when there are no list items`() {
        val html = """
            <main>
              <h1>示範超商 Z</h1>
              <section><p>示範品項 A</p><p>示範品項 B</p></section>
            </main>
        """.trimIndent()

        val intro = VendorIntroParser.parse(html)

        assertEquals(VendorIntroLayout.UNRECOGNISED, intro.layout)
        assertEquals(listOf("示範品項 A", "示範品項 B"), intro.categories.first().items)
    }

    /** 已知版型要標對，不能全部落到 UNRECOGNISED。 */
    @Test
    fun `known layouts are labelled`() {
        assertEquals(
            VendorIntroLayout.ITEM_LIST,
            VendorIntroParser.parse(loadFixture("vendor_intro_details")).layout,
        )
        assertEquals(
            VendorIntroLayout.CATEGORY_TABLE,
            VendorIntroParser.parse(loadFixture("vendor_intro_table")).layout,
        )
    }
}
