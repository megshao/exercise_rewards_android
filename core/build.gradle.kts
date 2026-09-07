import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// 刻意是純 Kotlin/JVM module，**不依賴 Android SDK**：
// 這裡的東西全部可以用 `./gradlew :core:test` 在 JVM 上 headless 跑完，
// 對應 iOS 端「`swift test` 不需要 Xcode 專案」的那條線。
kotlin {
    jvmToolchain(17)
    // 對外 API 一律要求顯式可見性與回傳型別，避免不小心把內部型別洩漏成公開介面。
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "skipped", "failed") }
}
