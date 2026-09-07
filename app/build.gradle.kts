import java.util.Properties
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

// MARK: - Release 簽章材料的來源
//
// **這個 repo 是公開的，所以金鑰材料一個位元組都不能進版控。** 四個值一律從
// `local.properties`（已在 .gitignore 裡）或環境變數讀，兩者都沒有就**優雅退化成產出未簽章
// 的 AAB**——任何人 clone 下來都要能跑 `./gradlew :app:bundleRelease` 檢查產物，
// 而不是被一個他不可能有的金鑰卡住。這是「開源可稽核」這個賣點的建置端對應物。
//
// `local.properties`（優先）：
//   exerciseRewards.releaseStoreFile=/絕對路徑/或相對於專案根目錄/upload-keystore.jks
//   exerciseRewards.releaseStorePassword=...
//   exerciseRewards.releaseKeyAlias=upload
//   exerciseRewards.releaseKeyPassword=...
//
// 環境變數（CI 用，local.properties 缺該項時才看）：
//   EXERCISE_REWARDS_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD
//
// **keystore 檔本身也不要放在專案目錄裡**（`*.jks`／`*.keystore` 雖然已被 .gitignore 擋掉，
// 但把它放在 repo 外面才是真的不可能誤 commit）。產生指令與備份建議見
// `docs/play-store/blockers.md`。
private val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

/** `local.properties` 優先，其次環境變數；空字串視為沒有。 */
private fun signingSecret(propertyKey: String, envKey: String): String? =
    (localProperties.getProperty(propertyKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

private val releaseStoreFile: File? =
    signingSecret("exerciseRewards.releaseStoreFile", "EXERCISE_REWARDS_STORE_FILE")
        ?.let { rootProject.file(it) }
        ?.takeIf { it.isFile }

private val releaseStorePassword = signingSecret("exerciseRewards.releaseStorePassword", "EXERCISE_REWARDS_STORE_PASSWORD")
private val releaseKeyAlias = signingSecret("exerciseRewards.releaseKeyAlias", "EXERCISE_REWARDS_KEY_ALIAS")
private val releaseKeyPassword = signingSecret("exerciseRewards.releaseKeyPassword", "EXERCISE_REWARDS_KEY_PASSWORD")

// 四項**全部**都在才算數。少一項就退化成未簽章，而不是拿半套設定去讓 AGP 在 build 到一半才炸。
private val hasReleaseSigning =
    releaseStoreFile != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

android {
    namespace = "com.megshao.exerciserewards"
    compileSdk = 37

    defaultConfig {
        // 對外的身分，一經上傳到 Play 就永久固定。
        // 刻意與 `namespace`／Kotlin package（com.megshao.exerciserewards）不同：
        // Java/Kotlin 的 package 慣例不用底線，而 applicationId 不是 Java 識別字。
        applicationId = "com.megshao.exercise_rewards"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 沒有金鑰材料時**根本不建立**這個 signingConfig——建了一個欄位全是 null 的
        // config，AGP 會在 signReleaseBundle 才丟出難讀的錯，不如一開始就沒有。
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // **刻意不設 enableV1Signing／enableV2Signing。**
                // 那兩個旗標管的是 APK 的簽章方案，而我們上傳的是 AAB（bundle 的簽章語意
                // 與 APK 不同，只用於 Play 驗證上傳者身分，真正發給使用者的 APK 由
                // Play App Signing 重新簽）。AGP 會依 minSdk 自己選對的組合，
                // 手動覆寫只會在沒有金鑰、無法實測的情況下多一個猜測。
            }
        }
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
            // 有金鑰就簽，沒有就產出未簽章 AAB（可以解開檢查，但不能上傳 Play）。
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// 未簽章的 AAB 看起來跟簽好的一模一樣（副檔名、大小都差不多），
// 一路帶到 Play Console 才被退很浪費時間，所以在產出前就講清楚。
//
// `doFirst { }` 裡的 `logger` 是 **Task 的 logger**（不是這個腳本的 Project logger），
// 所以這個 lambda 只捕獲字面字串，configuration cache 序列化得起來。
// 2026-09-07 實測：未設定金鑰時三次建置都正確印出；設好金鑰後不再出現。
if (!hasReleaseSigning) {
    tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
        doFirst {
            logger.lifecycle(
                """
                |
                |⚠️  找不到 release 簽章材料，這次會產出**未簽章**的 AAB／APK。
                |    可以解開檢查內容，但 **Play Console 不會接受**。
                |    設定方式見 app/build.gradle.kts 檔頭與 docs/play-store/blockers.md（B-2）。
                |
                """.trimMargin()
            )
        }
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
