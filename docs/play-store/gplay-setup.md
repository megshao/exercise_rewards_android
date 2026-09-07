# Google Play 連線設定與發版 runbook（gplay CLI）

> 撰寫日期：**2026-09-07**　套件名稱：`com.megshao.exercise_rewards`
>
> 做法沿用 `~/Personal Projects/Nyoki` 的既有設定（`docs/working/RUNBOOK-gplay-setup.md`
> 與 skill `nyoki-android-release`）。**那一套已經在 Nyoki 上實跑過四次**（v1.0.0 初上架、
> v1.0.1–v1.0.3 三次發版），所以這裡不重新發明流程，只記「換到這支 App 有什麼不同」。
>
> 這份文件的分界線是：**哪些能用 CLI 做、哪些 Google 沒開 API 只能你本人在 Console 點。**

---

## 0. 機器側：不用重做，直接沿用

Nyoki 那次已經把整條路打通，而且**是同一個 Google Play 開發者帳號**，所以下面每一項
都不需要再建一次：

| 項目 | 現況（2026-09-07 實測） |
|---|---|
| `gplay` CLI | `/opt/homebrew/bin/gplay`（Nyoki 那次從原始碼建 v0.8.1；`--version` 只回 `dev`） |
| GCP 專案 | `nyoki-10111`，已啟用 `androidpublisher` 與 `playdeveloperreporting` |
| Service account | `nyoki-play-publisher@nyoki-10111.iam.gserviceaccount.com` |
| JSON key | `~/keys/nyoki-play-sa.json`（0600，未進任何版控） |
| auth profile | `nyoki` → `~/.gplay/config.json`（只存 key 路徑，不存 key 內容） |

```sh
gplay auth status
# {"profile":"nyoki","profiles":[{"name":"nyoki","type":"service_account",
#   "key_path":"/Users/<你>/keys/nyoki-play-sa.json"}]}
```

> **profile 叫 `nyoki` 但它代表的是「開發者帳號」，不是「某一支 App」。** 同一個帳號底下的
> 每一支 App 都用這個 profile。不要為了名字好看再開一個 profile 指向同一把 key——
> 多一份 key 路徑的副本沒有好處。
>
> ⚠️ **代價是護欄變薄了**：這把憑證同時能碰 Nyoki。所以本專案的每一條指令
> **一律明寫 `--package com.megshao.exercise_rewards`**，不要依賴 `GPLAY_PACKAGE`
> 或 `.gplay/config.yaml` 的預設值——預設值錯了會安靜地作用在另一支 App 上。
> 這也是本 repo 刻意**不**跑 `gplay init` 的原因。

### 這支 App 目前的授權狀態（實測）

```sh
gplay apps list
# → 只有 Nyoki! 蘑菇計時器 / com.nyoki.android

gplay edits create --package com.megshao.exercise_rewards
# → Error: googleapi: Error 403: The caller does not have permission, forbidden
```

403 的原因可能是「App 還沒建立」或「SA 沒被授權」，API 分不出來。但
`https://play.google.com/store/apps/details?id=com.megshao.exercise_rewards` 回 **404**，
所以幾乎確定是前者：**App record 還沒建立**。

---

## 1. 只有你本人能做的（Google 沒開 API）

這些項目**沒有 CLI 可以代勞**，全部要在 Play Console 網頁上點。順序有依賴，別跳。

### A. 建立 App record

https://play.google.com/console → **Create app**

| 欄位 | 填什麼 |
|---|---|
| App name | `Exercise Rewards`（可事後改） |
| Default language | 繁體中文（台灣） |
| App or game | App |
| Free or paid | Free |

> ⚠️ **package name 在第一次上傳 AAB 時才固定，之後永久不可更改。**
> 必須是 `com.megshao.exercise_rewards`（來源 `app/build.gradle.kts` 的 `applicationId`）。
> 注意底線——Kotlin package 是 `com.megshao.exerciserewards`（無底線），
> 兩者刻意不同，別填錯。

### B. 授權 service account（給最小權限）

SA email：`nyoki-play-publisher@nyoki-10111.iam.gserviceaccount.com`

Play Console → **Users and permissions** → 找到這個 SA（Nyoki 那次已經邀請進帳號了，
所以應該已經在清單裡）→ 編輯權限：

- **Account permissions 分頁：全部不勾**（尤其不要 Admin）
- **App permissions 分頁** → Add app → **Exercise Rewards** → 只勾：
  - ✅ View app information and download bulk reports（所有讀取的基礎；漏勾 `apps list` 會看不到這支 App）
  - ✅ Manage testing track releases
  - ✅ Manage testing tracks and edit tester lists
  - ✅ Manage store presence
  - ✅ View app quality information (ANRs and crashes)
  - ⬜ **Manage production releases —— 先不勾。** 反正還拿不到 production access（見 §3），
    要發正式版再回來加。憑證外洩時的傷害半徑差很多。
  - ❌ View financial data、Manage orders and subscriptions、Reply to reviews、Admin

權限傳播通常數分鐘。生效判準：

```sh
gplay apps list        # 應該開始看到 Exercise Rewards
```

### C. App setup（Console-only，而且是 closed testing 的前置條件）

Dashboard 的 **Set up your app** 逐項完成。**這一步沒做完，App 會停在 draft 狀態**，
而 draft 的 App 發不出 completed 的 closed release —— Nyoki 實測會被回：

```
Only releases with status draft may be created on draft app.
```

各項的逐欄位答案已經寫好，直接照抄：

| Console 區塊 | 照哪份文件填 |
|---|---|
| Main store listing（名稱、短敘述、完整敘述、分類、聯絡 email） | `listing-zh-TW.md` |
| 隱私權政策 URL | `https://megshao.github.io/exercise_rewards_android/privacy.html`（**已上線，回 200**） |
| Data safety | `data-safety.md`（逐題答案 + §0-A 的軌道適用範圍） |
| Content rating（IARC 問卷） | `content-rating.md` |
| App access | 本 App 需要官網帳號才能看到真實資料 → **提供示範模式的說明**（審查員走示範模式即可看完整功能，不需要真實身分證號） |
| Ads | **No**（本 App 沒有廣告；廣告識別碼權限也已移除，見 §4） |
| Target audience | 18+ |
| News / COVID-19 / Government / financial | No |
| 素材（512×512 icon、1024×500 feature graphic、≥2 張手機截圖） | `assets-checklist.md`；icon 與 feature graphic 已備在 `assets/`，**截圖還沒有** |

### D. 第一顆 AAB 走網頁上傳

Nyoki 的 runbook 記載：**新 App 的第一個 AAB 必須經 Play Console 網頁上傳，之後才能用 API。**
我沒有獨立驗證這條限制（要驗就得先建 App），但照著做的成本是零 —— 反正 App setup
沒完成前也發不了 closed release。

Play Console → Testing → **Internal testing** → Create new release → 上傳 AAB → Save
（內部測試不需送審）。

---

## 2. 之後就能全部走 CLI

### 每次發版前的離線檢查（不需憑證）

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew clean bundleRelease

AAB=app/build/outputs/bundle/release/app-release.aab
jarsigner -verify "$AAB" | grep -E "jar verified|jar is unsigned"   # 必須是 jar verified
gplay preflight --file "$AAB" --fail-on error                       # 注意是 --file，不是位置參數
shasum -a 256 "$AAB" && stat -f%z "$AAB"
```

> **`jarsigner -verify` 不可跳過。** 這個 repo 的簽章設定在四個值缺任一項時會
> **優雅退化成產出未簽章 AAB**（見 `app/build.gradle.kts` 檔頭）。那是刻意的設計，
> 讓任何 clone 的人都能 build；代價就是「build 成功」不等於「簽好了」。

#### 本專案的 preflight 基線（2026-09-07，versionCode 1）

**0 errors / 3 warnings / 1 info。** error > 0 才是要停下來查的訊號。

| severity | 內容 | 處置 |
|---|---|---|
| warning | `google_api_key pattern matched`（`base/resources.pb`） | Firebase 的 API key，本來就會進 App。**待辦**：到 Cloud Console 把它限制到套件名稱＋簽章憑證 |
| warning | `misplaced_files`：`BUNDLE-METADATA/.../app-metadata.properties` | AGP 自己放的，無害。Nyoki 基線也有 |
| warning | **`advertising_id`：偵測到分析 SDK 但沒宣告 `AD_ID`，「without it the SDK reads zeros」** | **刻意如此，不要修。** 見 §4 |
| info | 偵測到 Firebase Analytics / Crashlytics | 提醒要在 Data safety 揭露，已在 `data-safety.md` 做完 |

### 發到 closed testing（alpha）

release notes 只寫 zh-TW（首發只繁中）：

```json
[{"language": "zh-TW", "text": "• 首個封閉測試版本。"}]
```

```sh
gplay release --package com.megshao.exercise_rewards --track alpha \
  --bundle "$AAB" --release-notes @notes.json \
  --version-name v1.0.0 --skip-metadata --skip-screenshots
```

`gplay release` 沒有 `--dry-run`（這個版本沒有，上游較新版才有）。
`--skip-metadata --skip-screenshots` 是因為商店文案與素材由 Console 管理，
不要讓發版指令順手覆蓋掉。

### 同一顆 AAB 也要出現在 internal track

**不能重傳同一顆 AAB**（會回 403 `Version code N has already been used.`）。
正解是把既有 versionCode 指派到目標 track：

```sh
ID=$(gplay edits create --package com.megshao.exercise_rewards --output json \
      | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
gplay tracks update --package com.megshao.exercise_rewards --edit "$ID" \
      --track internal --releases @internal.json
gplay edits validate --package com.megshao.exercise_rewards --edit "$ID"
gplay edits commit   --package com.megshao.exercise_rewards --edit "$ID"
```

`internal.json`：
```json
[{"name": "v1.0.0", "versionCodes": ["1"], "status": "completed",
  "releaseNotes": [{"language": "zh-TW", "text": "• 首個封閉測試版本。"}]}]
```

不用 `gplay promote --from alpha --to internal`：無法確認它會不會清掉來源 track 的 release。
`tracks update` 只動目標 track，風險可控。

### 管理測試者

```sh
gplay testers get   --package com.megshao.exercise_rewards --edit "$ID" --track alpha
gplay testers patch --package com.megshao.exercise_rewards --edit "$ID" --track alpha ...
```

測試者以 **opt-in 連結實際加入之後**才開始計 14 天（見 `closed-testing-plan.md` §4）。

### read-back 驗收（必做）

```sh
ID=$(gplay edits create --package com.megshao.exercise_rewards --output json \
      | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
gplay tracks list --package com.megshao.exercise_rewards --edit "$ID" --output table
```

`tracks list` **一定要帶 `--edit`**。兩個 track 都要顯示正確的 versionName／versionCode／
`completed` 才算完成。

---

## 3. 12 位測試者 × 14 天：這支 App 要自己再跑一次

Nyoki 的帳號**已經有 production access**（`com.nyoki.android` 在 Play 上公開可見，
而且它的 working notes 記載 `edits.commit` 到 production 直接成功）。

**但那不會讓這支 App 免測。** 2026-09-07 查證的結論：這個要求是**逐 App** 的，
測試者與天數不會在 App 之間累積 —— 同一批人可以再測，但必須對新 App 的軌道重新 opt-in，
14 天從零開始算。

> ⚠️ **這一條的證據強度要說清楚**：官方頁面
> （`support.google.com/.../answer/14151465`）從這台機器與沙箱**都被 CAPTCHA 擋住**，
> 我沒能親自讀到原文。上面的結論來自多個第三方來源的一致說法，以及 Play Developer
> Community 的指南。**填表前建議你自己開一次那頁確認**——你的瀏覽器不會被擋。
>
> 好消息是這件事**不影響今天的任何一步**：不管答案是哪個，今天要做的都是
> 建立 App → 完成 App setup → 上傳第一顆 AAB → 發到封測。差別只在「多久後能上正式版」。

---

## 4. 刻意留著的 preflight warning：`advertising_id`

```
warning  advertising_id
  an ads/attribution SDK is present but com.google.android.gms.permission.AD_ID
  is not declared (targetSdk 37)
  hint: apps targeting Android 13+ must declare this permission to receive the
        advertising ID; without it the SDK reads zeros
```

**這條不要照 hint 修。** 它假設你想要廣告識別碼；本 App 不要，所以在
`AndroidManifest.xml` 用 `tools:node="remove"` 把 `AD_ID` 與兩條 `ACCESS_ADSERVICES_*`
整條移除了（理由見那個檔案的註解與 `site/privacy.html` 第 4 節）。

「the SDK reads zeros」正是我們要的結果 —— 這條 warning 反而是**移除確實生效的獨立證據**，
而且是由第三方工具給出的，比我們自己說有說服力。

把權限加回去會連帶產生兩個後果：Data safety 必須改答「收集廣告識別碼」，
而使用者在系統設定裡會看到「廣告識別碼」，與隱私權政策第 4 節的承諾直接矛盾。

---

## 5. 永遠不會有 API 的部分

| 項目 | 為什麼 |
|---|---|
| 建立 App record | Android Publisher API 沒有 create app 端點 |
| Data safety 表單 | 無 API |
| Content rating 問卷（IARC） | 無 API |
| App content 各項聲明 | 無 API |
| 申請 production access | 無 API（結果以 email 通知帳號擁有者） |
| 第一顆 AAB | 見 §1-D |

所以「全自動上架」在 Google Play 上是做不到的。CLI 能自動化的是**第二次之後的每一次發版**，
那才是它真正省時間的地方。
