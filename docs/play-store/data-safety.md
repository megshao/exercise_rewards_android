# Play Console「資料安全性」（Data safety）逐欄位填答建議

> 對應版本：`versionName 1.0.0` / `versionCode 1`　套件名稱：`com.megshao.exercise_rewards`
> 撰寫日期：**2026-09-07**（政策條文於同日逐條查證，來源見 §0）
>
> **這份表單是本專案最不能填錯的一份文件。** App 的賣點是「個資只在你手機裡、開發者收不到」，
> 而 Data safety 是 Play 商店頁上唯一由 Google 幫你把這句話結構化呈現的地方。
> 少勾 → 標籤不實，Google 可以下架並記違規；多勾 → 商店頁多幾列，賣點被稀釋但沒有法律風險。
> **本專案的既定取捨（沿用 iOS 端 `docs/release/privacy-labels.md` 前提 B）：選多勾。**
>
> 上游對照是 iOS 端的隱私標籤，但**兩邊的答案會不一樣，而且那是正確的**——
> Apple 與 Google 對「蒐集」「分享」的定義不同。差異與理由整理在 §7，不要拿一邊去校正另一邊。

---

---

## 0. 政策依據（2026-09-07 查證）

| 主題 | 來源 |
|---|---|
| 表單規格、`collect`／`share` 定義、豁免、常見問答 | https://support.google.com/googleplay/android-developer/answer/10787469 |
| 資料類型分類與各類定義、哪些權限／API 會隱含哪一類 | https://developer.android.com/guide/topics/data/collect-share |
| User Data 政策（隱私權政策的硬性要求） | https://support.google.com/googleplay/android-developer/answer/10144311 |
| Crashlytics 實際蒐集什麼（Google 自己列的） | https://firebase.google.com/docs/android/play-data-disclosure |
| Firebase 版 Google Analytics 實際蒐集什麼 | https://support.google.com/analytics/answer/11582702 |

> ⚠️ **一個必須先講的事實**：**Google 沒有出版 Firebase → Play 資料類型的官方對照表。**
> Firebase／GA 只公布「SDK 收了什麼」，然後明白把對照的責任丟回開發者：
> 「you can use Android's guide about data types to help you determine which data type best
> describes the collected data」。所以 §5 的 Firebase 對照是**本文件的推論**，不是照抄官方表格。
> 推論的每一步都寫出來了，可以自己覆核。

---

## 0-A. 這張表在哪些軌道上是必填的（2026-09-07 查證，原文引述）

`answer/10787469` 是唯一明確回答「軌道」這個問題的官方頁面，原文：

> "All developers that have an app published on Google Play must complete the Data safety form,
> **including apps on closed, open, or production testing tracks**."

> "Apps that are active on internal testing tracks are exempt from inclusion in the data safety
> section. Apps that are **exclusively** active on this track do not need to complete the
> Data safety form."

> "Ensure that you've added a privacy policy; this is required to complete the Data safety form..."

三個推論，直接影響今天的排程：

1. **封閉測試就要填這張表。** 不能「先發封測、表單之後補」。
2. **隱私權政策是這張表的前置條件**，不是併行項目 —— 政策網址要先能打開
   （見 `blockers.md` B-3；網址是 `https://megshao.github.io/exercise_rewards_android/privacy.html`）。
3. **只有「專用內部測試」可以豁免。** 所以 `blockers.md` 建議的「今天先發內部測試驗技術風險」
   這個戰術，在表單還沒填完之前是**可行的**——但一旦要進封測，這張表就必須完成。

⚠️ **這裡有一個查不到答案的缺口，不要假裝有**：內容分級、隱私權政策本身、廣告聲明、
目標對象、權限聲明、新聞 App 聲明這幾項，官方可讀到的頁面**都沒有說**封閉測試軌道是否必要
（`answer/9859455` 與 `answer/9867159` 完全沒提到任何軌道）。另有六頁因來源 IP 被 CAPTCHA
擋住而抓不到原文（Ads、Health apps、Financial features、政府資訊 App、News and Magazine、
Play Console Requirements），所以那幾項本文件不做斷言。
唯一能間接推出的路徑是：封測要填 Data safety → Data safety 要求隱私權政策
→ 因此**隱私權政策在封測前一定要有**。

---

## 1. 先釘住兩個定義（整份表單的答案都由這兩句推出來）

### 1.1「蒐集（Collect）」= 資料離開裝置，**不管送給誰**

Google 原文：

> **"Collect" means transmitting data from your app off a user's device.**
> …includes data transmitted off device by libraries/SDKs, **"irrespective of whether data is
> transmitted to you or a third-party server."**

**這一句是本專案最容易誤判的地方。** 直覺上「開發者沒有伺服器、拿不到任何資料」＝什麼都沒蒐集，
但 Google 的定義**不看開發者拿到什麼，只看資料有沒有離開裝置**。
身分證號、出生日期、手機號碼在使用者按下登入時確實離開了裝置 → **算 collected。**

反面的豁免（原文標題 "The following use cases do not need to be disclosed as collected"）：

> **On-device access/processing**: "User data accessed by your app that is only processed locally
> on the user's device and not sent off device does not need to be disclosed."

→ 所以**「加密存在 Android Keystore 裡」這件事本身完全不需要申報**。
Data safety 問的是「有沒有送出去」，不是「有沒有存起來」。這兩件事要分開想：

| 事實 | 要不要申報 | 為什麼 |
|---|---|---|
| 三欄個資以 AES-256-GCM 加密存在本機 | **不用** | 只在裝置上，屬 on-device processing 豁免 |
| 三欄個資在登入時送到 `500.gov.tw` | **要（Collected）** | 離開了裝置 |
| 本機 `SecureLog` 的輸出 | **不用** | 從不離開裝置；release build 只留 `Log.e` 到 logcat，logcat 也不離開裝置 |

### 1.2「分享（Share）」= 轉移給第三方，**但有「使用者主動觸發」的豁免**

Google 原文：

> **"Sharing" refers to transferring user data collected from your app to a third party.**

豁免四項，其中兩項直接命中本專案：

> **User-initiated action or prominent disclosure and user consent**: "Transferring user data to a
> third party based on **a specific user-initiated action, where the user reasonably expects the
> data to be shared**, or based on a prominent in-app disclosure and consent…"
>
> **Service providers**: 代表開發者、依開發者指示處理資料者。

→ **送到 `500.gov.tw` 不算 shared**：使用者按的按鈕就叫「登入」，送出的就是他自己在那個網站的帳號憑證，
「reasonably expects the data to be shared」在這裡是最無爭議的一種情形。
Google 自己給的平行例子是「when you send an email to or share a document with another person」。

→ **送到 Firebase 也不算 shared**：Google 作為分析服務提供者處理本 App 的資料，符合 service provider
豁免（原文：「an analytics provider that processes user data from your app solely on your behalf
… will typically qualify as 'service providers'」）。

**所以整份表單裡，「Shared」一格都不勾。** 這不是取巧，是兩條各自明列的豁免；
而且**代價是要在別的地方講清楚**——隱私權政策必須把 `500.gov.tw` 與 Google 兩個收受方寫明（見 §8）。

> ### ⚠️ 最接近本案的官方問答，以及它為什麼不能直接照用
>
> Google 的 FAQ 裡最像本案的一題是「使用者把資料直接上傳到自己的 Google Drive／Dropbox」：
>
> > "If the user chooses to upload their data directly to their own external drive or cloud storage
> > account … and **your app never collects or accesses the data in question**, then your app does
> > not need to declare the collection of this data."
>
> 照這一條，理論上連 collected 都可以不勾。**但本 App 不符合最後那個條件**：
> 三欄個資是**本 App 自己的 UI 收來的**（`OnboardingScreen` 的表單），還加密存在本機以便下次重用——
> 這不叫「never collects or accesses」。
> **結論：collected 要勾，shared 不勾。** 這是有依據又不冒險的組合。

---

## 2. 第一節「資料收集和安全性」（3 題）

| # | 題目原文 | 建議答案 | 理由 |
|---|---|---|---|
| 1 | **"Does your app collect or share any of the required user data types?"** | **是（Yes）** | 三欄個資會離開裝置（§1.1）。就算把 Firebase 拿掉，這題還是 Yes |
| 2 | **"Is all of the user data collected by your app encrypted in transit?"** | **是（Yes）** | 三層都成立：① App 自己的網路層在型別上就只走 https——`OkHttpHttpClient` 強制 `url.scheme == "https"` 且 host 必須通過 `isAllowedHost`，否則丟 `AppError.BlockedEgress`；② **平台層再加一道**：`network_security_config.xml` 的 `base-config` 明寫 `cleartextTrafficPermitted="false"`，所以連「哪天有人不小心繞過 App 自己的檢查」也還是發不出明文（`<debug-overrides>` 只在 `debuggable=true` 生效，release 不套用）；③ Firebase 那半由 Google 自己保證：Crashlytics「encrypts the data in transit using HTTPS」、GA「encrypted in transit using the Transport Layer Security (TLS) protocol」 |
| 3 | **"Do you provide a way for users to request that their data is deleted?"** | **是（Yes）** | 見下方說明 |

### 第 3 題要小心，它問的比你想的窄

Google 允許的機制很寬（原文：「no prescribed mechanism」，App 內功能、聯絡表單、專用信箱都算），
但**要對得上「哪些資料」**。本 App 的三個收受方要分開講：

| 資料在哪 | 使用者能不能刪 | 機制 |
|---|---|---|
| 本機（三欄個資、cookie、任務快取） | **能，立即且永久** | 「我的資料 → 立即清除本機資料」；刪除 App 亦同 |
| Firebase（匿名事件、當機報告、安裝編號） | **能停止繼續送；已收集但未上傳的一併刪除；安裝編號重置** | 「我的資料 › 安全與隱私 › 傳送匿名使用統計」關掉。實作上不只關旗標，還會 `resetAnalyticsData()`（清事件佇列 + 重置 app instance id）與 `deleteUnsentReports()`（刪掉硬碟上還沒上傳的當機報告）——**光關旗標的話，佇列裡的東西下次還是會送出去，那與使用者按下開關的意思不符**。<br>**但已經送到 Google 的部分**因為沒有任何使用者身分，**查不到也刪不掉特定筆數**——這點必須在隱私權政策寫明，不能只寫「可以刪除」 |
| `500.gov.tw` 上的帳號 | **不能，本 App 無權處理** | 隱私權政策與支援頁要明白寫「請至 500.gov.tw 自行管理」 |

→ 答 Yes 沒問題（本機資料有立即刪除、Firebase 有停止與重置、另有聯絡信箱可提出請求），
但**隱私權政策不可以寫成「所有資料都可以刪除」**——那對 Firebase 已送出的部分與官網帳號都是不實陳述。

---

## 3. 第二節「資料類型」勾選總表

> ### ✅ Firebase 的狀態已經確定了：**release 版有接，debug 版刻意沒接**
>
> 這一節原本寫成「要不要放 `google-services.json`」的二選一。**2026-09-07 已經定案，不用再選**：
>
> | 事實 | 實測依據 |
> |---|---|
> | `app/google-services.json` **已放入**，`package_name` = `com.megshao.exercise_rewards`（與 applicationId 相符） | 檔案存在且已被 `.gitignore` 排除（`git status` 不顯示它） |
> | **release 版真的接上了** | `processReleaseGoogleServices` 產出 `google_app_id` 等六個字串資源，且**確認存在於 AAB 的 `base/resources.pb`**；`uploadCrashlyticsMappingFileRelease` 也會執行 |
> | **debug 版刻意沒接** | `app/build.gradle.kts` 把 `processDebugGoogleServices` 設成 `enabled = false`（檔案裡寫了理由）。所以 debug 版 `FirebaseApp.initializeApp()` 回 null、`Telemetry` 全程 no-op |
>
> **→ 下表照填（Firebase 那幾格全部是 Yes）。**
>
> **但有一件事要記住**：Data safety 表單是**按套件名稱、涵蓋所有仍在架上的版本**的
> （原文：「the sum of your app's data collection and sharing across all its versions currently
> distributed on Google Play」）。所以只要有**任何一個**仍在架上的版本會收集，就要申報——
> 現在 release 版會收集，所以要申報。**日後若把 Firebase 拿掉，也要等舊版都下架才能改表單。**
>
> ⚠️ **debug 版沒有 Firebase 有一個測試上的後果**：
> 「使用者按下同意 → Firebase 才初始化」這條路徑**只有 release 版驗得到**，
> 而那條路徑是整個隱私敘述的唯一技術支撐。封測前請用 release 版在實機上實測一次。
> 見 `blockers.md` R-1。

| 類別 | 資料類型 | 勾？ | 來源 | 理由 |
|---|---|:--:|---|---|
| **Location** | Approximate location | **Yes** | Firebase | App 自己完全不使用定位（原始 manifest 只宣告 `INTERNET`，且**沒有任何定位權限**——合併後多出的 `ACCESS_NETWORK_STATE`／`WAKE_LOCK` 是 Firebase 帶的，都與位置無關），但 **GA 會在伺服器端由連線 IP 推導國家／城市層級位置**（Google 自己的文件：「Derives coarse location data from the masked IP addresses of the device」）。而 Data safety 明文要求「where developers use IP addresses as a means to determine location, then that data type should be declared」。**這一格是 iOS 端當初標 `TODO(待確認)` 的那一格，Android 這邊查到明文了，勾 Yes** |
| | Precise location | No | — | 不使用定位，也沒有任何定位權限 |
| **Personal info** | Name | No | — | 從不收集。`Profile` 型別雖有 `name` 欄位，但 UI 從不填它（維持空字串），R8 甚至把 `getName()` 裁掉了 |
| | Email address | No | — | 同上 |
| | **User IDs** | **Yes** | 500.gov.tw | **身分證號放這裡。** Google 的定義是「identifiers that relate to an identified or identifiable person」；身分證號在 `500.gov.tw` 上**就是帳號本身**（`POST /access` 以 `idNo` 分流），是最貼切的一格 |
| | Address | No | — | 從不收集 |
| | **Phone number** | **Yes** | 500.gov.tw | 登入三碼之一 |
| | Race and ethnicity | No | — | — |
| | Political or religious beliefs | No | — | — |
| | Sexual orientation | No | — | — |
| | **Other info** | **Yes** | 500.gov.tw | **出生日期放這裡。** 不是猜的——Google 對這一格的舉例第一個就是 date of birth |
| **Financial info** | 全部四項 | No | — | 完全免費、無 IAP、不碰金流。加碼券不是金融商品，App 也不顯示任何餘額 |
| **Health and fitness** | Health info | No | — | App **完全不讀取健康資料**：合併後的 manifest 沒有任何健康權限，原始碼沒有 Health Connect／Google Fit 的任何相依 |
| | **Fitness info** | **見 §4，需要你決定** | 500.gov.tw | ⚠️ **本表唯一的判斷題**，不要跳過 |
| **Messages** | 全部三項 | No | — | 不讀簡訊；OTP 由使用者自己輸入（`VoucherScreen` 的 OTP 格） |
| **Photos and videos** | **Photos** | **Yes** | 500.gov.tw | 使用者自選的運動紀錄截圖，multipart 上傳到當期任務 |
| | Videos | No | — | 只收單張圖片 |
| **Audio files** | 全部三項 | No | — | 無錄音、無音訊功能 |
| **Files and docs** | Files and docs | No | — | 上傳走系統相片選取器，屬 Photos 而非泛用檔案 |
| **Calendar** | Calendar events | No | — | — |
| **Contacts** | Contacts | No | — | — |
| **App activity** | **App interactions** | **Yes** | Firebase | `screen_view` 與登入／任務／上傳／兌換／券碼各漏斗事件，加上 Firebase 自動事件（`first_open`、`session_start`…） |
| | In-app search history | No | — | 無搜尋功能 |
| | Installed apps | No | — | 不查詢已安裝 App，沒有 `QUERY_ALL_PACKAGES` |
| | Other user-generated content | No | — | 上傳的截圖已歸在 Photos；App 沒有 bio／筆記／自由文字欄位 |
| | Other actions | No | — | 上面兩格已涵蓋 |
| **Web browsing** | Web browsing history | No | — | App 內沒有 WebView 也沒有瀏覽器；「前往官網註冊」是用 `Intent.ACTION_VIEW` 交給系統瀏覽器開固定網址，之後發生什麼本 App 看不到 |
| **App info and performance** | **Crash logs** | **Yes** | Firebase | Crashlytics 當機報告：**當機的程式堆疊、手機型號、Android 版本、App 版本、記憶體與空間餘量、當機時間**（與 `site/privacy.html` §5「當機與錯誤報告」那一列的前半段逐項對應） |
| | **Diagnostics** | **Yes** | Firebase | **非致命錯誤的分類代碼**（例如「任務頁解析失敗」）**與 HTTP 狀態碼**，加上 custom keys（`screen` = 當機前停在哪一個畫面、`demo_mode` = 當時是不是示範模式…）與 breadcrumbs。**不含任何錯誤訊息原文**——程式裡沒有把訊息交給當機回報的路徑，只送封閉列舉的分類碼（對應 `privacy.html` §5 同一列的後半段） |
| | Other app performance data | No | — | **沒有加 Firebase Performance SDK。** Google 對這一格的定義是啟動時間／耗電這類，Crashlytics 不提供。日後若加了 Performance，這格要改 Yes |
| **Device or other IDs** | **Device or other IDs** | **Yes** | Firebase | Firebase app instance ID、Firebase installation ID（FID）、Crashlytics installation UUID。Google 對這一格的舉例明白寫了「Firebase installation ID」。⚠️ **另外還有一個廣告識別碼的問題，見 §6——那是本文件唯一的「必須先改程式才能誠實作答」的一格** |

---

## 4. ⚠️ 唯一的判斷題：Fitness info 要不要勾

**事實**：使用者上傳的是「運動紀錄截圖」。那張圖的**內容**就是他的運動資料，而且本 App 存在的
目的就是把它送到官方網站。App 本身**不解讀、不解析、不推導**那張圖——對程式來說它是一團 bytes。

**兩種讀法都有依據：**

| | 勾 Yes（保守） | 勾 No |
|---|---|---|
| 依據 | Data safety 的 FAQ 原文：**"If you are purposefully collecting a data type during the collection of another data type, you should disclose both."** 一張運動紀錄截圖，正是「在蒐集 Photos 的同時，刻意地一併蒐集了 Fitness info」 | Google 對 Fitness info 的定義指向「運動或體能活動資料」這種**結構化**資料（Health Connect、計步 API）。本 App 送出的是不透明影像，沒有任何欄位化的健康數值；圖片本身已在 Photos 申報過 |
| 風險 | 商店頁多出「健康與健身」一列，稍微稀釋隱私賣點 | **若審查員把商店說明的「上傳運動紀錄截圖」與 Data safety 的「Health and fitness: 未蒐集」並排看，會看起來像漏報** |

**建議：勾 Yes（Collected／不 shared／required／App functionality）。**

理由是風險不對稱，而且這個專案的立場本來就是多勾：

1. 商店說明裡**明白寫著**「從相簿挑一張運動紀錄截圖，直接送到當期任務」。
   說明與 Data safety 不一致，是 Play 最常見的退件與違規理由之一。
2. 多勾一列的代價是可逆的（下次改表單就好）；漏報被抓到的代價是違規記錄。
3. 「App 不解讀那張圖」這個辯護在**技術上正確**，但它辯的是「App 有沒有理解這筆資料」，
   而 Data safety 問的是「這筆資料有沒有離開裝置」。**問錯題的辯護不會贏。**

> **如果你決定勾 No**：請在本檔留下決策紀錄與日期，並確認商店說明的措辭不會讓人以為
> App 在處理健康資料。**不要留著這一段不做決定**——空著就是下次填表的人自己猜。

---

## 5. 第三節「資料使用與處理」逐類答案

每一個勾選的資料類型都會問四題。下表把答案分成三組，因為同一組的答案完全相同。

### 第 A 組：送到 `500.gov.tw` 的（User IDs、Phone number、Other info、Photos、Fitness info）

| 題目 | 答案 | 理由 |
|---|---|---|
| "Is this data collected, shared, or both?" | **只勾 Collected** | Shared 不勾，依據是 user-initiated action 豁免（§1.2）。使用者按的按鈕就叫「登入」／「確認上傳」 |
| "Is this data processed ephemerally?" | **No** | **不要因為「開發者沒有伺服器所以什麼都沒留」就勾 Yes。** ephemeral 的定義是「只存在記憶體、保留不超過即時服務該請求所需」，而這些資料送達 `500.gov.tw` 之後會**長期存在使用者的帳號裡**（那本來就是重點）。勾 Yes 會讓這幾列從商店頁消失——那正是「標籤看起來比實際好」的定義 |
| "Is this data required, or can users choose?" | **Data collection is required** | 三欄個資是登入的必要條件，不填就不能用；截圖不上傳就無法完成任務。Google 明文：「You should not describe collection as optional if it is required for any of your app's users」 |
| "Why is this user data collected?" | **只勾 App functionality** | 不勾 Analytics（這些資料一個位元組都不進遙測）、不勾 Account management（帳號是官方網站的，不是開發者的）、不勾 Advertising／Personalization |

### 第 B 組：送到 Firebase 的（App interactions、Crash logs、Diagnostics、Device or other IDs、Approximate location）

| 題目 | 答案 | 理由 |
|---|---|---|
| "Is this data collected, shared, or both?" | **只勾 Collected** | Google 作為分析服務提供者處理本 App 的資料，符合 service provider 豁免。**注意這個豁免有條件**：原文說若 SDK 供應商「is building advertising profiles across multiple customers based on your app data, that would not be considered 'service provider' activity」。所以 §6 的廣告識別碼問題不只影響 Device IDs 那一格，**也影響這一整組能不能用 service provider 豁免** |
| "Is this data processed ephemerally?" | **No** | Firebase 會保留（GA4 的資料保留期預設 2 個月至 14 個月，看設定）。要縮短就到 Firebase Console 把保留期設成最短，但那不會讓它變成 ephemeral |
| "Is this data required, or can users choose?" | **Users can choose whether this data is collected** | App 內有真的開關：「我的資料 › 安全與隱私 › 傳送匿名使用統計」，**而且所有使用者、所有地區、所有機型都有**（Google 對這個答案的硬性條件正是這句）。另外 `Telemetry.configure()` 有三道 guard，未同意免責聲明前根本不初始化 |
| "Why is this user data collected?" | **Crash logs／Diagnostics／Device IDs：勾 Analytics + App functionality**；**App interactions／Approximate location：只勾 Analytics** | Crashlytics 的安裝編號與當機報告是拿來修 bug 的，「維持 App 正常運作」算 App functionality；純粹的畫面瀏覽統計不是 |

**這一組絕不可以勾的**：Advertising or marketing、Personalization、Developer communications、Account management。
`google_analytics_default_allow_ad_personalization_signals=false` 已在 manifest 裡，不投放廣告、不做個人化。

#### 「當機報告」怎麼拆成兩格 —— 與隱私權政策的逐項對照

Play 把當機相關的東西分成 **Crash logs** 與 **Diagnostics** 兩格，而 `site/privacy.html` §5
把它們寫在**同一列**（「當機與錯誤報告」）。**兩份文件沒有衝突，只是切法不同**——
Play 的表單有固定格子，給使用者看的政策沒有必要照 Google 的分類法寫。對照如下：

| `privacy.html` §5 的敘述 | 歸到 Play 的哪一格 | 為什麼 |
|---|---|---|
| 當機的程式堆疊 | **Crash logs** | Google 對這格的定義就是 crash log／stack trace |
| 手機型號、Android 版本、App 版本、記憶體與空間餘量、當機時間 | **Crash logs** | 這些是 Crashlytics 隨當機一起送的裝置與執行環境快照，屬於同一筆當機記錄 |
| 當機前停在哪一個畫面（`screen`）、當時是不是示範模式（`demo_mode`） | **Diagnostics** | 這是我們自己設的 custom keys，不是 crash log 本體 |
| 非致命錯誤的分類代碼、HTTP 狀態碼 | **Diagnostics** | 非致命事件不是當機，Google 把這類歸在 Diagnostics |
| **不送錯誤訊息原文** | （不對應任何格子） | 沒有送出去的東西不需要申報。這句話在政策裡是承諾，在表單上的體現是「沒有多勾任何自由文字類的格子」 |
| 一組隨機編號（app instance ID／installation ID） | **Device or other IDs** | Google 對這格的舉例明白寫了「Firebase installation ID」 |
| 粗略地區（IP 推導的國家層級位置） | **Location › Approximate location** | 見 §3 該列 |
| 匿名操作事件、Firebase 自動事件 | **App activity › App interactions** | 見 §3 該列 |
| 使用者屬性「沒有」 | （不對應任何格子） | `UserProperty` 是不可建構的空列舉，`setUserId` 從不呼叫 |

→ **兩份文件目前一致，不需要改任何一邊。** 日後若動了 `CrashKey` 或 `AnalyticsEvent` 的成員，
**兩邊都要改**（觸發點見 §9）。

### 第 C 組：完全不勾的

其餘全部 No，理由見 §3 的逐列說明。**不要為了「保險」去勾沒發生的事**——
Google 的 FAQ 明文：「Do I need to declare data if my app includes a permission but does not actually
collect or share the data?」→「You do not need to declare collection or sharing unless data is
actually collected and/or shared.」**多勾的原則只適用於「確實發生但歸類有爭議」的格子（如 §4），
不適用於「根本沒發生」的格子。**

---

## 6. ✅ 廣告識別碼：manifest 已修好，這一題答「不使用」

> **2026-09-07 已完成。** `AndroidManifest.xml` 用三個 `tools:node="remove"` 移除了
> `AD_ID`／`ACCESS_ADSERVICES_AD_ID`／`ACCESS_ADSERVICES_ATTRIBUTION`。
> **實測**：merged release manifest 的 `AD_ID`／`ADSERVICES` 命中數 **3 → 0**；
> AAB 內只剩 `INTERNET`、`ACCESS_NETWORK_STATE`、`WAKE_LOCK`、
> `BIND_GET_INSTALL_REFERRER_SERVICE`，與一個簽章層級的 dynamic-receiver 權限。
>
> **所以：**
> - Play Console 的**廣告識別碼宣告答「不使用」**；
> - **Device or other IDs 那一格仍然是 Yes**，但內容只有 Firebase 的
>   app instance ID／FID／Crashlytics installation UUID，**不含廣告識別碼**；
> - 商店說明與隱私權政策**可以寫「不使用廣告識別碼」**（已加進 `listing-zh-TW.md` §3）。
>
> ⚠️ **這是「有條件的真」**：成立的唯一理由是那三行 `tools:node="remove"`。
> 建議加一條 CI 檢查守住它，見 `blockers.md` R-4。
>
> 以下保留原始分析，說明為什麼這件事非做不可。

### （原始分析）為什麼不修不行

**問題**：`firebase-analytics` 會自己往 merged manifest 塞四個權限。實測 release 版
merged manifest（`app/build/intermediates/merged_manifests/release/.../AndroidManifest.xml`）：

```
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
<uses-permission android:name="android.permission.ACCESS_ADSERVICES_ATTRIBUTION" />
<uses-permission android:name="android.permission.ACCESS_ADSERVICES_AD_ID" />
<uses-permission android:name="com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE" />
```

而 Google 自己的 GA 文件明列 GA 會自動蒐集 **Advertising ID（Android 廣告識別碼）**。

**這件事的三個後果**：

1. **Play Console 會針對 `AD_ID` 權限單獨問你一題**（廣告識別碼宣告）。
   有這個權限而答「不使用」會被要求更正；答「使用」則 Device or other IDs 那一格必須包含廣告識別碼，
   而廣告識別碼是**可跨 App 追蹤**的識別碼，商店頁的呈現會與「不做跨 App 追蹤」的賣點打對台。
2. **會破壞 §5 第 B 組的 service provider 豁免論述**：一個會收廣告識別碼的分析 SDK，
   要主張它純粹「代表開發者處理資料」比較難講。
3. **與 iOS 端的既有敘述直接衝突**。iOS 端刻意用 `FirebaseAnalyticsCore`（不含 IDFA 收集能力），
   並在隱私政策與 App 說明寫「不建立廣告識別碼、不做跨 App 追蹤」。
   **Android 端如果照抄那句文案而不改 manifest，那句話就是不實陳述。**

**建議的修法**（兩處都要，只做一處不夠）——**這是程式碼改動，本文件不自行實施，請見 `blockers.md` B-7**：

```xml
<!-- app/src/main/AndroidManifest.xml -->
<!-- 不做廣告歸因，也不要廣告識別碼：把 firebase-analytics 帶進來的權限移掉。 -->
<uses-permission android:name="com.google.android.gms.permission.AD_ID"
    tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_ADSERVICES_AD_ID"
    tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_ADSERVICES_ATTRIBUTION"
    tools:node="remove" />

<!-- 光移掉權限只是讓 SDK 拿不到；再加這個旗標讓它連要都不要。 -->
<meta-data android:name="google_analytics_adid_collection_enabled" android:value="false" />
```

**改完之後的驗證方式**（不要只看原始碼，要看合併後的結果）：

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:bundleRelease
grep -i "AD_ID\|ADSERVICES" app/build/intermediates/merged_manifests/release/*/AndroidManifest.xml
# 預期：零命中
```

**改完才可以做的兩件事**：Data safety 的廣告識別碼那一題答「不使用」；
商店說明與隱私權政策加回「不使用廣告識別碼」。**順序不能顛倒。**

---

## 7. 為什麼這份表和 iOS 的隱私標籤長得不一樣（不是其中一邊填錯）

| 項目 | iOS 隱私標籤 | Play Data safety | 為什麼不同 |
|---|---|---|---|
| 送到 `500.gov.tw` 的三欄個資 | Collected + **Linked to You** | Collected，**不勾 Shared** | Apple 沒有「使用者主動觸發」豁免，且 Apple 的問卷以使用者視角問「這支 App 把我的什麼送出去」；Google 明文給了 user-initiated action 豁免 |
| 「Linked / Not Linked」 | 有這個維度，且是 iOS 端論述的重點 | **Play 沒有這個欄位** | Play 改問「required vs optional」與「purposes」。iOS 端花很多力氣辯護的「Not Linked」在這裡無處可填，別去找 |
| 出生日期 | Other Data | Personal info › **Other info** | 只是分類名稱不同，Google 對這格的舉例第一個就是 date of birth |
| 身分證號 | Identifiers › User ID | Personal info › **User IDs** | 同上，Google 把它放在 Personal info 底下而不是獨立的 Identifiers 類 |
| 粗略位置 | `TODO(待確認)` | **Yes** | Android 這邊查到明文（IP 推導位置要申報），iOS 端當時查不到官方對照表。**建議回頭把 iOS 那一格也補成 Yes** |
| Health / Fitness | Not Collected | **Fitness info 建議 Yes**（§4） | iOS 的辯護是「根本沒讀 HealthKit」，那是對的；但 Play 問的是「有沒有離開裝置」，上傳的截圖內容確實是運動紀錄。**這是兩邊最值得注意的一處分歧，決定後請在兩邊都留紀錄** |
| 加密存在本機 | 不申報（未離開裝置） | 不申報（on-device 豁免） | 兩邊一致 |

---

## 8. 隱私權政策必須與這份表逐格對得上

Play 的 User Data 政策對隱私權政策有**六項硬性內容要求**，比 Apple 明確得多：

1. **明確標示是隱私權政策**（標題要有「隱私權政策」字樣）
2. **點名主體**：商店資訊上的開發者名稱或 App 名稱必須出現在政策裡
3. **說明存取、蒐集、使用、分享了哪些個人與敏感資料，以及分享給哪些對象**
4. **敏感資料的安全處理程序**
5. **資料保留與刪除政策**
6. **開發者資訊與隱私聯絡方式**

且必須：**公開可存取、不得地理封鎖、不可是 PDF、不可編輯**，並且**同時**要在 Play Console 的
指定欄位與 **App 內**各放一個連結或全文。

### ✅ Android 版隱私權政策已經寫好了（2026-09-07）

原本這裡寫的是「必須補一份 Android 的段落或獨立頁」。**已經做完了**，而且是獨立的一份，
不是把 iOS 那份翻譯過來：

| 檢查項 | 狀態 |
|---|---|
| 網址 | `https://megshao.github.io/exercise_rewards_android/privacy.html`（`site/` + `.github/workflows/pages.yml` 發佈）⚠️ **要先 commit + push 到 develop、workflow 跑完才會活** |
| 政策要求 1（標示為隱私權政策） | ✅ 標題就是「隱私權政策」 |
| 政策要求 2（點名主體） | ✅ 開頭寫「適用版本：Exercise Rewards 1.0.0（Android，套件名稱 `com.megshao.exercise_rewards`）」 |
| 政策要求 3、4（蒐集／分享／安全處理） | ✅ AES-256-GCM + Android Keystore、`setRandomizedEncryptionRequired(true)`、StrongBox、停用備份與裝置轉移 |
| 政策要求 5（保留與刪除） | ✅ 且**界線寫對了**：本機可立即永久刪除；已送到 Google 的「查不到也刪不掉特定筆數」；官網帳號本 App 無權處理 |
| 政策要求 6（聯絡方式） | ✅ |
| 廣告識別碼 | ✅ 明寫移除了 `AD_ID` 與兩條 AdServices 權限，並告訴使用者怎麼自己查核 |
| iOS 專屬內容已清除 | ✅ 實測 `Keychain` 0 次、`iCloud` 0 次 |
| 粗略位置（對應 §3 的 Approximate location = Yes） | ✅ 有寫（IP → 國家層級） |

### ⚠️ 但還有一個缺口：**EXIF／GPS 那一段沒有寫**

實測 `site/privacy.html`：**`EXIF` 出現 0 次、`GPS` 出現 0 次。**

**為什麼這個缺口要補**（三個理由疊起來，不是潔癖）：

1. **商店說明已經對外承諾了。** `listing-zh-TW.md` §3 的完整說明裡有這一條：
   「上傳的截圖會先重新編碼，把 EXIF 與 GPS 座標丟掉才送出」。
   **商店說明講了、隱私權政策沒講**，正是 Play 拿來比對的那兩份文件對不齊。
2. **它是「Precise location = No」在使用者眼中真的成立的理由。** 相簿原檔的 EXIF 帶 GPS，
   那是「使用者在哪裡運動」的精確位置。政策裡說「不使用定位」是對的，但沒有回答
   「那我上傳的照片裡本來就有的座標呢」——那是一個讀得仔細的人一定會想到的問題。
3. **這是 Android 端做得比 iOS 強的地方，白放掉很可惜。** `ImageReencoder` 沒有 fallback：
   編碼失敗就報錯，**絕不退回原檔**。這種「沒有旁路」的設計正是這份政策第 8 節
   （可驗證性）的風格，值得寫進去。

**建議補在第 3 節（會離開你手機的資料）那張表的下面**，一段話就夠：

> 你自己從相簿挑的那張截圖，在送出前會先被**重新編碼成 JPEG**。
> 這一步的唯一目的是隱私：相簿原檔的 EXIF 裡可能帶著 **GPS 座標**——那是「你在哪裡運動」的
> 精確位置，比運動紀錄本身更敏感，而你按「確認上傳」時完全不會預期它被一起送出去。
> 重新編碼會把整段 EXIF 丟掉。**而且它沒有退路**：編碼失敗時 App 會直接報錯，
> **絕不會退回原檔**——因為那樣就等於把座標送出去了。

> ℹ️ 另外兩段 iOS 專屬內容也確認已處理：`IDFA`／`ATT` 已改寫成 Android 的 `AD_ID` 敘述；
> 「App 隱私權報告」那一層已改成 Android 的權限清單自查 + `docs/verify-network.md` 的攔包驗證
> （而且 `network_security_config.xml` 的 `<debug-overrides>` 讓那件事在 Android 上真的做得到）。

---

## 9. 什麼時候這份文件就過期了（改動觸發點）

- **放入或移除 `app/google-services.json`** → §3 的方案 A／B 切換，半張表要重填
- **加 Firebase Performance／Remote Config／Messaging／In-App Messaging** → Other app performance data、Device IDs 要重評
- **`AnalyticsEvent`／`CrashKey` 加入任何帶健康數值或達標結論的成員** → Health and fitness 立刻變 Collected，而且會推翻 §4 的整段論述
- **加入帶 `vendor`／`item` 參數的兌換事件** → App activity › Other actions 要重評
- **呼叫 `FirebaseCrashlytics.setUserId()` 或 `FirebaseAnalytics.setUserId()`** → Personal info › User IDs 會多一個 Firebase 來源，而且第 B 組的「不含個資」敘述失效
- **把關閉開關時的 `resetAnalyticsData()` 或 `deleteUnsentReports()` 拿掉** → §2 第 3 題（刪除）的答案會從「能停止並刪掉未上傳的」退回「只是不再收集」，隱私權政策第 7 節與商店說明都要同步改寫
- **改動 `network_security_config.xml` 的 `base-config`**（例如為了 debug 方便把 `cleartextTrafficPermitted` 開成 `true`，或把 `user` CA 從 `debug-overrides` 搬到 `base-config`）→ §2 第 2 題（傳輸加密）的答案與隱私權政策的相關敘述都要重評
- **恢復收集姓名／Email／健保卡卡號** → Personal info 要重填
- **`OkHttpHttpClient` 的網域白名單放寬** → 第 A 組的收受方要重新盤點
- **§6 的 manifest 修正做完（或被改回去）** → 廣告識別碼那一題與商店文案要同步改
- **加入自建後端** → 整份表重做，`README.md` 與所有文案的「開發者沒有伺服器」聲明一併失效

---

## 10. 送出前的自我檢查

- [ ] 確認**實際要上傳的那顆 AAB** 真的含 Firebase 設定（不要只看 repo 狀態）：
      `cd /tmp && unzip -o -q <path>/app-release.aab base/resources.pb && grep -c google_app_id base/resources.pb`
      → 應為 1。若為 0，表示那顆 AAB 是在 `google-services.json` 放進來之前建的，Firebase 那幾格要全部改成 No
- [ ] §4 的 Fitness info 已經做出決定並留下紀錄
- [x] §6 的 manifest 修正已完成並驗過 merged manifest（`AD_ID`／`ADSERVICES` 命中數 3 → 0）
      ℹ️ AGP 9 會產生 `merged_manifest`（單數，AAB 實際用的）與 `merged_manifests`（複數）**兩份**，
      實測兩份都是 0。檢查時記得先確認檔案真的找到了——`grep -c` 對沒命中的 glob 會印 0，看起來像通過
- [ ] 隱私權政策已補 Android 段落，且 §8 的三件事都寫了、iOS 專屬內容都清掉了
- [ ] 隱私權政策同時放在 Play Console 指定欄位**與 App 內**（App 內：「我的資料」頁需有可點的連結——**請確認 Android 版真的有，這是政策硬性要求**）
- [ ] 商店說明、隱私權政策、Data safety 三處對「使用統計」的敘述一致（都是「先告知 → 主動同意 → 預設開啟 → 隨時可關」，**不得出現「預設關閉」或「opt-in」**）
- [ ] Firebase Console 端：資料保留期設最短、關 Google Signals、關廣告個人化、不開 BigQuery、API key 限制套件名稱
