package com.megshao.exerciserewards.data

import com.megshao.exerciserewards.core.models.Profile
import com.megshao.exerciserewards.core.security.ProfileStoring

/**
 * 示範模式專用的個資儲存：只在記憶體，App 一關就沒了，**絕不落盤、絕不進 Keystore**。
 * 預先塞好示範個資，讓審查員一進入就有完整資料可看，不必再填一次表單。
 */
public class InMemoryProfileStore(seed: Profile? = null) : ProfileStoring {

    private val lock = Any()
    private var stored: Profile? = seed

    override fun save(profile: Profile) {
        synchronized(lock) { stored = profile }
    }

    override fun load(): Profile? = synchronized(lock) { stored }

    override fun clear() {
        synchronized(lock) { stored = null }
    }
}
