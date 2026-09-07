package com.megshao.exerciserewards.core.models

import kotlinx.serialization.Serializable

/**
 * 使用者個資，只會存在裝置本機（Android Keystore 加持的 EncryptedSharedPreferences，
 * 見 `:app` 的 `KeystoreProfileStore`）。**切勿記錄到 log、切勿進遙測。**
 */
@Serializable
public data class Profile(
    public val name: String = "",
    /** 身分證號 e.g. A123456789 */
    public val idNo: String = "",
    /** ISO yyyy-MM-dd */
    public val birthDate: String = "",
    /** 09xxxxxxxx */
    public val phone: String = "",
    public val email: String = "",
    /** 健保卡卡號（首次註冊用） */
    public val nhiCardNo: String = "",
)

/** 登入所需三碼（登入無 OTP）。 */
public data class LoginCredentials(
    public val idNo: String,
    /** ISO yyyy-MM-dd */
    public val birthDate: String,
    public val phone: String,
)

public enum class TaskState {
    /** 尚未開始 */
    NOT_STARTED,

    /** 可上傳 */
    OPEN,

    /** 待審核 */
    PENDING_REVIEW,

    /** 任務完成，可兌換 */
    REDEEMABLE,

    /** 已兌換 */
    REDEEMED,

    UNKNOWN,
    ;

    /**
     * 這個狀態下，官網的 `period-remaining` 還有意義嗎？
     *
     * **官網那個欄位講的是「上傳窗」倒數**，文字是「本期任務可上傳時間 剩 N 小時 N 分」，
     * 而且**對已經走完審核的期別照樣回傳**——實測（2026-09-06）一張已兌換的券，
     * 卡片上仍寫著「本期任務可上傳時間 剩 3 小時 20 分」。官網頁面自己的註解也寫明
     * 「兌換窗是這一期自己的，與上傳窗無關」。
     *
     * 審核完成之後（可兌換／已兌換）上傳早就做完了，那個倒數指的是一個用不到的窗；
     * 照著顯示只會讓使用者以為還有東西要上傳。因此這兩個狀態一律不顯示。
     *
     * 待審核（[PENDING_REVIEW]）刻意**保留**：上傳窗還開著時，剩餘時間對「審核沒過還能不能
     * 重新上傳」仍然是有用的資訊。
     */
    public val showsUploadCountdown: Boolean
        get() = when (this) {
            REDEEMABLE, REDEEMED -> false
            NOT_STARTED, OPEN, PENDING_REVIEW, UNKNOWN -> true
        }
}

/** 登入結果分流。 */
public enum class LoginOutcome {
    /** 302 -> /member/tasks */
    SUCCESS,

    /** access 導向 /register（此身分證未註冊） */
    NOT_REGISTERED,

    /** 三碼不符，回登入頁 */
    INVALID_CREDENTIALS,
}

/**
 * 兌換頁（`/member/redeem/{uuid}`）解析出的一個可兌換品項（商家 + 品項）。
 */
public data class RedeemOption(
    public val vendorId: String,
    public val vendorName: String,
    public val itemName: String,
    public val itemId: String,
    /**
     * 該列「兌換品項」連結指向的廠商商品頁，已正規化成 base-relative path
     * （例如 `/intro/vendor-1.html`）。
     *
     * **null 是正常狀況**：官網的規則是「靜態頁 `intro/vendor-{id}.html` 存在才長出這個
     * 連結」，沒有另一份設定可以跟它不同步。因此這裡不自己用 [vendorId] 拼網址——
     * 拼出來的網址在官網沒有那一頁時會是 404，而解析不到就代表官網也沒給。
     */
    public val introPath: String? = null,
) {
    public val id: String get() = "$vendorId-$itemId"
}

/**
 * 廠商商品頁的版型。
 *
 * **為什麼要把它變成資料的一部分**：[UNRECOGNISED] 是「官網換版型了」這件事唯一的訊號。
 * 舊做法是兩種版型都對不上就丟 [AppError.Parsing]——警報有了，但使用者只看到一個錯誤畫面。
 * 現在改成退到純文字仍然給出內容，代價是**失敗不再自動變成例外**，
 * 所以這個欄位就是要求呼叫端自己回報的那份契約。
 */
public enum class VendorIntroLayout(public val raw: String) {
    /** `<details data-category>` 分類卡 + `<li data-name>` 逐項清單（全家／7-11／萊爾富）。 */
    ITEM_LIST("item_list"),

    /** `類別名稱 / 商品名稱（列舉）` 表格（全聯／萬家福・樂家康）。 */
    CATEGORY_TABLE("category_table"),

    /** 兩種都對不上，靠 `<li>`／`<p>` 純文字兜出來的最小可用結果。 */
    UNRECOGNISED("unrecognised"),
}

/**
 * 廠商可兌換商品頁（`/intro/vendor-{id}.html`）解析出的內容。
 *
 * 官網這幾頁有**兩種版型**，兩種都要吃（見 [VendorIntroLayout]）。
 * 兩者共用 [VendorIntroCategory]：逐項版填 `items`，列舉版填 `examples`。
 * **刻意不把 `examples` 拆成 `items`**——官網那欄本來就是「舉例」不是完整清單，
 * 拆開會讓使用者以為那就是全部。
 */
public data class VendorIntro(
    /** 頁面主標，例如「全家便利商店可兌換商品」。 */
    public val title: String,
    /** 主標下方的說明。可能沒有。 */
    public val subtitle: String?,
    public val categories: List<VendorIntroCategory>,
    /** 頁尾「兌換注意事項」的每一段。 */
    public val notices: List<String>,
    /**
     * 這一頁是用哪一種版型解析出來的。[VendorIntroLayout.UNRECOGNISED] 代表兩種已知版型都
     * 對不上、靠純文字兜出來的——呼叫端**必須**為它發警報。
     */
    public val layout: VendorIntroLayout = VendorIntroLayout.ITEM_LIST,
)

/** 商品頁上的一個分類。 */
public data class VendorIntroCategory(
    /** 分類名稱，例如「Let's Café」「冷藏鮮乳」。 */
    public val name: String,
    /** 逐項列出的品項（只有逐項版的頁面有）。 */
    public val items: List<String> = emptyList(),
    /** 官網原文的舉例字串（只有列舉版的頁面有），例如「光泉低脂鮮乳、林鳳營高品質鮮乳等」。 */
    public val examples: String? = null,
    /**
     * 官網自己標的品項數（「54 項」）。**以官網為準，不用 `items.size` 取代**——
     * 兩者不一致時代表解析漏了東西，是個看得見的訊號。
     */
    public val statedCount: Int? = null,
    /** 是不是「全部品項」那張彙總卡（官網用 `class="... all-items"` 標記）。 */
    public val isAllItems: Boolean = false,
) {
    public val id: String get() = name
}

/**
 * 送出兌換申請的結果。best-effort：官網送出兌換表單後還要走一次簡訊 OTP 才會出示券碼，
 * 該流程由 `VoucherServicing` 另行處理，因此這裡只能回報表單是否成功送出、附上友善訊息。
 */
public data class RedeemResult(
    public val submitted: Boolean,
    public val message: String,
)

/**
 * 券碼頁（`/member/voucher/{uuid}/view`）中一段 `.voucher-figure`。
 *
 * [format] 保留原始字串（"CODE_128" / "QR_CODE"），由 App 端分流成不同的條碼產生器。
 * 萊爾富超值商品券為兩段式（商品條碼＋券號條碼），兩段缺一不可，因此 [Voucher.figures]
 * 是陣列而非單一值。
 */
public data class VoucherFigure(
    public val format: String,
    public val value: String,
    public val caption: String,
)

/**
 * 通過 OTP 驗證後看到的加碼券內容。依合規要求，這個型別**絕不可被快取**——
 * 每次進入券碼畫面都要重新走一次 OTP 驗證才能取得。
 */
public data class Voucher(
    public val vendorName: String,
    public val itemName: String,
    public val expiry: String,
    public val figures: List<VoucherFigure>,
    public val notices: List<String>,
)

/** 驗證簡訊 OTP 的結果分流。 */
public sealed interface VoucherOtpResult {
    /** 驗證通過（302 -> `.../view`）。 */
    public data object Success : VoucherOtpResult

    /** 驗證碼錯誤，回同一頁附錯誤訊息；[remaining] 是解析到的剩餘可再試次數（共 3 次）。 */
    public data class WrongCode(public val remaining: Int?) : VoucherOtpResult

    /** 其他非預期狀況（例如已用罄仍未收到重發提示、頁面格式不符預期）。 */
    public data class Failed(public val message: String) : VoucherOtpResult
}
