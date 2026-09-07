package com.megshao.exerciserewards.core

import java.lang.management.ManagementFactory
import kotlin.test.assertTrue

/**
 * 計時測試的共用量測工具：量的是 **CPU 時間**，而且連跑幾輪取最短的一次。
 *
 * **為什麼不用牆上時間。** ReDoS 的症狀是「燒 CPU 燒到回不來」，但 `System.nanoTime()`
 * 量到的是牆上時間——裡面包含這個 process 被作業系統排開、完全沒在跑的那一段。
 * 用牆上時間當門檻，等於把「機器當下有多忙」寫進斷言裡，那正是這組測試在多 agent／CI
 * 環境下偽陽性的來源。
 *
 * `ThreadMXBean.getCurrentThreadCpuTime()` 只累計「這條執行緒真的站在 CPU 上」的時間，
 * 被排開的不計。ReDoS 回歸一定會反映在這個數字上（回溯就是在燒 CPU），旁邊有人在編譯不會。
 *
 * **為什麼還要取多輪最小值。** CPU 時間仍會被核心頻率影響：同一份工作被排到 Apple Silicon
 * 的效率核上，CPU 時間會膨脹到 3–4 倍。連跑幾輪取最短的一次等於取「沒被干擾時的成本」——
 * 干擾只會讓數字變大，不會讓它變小，所以最小值是這裡唯一穩定的估計量。
 *
 * 只有效能／ReDoS 回歸測試會用到它；功能測試不該有任何時間斷言。
 */
internal object CpuClock {
    private val threadMx = ManagementFactory.getThreadMXBean()

    /** 目前這條執行緒累計用掉的 CPU 時間（秒）。 */
    fun now(): Double = threadMx.currentThreadCpuTime / 1_000_000_000.0

    /** 量一次 [body] 的 CPU 成本（秒）。 */
    fun measure(body: () -> Unit): Double {
        val start = now()
        body()
        return now() - start
    }

    /** 連跑 [rounds] 輪，回傳「最短一輪的 CPU 成本」與最後一輪的回傳值。 */
    fun <T> bestOf(rounds: Int, body: () -> T): Pair<Double, T> {
        require(rounds >= 1)
        var best = Double.MAX_VALUE
        var value: T? = null
        repeat(rounds) {
            val start = now()
            value = body()
            best = minOf(best, now() - start)
        }
        @Suppress("UNCHECKED_CAST")
        return best to (value as T)
    }
}

/**
 * 斷言 [body] 的 CPU 成本在預算內；回傳最後一輪的結果，方便同一支測試接著驗行為。
 *
 * @param budget CPU 秒數上限。訂法見各呼叫端——都必須寫出實測值與餘裕的理由。
 * @param rounds 取最小值的輪數。輕量輸入用預設 3；吃滿 2 MB 的那幾條傳 2，
 *   免得為了抗噪把整包測試的時間翻倍。
 */
internal fun <T> assertCpuBudget(
    budget: Double,
    label: String,
    rounds: Int = 3,
    hint: String = "",
    body: () -> T,
): T {
    val (cpu, value) = CpuClock.bestOf(rounds, body)
    assertTrue(
        cpu < budget,
        "$label：$rounds 輪取最短仍要 $cpu 秒 CPU，超過 $budget 秒的預算。$hint",
    )
    return value
}

/** 讀取 `src/test/resources/fixtures/` 下的 HTML fixture。 */
internal fun loadFixture(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/fixtures/$name.html")) {
        "fixture $name.html not found on the test classpath"
    }.readText()
