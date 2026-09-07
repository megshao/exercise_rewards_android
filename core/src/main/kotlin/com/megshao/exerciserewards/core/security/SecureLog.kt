package com.megshao.exerciserewards.core.security

public enum class LogCategory(public val raw: String) {
    AUTH("auth"),
    NETWORK("network"),
    TASKS("tasks"),
    REDEEM("redeem"),
    VOUCHER("voucher"),
    UI("ui"),
    SECURITY("security"),
}

/**
 * log 的實際輸出端。
 *
 * core 是純 Kotlin/JVM module，看不到 `android.util.Log`，所以真正的輸出由 `:app` 在啟動時
 * 裝上（見 `LogcatSink`）。預設是 [NoopSink]：**沒裝就什麼都不印**，
 * 而不是退回 `println` 把東西灑到 stdout。
 */
public interface LogSink {
    public fun debug(category: LogCategory, message: String)
    public fun info(category: LogCategory, message: String)
    public fun error(category: LogCategory, message: String)
}

public object NoopSink : LogSink {
    override fun debug(category: LogCategory, message: String): Unit = Unit
    override fun info(category: LogCategory, message: String): Unit = Unit
    override fun error(category: LogCategory, message: String): Unit = Unit
}

/**
 * 全專案唯一 log 入口。設計原則：
 * - 個資（身分證／生日／手機／email／健保卡／cookie／CSRF／OTP／session）一律禁止進 log。
 * - 呼叫端只能傳「已遮罩或非敏感」的訊息；提供 category 分流。
 * - [debug] 收的是 lambda：release build 下 sink 不輸出時，連字串都不會被組出來。
 */
public class SecureLog(private val category: LogCategory) {

    /** 開發用細節。release 不輸出（由 sink 決定）。 */
    public fun debug(message: () -> String) {
        if (!sinkWantsDebug) return
        sink.debug(category, message())
    }

    /** 一般事件（不得含個資）。 */
    public fun info(message: String) {
        sink.info(category, message)
    }

    /** 錯誤（不得含個資；如需帶值請先用 [Redact]）。 */
    public fun error(message: String) {
        sink.error(category, message)
    }

    public companion object {
        @Volatile
        public var sink: LogSink = NoopSink

        /**
         * debug 訊息要不要組字串。預設 false——沒裝 sink 就不必付出組字串的成本；
         * `:app` 在 debug build 裝上 sink 時一併打開。
         */
        @Volatile
        public var sinkWantsDebug: Boolean = false
    }
}
