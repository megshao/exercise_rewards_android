package com.megshao.exerciserewards.data

import android.content.Context
import com.megshao.exerciserewards.core.models.Profile
import com.megshao.exerciserewards.core.models.ProfileCodec
import com.megshao.exerciserewards.core.security.ProfileStoring

/**
 * 個資的本機落地。對應 iOS 端的 `KeychainStore`。
 *
 * **只存這一份，而且只存在這台裝置上。** 開發者沒有任何自建後端，這些欄位唯一離開裝置的
 * 時機是使用者按下登入時由裝置直送 `500.gov.tw`。
 */
public class KeystoreProfileStore(
    context: Context,
) : ProfileStoring {

    private val store = SecureBlobStore(context, FILE_NAME)

    override fun save(profile: Profile) {
        store.write(ProfileCodec.encode(profile))
    }

    override fun load(): Profile? = store.read()?.let(ProfileCodec::decode)

    override fun clear() {
        store.clear()
    }

    private companion object {
        const val FILE_NAME = "profile.enc"
    }
}
