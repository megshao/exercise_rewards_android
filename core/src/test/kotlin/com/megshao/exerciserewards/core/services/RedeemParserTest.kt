package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.loadFixture
import com.megshao.exerciserewards.core.models.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 驗證 [RedeemParser] 對 `/member/redeem/{uuid}` 頁面的解析：
 * `fixtures/redeem.html` 為合成測試資料：5 家示範商家、其中示範超商 C 有兩個品項，
 * 共 6 支 item-row__form。
 *
 * fixture 也刻意涵蓋「兌換品項」連結的四種情形：帶 context path、不帶 context path、
 * 完全沒有連結、以及指向站外的惡意 href。
 */
class RedeemParserTest {

    @Test
    fun `parse returns all items across all vendors`() {
        // 示範超商 A、B、C x2、示範超市 D、示範量販 E = 6
        assertEquals(6, RedeemParser.parse(loadFixture("redeem")).size)
    }

    @Test
    fun `first option is vendor A with vendor id one`() {
        val first = RedeemParser.parse(loadFixture("redeem")).first()

        assertEquals("1", first.vendorId)
        assertTrue(first.vendorName.contains("示範超商 A"))
        assertFalse(first.itemId.isEmpty())
        assertEquals("test-item-0001", first.itemId)
        assertEquals("測試品項 A1", first.itemName)
        assertEquals("1-test-item-0001", first.id)
    }

    @Test
    fun `vendor C has two distinct items`() {
        val vendorC = RedeemParser.parse(loadFixture("redeem")).filter { it.vendorName.contains("示範超商 C") }

        assertEquals(2, vendorC.size)
        assertTrue(vendorC.all { it.vendorId == "3" })
        assertEquals(2, vendorC.map { it.itemId }.toSet().size)
    }

    @Test
    fun `vendor B and vendor D are parsed`() {
        val options = RedeemParser.parse(loadFixture("redeem"))

        val vendorB = assertNotNull(options.firstOrNull { it.vendorId == "2" })
        assertTrue(vendorB.vendorName.contains("示範超商 B"))
        assertEquals("test-item-0002", vendorB.itemId)

        val vendorD = assertNotNull(options.firstOrNull { it.vendorId == "5" })
        assertTrue(vendorD.vendorName.contains("示範超市 D"))
    }

    @Test
    fun `throws parsing error when no forms are found`() {
        assertFailsWith<AppError.Parsing> { RedeemParser.parse("<html>no forms here</html>") }
    }

    // MARK: - 兌換品項連結（introPath）

    @Test
    fun `intro path strips the context path`() {
        // 官網的 href 是 `/registrant/intro/vendor-1.html`，
        // 但 HTTPClienting.getHtml 收的是 base-relative path。
        assertEquals("/intro/vendor-1.html", RedeemParser.parse(loadFixture("redeem")).first().introPath)
    }

    @Test
    fun `intro path accepts an href without the context path`() {
        val vendorD = assertNotNull(
            RedeemParser.parse(loadFixture("redeem")).firstOrNull { it.vendorName.contains("示範超市 D") },
        )

        assertEquals("/intro/vendor-5.html", vendorD.introPath)
    }

    /**
     * 官網的規則是「靜態頁存在才長出連結」，所以沒有連結是正常狀況，
     * 不可以自己用 vendorId 拼一個網址出來（那會拼出 404）。
     */
    @Test
    fun `intro path is null when the vendor has no intro link`() {
        val vendorB = assertNotNull(
            RedeemParser.parse(loadFixture("redeem")).firstOrNull { it.vendorName.contains("示範超商 B") },
        )

        assertNull(vendorB.introPath)
    }

    /**
     * href 是不受信任輸入，而它會被拿去發請求，因此採白名單：
     * 只收 `/intro/<檔名>.html`，站外絕對網址一律不採用。
     */
    @Test
    fun `intro path rejects an off site absolute url`() {
        val vendorE = assertNotNull(
            RedeemParser.parse(loadFixture("redeem")).firstOrNull { it.vendorName.contains("示範量販 E") },
        )

        assertNull(vendorE.introPath)
    }

    @Test
    fun `intro path rejects path traversal and other namespaces`() {
        val cases = listOf(
            "/registrant/intro/../member/tasks",
            "/registrant/member/redeem/00000000-0000-4000-8000-000000000001",
            "/registrant/intro/vendor-1.html?next=https://evil.example.com",
        )

        for (href in cases) {
            val html = """
                <li class="item-row">
                  <div class="item-row__actions">
                    <a href="$href" class="btn item-row__intro">兌換品項</a>
                    <form class="item-row__form" data-vendor-name="示範超商 A" data-item-name="測試品項 A1">
                      <input type="hidden" name="vendorId" value="1">
                      <input type="hidden" name="item" value="test-item-0001">
                    </form>
                  </div>
                </li>
            """.trimIndent()

            assertNull(RedeemParser.parse(html).firstOrNull()?.introPath, "不該採用的 href：$href")
        }
    }

    /**
     * 介紹頁連結是 `<form>` 的前一個兄弟節點。切塊邊界若退回以 form 為單位，
     * 這個測試會抓到——品項照樣解析得到，但 introPath 會變成 null。
     */
    @Test
    fun `intro path belongs to its own row not the neighbouring one`() {
        // B 沒有連結，不可以把 A 的連結沾過來；C 的兩個品項各自都有。
        assertEquals(
            listOf(
                "/intro/vendor-1.html",
                null,
                "/intro/vendor-3.html",
                "/intro/vendor-3.html",
                "/intro/vendor-5.html",
                null,
            ),
            RedeemParser.parse(loadFixture("redeem")).map { it.introPath },
        )
    }

    // MARK: - 切列邊界的韌性

    /**
     * 官網把 class 加上修飾詞、調換順序、或在 `<li>` 上多加屬性，都不該讓切列失敗。
     *
     * **為什麼這件事值得一個測試**：切列失敗不會丟錯誤——[RedeemParser.parse] 會退回切表單的
     * 路徑，品項照樣解析成功、兌換照樣可用，只是每一列的 `introPath` 都變成 null，
     * 「兌換品項」按鈕靜默消失。那種退化沒有任何警報，只有使用者回報才會被發現。
     */
    @Test
    fun `intro path survives class attribute variations`() {
        val variants = listOf(
            """<li class="item-row item-row--featured">""",
            """<li class="row item-row">""",
            """<li data-index="1" class="item-row" data-vendor="1">""",
            """<li class='item-row'>""",
            """<li  class = "item-row" >""",
        )

        for (openTag in variants) {
            val html = """
                <ul class="item-list">
                  $openTag
                    <div class="item-row__actions">
                      <a href="/registrant/intro/vendor-1.html" class="btn item-row__intro">兌換品項</a>
                      <form class="item-row__form" data-vendor-name="示範超商 A" data-item-name="測試品項 A1">
                        <input type="hidden" name="vendorId" value="1">
                        <input type="hidden" name="item" value="test-item-0001">
                      </form>
                    </div>
                  </li>
                </ul>
            """.trimIndent()

            val options = RedeemParser.parse(html)
            assertEquals(1, options.size, "切列失敗：$openTag")
            assertEquals(
                "/intro/vendor-1.html",
                options.first().introPath,
                "切列退回 form 邊界，introPath 掉了：$openTag",
            )
        }
    }

    /** 每一列各自的連結不可以互相沾染，即使 class 寫法不同。 */
    @Test
    fun `each row keeps its own intro path with varied classes`() {
        val html = """
            <ul class="item-list">
              <li class="item-row item-row--first">
                <a href="/registrant/intro/vendor-1.html" class="btn item-row__intro">兌換品項</a>
                <form class="item-row__form" data-vendor-name="A" data-item-name="A1">
                  <input type="hidden" name="vendorId" value="1">
                  <input type="hidden" name="item" value="a1">
                </form>
              </li>
              <li class="item-row">
                <form class="item-row__form" data-vendor-name="B" data-item-name="B1">
                  <input type="hidden" name="vendorId" value="2">
                  <input type="hidden" name="item" value="b1">
                </form>
              </li>
            </ul>
        """.trimIndent()

        assertEquals(listOf("/intro/vendor-1.html", null), RedeemParser.parse(html).map { it.introPath })
    }
}
