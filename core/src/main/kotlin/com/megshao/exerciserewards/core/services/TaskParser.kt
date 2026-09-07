package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.TaskPeriod
import com.megshao.exerciserewards.core.models.TaskState

/**
 * 解析 `/member/tasks` 頁面的 HTML，取出 14 期任務卡片。純函式、無副作用、無網路呼叫。
 *
 * 每一期為一張 `<li class="period-card ...">` 卡片，內含：
 * - `.period-no`：第 N 期
 * - `.period-range`：yyyy/MM/dd ~ yyyy/MM/dd
 * - `.period-state--XXX`：後端算好的互斥狀態（見 [TaskState]）
 * - `.period-remaining`：剩餘時間文字（可能沒有）
 * - `.period-detail` 內的上傳／審核時間（可能沒有）
 * - `.period-voucher` 內的「兌換內容：通路／品項」（只有已兌換的期別有）
 * - 兌換 `/member/redeem/{uuid}`、截圖 `/member/screenshot/{uuid}` 或券碼
 *   `/member/voucher/{uuid}` 連結中的期別 UUID（可能沒有）
 */
public object TaskParser {

    /**
     * 解析整頁 HTML，回傳依卡片出現順序排列的任務清單。
     *
     * @throws AppError.Parsing 當頁面內完全找不到任何 `period-card` 卡片時
     * （代表頁面結構跟預期不符）。UI 層拿到這個錯誤會走 [SiteHandoff] 的交接畫面。
     */
    public fun parse(html: String): List<TaskPeriod> {
        val cards = splitCards(html)
        if (cards.isEmpty()) {
            throw AppError.Parsing("no period-card found in /member/tasks HTML")
        }
        return cards.mapIndexed { offset, card -> parseCard(card, fallbackIndex = offset + 1) }
    }

    /**
     * 把整份 HTML 依卡片的 `<li>` 開頭切成一張一張的原始片段。每一塊從標記開始，
     * 到下一個標記／`</ul>`／文件尾為止（切法與 [RedeemParser] 相同）。
     *
     * 只保留**真的含有卡片內容**（`period-state--` 或 `period-range`）的塊——官網每一期都有這
     * 兩樣，頁面上若有別的 `<li>` 剛好帶著 `period-card` 這個 token（導覽、圖例），不會被當成一期。
     *
     * **為什麼用正則而不是比對 `<li class="period-card"` 這個完整字串**（這裡改過一次，
     * 與 [RedeemParser.splitRowBlocks] 是同一類問題）：完整字串比對把 class 屬性的**寫法**也當成
     * 契約的一部分。官網只要調換 class 順序（`class="card period-card"`）、在 `<li>` 上多加一個
     * 排在 class 前面的屬性、改用單引號、或 `=` 兩側多一個空白，就會**一張卡都切不到**——
     * [parse] 丟出 [AppError.Parsing]，整個任務頁死掉，而官網的 DOM 契約其實一個字都沒變。
     * [RedeemParser] 那邊同樣的失敗還有退回路徑可以撐住，這裡沒有，所以更該把邊界改成
     * 「class 裡有 `period-card` 這個 token」。
     *
     * `\bperiod-card\b` 的邊界行為：`period-card--current` 會被匹配（`-` 不是 word char），
     * `period-card__title` 不會（`_` 是 word char，`\b` 擋掉）——子元素不會被誤切成一張卡。
     */
    private fun splitCards(html: String): List<String> {
        val starts = periodCardOpenTagRegex.findAll(html).map { it.range.first }.toList()
        if (starts.isEmpty()) return emptyList()

        val cards = mutableListOf<String>()
        for ((offset, cardStart) in starts.withIndex()) {
            val cardEnd = when {
                offset + 1 < starts.size -> starts[offset + 1]
                else -> html.indexOf("</ul>", cardStart).takeIf { it >= 0 } ?: html.length
            }
            val card = html.substring(cardStart, cardEnd)
            if (card.contains("period-state--") || card.contains("period-range")) {
                cards.add(card)
            }
        }
        return cards
    }

    // MARK: - Patterns
    //
    // ReDoS 防線：這些樣式吃的是官方站回傳的 HTML，屬**不受信任輸入**。
    // 三條規則，違反其中任一條都可能讓單一請求把整個 IO dispatcher 卡死：
    // 1. 相鄰量詞的字元集合不得重疊。`\s*([^<]+?)\s*<` 就是反例——`\s` ⊂ `[^<]`，
    //    三層量詞互相回溯，iOS 端實測 800 個空白要 4 秒、1600 個要 34 秒（O(n³)）。
    //    正解是 `([^<]*)<`：`[^<]` 貪婪一定停在第一個 `<`，零回溯，事後再 trim。
    // 2. 所有量詞都要有長度上限（`{0,N}`），別讓單次比對的成本跟整頁長度成正比。
    // 3. 標籤屬性用 `[^<>]` 而非 `[^>]`——屬性內不可能出現裸 `<`，排除它可讓
    //    「大量未閉合標籤」的攻擊在下一個 `<` 就停住，而不是掃到文件尾。
    //
    // 另一道獨立防線在 OkHttpHttpClient：response body 超過 2 MB 直接丟
    // AppError.ResponseTooLarge，parser 根本不會看到超長輸入。

    /**
     * 卡片的開頭標籤。**刻意不比對完整字串** `<li class="period-card"`——見 [splitCards]。
     * 樣式字串與 [RedeemParser] 的 `itemRowOpenTagRegex` 逐字相同（只換 token），
     * 也與 iOS 端的 `TaskParser.swift` 一致，方便跨 repo grep 對照。
     */
    private val periodCardOpenTagRegex =
        compile("""<li\b[^<>]{0,400}\bclass\s{0,8}=\s{0,8}["'][^"']{0,300}\bperiod-card\b[^"']{0,300}["'][^<>]{0,400}>""")

    private val indexRegex = compile("""第\s{0,8}(\d{1,6})\s{0,8}期""")
    private val rangeRegex = compile("""period-range">\s{0,8}([0-9/]{1,40})\s{0,8}~\s{0,8}([0-9/]{1,40})""")
    private val stateRegex = compile("""period-state--([A-Z_]{1,40})""")
    private val remainingRegex = compile("""period-remaining">([^<]{0,2000})<""")
    private val uploadedAtRegex = compile("""上傳時間：([^<]{0,2000})<""")
    private val reviewedAtRegex = compile("""審核時間：([^<]{0,2000})<""")
    private val voucherSummaryRegex = compile("""兌換內容：([^<]{0,2000})<""")

    // 已兌換的期別在官網卡片上只剩 voucher／screenshot 連結（沒有 redeem 連結了），
    // 所以 UUID 必須也認 `voucher`，否則已兌換那期會抓不到 id。
    private val idRegex = compile("""/member/(?:redeem|screenshot|voucher)/([0-9a-fA-F-]{1,64})""")

    private fun compile(pattern: String): Regex = Regex(pattern, RegexOption.DOT_MATCHES_ALL)

    /** 解析單張卡片片段。所有欄位都容錯：抓不到就給合理預設值，不丟例外。 */
    private fun parseCard(card: String, fallbackIndex: Int): TaskPeriod {
        val index = indexRegex.find(card)?.groupValues?.get(1)?.toIntOrNull() ?: fallbackIndex

        var startDate = ""
        var endDate = ""
        rangeRegex.find(card)?.let { match ->
            startDate = match.groupValues[1]
            endDate = match.groupValues[2]
        }

        val state = mapState(stateRegex.find(card)?.groupValues?.get(1) ?: "")

        // `([^<]*)` 連同前後空白一起抓，再 trim——語意等同原本的 `\s*([^<]+?)\s*`，
        // 但沒有量詞重疊。全空白的欄位 trim 後為空字串，視同「沒有這個欄位」回傳 null。
        val remainingText = trimmedMatch(card, remainingRegex)
        val uploadedAt = trimmedMatch(card, uploadedAtRegex)
        val reviewedAt = trimmedMatch(card, reviewedAtRegex)
        val voucherSummary = trimmedMatch(card, voucherSummaryRegex)

        return TaskPeriod(
            id = idRegex.find(card)?.groupValues?.get(1) ?: "",
            index = index,
            startDate = startDate,
            endDate = endDate,
            state = state,
            remainingText = remainingText,
            uploadedAt = uploadedAt,
            reviewedAt = reviewedAt,
            voucherSummary = voucherSummary?.let(HtmlEntities::decode),
        )
    }

    private fun mapState(raw: String): TaskState = when (raw) {
        "NOT_STARTED" -> TaskState.NOT_STARTED
        // 官網對應狀態：可上傳／尚未上傳的當期 state 是 NOT_UPLOADED（徽章「尚未上傳」）。
        "OPEN", "NOT_UPLOADED" -> TaskState.OPEN
        // 官網對應狀態：待審核的 class 是 UNDER_REVIEW。PENDING_REVIEW 一併容錯。
        "UNDER_REVIEW", "PENDING_REVIEW" -> TaskState.PENDING_REVIEW
        "REDEEMABLE" -> TaskState.REDEEMABLE
        "REDEEMED" -> TaskState.REDEEMED
        else -> TaskState.UNKNOWN
    }

    /** 取第一個 capture group、去掉前後空白；trim 後為空字串時回傳 null。 */
    private fun trimmedMatch(text: String, regex: Regex): String? =
        regex.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}
