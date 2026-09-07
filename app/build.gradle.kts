import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// AGP 9 內建 Kotlin 支援，不再需要 org.jetbrains.kotlin.android plugin
// （見 https://kotl.in/gradle/agp-built-in-kotlin）。Compose 的 compiler plugin 仍要自己套。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // 本機任務快取的檔案格式（見 data/TasksCache.kt）。
    alias(libs.plugins.kotlin.serialization)
}

// Firebase 的兩個 plugin **只在 google-services.json 存在時才套用**。
// 理由與 iOS 端相同（見 README「沒有 Firebase 設定檔也能 build」）：
// 這個 repo 會開源，設定檔不進版控；任何人 clone 下來都必須能直接 build 出可執行的 App，
// 而不是先去 Firebase Console 開一個專案。沒有設定檔時 `Telemetry` 全程 no-op。
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.megshao.exerciserewards"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.megshao.exerciserewards"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.zxing.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // :app 自己實作了一個 CookieJar（EncryptedCookieJar），所以 okhttp 要在它的 classpath 上。
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Firebase：沒有 google-services.json 時 plugin 不會被套用，這些相依仍會進 APK，
    // 但 `FirebaseApp.initializeApp` 找不到設定就回 null，`Telemetry` 全程 no-op。
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
}
