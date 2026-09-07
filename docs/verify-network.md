# 自己驗證 App 的網路行為（Android）

這份文件給想親眼確認「這支 App 到底連了哪裡、送了什麼」的人。

隱私權政策第 8 節把「你能驗證到什麼程度」分成四層；這份文件是第 1 層（看它連過哪些網域）
與第 2 層（看每一筆請求的內容）的操作說明。

Android 跟 iOS 在這件事上的差別，先講清楚，因為它決定你要走哪條路：

| | iOS | Android |
|---|---|---|
| 看網域清單 | 系統內建「App 隱私權報告」，只留 7 天 | 沒有內建對等功能，但**裝一支 App 就能逐 App 看連線**（不需 root、即時、可匯出） |
| 看請求內容 | 商店版就能攔（App 刻意不做 pinning） | **商店版攔不到**——Android 7+ 預設不信任你自己安裝的憑證。要自己 build 一個 debug 版 |
| 對照商店版與原始碼 | 商店版程式碼被 FairPlay 加密，讀不到 | 安裝檔**沒有加密**，可以反編譯 |

也就是說：第 1 層 Android 比 iOS 好、第 2 層 Android 比 iOS 麻煩、第 3 層 Android 比 iOS 好。

---

## 先講清楚：這些方法能驗到什麼、驗不到什麼

能驗到：

- App 連了哪些網域、每一筆請求的完整內容（網址、標頭、表單欄位、上傳的檔案）。
- 開關關著時，有沒有任何東西送到 Google。
- 送到 `500.gov.tw` 的欄位是不是只有登入必需的那三個。
- 安裝檔裡有沒有廣告識別碼權限（這一條連抓包都不用，見第 0 節）。

驗不到：

- 「所有情況下」的行為。你看到的是這一次操作的行為；程式理論上可以只在特定條件下才送東西。
  要補這塊，得讀原始碼（第 3 層），而讀原始碼又得處理「商店版是不是這份原始碼」（第 4 層）。
  黑箱測試的天限就在這裡，我們不假裝它沒有。
- Analytics 的事件內容不容易讀（二進位 protobuf）。你看得到網域、頻率、大小，字串欄位也會以
  明文 UTF-8 出現在封包裡（所以搜得到有沒有你的身分證號），但沒辦法像讀 JSON 那樣一眼看懂每個欄位。

> **驗證前必讀**：「傳送匿名使用統計」在你同意首次啟動的免責聲明之後**預設是開的**。
> 所以剛裝好正常使用時，看到 Google 網域是**預期中的**，不是異常。
>
> 要驗「零連線」有兩個時機：
> 1. **還沒按下免責聲明的同意**——那時 Firebase 一行程式碼都還沒執行。
> 2. **同意之後把開關關掉**（我的資料 › 安全與隱私），然後**強制停止 App 再重開**。
>    Firebase 沒有反初始化，同一次執行期間 SDK 仍在記憶體裡，所以一定要重開。
>    強制停止：設定 › 應用程式 › Exercise Rewards › 強制停止（從最近使用清單滑掉不一定夠）。

門檻與代價：

- 第 2 層要在手機上安裝並信任一張你自己產生的憑證，而且要自己建置 debug 版。驗完請移除憑證。
- 你自己的身分證號、生日、手機號碼會出現在 mitmproxy 的畫面與存檔裡。
  **不要把 flow 存檔直接貼到公開的 issue。**

---

## 0. 完全不用工具的兩件事

### 0.1 權限清單

設定 › 應用程式 › Exercise Rewards › 權限 →（往下找）「查看全部權限」。

應該只有這些，而且**沒有任何一條需要你按下同意**：

| 權限 | 誰要的 | 做什麼 |
|---|---|---|
| `INTERNET` | 本 App | 連官方網站。這是 App 唯一自己宣告的權限 |
| `ACCESS_NETWORK_STATE` | Firebase | 判斷有沒有網路 |
| `WAKE_LOCK` | Firebase Analytics | 上傳統計時避免中途休眠 |
| `…finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` | Firebase Analytics | 向 Play 商店問安裝來源 |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX 自動加入 | 確保 App 內部廣播不外洩 |

**看到「廣告識別碼」（`AD_ID`）、`ACCESS_ADSERVICES_*`，或任何位置／相機／相簿／身體活動的
權限，就是我們違反了隱私權政策第 4 節，請回報。** Firebase Analytics 預設會塞 `AD_ID`，
本 App 在 manifest 用 `tools:node="remove"` 把它連同兩條 AdServices 權限整條移除——
這是系統從安裝檔讀出來的事實，我們造不了假。

用電腦查更精確（不需 root）：

```sh
adb shell dumpsys package com.megshao.exercise_rewards | sed -n '/requested permissions/,/install permissions/p'
```

### 0.2 商店版的清單長什麼樣

```sh
adb shell pm path com.megshao.exercise_rewards      # 可能有多個 split
adb pull /data/app/~~xxxx/base.apk
apktool d base.apk -o out                            # 或 aapt2 dump badging base.apk
```

可以直接查核三件本頁宣稱的事：

1. `out/AndroidManifest.xml` 裡**沒有** `AD_ID`。
2. 裡面**沒有** `com.google.firebase.provider.FirebaseInitProvider`
   （這就是「同意之前 Firebase 不會偷跑」的實作，見隱私權政策第 5 節）。
3. `out/res/xml/network_security_config.xml` 的 `base-config` 只信任 `system` 憑證，
   `<debug-overrides>` 那一段不影響正式版。

---

## 1. 第 1 層：看它連過哪些網域（不需 root）

Android 允許 App 透過系統 VPN 介面觀察其他 App 的連線。裝一支這類工具就能**逐 App**看連線：

- **PCAPdroid**（開源，另有 TLS 解密功能，第 2 層也用得到）
- **NetGuard**（開源防火牆，可開啟連線紀錄）

在工具裡**只勾選 Exercise Rewards**，然後正常操作 App。

> 這類工具要佔用系統 VPN 介面，所以使用期間不能同時連別的 VPN。

### 預期會看到的網域

| 網域 | 誰發的 | 什麼時候 | 開關關著時會出現嗎 |
|---|---|---|---|
| `500.gov.tw` | App 自己的網路程式碼 | 登入、抓任務、上傳、兌換、券碼 | 會 |
| 官方網站存放截圖的圖片網域（實測為 `*.amazonaws.com`） | `ScreenshotLoader` 的專用 OkHttpClient（無 cookie 罐、無快取） | 只在點開「查看已上傳的截圖」時 | 會（只在那一刻） |
| `firebaseinstallations.googleapis.com` | Firebase SDK | 打開統計開關那一刻（要一組安裝編號） | **不會** |
| `app-measurement.com` | Firebase SDK | 開關開著時，事件批次上傳 | **不會** |
| `firebase-settings.crashlytics.com` | Firebase SDK | 開關開著時，啟動後抓設定 | **不會** |
| Google 的當機報告上傳端點 | Firebase SDK（`datatransport`） | 開關開著、且有當機或非致命錯誤要回報時 | **不會** |

前三個 Google 網域是從實際使用的 SDK 版本（`play-services-measurement 23.2.0`、
`firebase-installations 19.1.2`、`firebase-crashlytics 20.1.0`）的二進位裡直接撈出來的字串。

**最後一列我們寫不出確切網域，這一點必須誠實說**：當機報告是由 Google 的 `datatransport`
元件上傳的，端點寫在閉源設定裡，我們反編譯不出來。這剛好說明為什麼這一層比我們的說明可靠——
清單是你的手機記下來的，不是我們寫的。

也因此判準不是「只有這幾個」，而是：

> **在還沒同意免責聲明之前、或關掉開關並強制停止重開之後，任何 Google 網域都不該出現。**
> 同意後且開關開著時它們出現是正常的。

`app-measurement.com` 是 **Android 版** Analytics 的上傳網域；iOS 版用的是
`app-analytics-services.com`。同一個 Firebase 專案兩個平台連的網域不同，這是 SDK 的差別。

圖片網域是**官方網站**決定的，不是本 App 決定的：截圖網址由官網以 302 回傳。但 App 不是照單全收——
`TasksService.isAllowedScreenshotImageUrl` 會先檢查那個 `Location`：**scheme 必須是 `https`，
host 必須是 `500.gov.tw`（含子網域）或 `*.amazonaws.com`**，否則丟 `BlockedEgress` 並顯示載入失敗。
官方站若換到白名單外的網域，你會看到「無法載入截圖」而不是一個往陌生網域的請求
（那時請開 issue，我們會補上新網域）。

---

## 2. 第 2 層：看每一筆請求的內容

### 2.1 為什麼商店版攔不到

Android 7（API 24）之後，App 預設**不信任使用者安裝的 CA**。所以從 Play 裝到的正式版，
mitmproxy／Charles／PCAPdroid 的解密都會失敗（你會看到 App 顯示網路錯誤）。

這是平台的安全預設，不是我們加的限制，而且**我們不打算為了方便查核就對所有使用者關掉它**。

本 App 仍然**刻意不做 certificate pinning**——pinning 常被當成安全功能，但對一支主打
「你可以自己查」的 App 來說，它擋掉的其實是你。不做 pinning，加上下面的 debug 版設定，
就是我們能給的最大方便。

### 2.2 建置一個看得到內容的版本

```sh
git clone https://github.com/megshao/exercise_rewards_android.git
cd exercise_rewards_android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # 需要 JDK 21
./gradlew :app:installDebug
```

debug 版在 `app/src/main/res/xml/network_security_config.xml` 的 `<debug-overrides>` 裡
**明確允許**信任你安裝的憑證。那一段只對 `debuggable` 的版本生效，正式版不會套用——
那一行就是為了讓這件事做得到才寫的。

> **debug 版完全不接 Firebase。** 建置設定裡直接停用了 debug 的 Firebase 設定檔處理
> （`app/build.gradle.kts` 裡停用 `processDebugGoogleServices` 的那一行），所以 debug 版
> **沒有** `google_app_id` 之類的資源，`FirebaseApp.initializeApp` 回 `null`，遙測全程 no-op。
> 你在 debug 版上看到的流量就是純粹的 `500.gov.tw`——這剛好讓你把「App 自己的行為」單獨看清楚。

### 2.3 想連 Firebase 送出去的內容也一起看（最徹底的做法）

用**你自己的 Firebase 專案**：

1. 到 Firebase Console 開一個你自己的專案，加一個 Android app，套件名稱填
   `com.megshao.exercise_rewards.debug`（debug 版有 `.debug` 後綴）。
2. 下載**你自己的** `google-services.json`，放到 `app/`。
3. 把 `app/build.gradle.kts` 裡停用 `processDebugGoogleServices` 的那一段拿掉。
4. `./gradlew :app:installDebug`。

這樣所有統計都會送到**你自己的主控台**，你可以逐筆看到我們到底送了什麼事件、什麼參數。
這比讀我們的說明可靠得多。

### 2.4 接上 mitmproxy

電腦端：

```sh
brew install mitmproxy        # macOS；其他平台見 mitmproxy.org
mitmweb                       # 監聽 8080，瀏覽器介面在 http://127.0.0.1:8081
ipconfig getifaddr en0        # 查自己在 Wi-Fi 上的 IP
```

手機端：

1. 設定 › 網路和網際網路 › Wi-Fi › 目前網路 › 修改 › 進階選項 › 代理伺服器 › 手動：
   主機名稱填電腦 IP，連接埠 8080。
2. 用瀏覽器開 `http://mitm.it`，下載 Android 版憑證。
3. 設定 › 安全性 › 加密與憑證 › 安裝憑證 › **CA 憑證** →（確認警告）→ 選剛下載的檔案。

（也可以完全不用電腦：PCAPdroid 內建 TLS 解密，安裝它自己的 CA 憑證即可，同樣只對
信任使用者憑證的 debug 版有效。）

建議在 mitmweb 的過濾框輸入：

```
~d 500.gov.tw | ~d amazonaws.com | ~d google | ~d crashlytics | ~d firebase | ~d app-measurement
```

手機上其他 App 與系統服務的流量也會經過 proxy（其中不少 Google 服務有做 pinning，
會顯示成握手失敗，那是正常噪音，跟本 App 無關）。

---

## 3. 每個操作預期的請求

所有 `500.gov.tw` 的請求都在 `https://500.gov.tw/registrant/...` 底下。官方網站沒有 API，
全部是一般的網頁表單；App 以一般瀏覽器的身分提交同一份表單，因此 `User-Agent` 是
Android Chrome 的字串。

### 登入（首次設定、「重新登入」、冷啟動自動登入）

```
GET  /registrant/access                         ← 可能先被 302 到 ?_cookie_check=1 再回來，這是官網 CDN 的 cookie 握手，正常
POST /registrant/access
     _csrf=<官網給的一次性 token>&idNo=<你的身分證號>
     → 302 /registrant/login（已註冊）或 /registrant/register（未註冊，App 會停在這裡並導你去瀏覽器）

GET  /registrant/login
POST /registrant/login
     _csrf=<token>&idNo=<身分證號>&birthDate=<yyyy-MM-dd>&phone=<09xxxxxxxx>
     → 302 /registrant/member/tasks（成功）；回到 /login 或停在 200 就是三碼不符

GET  /registrant/member/tasks                   ← 登入成功後立刻抓一次任務
```

**要看的重點**：兩個 POST 的表單欄位就是上面那些。`_csrf` 是官網每頁給的一次性 token，
不是 App 產生的識別碼。沒有裝置 ID、沒有步數、沒有任何其他欄位。

### 抓任務（首頁刷新、任務分頁、券夾）

```
GET  /registrant/member/tasks                   → 200 HTML
```

沒登入或 session 過期時會 302 到 `/registrant/login`，App 會自動重新登入
（於是你會再看到一組上面的登入序列）。

App 有 60 秒的抓取節流與本機快取，所以連續刷新不一定每次都真的發請求——
看不到請求時先確認是不是被節流了。

### 上傳截圖

```
GET  /registrant/member/upload                  ← 抓 _csrf、確認本期還能上傳
POST /registrant/member/upload                  multipart/form-data
     _csrf=<token>
     screenshot=<你選的那張圖>
     → 302 /registrant/member/tasks（成功）；停在 200 是官網退件
```

**要看的重點**：multipart 裡只有 `_csrf` 與 `screenshot` 兩個部分。

**送出的圖片跟你相簿裡的原檔不會逐位元相同**——這是刻意的：相簿原檔的 EXIF 常含 GPS 座標，
那是「你在哪裡運動」的精確位置。App 上傳前一律用 `ImageReencoder` 重新編碼，EXIF 與 GPS
一併去掉。這一步**沒有備援路徑**：重新編碼失敗就直接報錯，絕不退回送出原檔。
你可以把送出的 bytes 存下來，用 `exiftool` 確認裡面沒有任何 GPS 欄位。

### 查看已上傳的截圖

```
GET  /registrant/member/screenshot/<期別 UUID>
     → 302 Location: https://<官方網站的圖片儲存網域>/...?...簽章...（實測是 AWS S3 的 presigned URL）

GET  https://<同上>                              ← 這一筆是 ScreenshotLoader 的專用 client 發的
```

**要看的重點**：往那個圖片網域的請求**不該帶 `Cookie` 標頭**，也不該帶 `Authorization`。
它能讀是因為網址裡自帶簽章，而那個網址是官網給的。

這件事有兩層保證，講清楚哪一層在做事：

- **第一層是 cookie 自己的網域範圍**：登入 cookie 的 domain 是 `500.gov.tw`，
  cookie jar 本來就不會把它送去別的網域。
- **第二層是這條路徑用的連線**：`ScreenshotLoader` 用自己建的 `OkHttpClient`，
  `cookieJar(CookieJar.NO_COOKIES)`、`cache(null)`，**完全沒有 cookie 罐**。
  這一層才擋得住「`Location` 指回 `500.gov.tw` 自己」的情況——那是同源，只靠第一層擋不住。
  （這也是為什麼本 App **不用 Coil／Glide**：它們預設共用 client 與磁碟快取，兩件事都跟上面的承諾相反。）

### 兌換

```
GET  /registrant/member/redeem/<期別 UUID>       ← 商家清單 + _csrf
GET  /registrant/intro/vendor-<代碼>.html        ← 只在你點「兌換品項」時
POST /registrant/member/redeem/<期別 UUID>
     _csrf=<token>&vendorId=<商家代碼>&item=<品項代碼>
     → 302（送出成功）；停在 200 是官網拒絕
```

### 券碼（簡訊驗證）

```
GET  /registrant/member/voucher/<期別 UUID>              ← 簡訊驗證頁 + _csrf
POST /registrant/member/voucher/<期別 UUID>/resend       _csrf=<token>          ← 重新發送簡訊
POST /registrant/member/voucher/<期別 UUID>              _csrf=<token>&otp=<你輸入的 6 碼>
     → 302 .../view（驗證成功）；停在 200 是輸錯
GET  /registrant/member/voucher/<期別 UUID>/view         → 200 HTML，內含券碼與條碼
```

**要看的重點**：簡訊驗證碼只出現在往 `500.gov.tw` 的那一筆 POST。券碼只出現在 `/view`
的回應裡，之後不會再被送到任何地方（條碼是在本機用 ZXing 畫出來的，不經任何服務）。

### 「立即清除本機資料」

App **沒有登出按鈕**，所以你不會看到 `POST /registrant/logout`。cookie 會在兩個時機被清掉，
兩者都不發任何額外請求：

- **重新登入時**：登入流程一開始就先清空舊 cookie，再走 `access` → `login` 握手。
- **「立即清除本機資料」時**：直接清空本機 cookie 與加密保存的欄位。

清除本機資料時，如果統計開關本來是開的，**會先送出最後一筆 `local_data_clear` 事件**，
然後才重置偏好與同意紀錄——順序是刻意的（見 `ProfileScreen.clearLocalData` 的註解），
因為偏好一旦重置那筆事件就送不出去了。所以你會在抓包工具上看到一筆往 Google 的請求，
**那是正常的**。之後就不會再有了：同意紀錄也被清掉，下次啟動會重新看到免責聲明。

---

## 4. 遙測打開後會多出什麼

在「我的資料 › 安全與隱私 › 傳送匿名使用統計」打開開關之後，會陸續看到：

1. `firebaseinstallations.googleapis.com`——要一組隨機安裝編號。這是整支 App 第一次接觸 Google。
2. `firebase-settings.crashlytics.com`——抓 Crashlytics 設定。
3. `app-measurement.com`——事件批次上傳，通常間隔幾十秒到幾分鐘。
4. 當機報告的上傳（有當機或非致命錯誤要回報時才出現；端點見第 1 節的說明）。

事件內容是 protobuf，mitmweb 會顯示成一堆不可讀的位元組，中間夾著可讀的字串。你能做的檢查是：

- 在 mitmweb 用搜尋（`~b <你的身分證號>`、`~b <你的手機號碼>`、`~b <你的生日>`）掃所有往
  Google 的請求。**應該一筆都搜不到。** protobuf 裡的字串是明文 UTF-8，真的有送就會被搜到。
- 順便搜期別 UUID 與券碼——一樣應該搜不到。
- 看每一筆的大小。事件名與參數值都是固定清單裡的短字串（`home`、`tasks`、`login_failed`、
  `site_error`……），單筆請求通常只有幾 KB。

想看得更清楚，走 2.3 節：把統計導到你自己的 Firebase 專案，直接在主控台上逐筆讀。

關掉開關後：這一次執行期間 SDK 還在記憶體裡（Firebase 沒有反初始化），但收集旗標已關、
安裝編號已重置、還沒上傳的事件與當機報告已刪除。要驗「關著時零連線」，請先強制停止 App 再重開。

---

## 5. 怎麼判斷「有沒有異常」

六條判準，任何一條不成立都請回報：

1. **權限清單裡沒有 `AD_ID`、`ACCESS_ADSERVICES_*`，也沒有位置／相機／相簿／身體活動。**
   （這條最容易查，見第 0 節。）
2. **開關關著、且 App 已強制停止重開之後**：除了 `500.gov.tw` 與官方網站回傳的圖片儲存網域，
   本 App 不該連任何地方。
3. **往 `500.gov.tw` 的表單欄位只有這幾個**：`_csrf`、`idNo`、`birthDate`、`phone`、
   `screenshot`（檔案）、`vendorId`、`item`、`otp`。多出任何欄位——尤其像步數、距離、
   裝置識別碼——就是異常。
4. **往圖片儲存網域的請求**：不帶 `Cookie`、不帶 `Authorization`，只在你點開截圖時發生，
   而且網域只會是 `500.gov.tw`（含子網域）或 `*.amazonaws.com`。
5. **遙測開著時，往 Google 的請求裡搜不到**你的身分證號、生日、手機號碼，也搜不到期別 UUID 與券碼。
6. **每一筆都是 `https://`**。官方站 302 的 `Location` 有時是 `http://`，client 一律正規化回
   https 再送，所以你看到的實際請求全部應該是 https。看到任何一筆 `http://` 就是異常
   （正式版的 `network_security_config` 也會直接擋掉明文流量）。

---

## 6. 驗完之後

1. 設定 › Wi-Fi › 目前網路 › 修改 › 代理伺服器 › 無。
2. 設定 › 安全性 › 加密與憑證 › 信任的憑證 › 使用者 → 移除 mitmproxy 憑證。
   （**這一步別忘了**：留著一張你電腦的 CA 在手機上，等於讓那台電腦能攔你所有信任使用者憑證的流量。）
3. 解除安裝你自己建置的 debug 版（它跟商店版是不同的套件名稱，可以並存）。
4. 如果你有存 flow 檔（mitmweb 的 File › Save），記得它裡面有你的個資，不要外傳。

---

## 7. 發現異常怎麼回報

- 信箱：megshao0918@gmail.com
- GitHub issue：<https://github.com/megshao/exercise_rewards_android/issues>

請附：App 版本（「我的資料」頁最底下）、Android 版本、手機型號、你看到的網域或欄位名、
當時在做什麼操作。**請把身分證號、生日、手機、cookie、`_csrf`、驗證碼、券碼先塗掉**——
我們不需要那些就能查。

---

## 8. 這份文件跟原始碼的對應

想對照原始碼確認上面寫的東西：

| 上面寫的 | 原始碼 |
|---|---|
| 網域白名單、強制 https、不跟隨 redirect、2 MB body 上限 | `core/.../networking/OkHttpHttpClient.kt`（`isAllowedHost`） |
| 登入序列與欄位 | `core/.../services/AuthService.kt` |
| 上傳欄位 | `core/.../services/UploadService.kt` |
| 上傳前去 EXIF／GPS | `app/.../data/ImageReencoder.kt` |
| 兌換與券碼 | `core/.../services/RedeemService.kt`、`VoucherService.kt` |
| 截圖 302 → S3 的白名單 | `core/.../services/TasksService.kt`（`isAllowedScreenshotImageUrl`） |
| 截圖的專用 client（無 cookie、不落盤） | `app/.../data/ScreenshotLoader.kt` |
| Firebase 什麼時候才初始化、送得出去的事件清單、六道閘門 | `app/.../telemetry/Telemetry.kt` |
| 移掉 `FirebaseInitProvider`、移掉 `AD_ID` 權限 | `app/src/main/AndroidManifest.xml` |
| 正式版只信任系統憑證、debug 版信任使用者憑證 | `app/src/main/res/xml/network_security_config.xml` |
| 個資與 cookie 的加密 | `app/.../data/KeystoreCrypto.kt`、`SecureBlobStore.kt`、`EncryptedCookieJar.kt` |
| 不進備份、不隨換機轉移 | `app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/data_extraction_rules.xml` |
| debug 版不接 Firebase | `app/build.gradle.kts`（停用 `processDebugGoogleServices`） |

（`core/...` 與 `app/...` 的完整前綴分別是
`core/src/main/kotlin/com/megshao/exerciserewards/core/` 與
`app/src/main/kotlin/com/megshao/exerciserewards/`。）

沒有任何一個檔案做 certificate pinning。這是刻意的：pinning 會讓你上面做的每一步都失敗。
