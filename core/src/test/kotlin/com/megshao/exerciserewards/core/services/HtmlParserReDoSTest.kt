package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.CpuClock
import com.megshao.exerciserewards.core.assertCpuBudget
import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.networking.CsrfParser
import com.megshao.exerciserewards.core.networking.OkHttpHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **效能回歸測試。**
 *
 * 四個 HTML parser 吃的都是官方站回傳的 HTML——那是**不受信任的輸入**。官網被入侵、
 * 或使用者裝置信任了 MITM 憑證（本專案刻意不做 certificate pinning，理由見 README）
 * 時，回一頁精心構造的 HTML 就能讓正規表示式進入災難性回溯。
 *
 * 這不會 ANR：`fetchTasks` 跑在 IO dispatcher 上而不是主執行緒。症狀是任務／券夾／首頁的
 * 所有抓取**永遠不會回來**、CPU 滿載耗電，直到使用者自己殺掉 App——一個沒有錯誤訊息、
 * 看不出原因的當機。
 *
 * iOS 端修好之前的實測（`period-remaining">` 後接 N 個空白且無 `<`）：
 * N=200 → 51 ms、400 → 447 ms、800 → 4.1 秒、1600 → **33.6 秒**。每倍增約 8×，
 * 確認是 O(n³)。`上傳時間：` 與 `兌換期限：` 兩條更慢，1600 → **78／81 秒**。
 *
 * **這些測試是唯一能防止有人日後把樣式改回舊寫法的保險。** 每個修過的樣式餵 4 KB
 * 對抗輸入，斷言在 CPU 預算內完成。
 *
 * 計時一律走 [CpuClock]（量 CPU 時間、多輪取最小值）而不是牆上時間，理由見 CpuBudget.kt。
 *
 * 另一道獨立防線是 [OkHttpHttpClient] 的 2 MB body 上限（見本檔最後一節）：
 * 逐條修 regex 擋不住之後新加的 parser，把輸入長度夾住才擋得住。
 */
class HtmlParserReDoSTest {

    /** 執行 [body] 並斷言在預算內完成。 */
    private fun assertPrompt(label: String, body: () -> Unit) {
        assertCpuBudget(
            BUDGET,
            "$label（4 KB 對抗輸入）",
            hint = "這代表樣式又出現了災難性回溯——檢查是不是有相鄰量詞的字元集合重疊" +
                "（例如 `\\s*([^<]+?)\\s*<`），或是有量詞少了長度上限。",
            body = body,
        )
    }

    private val pad: String get() = " ".repeat(ADVERSARIAL_LENGTH)
    private val digits: String get() = "1".repeat(ADVERSARIAL_LENGTH)

    // MARK: - TaskParser

    /** 原 `period-remaining">\s*([^<]+?)\s*<`：`\s` ⊂ `[^<]`，三層量詞互相回溯。 */
    @Test
    fun `task parser remaining text with unterminated whitespace`() {
        val html = CARD_OPEN + """<span class="period-remaining">""" + pad
        assertPrompt("TaskParser.period-remaining（純空白、無收尾 `<`）") { runCatching { TaskParser.parse(html) } }
    }

    /** 空白與非空白交錯的變體（實測最慢的一種形狀）。 */
    @Test
    fun `task parser remaining text with a mixed whitespace run`() {
        val filler = " a  b ".repeat(ADVERSARIAL_LENGTH / 6)
        val html = CARD_OPEN + """<span class="period-remaining">""" + filler
        assertPrompt("TaskParser.period-remaining（空白-字元-空白交錯）") { runCatching { TaskParser.parse(html) } }
    }

    @Test
    fun `task parser uploaded at with unterminated whitespace`() {
        val html = CARD_OPEN + "上傳時間：" + pad
        assertPrompt("TaskParser.上傳時間") { runCatching { TaskParser.parse(html) } }
    }

    @Test
    fun `task parser reviewed at with unterminated whitespace`() {
        val html = CARD_OPEN + "審核時間：" + pad
        assertPrompt("TaskParser.審核時間") { runCatching { TaskParser.parse(html) } }
    }

    /** `第\s*(\d+)\s*期` / `period-range">…([0-9/]+)…`：長數字串的 O(n²)。 */
    @Test
    fun `task parser index and range with a long digit run`() {
        val html = CARD_OPEN + "第" + digits + """<span class="period-range">""" + digits
        assertPrompt("TaskParser.第 N 期／period-range（超長數字串）") { runCatching { TaskParser.parse(html) } }
    }

    /** 大量未閉合標籤：`[^>]*` 會一路掃到文件尾（改用 `[^<>]` 後在下一個 `<` 就停）。 */
    @Test
    fun `task parser with many unclosed tags`() {
        val html = CARD_OPEN + "<span ".repeat(ADVERSARIAL_LENGTH / 6)
        assertPrompt("TaskParser（大量未閉合標籤）") { runCatching { TaskParser.parse(html) } }
    }

    /**
     * 切卡片的開頭標籤改成正則之後多出來的攻擊面：大量未閉合 `<li `。
     * `[^<>]{0,400}` 有上限、且在下一個 `<` 就停，所以每個 `<li` 的嘗試成本是常數，整體線性。
     */
    @Test
    fun `task parser with many unclosed list items`() {
        val html = "<li ".repeat(ADVERSARIAL_LENGTH / 4)
        assertPrompt("TaskParser.splitCards（大量未閉合 <li）") { runCatching { TaskParser.parse(html) } }
    }

    /** 同一件事的另一個形狀：`<li` 後面接一個永遠閉不起來的超長 class 屬性。 */
    @Test
    fun `task parser with an unterminated class attribute`() {
        val html = """<li class="period-card """ + "a".repeat(ADVERSARIAL_LENGTH)
        assertPrompt("TaskParser.splitCards（class 屬性未閉合）") { runCatching { TaskParser.parse(html) } }
    }

    // MARK: - VoucherParser

    /** 原 `兌換期限[：:]\s*([^<]*?)\s*</p>`：同樣的三層量詞重疊。 */
    @Test
    fun `voucher parser expiry with unterminated whitespace`() {
        val html = FIGURE + "兌換期限：" + pad
        assertPrompt("VoucherParser.兌換期限") { runCatching { VoucherParser.parseView(html) } }
    }

    /** 原 `(\d+)\s*次`：`\d+` 每次貪婪到底再逐格回溯，O(n²)。 */
    @Test
    fun `voucher parser remaining count with a long digit run`() {
        val html = """<p class="notice notice--error">""" + digits + "</p>"
        assertPrompt("VoucherParser.剩餘次數（超長數字串）") { VoucherParser.parseVerifyError(html) }
    }

    /** 原 `<li\b[^>]*>([\s\S]*?)</li>`：大量未閉合 `<li` 的 O(n²)。 */
    @Test
    fun `voucher parser notices with many unclosed list items`() {
        val html = FIGURE + """<section class="voucher-notices">""" +
            "<li ".repeat(ADVERSARIAL_LENGTH / 4) + "</section>"
        assertPrompt("VoucherParser.notices（大量未閉合 <li）") { runCatching { VoucherParser.parseView(html) } }
    }

    /** 原 `<section\b[^>]*>`：大量未閉合 `<section` 的 O(n²)。 */
    @Test
    fun `voucher parser figures with many unclosed sections`() {
        val html = "<section ".repeat(ADVERSARIAL_LENGTH / 9)
        assertPrompt("VoucherParser.figures（大量未閉合 <section）") { runCatching { VoucherParser.parseView(html) } }
    }

    /** `voucher-meta__channel[\s\S]*?<span…`：跨標籤的惰性掃描，重複出現的錨點會放大成本。 */
    @Test
    fun `voucher parser channel with repeated anchors`() {
        val anchor = "voucher-meta__channel"
        val html = FIGURE + anchor.repeat(ADVERSARIAL_LENGTH / anchor.length)
        assertPrompt("VoucherParser.voucher-meta__channel（重複錨點）") {
            runCatching { VoucherParser.parseView(html) }
        }
    }

    // MARK: - CsrfParser

    /** 原 `<input\b[^>]*\bname…`：大量未閉合 `<input` 的 O(n²)（iOS 端 56 KB 實測 6.7 秒）。 */
    @Test
    fun `csrf parser with many unclosed inputs`() {
        val html = "<input ".repeat(ADVERSARIAL_LENGTH / 7)
        assertPrompt("CsrfParser（大量未閉合 <input）") { runCatching { CsrfParser.extract(html) } }
    }

    /** 有 `name="_csrf"` 但 value 屬性永遠閉不起來。 */
    @Test
    fun `csrf parser with an unterminated value attribute`() {
        val html = """<input name="_csrf" value="""" + "a".repeat(ADVERSARIAL_LENGTH)
        assertPrompt("CsrfParser（value 屬性未閉合）") { runCatching { CsrfParser.extract(html) } }
    }

    // MARK: - RedeemParser

    /** 原 `<form\b[^>]*>`：大量未閉合 `<form` 的 O(n²)（iOS 端 48 KB 實測 1.9 秒）。 */
    @Test
    fun `redeem parser with many unclosed forms`() {
        val html = "<form ".repeat(ADVERSARIAL_LENGTH / 6)
        assertPrompt("RedeemParser（大量未閉合 <form）") { runCatching { RedeemParser.parse(html) } }
    }

    /** 合法的 `item-row__form` 開頭 + 大量未閉合 `<input`（打的是 hiddenInputValue）。 */
    @Test
    fun `redeem parser with many unclosed inputs inside a form`() {
        val html = """<form class="item-row__form" data-vendor-name="V" data-item-name="I">""" +
            "<input ".repeat(ADVERSARIAL_LENGTH / 7)
        assertPrompt("RedeemParser.hiddenInputValue（大量未閉合 <input）") {
            runCatching { RedeemParser.parse(html) }
        }
    }

    // MARK: - 正常 HTML 的解析結果不能被上面的修改弄壞
    //
    // 既有的 *ParserTest 已經覆蓋 fixture 的完整解析；這裡只補一個「全空白欄位」
    // 的邊界：舊樣式 `\s*([^<]+?)\s*<` 會回傳一個空白字元，新樣式 trim 後視為沒有值。

    @Test
    fun `whitespace only remaining text is treated as absent`() {
        val html = """
            <ul class="period-list">
            <li class="period-card">
            <span class="period-no">第 1 期</span>
            <span class="period-remaining">   </span>
            <p class="period-state period-state--NOT_UPLOADED"><span>尚未上傳</span></p>
            </li>
            </ul>
        """.trimIndent()

        assertNull(TaskParser.parse(html).first().remainingText, "只有空白的欄位應視同沒有值")
    }

    @Test
    fun `remaining text is trimmed but preserves inner spacing`() {
        // 空的 `period-range` 是卡片骨架標記（理由同 CARD_OPEN）：沒有它這塊不會被當成卡片。
        val html = """
            <ul class="period-list">
            <li class="period-card">
            <span class="period-range"></span>
            <span class="period-remaining">
              剩 1 天 22 小時
            </span>
            </li>
            </ul>
        """.trimIndent()

        assertEquals("剩 1 天 22 小時", TaskParser.parse(html).first().remainingText)
    }

    // MARK: - 共用止血點：response body 大小上限

    @Test
    fun `body size under the limit is accepted`() {
        OkHttpHttpClient.validateBodySize(OkHttpHttpClient.MAX_RESPONSE_BYTES)
        OkHttpHttpClient.validateBodySize(0)
    }

    @Test
    fun `body size over the limit throws response too large`() {
        val tooBig = OkHttpHttpClient.MAX_RESPONSE_BYTES + 1
        val error = runCatching { OkHttpHttpClient.validateBodySize(tooBig) }.exceptionOrNull()
        assertEquals(AppError.ResponseTooLarge(tooBig), assertNotNull(error))
    }

    /** 上限本身也要被測到——調小它等於放大所有 parser 的攻擊面。 */
    @Test
    fun `max response bytes is two megabytes`() {
        assertEquals(2L * 1024 * 1024, OkHttpHttpClient.MAX_RESPONSE_BYTES)
    }

    /**
     * 即使有人把上限放寬到 2 MB 的邊界值，parser 也還是要撐得住。
     * 這是「regex 修好了」的最終證明：滿載 2 MB 對抗輸入仍在預算內返回
     * （iOS 端修好之前光是 1.6 KB 就要 34–81 秒）。
     *
     * 門檻取 4.0 秒：JVM 的 `java.util.regex` 沒有 JIT 暖機保證，第一輪成本明顯高於後續，
     * 加上 CI 機器可能被排到效率核。實測遠低於此；真正把「線性」釘死的是下面那條
     * [parser cost grows linearly with input size]。
     */
    @Test
    fun `parsers survive a full size adversarial body`() {
        val inputs = adversarialInputs(OkHttpHttpClient.MAX_RESPONSE_BYTES.toInt())
        // rounds 用 2 不用 3：這份工作每輪成本不低，多跑一輪只為抗噪不划算。
        assertCpuBudget(4.0, "滿載 2 MB 對抗輸入", rounds = 2, hint = "檢查最近改動的樣式。") {
            parseAll(inputs)
        }
    }

    /**
     * 真正的 ReDoS 防線：成本必須**隨輸入長度線性成長**。
     *
     * 絕對秒數會隨機器速度與 JIT 狀態浮動，成長率不會：輸入放大 8 倍，線性樣式的成本就是
     * 8 倍上下，O(n²) 是 64 倍，O(n³) 是 512 倍。這條斷言不管機器多快多慢都成立，
     * 所以它是這一整組測試裡唯一不需要「訂一個秒數」的一條。
     *
     * 上限取 3 倍線性：離 O(n²) 的 64 倍還差得遠，中間不存在會被誤判的形狀。
     *
     * 尺寸取 1/16 與 1/2 上限而不是直接用滿 2 MB：成長率不需要跑到邊界也量得出來。
     *
     * 兩個尺寸**交錯**量測（每輪都先小後大），是為了讓兩邊經歷相同的核心頻率與 JIT 狀態；
     * 各自量一次的話，小的那次剛好在 JIT 暖機期就會把比值灌大成偽陽性。
     */
    @Test
    fun `parser cost grows linearly with input size`() {
        val smallBytes = (OkHttpHttpClient.MAX_RESPONSE_BYTES / 16).toInt() // 128 KB
        val largeBytes = (OkHttpHttpClient.MAX_RESPONSE_BYTES / 2).toInt() // 1 MB，= 8 倍
        val small = adversarialInputs(smallBytes)
        val large = adversarialInputs(largeBytes)

        // 先暖機一輪再開始量，讓 JIT 對兩個尺寸一視同仁。
        parseAll(small)
        parseAll(large)

        var smallCost = Double.MAX_VALUE
        var largeCost = Double.MAX_VALUE
        repeat(2) {
            smallCost = minOf(smallCost, CpuClock.measure { parseAll(small) })
            largeCost = minOf(largeCost, CpuClock.measure { parseAll(large) })
        }

        // 分母太小的話比值會被雜訊主宰。
        assertTrue(smallCost > 0, "CPU 時鐘沒有前進，量測本身壞了")

        val sizeRatio = largeBytes.toDouble() / smallBytes.toDouble()
        val costRatio = largeCost / smallCost
        assertTrue(
            costRatio < 3 * sizeRatio,
            "輸入放大 $sizeRatio 倍，成本卻放大了 $costRatio 倍" +
                "（$smallBytes bytes → $smallCost 秒、$largeBytes bytes → $largeCost 秒）。" +
                "線性樣式應該落在 $sizeRatio 倍上下；超過三倍代表有樣式退回成超線性——" +
                "檢查是不是有相鄰量詞的字元集合重疊、量詞少了長度上限，" +
                "或標籤屬性用了 `[^>]` 而不是 `[^<>]`。",
        )
    }

    /** 四個 parser 各自最壞形狀的對抗輸入。刻意在計時範圍外建好：產生字串是測試自己的成本。 */
    private data class AdversarialInputs(
        val task: String,
        val csrf: String,
        val redeem: String,
        val voucher: String,
    )

    private fun adversarialInputs(bytes: Int) = AdversarialInputs(
        task = CARD_OPEN + """<span class="period-remaining">""" + " ".repeat(bytes),
        csrf = "<input ".repeat(bytes / 7),
        redeem = "<form ".repeat(bytes / 6),
        voucher = FIGURE + "兌換期限：" + " ".repeat(bytes),
    )

    private fun parseAll(inputs: AdversarialInputs) {
        runCatching { TaskParser.parse(inputs.task) }
        runCatching { CsrfParser.extract(inputs.csrf) }
        runCatching { RedeemParser.parse(inputs.redeem) }
        runCatching { VoucherParser.parseView(inputs.voucher) }
    }

    private companion object {
        /** 對抗輸入的長度。遠大於任何真實欄位的量級。 */
        const val ADVERSARIAL_LENGTH = 4 * 1024

        /** 每個樣式的 CPU 成本上限。 */
        const val BUDGET = 0.1

        /**
         * 一張卡片的開頭。`TaskParser.splitCards` 只保留含有 `period-state--`／`period-range` 的塊，
         * 所以對抗輸入**必須帶一個**，否則在碰到被測的樣式之前就以 Parsing 收場——測試會綠，
         * 但什麼都沒量到。這裡放的是空的 `period-range`，不會餵給 `rangeRegex` 任何內容。
         */
        const val CARD_OPEN = """<li class="period-card"><span class="period-range"></span>"""

        /** 一個合法的 `voucher-figure`，讓 parseView 不會在抓到 figure 之前就丟錯。 */
        const val FIGURE =
            """<section class="voucher-figure" data-format="CODE_128" data-value="X"></section>"""
    }
}
