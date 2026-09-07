package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.assertCpuBudget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 驗證 [HtmlEntities.decode]。這支的用途是把官網屬性值（Thymeleaf 跳脫過）還原成
 * 給人看的文字，因此重點在「認得的要還原」與「不認得的原樣保留、不亂猜」兩件事。
 */
class HtmlEntitiesTest {

    @Test
    fun `decodes named entities`() {
        assertEquals("堅果 & 蛋類", HtmlEntities.decode("堅果 &amp; 蛋類"))
        assertEquals("<script>", HtmlEntities.decode("&lt;script&gt;"))
        assertEquals("\"引號\"", HtmlEntities.decode("&quot;引號&quot;"))
        assertEquals("Let's Café", HtmlEntities.decode("Let&apos;s Café"))
    }

    @Test
    fun `decodes numeric references`() {
        assertEquals("Let's Café", HtmlEntities.decode("Let&#x27;s Caf&#233;"))
        assertEquals("中文", HtmlEntities.decode("&#20013;&#25991;"))
    }

    @Test
    fun `is case insensitive for named and hex entities`() {
        assertEquals("&", HtmlEntities.decode("&AMP;"))
        assertEquals("'", HtmlEntities.decode("&#X27;"))
    }

    /** 沒有 `&` 的字串要原樣回來（也是最常見的快速路徑）。 */
    @Test
    fun `leaves plain text untouched`() {
        assertEquals("全家便利商店可兌換商品", HtmlEntities.decode("全家便利商店可兌換商品"))
        assertEquals("", HtmlEntities.decode(""))
    }

    /** 認不得的一律原樣保留：這裡不是 HTML 剖析器，猜錯比留著原文更糟。 */
    @Test
    fun `keeps unknown or malformed sequences as is`() {
        assertEquals("A &不是實體; B", HtmlEntities.decode("A &不是實體; B"))
        assertEquals("&notarealentity;", HtmlEntities.decode("&notarealentity;"))
        assertEquals("100% 純果汁 & 蔬菜", HtmlEntities.decode("100% 純果汁 & 蔬菜"))
        assertEquals("&#;", HtmlEntities.decode("&#;"))
        assertEquals("&#xZZ;", HtmlEntities.decode("&#xZZ;"))
    }

    /** 沒有結尾分號、或分號離得太遠，都不算字元參照。 */
    @Test
    fun `requires a nearby semicolon`() {
        assertEquals("&amp", HtmlEntities.decode("&amp"))
        val long = "&" + "a".repeat(40) + ";"
        assertEquals(long, HtmlEntities.decode(long))
    }

    /** 超出 Unicode 範圍的碼位不可以讓解碼器崩潰。 */
    @Test
    fun `rejects out of range scalars`() {
        assertEquals("&#xFFFFFF;", HtmlEntities.decode("&#xFFFFFF;"))
        assertEquals("&#99999999;", HtmlEntities.decode("&#99999999;"))
    }

    /** 只解一輪，不重複解碼——`&amp;#x27;` 的原意就是要顯示 `&#x27;` 這串字。 */
    @Test
    fun `does not double decode`() {
        assertEquals("&#x27;", HtmlEntities.decode("&amp;#x27;"))
    }

    /** 這支會吃到整頁 HTML 大小的輸入，必須是線性成本。 */
    @Test
    fun `handles long input quickly`() {
        val input = "商品名稱 &amp; 說明 &#x27;A&#x27; ".repeat(5_000)

        val decoded = assertCpuBudget(2.0, "HtmlEntities.decode 長輸入") { HtmlEntities.decode(input) }

        assertFalse(decoded.contains("&amp;"))
    }
}
