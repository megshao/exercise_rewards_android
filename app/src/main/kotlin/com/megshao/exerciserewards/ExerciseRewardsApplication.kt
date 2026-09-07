package com.megshao.exerciserewards

import android.app.Application
import com.megshao.exerciserewards.core.security.SecureLog
import com.megshao.exerciserewards.data.DisclaimerConsent
import com.megshao.exerciserewards.data.LogcatSink
import com.megshao.exerciserewards.telemetry.Telemetry

public class ExerciseRewardsApplication : Application() {

    public lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // log 出口：release build 只留 error（見 LogcatSink）。
        SecureLog.sink = LogcatSink(isDebugBuild = BuildConfig.DEBUG)
        SecureLog.sinkWantsDebug = BuildConfig.DEBUG

        container = AppContainer(this)

        Telemetry.isDemoMode = container.preferences.isDemoMode

        // **Firebase 的初始化綁在免責聲明的同意之後。**
        // 還沒同意就完全不初始化——不是「初始化了但不送」，是連 SDK 都不啟動。
        // manifest 另外把 `FirebaseInitProvider` 拆掉了，否則那個 ContentProvider 會在
        // 這個方法之前就先初始化掉（見 AndroidManifest.xml 的註解）。
        //
        // 這是 README「不用懂程式也驗得到」那一條的實作面保證：同意之前用系統的
        // 網路檢視工具看不到任何 Google 網域。
        if (container.preferences.disclaimerAgreedVersion >= DisclaimerConsent.CURRENT_VERSION) {
            Telemetry.configure(this, userEnabled = container.preferences.isTelemetryEnabled)
        }
    }
}
