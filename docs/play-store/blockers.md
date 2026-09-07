# 只有人類能做的事（依「今天開始封測」的關鍵路徑排序）

> 撰寫日期：**2026-09-07**　套件名稱：`com.megshao.exercise_rewards`
>
> 這份清單只列**我（agent）做不到、必須由你本人做**的事。已經做完的、以及我在本機處理掉的，
> 列在 §0 讓你不用重複確認。
>
> 每一項標了**預估時間**與**卡點**。⏱ 是你自己動手的時間，⏳ 是等別人（Google／測試者）的時間——
> **⏳ 才是真正決定「今天能不能開始」的東西。**


> 📌 **上傳與發版的實際操作**（gplay CLI 的連線設定、哪些只能在 Console 點、preflight 基線、發版指令）另見 [`gplay-setup.md`](gplay-setup.md)。
> 那份是沿用 `~/Personal Projects/Nyoki` 已實跑過四次的設定——**同一個 Play 開發者帳號、同一個 service account**，機器側不需要重做。

---

## 0. 已經完成的（不用再做）

| 項目 | 狀態 | 依據 |
|---|---|---|
| `:app:bundleRelease` 能產出**已簽章**的 AAB | ✅ | 實測 BUILD SUCCESSFUL；`app/build/outputs/bundle/release/app-release.aab` = **6,323,728 bytes**（6.0 MiB），內含 `META-INF/UPLOAD.RSA` |
| R8／資源壓縮不會弄壞 Firebase／ZXing／kotlinx-serialization | ✅ 實測驗證過 | `app/proguard-rules.pro` 檔頭記錄了逐項驗證方法與結果 |
| signing 設定（從 `local.properties` 或環境變數讀，無金鑰時優雅退化） | ✅ | `app/build.gradle.kts` 檔頭；已實測「金鑰路徑不存在」時仍 BUILD SUCCESSFUL 並印出警告 |
| **upload keystore 已產生並接上 build，AAB 真的被簽了** | ✅ **B-2 已完成** | `local.properties` 四項齊備（keystore 在 repo 外）；AAB 內含 `META-INF/UPLOAD.SF`／`UPLOAD.RSA`（alias = `upload`）。**下面 B-2 只剩「備份」那一段還要做** |
| release build 沒有夾帶 debug 內容 | ✅ 實測 | merged manifest 的 `package` 無 `.debug`；`BuildConfig.DEBUG=false`；compose-ui-tooling／espresso／test-manifest 在 release dex 裡零命中；`LogcatSink` 在 release 只留 `Log.e` |
| App 圖示（啟動器 + 512 商店圖） | ✅ commit `59f811f` | 佔位向量已不存在；adaptive icon 三層齊全，實測圖案落在 72dp 安全區內 |
| **Play 用的 32-bit 512×512 圖示** | ✅ 我已產出 | `docs/play-store/assets/play-icon-512.png`（原檔是 24-bit，Play 要 32-bit，見 `assets-checklist.md` §1） |
| **主要宣傳圖 1024×500** | ✅ 我已產出可用版本 | `docs/play-store/assets/play-feature-graphic-1024x500.png`（正式上架前建議請設計師重做） |
| `google-services.json` 已放入且套件名正確 | ✅ | `package_name` = `com.megshao.exercise_rewards`，project `exercise-rewards`；已 gitignore；`uploadCrashlyticsMappingFileRelease` 實測會執行 |
| 商店文案、Data safety、內容分級的逐欄位答案 | ✅ 文件已備妥 | `listing-zh-TW.md`／`data-safety.md`／`content-rating.md` |
| **Android 版隱私權政策** | ✅ **B-3 已完成**（只剩一個缺口，見 B-3） | `site/privacy.html` 為獨立的 Android 版；實測已無 `Keychain`／`iCloud` 殘留。發佈網址 `https://megshao.github.io/exercise_rewards_android/privacy.html` |
| **對外說明頁與自動發佈** | ✅ 已加入 | `site/{index,privacy,support}.html` + `.github/workflows/pages.yml`（`enablement: true`，從 develop 發佈） |
| **Android 版自行驗證步驟** | ✅ 已加入 | `docs/verify-network.md`，由 privacy.html 第 8 節第 2 層指向 |
| **關閉遙測時一併清除未上傳資料** | ✅ 已強化 | `Telemetry.kt`：關開關時除了關旗標，另做 `resetAnalyticsData()` + `deleteUnsentReports()`。**這讓 Data safety 第 3 題（刪除）的答案從「只是不再收集」升級成「並且刪掉已收集未上傳的」** |
| **移除 `AD_ID` 等三個廣告權限** | ✅ **B-7 已完成並實測** | merged release manifest 的 `AD_ID`／`ADSERVICES` 命中數 **3 → 0**；AAB 內只剩 `INTERNET`／`ACCESS_NETWORK_STATE`／`WAKE_LOCK`／`BIND_GET_INSTALL_REFERRER_SERVICE` 與簽章層級的 dynamic-receiver 權限 |
| **network security config**（release 嚴格預設、debug 才信任使用者 CA） | ✅ 已加入 | `app/src/main/res/xml/network_security_config.xml`。讓隱私頁「自己攔封包驗證」那句話在 Android 上真的可行，同時不對 release 使用者降低安全性 |

---

## 1. 關鍵路徑（今天）

### B-1 ⛔ Google Play 開發者帳號 —— **這是唯一可能讓「今天」直接不成立的一項**

⏱ 15 分鐘（填表）　⏳ **數小時到數天（身分驗證，無法加速）**

**你必須先回答一個問題：帳號已經有了嗎？**

| 情況 | 影響 |
|---|---|
| **已有可用的個人開發者帳號**（已付費、身分驗證已通過） | 今天可以往下走。跳到 B-2 |
| **還沒有** | **今天不可能開始封測。** 註冊要付 US$25 一次性費用，且要通過 Google 的身分驗證（證件、地址），**驗證由 Google 排程，你無法加速**。實務上數小時到數天不等 |

**如果還沒有，今天能做的是**：註冊 + 付款 + 送出身分驗證，然後把 B-2～B-9 全部做完等在那裡。
驗證一過就能立刻建立 App 並上傳。

> ⚠️ **另一件必須今天知道的事：2026-09-30 的開發者驗證期限（距今 23 天）**
>
> Google 的〈Play Console Requirements〉規定：
> 「By September 30, 2026, register any remaining apps you want to continue distributing to avoid
> global removal from Google Play」，開發者也必須完成 Android developer verification。
>
> **對新建立的 App 是好消息**：官方明文
> 「For new apps, when you create an app in the Google Play Console, Google Play **automatically
> registers the package name** and links it to your account.」
> → 套件名稱註冊會自動完成，不用另外做。
>
> **但帳號層級的驗證要自己確認**。建立帳號時就會一併走，只是**如果你的帳號是舊的、
> 之前沒做過驗證，請今天就去 Console 確認狀態**——這個期限距今只有 23 天，
> 而且逾期的後果是「global removal」。
> 來源：https://support.google.com/googleplay/android-developer/answer/10788890
> ／https://developer.android.com/developer-verification/guides/google-play-console

**⚠️ 我沒有查證到的**：US$25 這個金額與「一次性」在 2026-09-07 當天是否仍然如此，
以及身分驗證目前的實際工時。註冊頁面上會直接顯示，**請以你看到的為準**。

---

### B-2 ✅ 產生 upload keystore —— **金鑰已經產好並接上了，只剩備份**

⏱ 剩 10 分鐘（只剩備份）　⏳ 0

> **2026-09-07 更新**：你已經自己把 keystore 產好、填進 `local.properties`，
> 並實測 AAB 內含 `META-INF/UPLOAD.SF`／`UPLOAD.RSA`（alias = `upload`）。
> **產生這一步已完成。** 下面保留產生指令是為了留紀錄（將來換機或建 CI 時要對照參數），
> **真正還沒做完的是「備份建議」那一段**，以及下面這個更正：
>
> ### ⚠️ 一個要更正的說法
>
> 你在 `local.properties` 的註解裡寫了「**這把金鑰遺失就永遠無法更新這個 App**」。
> **在 Play App Signing 之下這句話不成立**，建議改掉，否則它會讓將來的你（或別人）
> 在真的弄丟時以為完蛋了而做出錯誤決定（例如改套件名重新上架——那才真的回不去）。
>
> 準確的說法是：**這是 upload key，弄丟可以向 Play Console 申請重設**；
> 真正無可取代的 app signing key 由 Google 保管，你不會弄丟。詳見下方「兩把金鑰的差別」。
> **這不代表可以隨便**——弄丟要開支援單、要等 Google 處理，期間發不了版。但不是絕路。

**為什麼原本沒幫你做**：這把金鑰決定了「誰能發布這個套件名稱的更新」。
它該放哪、密碼怎麼記、怎麼備份，是只有你能決定的事，
而且一支由 agent 產生、密碼曾出現在某個 log 裡的金鑰，本身就是個安全問題。
（結果你自己做了，這樣最好。）

#### 先搞懂兩把金鑰的差別（這會影響你要多緊張）

Play 對新 App **一律使用 Play App Signing**（上傳 AAB 就是走這條）。所以有兩把金鑰：

| | **App signing key**（應用程式簽署金鑰） | **Upload key**（上傳金鑰） |
|---|---|---|
| 誰保管 | **Google 幫你保管** | **你自己保管**（就是你剛產好的那一把，alias `upload`） |
| 用途 | Google 用它簽出真正發給使用者的 APK | 你用它簽 AAB，Google 用它確認「這顆真的是你上傳的」 |
| 弄丟了怎麼辦 | Google 有備份，你不會弄丟 | **可以重設**：向 Play Console 支援申請換一把新的 upload key |

> **所以「金鑰弄丟就永遠無法更新 App」這個說法，在 Play App Signing 之下並不成立**——
> 那是舊的（自己保管簽署金鑰、直接上傳 APK）時代的規則。
> upload key 弄丟很麻煩（要開支援單、要等 Google 處理，期間發不了版），**但不是絕路**。
> 參考：https://support.google.com/googleplay/android-developer/answer/9842756
>
> **這不代表可以隨便**。還是要好好備份——只是你不必因為害怕而把備份做得亂七八糟。

#### 產生指令

```sh
mkdir -p ~/keys && chmod 700 ~/keys

keytool -genkeypair -v \
  -keystore ~/keys/exercise-rewards-upload.jks \
  -storetype PKCS12 \
  -alias upload \
  -keyalg RSA -keysize 4096 \
  -validity 10950 \
  -dname "CN=megshao, O=megshao, L=Taipei, C=TW"
```

逐個參數的理由：

| 參數 | 為什麼這樣選 |
|---|---|
| `-storetype PKCS12` | 業界標準格式。JKS 是 Java 專屬的舊格式，`keytool` 現在會對它印出「should be migrated to PKCS12」的警告 |
| `-keyalg RSA -keysize 4096` | Play 要求 RSA 且 ≥ 2048 bits。4096 沒有壞處 |
| `-validity 10950` | 30 年。Play 要求金鑰的有效期要**遠超過**你預期的 App 壽命（官方的門檻是 2033-10-22 之後）。**寧可長不要短**——過期了要換金鑰很麻煩 |
| `-keystore ~/keys/...` | **刻意放在專案目錄外面。** `.gitignore` 雖然已經擋掉 `*.jks`／`*.keystore`，但放在 repo 外面才是真的不可能誤 commit（這個 repo 是公開的） |
| 密碼 | **store 與 key 用同一個也可以**，用密碼管理器產生 20+ 字元的隨機密碼。**不要用你記得住的密碼** |

#### 接上 build

在 `local.properties`（**不進版控**，已在 `.gitignore`）加四行：

```properties
exerciseRewards.releaseStoreFile=/Users/<你的帳號>/keys/exercise-rewards-upload.jks
exerciseRewards.releaseStorePassword=<你的密碼>
exerciseRewards.releaseKeyAlias=upload
exerciseRewards.releaseKeyPassword=<你的密碼>
```

驗證（**沒有那行警告** = 簽章生效）：

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:bundleRelease
# 若仍看到「⚠️ 找不到 release 簽章材料」，就是四個值有哪個沒讀到
```

再確認 AAB 真的被簽了：

```sh
unzip -l app/build/outputs/bundle/release/app-release.aab | grep -E "META-INF/.*\.(RSA|SF)"
# 有輸出 = 已簽章。（先前未設定時，這裡是空的）
```

#### 備份建議（3 份、2 種媒介、1 份離線）

1. **密碼** → 密碼管理器（1Password／Bitwarden…），連同 alias 一起記。
   **密碼與 keystore 檔要分開放**——放在一起等於沒加密。
2. **keystore 檔** → 至少兩處：
   - 加密的雲端硬碟（不要用公司帳號，離職會出事）
   - 一支離線 USB 或加密磁碟映像，收在實體安全的地方
3. **在密碼管理器的備註裡寫下**：套件名稱 `com.megshao.exercise_rewards`、alias `upload`、
   產生日期、`-validity` 到哪一年、以及「這是 upload key，App signing key 由 Google 保管」。
   **三年後的你不會記得這些。**
4. ❌ **不要**把 keystore 放進專案目錄、不要寄給自己、不要放進任何 git repo（**包含私有的**）。

---

### B-3 ✅ 隱私權政策 URL（Android 版）—— **已寫好，剩兩件事**

⏱ 剩 10 分鐘　⏳ 0（GitHub Pages 上線約 1–2 分鐘）

> **2026-09-07 更新**：你已經寫好一份**獨立的 Android 版**隱私權政策（`site/privacy.html`），
> 不是把 iOS 那份翻譯過來。實測檢查：`Keychain` 0 次、`iCloud` 0 次、
> `廣告識別碼` 9 次、AES-256-GCM／Android Keystore／`allowBackup` 都有寫，
> 刪除界線也寫對了（已送到 Google 的部分「查不到也刪不掉特定筆數」）。
> **Play 的六項政策要求逐項都對得上**（逐項對照見 `data-safety.md` §8）。
>
> **還剩兩件事：**
>
> **① ⚠️ 補上 EXIF／GPS 那一段（實測 `EXIF` 與 `GPS` 各出現 0 次）**
>
> 這不是潔癖，而是**商店說明已經對外承諾了**：`listing-zh-TW.md` §3 寫著
> 「上傳的截圖會先重新編碼，把 EXIF 與 GPS 座標丟掉才送出」。
> **說明講了、政策沒講**，正好是 Play 拿來比對的那兩份文件對不齊。
> 而且它是「不蒐集精確位置」在使用者眼中真的成立的理由——
> 讀得仔細的人一定會想到「那我照片裡本來就有的座標呢」。
> 建議補在第 3 節那張表下面，**建議文字已寫在 `data-safety.md` §8**，可直接取用。
>
> **② ⚠️ 網址要 push 之後才會活**
>
> `https://megshao.github.io/exercise_rewards_android/privacy.html`
> 要等 **commit + push 到 `develop`、workflow 跑完**才存在。
> **Play Console 的隱私權政策欄位會即時驗證那個網址**，
> 所以**先 push、確認網址真的打得開，再去填 Console**——順序反了會被擋。
>
> 以下保留原始分析（政策的六項硬性要求、以及當初評估「能不能沿用 iOS 那份」的理由）。

**Play 對所有 App 都要求隱私權政策，沒有例外**（原文：「Apps that do not access any personal and
sensitive user data must still submit a privacy policy」）。而且要求比 Apple 明確得多：

- **公開可存取、不得地理封鎖、不可是 PDF、不可編輯**（原文：「available on an active,
  publicly accessible and non-geofenced URL (no PDFs) and is non-editable」）
  → **具體後果：Google Docs、Notion、HackMD 這類可編輯的頁面不合格，PDF 也不合格。**
  GitHub Pages 的靜態 HTML 正好完全符合（這也是下面方案 A 的另一個好處）
- 標題要有「隱私權政策」字樣
- **必須點名主體**（商店資訊上的開發者名稱或 App 名稱要出現在政策裡）
- 要寫：存取／蒐集／使用／分享了哪些資料、分享給誰、安全處理程序、保留與刪除政策、聯絡方式
- **要放兩個地方**：Play Console 的指定欄位 **與 App 內**（見 B-4）

#### 能不能直接沿用 iOS 那份？—— **不能，但可以改**

現有的 `https://megshao.github.io/exercise_rewards_ios/privacy.html` 已經涵蓋大部分要求，
但有三個具體問題：

| 問題 | 為什麼不能忽略 |
|---|---|
| 開頭寫「適用版本：Exercise Rewards 1.0.0（**iOS**）」 | 字面上不涵蓋 Android 版 → 違反「必須點名主體／App」 |
| 技術敘述是 iOS 的（Keychain、iCloud、IDFA／ATT、App 隱私權報告、FairPlay） | **在 Android 上是錯的**。政策內容不實比沒有政策更糟 |
| 網址 slug 是 `exercise_rewards_ios` | 不是錯誤，但一個 Android App 的隱私政策掛在 `_ios` 的網址下很怪，而且日後 repo 改名連結就死 |

#### 兩個方案

> ℹ️ **實際採用的是比這兩個方案都好的第三種**：在 **Android repo 自己的 `site/`** 放一份
> 完整的 Android 版政策，用自己的 GitHub Pages 發佈
> （`https://megshao.github.io/exercise_rewards_android/privacy.html`）。
> 這樣兩個平台的政策各自獨立、各自正確，網址也不會掛在對方的 repo slug 底下。
> 下面兩個方案是當初的評估紀錄，已不適用。

**方案 A（當初的省時方案，未採用）**：在 iOS 的 `site/` 裡新增一頁 `privacy-android.html`。
缺點是網址裡的 `_ios` 刺眼，且日後 repo 改名連結就死。

**方案 B（當初認為今天做不完，未採用）**：另開一個共用的 site repo。

#### 改寫時**一定要改**的內容

| 要刪掉／改寫（iOS 專屬） | 換成（Android 事實） |
|---|---|
| 「加密保存在 iOS Keychain（`WhenUnlockedThisDeviceOnly`）」 | **AES-256-GCM 加密，金鑰由 Android Keystore 保管**（支援 StrongBox 的機型存在安全晶片內、永不離開）。**登入 session（cookie）比照個資加密** |
| 「不同步 iCloud、不隨備份轉移」 | **`allowBackup=false` + `data_extraction_rules.xml` 全排除**：不進雲端備份、不隨裝置轉移 |
| 「不建立廣告識別碼（IDFA）」「不會跳出 iOS 的『要求追蹤』詢問」 | ⚠️ **在 B-7 做完之前不可以寫這句。** Android 的 merged manifest 目前**確實有** `AD_ID` 權限。做完 B-7 再加回來 |
| 第 8 節「App 隱私權報告」那一整層驗證法 | **Android 沒有等價功能**（隱私權主頁只顯示權限使用，而本 App 只有 `INTERNET`）。整段刪掉，或改寫成「用 mitmproxy 看流量」那一層 |
| FairPlay／「無法從商店版算出對回原始碼的雜湊」 | **Android 的情況不同，可以寫得比 iOS 強**：Play 不加密程式碼段，AAB 還支援 code transparency。這一段是可以升級的，不是只能刪 |
| 「不讀取 Apple 健康的任何資料」 | 「**不宣告任何健康、位置、相機權限**——manifest 裡唯一的權限是 `INTERNET`」（這句比 iOS 版更有力，因為權限清單是使用者自己查得到的） |

#### 還要**新增**的三段（Android 才有的事實）

1. **上傳前會重新編碼圖片，把 EXIF／GPS 丟掉，且失敗不退回原檔**
   （`ImageReencoder` 沒有 fallback）。這是「不蒐集精確位置」在使用者眼中真的成立的原因。
2. **粗略位置**：App 不使用定位，但 GA 會由連線 IP 在**伺服器端**推導國家層級位置。
   → 這一列會出現在商店的 Data safety 卡片上（見 `data-safety.md` §3），政策裡沒對應說明就是不一致。
3. **刪除的界線要寫清楚**：本機資料可立即永久刪除；Firebase 已送出的部分**因為沒有任何使用者身分，
   查不到也刪不掉特定筆數**，只能停止繼續送；`500.gov.tw` 上的帳號本 App 無權處理。
   → **不要寫成「所有資料都可以刪除」**，那對後兩者是不實陳述。

---

### B-4 ⛔ App 內沒有隱私權政策連結 —— **這是政策硬性要求，目前不符合**

⏱ 15 分鐘（但**需要改 Kotlin**，我依界線沒有動）　⏳ 0

**發現的事實**：`ProfileScreen.kt` 裡唯一的外部連結是

```kotlin
// app/src/main/kotlin/com/megshao/exerciserewards/ui/screens/ProfileScreen.kt:407
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/megshao/exercise_rewards_ios"))
```

**兩個問題**：

1. **沒有任何隱私權政策的連結或全文。** Play 的 User Data 政策明文：
   「All apps must post a privacy policy link in the designated field within Play Console,
   **and a privacy policy link or text within the app itself**.」
   → **Console 填了不夠，App 內也要有。目前沒有。**
2. **那個 GitHub 網址指向 iOS 的 repo**（`exercise_rewards_ios`）。
   一個 Android App 的「查看原始碼」按到 iOS 專案，對主打「開源可稽核」的 App 來說是實質的錯誤——
   使用者點進去找不到他手上這支 App 的程式碼。

**建議修法**（在「我的資料」頁的「安全與隱私」區塊加一列）：

```kotlin
// 新增：隱私權政策（Play 的 User Data 政策要求 App 內必須有連結或全文）
"https://megshao.github.io/exercise_rewards_android/privacy.html"

// 修正：原始碼連結指向 Android 的 repo（目前指向 iOS 的）
"https://github.com/megshao/exercise_rewards_android"
```

**改完要重跑 `:app:bundleRelease`**（AAB 內容變了）。

---

### B-5 ⛔ 手機截圖（至少 2 張，建議 4–6 張）

⏱ 30–90 分鐘　⏳ 0

**素材類唯一真的缺口。** 圖示與宣傳圖我都處理掉了（§0），只剩截圖。

**⚠️ 最重要的一件事：iOS 端現成的商店截圖不能直接用。**
它們是 1320×2868，而 `2868/1320 = 2.17`，違反 Play 的
「最長邊不得超過最短邊的 2 倍」。**直接上傳會被擋。**
從現代手機直接截圖（例如 1080×2400 = 2.22）**同樣不合格**。

**最快的路**：開一台 **1080×1920（16:9）** 的模擬器（`1920/1080 = 1.78`，直接合格），
跑示範模式截 4–6 張：歡迎／免責聲明 → 首頁 → 任務 → 券夾 → 兌換 → 我的資料。
`DemoFlowTest` 本來就會走完這些畫面。

完整的三個方案、後製補邊指令、內容禁則見 `assets-checklist.md` §3。

**截完務必逐張目視確認沒有真實個資**（只該出現 `A000000000` / `1990-01-01` / `0900000000`）。

---

### B-6 ⛔ Play Console 上的表單（答案都寫好了，照抄即可）

⏱ 60–90 分鐘　⏳ 0（內容分級是**立即**發出的）

順序有依賴關係，照這個順序做最順：

| # | 項目 | 照哪份文件填 | 備註 |
|---|---|---|---|
| 1 | 建立 App（名稱、預設語言 zh-TW、免費、App 而非遊戲） | `listing-zh-TW.md` §1 | 套件名稱**一經建立就永久固定**，且不可刪除、不可再利用。**確認是 `com.megshao.exercise_rewards`（有底線）** |
| 2 | **隱私權政策 URL** | B-3 | 其他幾項的前置條件 |
| 3 | **廣告宣告** → 不含廣告 | `content-rating.md` §5 | |
| 4 | **應用程式存取權** → 提供示範帳號 | `content-rating.md` §5.1 | **有現成的英文說明可直接複製**。Google 要求說明必須是英文 |
| 5 | **內容分級問卷** | `content-rating.md` | §3.3「分享個人資訊」答**是**。⚠️ 那一題與 Data safety 的答案故意相反，理由見該節 |
| 6 | **Data safety** | `data-safety.md` | ⚠️ 先做 B-7，否則廣告識別碼那題無法誠實作答 |
| 7 | **目標對象與內容** → 18 歲以上 | `content-rating.md` §5.2 | |
| 8 | **政府應用程式宣告** → 非政府 | `content-rating.md` §5 | 2023-01-31 起強制 |
| 9 | **健康應用程式宣告** → 聲明「不提供健康功能」 | `content-rating.md` §5 | ⚠️ **最容易漏的一項。** Google 明文：**沒有健康功能的 App 也必須填這張表並聲明沒有** |
| 10 | 財務功能宣告 → 無 | | |
| 11 | 商店資訊（名稱／簡短說明／完整說明／素材／類別／聯絡方式） | `listing-zh-TW.md` | |
| 12 | 上傳 AAB 到封測軌道 + 測試者名單 + 測試說明 + 版本資訊 | `closed-testing-plan.md` | |

> ### ✅ 已查證：上面**全部**都是封測的前置條件（2026-09-07 補查）
>
> 原本這裡標的是「不確定哪幾項只擋正式發布」。已經查到明文了，**答案是全部都擋**：
>
> `answer/14151465` 的 **「Requirements to access track」** 表格對
> **Closed testing 的要求就是一句「Complete app setup.」**
> —— 也就是 Console 上「設定你的應用程式」那整份檢查清單要全綠，封測軌道才發得出去。
> 所以不必逐項去猜哪個擋哪個：**整份清單就是那道門。**
>
> 另外兩項有更明確的逐字依據，它們**明白涵蓋封測**：
> - **健康應用程式宣告**（`answer/14738291`）與**財務功能宣告**（`answer/13849271`）的條文
>   都含「**including apps on closed testing, open testing, or production tracks**」。
> - **內容分級**（`answer/9898843`）：「We don't allow apps without a content rating on Google Play」、
>   「**Apps without a content rating will be removed from the Play Store**」
>   —— 後果是**移除**，不只是掛個「未分級」標籤。
>
> ### 💡 一個可以讓今天更有進展的戰術：先發**內部測試**
>
> 同一張表格裡，**Internal testing 的存取要求是「None.」**——不需要完成 app setup。
>
> 所以即使商店資訊、素材、表單還沒弄完，**你今天就可以把這顆已簽章的 AAB 發到內部測試軌道**，
> 用來驗證整條上傳管線真的通：套件名稱、簽章被 Play 接受、Play App Signing 正常掛上、
> 自己的手機真的裝得下來、release 版的「同意後才初始化 Firebase」實機行為正確（R-1）。
>
> **⚠️ 但要非常清楚它不能做什麼**：
> **內部測試完全不計入「12 人 × 連續 14 天」**——那個要求只認 closed testing
> （`answer/14151465` 明文排除 internal 與 open）。
> **所以內部測試不會讓那 14 天提早開始一天。** 它的價值純粹是「今天就把技術風險驗掉」，
> 不是加速上架。真正的計時仍然要等封測軌道發布、測試者逐一加入才開始。

---

### B-7 ✅ 移除 `AD_ID` 權限 —— **已完成並實測通過**

⏱ 0（已做完）　⏳ 0

> **2026-09-07 更新**：你已經在 `AndroidManifest.xml` 用三個 `tools:node="remove"` 把它們移掉了，
> 而且註解寫得比我下面的建議更完整。**實測驗證通過**：
> merged release manifest 的 `AD_ID`／`ADSERVICES` 命中數 **從 3 降為 0**。
>
> **因此下面兩件事現在都可以做了**：
> ・Data safety 的廣告識別碼那題答**「不使用」**；
> ・商店說明與隱私權政策可以寫**「不使用廣告識別碼」**（已加進 `listing-zh-TW.md` §3）。
>
> ℹ️ **我原本在這裡寫「`merged_manifest` 是錯字」，那是我看錯了，已撤回。**
> AGP 9 **兩個目錄都會產生**，而且註解裡那條單數路徑是對的（那份才是 AAB 實際打包的）：
> ・`merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`（AAB 用這份）
> ・`merged_manifests/release/processReleaseManifest/AndroidManifest.xml`
> 實測**兩份的 `AD_ID`／`ADSERVICES` 命中數都是 0**。
>
> 底層那個問題仍然成立、也已經修掉了：**`grep -c` 對沒命中的 glob 會印 0，
> 看起來像通過其實根本沒讀到檔案**。manifest 的註解已改成先 `find` 再判斷、
> 找不到就 `exit 1`，並實測過（故意給錯路徑會報錯而不是靜靜印 0）。
>
> ℹ️ **還剩一個權限值得知道（不用處理）**：`BIND_GET_INSTALL_REFERRER_SERVICE` 仍在，
> 那是 Analytics 用來讀「這次安裝是從哪個 Play 連結來的」。它是 `normal` 層級、
> **不會出現在使用者看到的權限清單裡**，也不是廣告識別碼。要不要一起移掉是取捨：
> 移掉會失去安裝來源歸因（對只發封測連結的情況本來就沒什麼用），留著不影響任何對外聲明。

以下保留原始分析作為紀錄。

**實測發現**：release 版的 merged manifest 裡有四個沒人要的權限，全部由 `firebase-analytics` 帶進來：

```
com.google.android.gms.permission.AD_ID
android.permission.ACCESS_ADSERVICES_ATTRIBUTION
android.permission.ACCESS_ADSERVICES_AD_ID
com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE
```

而 Google 自己的 GA 文件明列 GA 會自動蒐集 **Android 廣告識別碼**。

**後果**（三個都是實質問題，不是理論風險）：

1. Play Console 會針對 `AD_ID` 權限**單獨問你一題**。有這個權限而答「不使用」會被要求更正；
   答「使用」則 Data safety 的 Device IDs 那一格要包含廣告識別碼——
   而廣告識別碼是**可跨 App 追蹤**的，商店卡片會與「不做跨 App 追蹤」的賣點直接打對台。
2. 會削弱 `data-safety.md` §5 第 B 組的 **service provider 豁免**論述
   （一個會收廣告識別碼的分析 SDK，比較難主張它純粹代表開發者處理資料）。
3. **與 iOS 端的既有文案直接衝突**。iOS 刻意用 `FirebaseAnalyticsCore`（結構上不含 IDFA 能力），
   並在隱私政策與商店說明寫「不建立廣告識別碼」。
   **Android 照抄那句話而不改 manifest，那句話就是不實陳述。**

**修法**（`app/src/main/AndroidManifest.xml`，兩處都要，只做一處不夠）：

```xml
<!-- 不做廣告歸因，也不要廣告識別碼：把 firebase-analytics 帶進來的權限移掉。
     `tools:node="remove"` 與上面移除 FirebaseInitProvider 是同一個手法。 -->
<uses-permission android:name="com.google.android.gms.permission.AD_ID"
    tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_ADSERVICES_AD_ID"
    tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_ADSERVICES_ATTRIBUTION"
    tools:node="remove" />

<!-- 移權限只是讓 SDK 拿不到；這個旗標讓它連要都不要。 -->
<meta-data android:name="google_analytics_adid_collection_enabled" android:value="false" />
```

**驗證**（要看合併後的結果，不是原始碼）：

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:bundleRelease
grep -i "AD_ID\|ADSERVICES" app/build/intermediates/merged_manifests/release/*/AndroidManifest.xml
# 預期：零命中（目前是 3 命中）
```

**做完之後才可以**：Data safety 的廣告識別碼那題答「不使用」；
商店說明與隱私權政策加回「不使用廣告識別碼」。**順序不能顛倒。**

---

### B-8 ⏳ 招募 16 位測試者

⏱ 30 分鐘（發訊息）　⏳ **這是整條路上最長的等待：14 天，而且無法壓縮**

- **招 16 位，不是 12 位**。理由（有人不會真的加入、有人會中途退出重算、Google 會看參與度）
  見 `closed-testing-plan.md` §2。
- **要的是對方「Play 商店登入用的那個 Google 帳號 email」**，不是常用信箱。
  這是實務上最常見的失敗點。
- 招募訊息與加入後的完整說明（含「不用真帳號，用示範三碼就能測」）
  在 `closed-testing-plan.md` §5，可以直接複製。
- ⚠️ **opt-in 連結要等封測版本狀態變成「已發布」才會出現**，
  所以**先把 AAB 送出審查，再去招人**，順序反了會白等（`closed-testing-plan.md` §4）。

---

## 2. 今天不用做，但上架前躲不掉

| # | 項目 | 為什麼不是今天 | 預估 |
|---|---|---|---|
| C-1 | **Firebase Console 的隱私設定**：GA 資料保留期設最短、關 Google Signals、關廣告個人化、關精細位置、不開 BigQuery | 不影響封測能否開始，但**影響 Data safety 幾格的辯護力道**，而且愈早關愈少資料被留下 | 20 分 |
| C-2 | **限制 Firebase API key**：在 Google Cloud Console 把 Android API key 綁定套件名 `com.megshao.exercise_rewards` + 簽章 SHA-1 | API key 一定會被打包進 APK（無法避免），唯一的防護就是綁定套件名與簽章。**AAB 一發出去就等於公開了那把 key** | 15 分 |
| C-3 | **用真實帳號完整跑一次**（登入 → 上傳 → 審核 → 兌換 → OTP → 條碼） | README 已列為待辦。目前只驗過示範模式。**每一步都會消耗官網的次數額度，要省著測** | 依活動期程 |
| C-4 | **平板／可折疊裝置的版面實測** | targetSdk 36 起，在最短邊 ≥600dp 的螢幕上系統**會忽略 `screenOrientation="portrait"`** 並讓 App 可調整大小 → 目前的直向鎖定在平板上不生效。不修會拿到大螢幕的使用者提示與排名扣分 | 1–2 小時 |
| C-5 | **主要宣傳圖請設計師重做** | 我產的那張夠格、政策安全，但是程式產生的，不是設計品 | — |
| C-6 | **`versionCode` 遞增紀律** | 目前是 `1`。Play **同一個 versionCode 只能上傳一次**，被拒的版本也算用掉了。改一次就要 +1 | — |
| C-7 | **`app/google-services.json.template` 的 `package_name` 還是舊的** | 檔案裡仍寫 `com.megshao.exerciserewards`（無底線）。任何人照範本填會產生一份**與 applicationId 不符**的設定檔，`google-services` plugin 會丟 `No matching client found`。我依界線沒有動這個檔 | 1 分 |

---

## 3. 我發現、但你沒問到的風險

### R-1 ⚠️ debug 版完全沒有 Firebase —— 有一條路徑封測驗不到

`app/build.gradle.kts` 刻意把 `processDebugGoogleServices` 關掉（檔案裡有寫理由，我認同）。
但它有一個沒被寫下來的後果：

**「使用者按下同意 → Firebase 才初始化」這條路徑，只有 release 版驗得到。**
debug 版驗到的是另一件事（「沒有設定檔時安全地什麼都不做」）。

而這條路徑是整個隱私敘述的**唯一技術支撐**——隱私政策、商店說明、免責聲明三處都靠它。
→ **建議在封測開始前，親自用 release 版在實機上驗一次**：
安裝後**先不要按同意**，看有沒有任何 Google 網域的連線；按下同意之後才該出現。
（Android 沒有 iOS 那種「App 隱私權報告」，所以要用 `adb logcat` 看
`FirebaseApp initialization` 出現的時機，或用 mitmproxy 看流量。）

### R-2 ⚠️ Crashlytics 的堆疊看得懂，但**依賴一份 51 MB 的檔案**

R8 預設會把行號重新編號、把 SourceFile 換成 `r8-map-id-<hash>`。
我已經在 `proguard-rules.pro` 加了 `-keepattributes SourceFile,LineNumberTable` 把真實行號留在 dex 裡
（代價 +1.47% 體積），所以**logcat 上的堆疊現在有可用行號**。

但**類別與方法名仍然是混淆的**，要還原得靠 `mapping.txt`（51 MB，不進版控）。好消息是有兩份保險：

- `uploadCrashlyticsMappingFileRelease` 會自動上傳到 Crashlytics（實測會執行）；
- AAB 內含 `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`，**Play 會自己拿到**，
  所以 Android vitals 的當機叢集也看得懂。

→ **不需要額外動作**，但要知道：**本機的 `app/build/` 被清掉之後，那一版的 mapping 就只存在
Crashlytics 與 Play 上了。** 不要依賴本機那份。

### R-3 ⚠️ `LogcatSink` 在 release 仍會輸出 `Log.e`

`LogcatSink.error()` 沒有 `isDebugBuild` 的保護（`debug`／`info` 有）。
檔案註解說這是刻意的（「release build 只留 error」），我認同這個取捨——
但它意味著**錯誤訊息在正式版會進 logcat，而 logcat 在有 root 或有 adb 的裝置上讀得到**。

現況是安全的，因為 `SecureLog.error` 的呼叫端**都被要求先過 `Redact`**。
→ **這是一條靠紀律維持的界線，不是靠型別。** 任何人日後在 `error()` 裡塞一個
未遮罩的官網回應原文，個資就會進 logcat。**建議加一條 CI 檢查或至少在 code review 清單裡列出來。**
（我沒有改程式，這只是回報。）

### R-4 ⚠️「不使用廣告識別碼」現在是真的，但它是**有條件的真**

B-7 做完之後這句話已經加進 `listing-zh-TW.md` §3 了。但它成立的唯一理由是
`AndroidManifest.xml` 裡那三個 `tools:node="remove"`——**任何人把它們拿掉（或升級
firebase-analytics 後合併行為改變），這句話就變成不實陳述，而且沒有任何測試會抓到。**

→ 這正是 iOS 端 `app-review-risk.md` 講的「一致性風險」在 Android 的翻版：
**實作沒問題，出問題會出在文件、表單與實作三者對不齊。**
→ **建議做成 CI 檢查**（一行就夠，而且比任何文件註解可靠）：

```sh
# build 之後跑。先確認真的找到 manifest（否則「零命中」是假的通過），再檢查權限。
set -euo pipefail
manifests=$(find app/build/intermediates/merged_manifest app/build/intermediates/merged_manifests \
              -path '*release*' -name AndroidManifest.xml 2>/dev/null)
[ -n "$manifests" ] || { echo "找不到 merged manifest —— 這次檢查沒有意義"; exit 1; }
! grep -l "AD_ID\|ADSERVICES" $manifests || { echo "廣告權限又回來了"; exit 1; }
```

### R-5 ⚠️ 內容分級與 Data safety 對「分享」的答案**故意相反**

`content-rating.md` §3.3「分享個人資訊」答**是**；`data-safety.md` 的 Shared **不勾**。
兩者都有明文依據（IARC 沒有主動觸發豁免、Data safety 有），
**但這看起來就像填錯了**，日後任何人（包括你自己）回頭看都會想「統一一下」。

→ **兩份文件都已經寫了警告，請不要動它們。** 改成一致，其中一份就變成不實陳述。

### R-6 ℹ️ `applicationId` 與 `namespace` 不一致沒有問題（我查證過）

你打算保留 `namespace = com.megshao.exerciserewards`（無底線）而
`applicationId = com.megshao.exercise_rewards`（有底線）。**從 Play 與 Firebase 的角度，這是正確且官方支援的做法**：

- 底線在 `applicationId` 裡**合法**。官方規則原文：
  「All characters must be alphanumeric or an underscore [a-zA-Z0-9_]」，
  且每一段須以字母開頭（`exercise_rewards` 以 `e` 開頭，合規）。
- `namespace` **只影響編譯期產物**（`R`／`BuildConfig` 的 package）。
  官方原文：「**The merged manifest's `package` attribute is where the Google Play Store and the
  Android platform actually look to identify your app**」——而那個值就是 `applicationId`。
  實測確認：release merged manifest 的 `package="com.megshao.exercise_rewards"`，
  `BuildConfig` 產生在 `com/megshao/exerciserewards/`。
- **Firebase 比對的是 `applicationId`，不是 `namespace`**（`google-services` plugin 原始碼是對
  `variant.applicationId` 做字串相等比較，且**不會去掉 `applicationIdSuffix`**）。
  你的 `google-services.json` 已經是 `com.megshao.exercise_rewards`，實測 release 建置正常。
- **Kotlin 的命名慣例確實不用底線**（官方 coding conventions 明文），
  所以「namespace 不加底線、applicationId 加底線」正好同時滿足兩邊。**不用改那 112 個檔。**

**唯一值得知道的副作用（不是問題，只是要知道）**：iOS 的 bundle ID
**不允許底線**（Apple 明文只允許英數字、連字號、句點）。
所以兩個平台的識別碼從此永久分岔：iOS 是 `com.megshao.exerciserewards`，
Android 是 `com.megshao.exercise_rewards`。同一個 Firebase 專案裡並存兩個不同 package 的 app
完全沒問題，只是**看 Firebase 或做跨平台比對時，要記得它們長得不一樣**。

### R-7 ℹ️ targetSdk 37 沒問題（我查證過）

Play 現行要求是**新 App 與更新都必須 targetSdk ≥ 36（Android 16）**，
而該期限（2026-08-31）**已經過了**，現在正在強制執行。
你是 **37（Android 17，2026 年中已正式發布，不是 preview）**，**高於門檻且有一年餘裕**。
政策頁面只規定下限，**沒有任何「不能太新」的上限**。

---

## 4. 誠實的結論：今天最快能到哪裡

**分兩種情況：**

### 情況一：開發者帳號**已經有了**（已付費、驗證已過）

| 時段 | 做什麼 |
|---|---|
| 第 1 小時 | ~~B-2 產生 keystore~~、~~B-7 移除 AD_ID~~（**兩項都已完成**）／B-2 的**備份**（10 分）／B-4 加 App 內隱私政策連結並修正 GitHub 網址（15 分）／重跑 `bundleRelease`（4 分） |
| 第 2–3 小時 | B-3 隱私權政策 Android 版並上線（30–60 分）／B-5 開模擬器截 4–6 張圖（30–90 分） |
| 第 4–5 小時 | B-6 Console 上的 12 項表單，照文件抄（60–90 分）／上傳 AAB → **送出封測審查** |
| 之後 | B-8 招募 16 位測試者（等審查通過拿到 opt-in 連結） |

→ **「今天把封測版本送出審查」最快約 4–5 小時內可以完成。**

### 情況二：開發者帳號**還沒有**

→ **今天不可能開始封測。** 卡在 Google 的身分驗證，那不是你能加速的。
今天能做的是：註冊付費、送出驗證，然後把剩下的 **B-3／B-4／B-5** 做完等在那裡
（B-2 與 B-7 已完成）。驗證一過就能立刻建立 App、填 B-6 的表單並上傳。

### 兩種情況都建議做的一件事：今天先發**內部測試**

`answer/14151465` 的軌道存取表：**Internal testing 的要求是「None.」**，
**Closed testing 是「Complete app setup.」**。所以只要帳號能用，
**即使 B-3～B-6 還沒做完，今天就可以把這顆已簽章的 AAB 發到內部測試軌道**，
驗掉幾件只有真的上傳過才驗得到的事：套件名稱被接受（一經上傳永久固定）、
簽章被接受、Play App Signing 正常掛上、手機真的裝得下來、
以及 **release 版「同意前不連 Google」的實機行為**（R-1，debug 版驗不到）。

⚠️ **但它不會讓那 14 天提早開始一天** —— 12 人 × 14 天只認 closed testing，
官方明文排除 internal 與 open。內部測試是**提早除錯**，不是加速上架。

### 無論哪一種情況，都要理解的一件事

**「開始封測」≠「完成封測」。**

```
今天送出封測 → Google 審查（新帳號首次可能數天）→ 狀態變「已發布」
→ 拿到 opt-in 連結 → 16 位測試者逐一加入
→ 最後一位加入那天 +14 天 → 才能申請正式發布權限
→ Google 再審查（官方說法「usually seven days or less」）
```

**從今天算起到能上正式版，現實的下限是三週多。** 那 14 天是硬性的、無法用錢或加班壓縮的。
所以**今天最有價值的動作，就是讓那 14 天的計時盡早開始**——
也就是**優先把 AAB 送出審查**，其他能並行的事都往後排。
