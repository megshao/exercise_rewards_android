package com.megshao.exerciserewards.telemetry

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.VendorIntroLayout
import com.megshao.exerciserewards.core.models.VoucherFigure

// MARK: - 值域列舉
//
// 這一整區的存在理由只有一個：**遙測參數的值只能來自封閉集合**。
// 任何「從官網字串轉成分類」的動作都在這裡做完，之後的 API 就再也收不到自由字串。

/**
 * 允許被送出的畫面名稱。**刻意做成封閉列舉**：畫面名不可以是自由字串，
 * 否則哪天有人寫 `screenViewed(voucher.code)` 就直接把券碼送出去了。
 * 不要為了方便加一個 `Custom(String)`——那等於把這道保護整個拆掉。
 */
public enum class ScreenName(public val raw: String) {
    WELCOME("welcome"),
    DISCLAIMER("disclaimer"),
    ONBOARDING_FORM("onboarding_form"),
    HOME("home"),
    TASKS("tasks"),
    WALLET("wallet"),
    PROFILE("profile"),
    UPLOAD("upload"),
    SCREENSHOT("screenshot"),
    REDEEM("redeem"),
    VENDOR_INTRO("vendor_intro"),
    VOUCHER("voucher"),
}

/** 登入是從哪裡觸發的。 */
public enum class LoginTrigger(public val raw: String) {
    /** Onboarding 表單的「送出並驗證」。 */
    ONBOARDING("onboarding"),

    /** 冷啟動時的自動登入。 */
    AUTO("auto"),

    /** 首頁「重新登入」按鈕。 */
    MANUAL("manual"),
}

/**
 * 失敗原因分類。由 [Telemetry.classify] 從 [AppError] 映射，**只取型別不取內容**——
 * `BlockedEgress` 的 host、`Parsing` 的訊息、`Network` 的字串全部丟掉。
 */
public enum class FailReason(public val raw: String) {
    INVALID_CREDENTIALS("invalid_credentials"),
    NOT_REGISTERED("not_registered"),
    NETWORK("network"),
    SITE_STATUS("site_status"),
    SITE_PARSE("site_parse"),
    CSRF_MISSING("csrf_missing"),
    BLOCKED_EGRESS("blocked_egress"),
    REDIRECT_LOOP("redirect_loop"),

    /**
     * 官網回應的 body 超過 2 MB 上限。
     * **只送這個分類，不送實際位元組數**——長度是關於回應內容的測量值。
     */
    RESPONSE_TOO_LARGE("response_too_large"),

    /**
     * 解析失敗但**很可能只是 session 過期**（官網 302 到登入頁，回的是登入頁 HTML，
     * 解析器一樣丟 Parsing）。冷啟動時的第一次抓取歸這一類，不視為官網改版。
     */
    SESSION_PROBABLE("session_probable"),
    UNKNOWN("unknown"),
}

/** 端點**樣板**。永遠是樣板，永遠不含 UUID——這就是它是列舉而不是 String 的原因。 */
public enum class Endpoint(public val raw: String) {
    ACCESS("access"),
    LOGIN("login"),
    LOGOUT("logout"),
    TASKS("tasks"),
    UPLOAD("upload"),
    SCREENSHOT("screenshot"),
    REDEEM("redeem"),
    VENDOR_INTRO("vendor_intro"),
    VOUCHER("voucher"),
    VOUCHER_RESEND("voucher_resend"),
    VOUCHER_VIEW("voucher_view"),
}

/** 任務抓取是從哪個畫面／時機觸發的。 */
public enum class TasksSource(public val raw: String) {
    HOME_BOOTSTRAP("home_bootstrap"),
    HOME_REFRESH("home_refresh"),
    POST_LOGIN("post_login"),
    TASKS_TAB("tasks_tab"),
    WALLET("wallet"),
}

/**
 * 合作商家的分類。
 *
 * **由公開的商家名稱分類出來**，不是 `vendorId`／`itemId`（那是官網識別碼），
 * 也不是 `itemName`（官網文字）。認不出來的商家（[OTHER]）不是壞事——
 * 那正是「官網新增合作店家」的正常樣子。
 *
 * **只有這一份比對表。** iOS 端曾經 logo 與遙測各有一份 `contains` 判斷，結果漂掉了：
 * logo 認得萬家福／樂家康，遙測卻把它們算成 other。要新增商家就只改這裡。
 */
public enum class Vendor(public val raw: String) {
    FAMILY_MART("family_mart"),
    SEVEN_ELEVEN("seven_eleven"),
    HILIFE("hilife"),
    PXMART("pxmart"),
    WANJIAFU("wanjiafu"),
    OTHER("other"),
    ;

    public companion object {
        /** @param vendorName 官網回傳的商家名稱。**只用來做分類，不會被送出。** */
        public fun of(vendorName: String): Vendor = when {
            vendorName.contains("全家") -> FAMILY_MART
            vendorName.contains("7-11") || vendorName.contains("7-eleven", ignoreCase = true) -> SEVEN_ELEVEN
            vendorName.contains("萊爾富") -> HILIFE
            vendorName.contains("全聯") -> PXMART
            vendorName.contains("萬家福") || vendorName.contains("樂家康") -> WANJIAFU
            else -> OTHER
        }
    }
}

/** 券碼頁是從哪裡打開的。 */
public enum class VoucherSource(public val raw: String) {
    WALLET("wallet"),
    TASKS("tasks"),
    REDEEM_RESULT("redeem_result"),
}

/** 條碼符號集分類。**只送格式，絕不送 `VoucherFigure.value`（券碼本身）。** */
public enum class BarcodeFormat(public val raw: String) {
    CODE_128("code_128"),
    QR_CODE("qr_code"),
    AZTEC("aztec"),
    PDF_417("pdf_417"),
    MIXED("mixed"),
    OTHER("other"),
    ;

    public companion object {
        public fun of(format: String): BarcodeFormat = when (format.uppercase()) {
            "CODE_128" -> CODE_128
            "QR_CODE" -> QR_CODE
            "AZTEC" -> AZTEC
            "PDF_417", "PDF417" -> PDF_417
            else -> OTHER
        }

        /** 一張券可能是兩段式且格式不同，那就送 [MIXED]。 */
        public fun of(figures: List<VoucherFigure>): BarcodeFormat {
            val kinds = figures.map { of(it.format) }.toSet()
            return kinds.singleOrNull() ?: if (kinds.isEmpty()) OTHER else MIXED
        }
    }
}

/** Onboarding 表單哪一欄格式不對。**不送長度、不送第一碼、不送任何使用者輸入的字元。** */
public enum class OnboardingField(public val raw: String) {
    EMPTY("empty"),
    ID_NO("id_no"),
    BIRTH_DATE("birth_date"),
    PHONE("phone"),
}

public enum class UploadPickOutcome(public val raw: String) {
    PICKED("picked"),
    UNREADABLE("unreadable"),
}

public enum class UploadOutcome(public val raw: String) {
    SUBMITTED("submitted"),
    WINDOW_CLOSED("window_closed"),
    CSRF_MISSING("csrf_missing"),
    SITE_REJECTED("site_rejected"),
    HTTP_ERROR("http_error"),
    NETWORK("network"),
    UNKNOWN("unknown"),
}

public enum class ScreenshotOutcome(public val raw: String) {
    OK("ok"),
    NO_ID("no_id"),
    URL_FAILED("url_failed"),
    IMAGE_FAILED("image_failed"),
}

public enum class ListOutcome(public val raw: String) {
    OK("ok"),
    EMPTY("empty"),
    ERROR("error"),
}

public enum class RedeemOutcome(public val raw: String) {
    SUBMITTED("submitted"),
    STAYED_ON_PAGE("stayed_on_page"),
    NETWORK("network"),
    HTTP_ERROR("http_error"),
    UNKNOWN("unknown"),
}

public enum class OtpSendOutcome(public val raw: String) {
    OK("ok"),
    ERROR("error"),
}

public enum class OtpVerifyOutcome(public val raw: String) {
    SUCCESS("success"),
    WRONG_CODE("wrong_code"),
    EXHAUSTED("exhausted"),
    FAILED("failed"),
    ERROR("error"),
}

public enum class VoucherRevealOutcome(public val raw: String) {
    OK("ok"),
    PARSE_ERROR("parse_error"),
    ERROR("error"),
}

public enum class SimpleOutcome(public val raw: String) {
    OK("ok"),
    ERROR("error"),
}

/** 非致命錯誤（Crashlytics）的分類。 */
public enum class TelemetryIssue(public val raw: String) {
    TASKS_PARSE("tasks_parse"),
    TASKS_STATUS("tasks_status"),
    UPLOAD("upload"),
    REDEEM_INTRO_MISSING("redeem_intro_missing"),
    VENDOR_INTRO_LAYOUT("vendor_intro_layout"),
    BARCODE("barcode"),
    PROFILE_STORE("profile_store"),
    CACHE_DECODE("cache_decode"),
}

/** 廠商商品頁的版型分類（給非致命錯誤的 extras 用）。 */
internal fun VendorIntroLayout.telemetryCode(): String = raw
