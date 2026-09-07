package com.megshao.exerciserewards.data

import android.util.Log
import com.megshao.exerciserewards.core.security.LogCategory
import com.megshao.exerciserewards.core.security.LogSink

/**
 * [LogSink] 的 Android 實作。
 *
 * **release build 只留 error**：`debug`／`info` 在正式版一律吞掉。理由與 iOS 端的
 * `SecureLog` 相同——log 是個資最容易不小心外洩的地方，正式版沒有人在看 logcat，
 * 留著只會多一個洩漏面。
 */
internal class LogcatSink(private val isDebugBuild: Boolean) : LogSink {

    override fun debug(category: LogCategory, message: String) {
        if (!isDebugBuild) return
        Log.d(tag(category), message)
    }

    override fun info(category: LogCategory, message: String) {
        if (!isDebugBuild) return
        Log.i(tag(category), message)
    }

    override fun error(category: LogCategory, message: String) {
        Log.e(tag(category), message)
    }

    private fun tag(category: LogCategory): String = "ExRewards.${category.raw}"
}
