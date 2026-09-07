package com.megshao.exerciserewards.core.models

import kotlinx.serialization.json.Json

/**
 * [Profile] 的序列化。
 *
 * **刻意留在 core**：`:app` 的儲存實作只需要「一個字串進、一個字串出」，
 * 不必為了存個資而依賴序列化框架。哪天換掉編碼方式，也只有這一個檔案要動。
 */
public object ProfileCodec {

    private val json = Json { ignoreUnknownKeys = true }

    public fun encode(profile: Profile): String = json.encodeToString(profile)

    /** 解不開就回 null——格式變動不該讓 App 開不起來，重新輸入一次即可。 */
    public fun decode(raw: String): Profile? =
        runCatching { json.decodeFromString<Profile>(raw) }.getOrNull()
}
