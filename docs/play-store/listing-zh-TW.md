# Google Play 商店資訊文案（繁體中文 zh-TW）

> 對應版本：`versionName 1.0.0` / `versionCode 1`
> 套件名稱：**`com.megshao.exercise_rewards`**（debug 版為 `com.megshao.exercise_rewards.debug`）
> 撰寫日期：2026-09-07　語系：只提供「繁體中文（台灣）」一種（App 本身鎖 zh-Hant）
>
> 上游文案基礎是 iOS 端 `docs/release/app-store-metadata.md`。**這份不是重寫，是對齊**——
> 立場、用語、界線全部沿用，只改掉平台專有名詞（Keychain → Android Keystore、
> iCloud → 雲端備份、IDFA → 廣告識別碼）與 Play 特有的政策要求（見下方 §0）。

---

## 0. 三條不可越過的文案界線（改任何一個字之前先讀這裡）

原文與理由在 iOS 端 `App/Sources/Views/DisclaimerView.swift` 的檔頭註解，Android 端對應
`app/src/main/kotlin/com/megshao/exerciserewards/ui/screens/DisclaimerScreen.kt`。
**違反其中任何一條，等於推翻整個專案的立場，而且在 Play 這邊會直接變成政策違規。**

| # | 界線 | 為什麼 | Play 對應條款 |
|---|---|---|---|
| 1 | **不可暗示官方身分、授權或合作。** App 名稱一律 `Exercise Rewards`；「揮汗有禮」只能出現在「協助你參加『揮汗有禮』活動」這種描述用途的句子裡，且必須與「非官方」相鄰 | 官方沒有授權，也沒有合作。用活動名當 App 名等於冒充 | 〈Misrepresentation／Misleading Claims〉禁止「falsely claim affiliation with a government entity」；〈communicate government information〉要求「Make it clear that the app doesn't represent a government or political entity」 |
| 2 | **不可寫「不儲存個資」。** 三欄個資確實存在裝置上（AES-256-GCM + Android Keystore）。準確說法是「**只存在你的手機，開發者收不到**」 | 寫「不儲存」與實作不符，且會與隱私權政策、Data safety 表單互相矛盾 | 〈Metadata〉要求 metadata「accurately reflect its functionality」；與 Data safety 不一致是常見的退件理由 |
| 3 | **不可寫「串接官方 API」。** 官方網站沒有公開 API，本 App 是**以一般瀏覽器的身分提交同一份網頁表單** | 寫成 API 會暗示某種官方授權或技術合作關係 | 同 #1 |

**Play 另外要求的兩件事**（iOS 沒有的，這是 Android 端新增的硬性要求）：

- **必須在說明裡標明資料來源，而且要好找。** 原文：「Include easy-to-see information sources in
  your app's description and store listing page」，且來源應指向官方網域好讓使用者自行查證。
  → 完整說明的**第一段**與**【資料從哪裡來】**兩處都明寫 `https://500.gov.tw`。
- **名稱、圖示、主要宣傳圖、截圖都不得出現官方標誌／活動識別。** 這是 Misrepresentation 與
  IP 兩條各自獨立的下架理由。→ 見 `assets-checklist.md`。

**另外，Play 的〈Metadata〉政策禁止而 App Store 不管的**：標題不得含 emoji 或重複特殊符號；
避免全大寫（除非是品牌名）；不得出現 `免費`／`無廣告`／`#1`／`最佳`／`Editor's choice` 這類
價格、排名、獎項或 Play 專案的字樣；不得堆疊關鍵字。**下列文案已逐條檢查過。**

---

## 1. 應用程式名稱（App name）

- **上限 30 字元**　**實際 16 字元**

```
Exercise Rewards
```

- 與 `app/src/main/res/values/strings.xml` 的 `app_name` 一致（已是 `Exercise Rewards`），
  兩者必須相同，否則使用者在 Play 上看到的名字與裝機後的圖示名字會不一樣。
- **不可以**用「揮汗有禮」當名稱（界線 #1）。也不可含「運動部／政府／官方／500／國徽」。
- 為什麼是 `Exercise` 而不是 `Sports`：運動部的英文名是 Ministry of **Sports**，
  非官方 App 用 Sports Rewards 等於在英文名上與主辦機關共用關鍵字，徒增裁量風險。
  （沿用 iOS 端 2026-09-07 的定案理由。）

---

## 2. 簡短說明（Short description）

- **上限 80 字元**　**實際 49 字元**

```
「揮汗有禮」活動的非官方工具。免重打個資、看懂任務進度、管理加碼券。資料來源：500.gov.tw
```

**設計理由**：80 字元要同時塞進「這是什麼」「這不是官方的」「資料來源」三件事。
「非官方」緊接在活動名之後（界線 #1），資料來源放句尾（Play 的政府資訊要求）。
Play 的字元上限對全形／半形一視同仁，所以中文一字算一字元，這裡還剩 31 字元的餘裕。

**刻意沒寫的**：「免費」「無廣告」（Play 的 Metadata 政策禁止價格與促銷字樣）。

---

## 3. 完整說明（Full description）

- **上限 4000 字元**　**實際 1378 字元**

```
Exercise Rewards 是一款非官方的個人輔助工具，協助你更省事地參加「揮汗有禮・全民動起來」運動幣加碼活動。本 App 由獨立開發者製作，與運動部及任何政府機關沒有隸屬、合作、贊助或授權關係，也不代表活動主辦單位。畫面上的任務、審核結果與加碼券資料，全部來自活動官方網站 https://500.gov.tw ；活動規則與最終權益一律以官方公告為準。

【它幫你做什麼】
・免重複打字：身分證號、出生日期、手機號碼填一次，之後一鍵登入官方「我的任務」。
・一眼看進度：14 期任務的狀態、開放時間與倒數，整理成看得懂的卡片，本週的排最上面。
・先看能換什麼：兌換前可以逐一查看每個超商／賣場的可兌換商品分類與品項，挑定了再送出。
・上傳不迷路：從相簿挑一張運動紀錄截圖，直接送到當期任務。
・券夾收好：兌換到的加碼券集中一頁，要用的時候直接出示條碼。

【資料從哪裡來】
本 App 沒有官方授權，也沒有使用任何官方 API——官方網站並未提供。它做的事情跟你自己開瀏覽器一樣：以一般瀏覽器的身分，把同一份網頁表單送到 https://500.gov.tw 。所以你在 App 裡看到的每一個數字，都是官方網站當下回給你的內容；官方網站改版或維護時，本 App 可能顯示不正確或暫時無法使用。

【你的資料在哪裡】
・只存在你的手機，開發者收不到。開發者沒有任何伺服器與後台，看不到也拿不到你的身分資料。
・加密保存：身分證號、出生日期、手機號碼以 AES-256-GCM 加密，金鑰由 Android Keystore 保管（支援 StrongBox 的機型存在安全晶片內）。不進雲端備份、不隨裝置轉移。
・只在你按下登入時，由這支手機直接送到 500.gov.tw，中間不經過開發者或任何其他服務。
・不做多餘蒐集：姓名、Email、健保卡卡號一律不收，只留登入必要的三欄。
・上傳的截圖會先重新編碼，把 EXIF 與 GPS 座標丟掉才送出——你在哪裡運動不該跟著截圖一起離開手機。
・隨時刪光：「我的資料」頁按下「立即清除本機資料」即永久刪除。
・使用統計：先告知，同意後預設開啟。第一次打開 App 會先擋一張免責聲明，上面明寫「會把匿名的操作紀錄與當機報告送給 Google Firebase」，你按下同意它才啟動——在那之前一行程式碼都不會執行。之後預設是開的，可隨時在「我的資料 › 安全與隱私」關掉。送出的只有「開了哪個畫面、哪一步失敗、有沒有當機」，不含身分證號、生日、手機號碼、你上傳的截圖與券碼。
・沒有廣告、不使用廣告識別碼、不做跨 App 追蹤。

【開源可稽核】
程式碼以 MIT 授權公開，處理個資與網路連線的每一行都可以自己看、自己查。

【使用前請先知道】
・你必須先在官方網站完成註冊。本 App 不提供註冊功能，也不會替你通過任何身分驗證。
・審核結果、兌換次數與券的使用權益全部由官方網站決定，本 App 不做任何判定，也無法代為修改。
・活動或帳號有問題請直接聯繫官方客服；本 App 查不到你的帳號狀態。App 本身的問題（畫面錯誤、當機）才需要找開發者。
・需要 Android 8.0（API 26）以上，僅支援直向，介面為繁體中文。

【回報問題】
megshao0918@gmail.com
```

### 這段文案的幾個刻意選擇

- **第一段就把三件事講完**：非官方、無授權、資料來源網址。Play 的政府資訊要求是
  「easy-to-see」，塞在最後一段不算好找。
- **【資料從哪裡來】是 Android 端新增的一段**（iOS 版沒有）。它同時處理界線 #3
  （明說「沒有使用任何官方 API」）與 Play 的來源標示要求，而且順手把
  「官網改版就可能壞掉」的期待值先講掉——那是這類非官方 client 最常見的負評來源。
- **「只存在你的手機，開發者收不到」是第一句**（界線 #2）。全文沒有出現「不儲存」。
- **EXIF／GPS 那一條是 Android 端才寫的**：`ImageReencoder` 沒有 fallback，
  這是可以拿出來講的實作事實，而且它正好對應 Data safety 表單裡
  「Photos and videos」那一格的答案（見 `data-safety.md` §5）。
- **「不使用廣告識別碼」這句已經可以寫了**（2026-09-07 下午起）。
  原本不能寫，因為 merged manifest 帶著 firebase-analytics 塞進來的
  `com.google.android.gms.permission.AD_ID`。該修正（`blockers.md` B-7）**已完成並實測**：
  merged release manifest 的 `AD_ID`／`ADSERVICES` 命中數從 3 降為 **0**。
  → 這句話現在是真的，可以留在文案裡。**但它是有條件的**：
  哪天有人把那三個 `tools:node="remove"` 拿掉，這句就會變成不實陳述。

---

## 4. 更新說明（What's new / Release notes）

- **上限 500 字元**（Play 的版本資訊欄位比 App Store 短很多，注意）　**實際 276 字元**

```
1.0 首次釋出（封閉測試）。

・一鍵登入官方「我的任務」：身分證號、出生日期、手機號碼只要填一次。
・14 期任務儀表板：狀態、開放時間與倒數一次看完，本週的排最上面。
・從相簿挑一張截圖直接上傳到當期任務，送出前會先去掉 EXIF 與 GPS。
・兌換加碼券並收進券夾，要用時出示條碼。
・個資以 AES-256-GCM 加密只存本機，可一鍵永久清除；開發者沒有伺服器。
・匿名使用統計與當機回報（Google Firebase）：首次啟動的免責聲明會先告知，你按下同意才啟動，之後預設開啟、可隨時關掉，不含個資。
・程式碼以 MIT 授權開源。
```

> ⚠️ Play 的「版本資訊」欄位上限是 **500 字元**（不是 App Store 的 4000）。
> 每個語系各自一份；只有 zh-TW 要填。

---

## 5. 其他商店資訊欄位

| 欄位 | 值 | 備註 |
|---|---|---|
| 應用程式類別 | **健康與健身**？→ **不建議**。選 **生活風格（Lifestyle）** | 沿用 iOS 端 2026-09-07 的定案理由：App 不量測也不追蹤任何運動表現，放在健康與健身會與內容不符，且使用者有期待落差。Play 沒有「次要類別」欄位，所以工具性質無處可放，只能二選一 |
| 標籤（Tags） | 最多 5 個，建議：生活風格、實用工具 | Play 的 tag 是從固定清單挑，不是自由關鍵字 |
| 電子郵件地址 | `megshao0918@gmail.com` | **必填**，會公開顯示在商店頁 |
| 網站 | `https://megshao.github.io/exercise_rewards_android/` | 選填。Android repo 自己的 GitHub Pages（`site/index.html`）。⚠️ **要先 push 到 develop、workflow 跑完才會活** |
| 電話 | 留空 | 選填，會公開顯示 |
| 隱私權政策 | `https://megshao.github.io/exercise_rewards_android/privacy.html` | **必填**，Play 對所有 App 都要求，沒有例外。⚠️ 同樣要先 push 才會活；Console 會即時驗證網址，**先確認打得開再填** |

### Play 沒有的欄位（別去找）

iOS 有、Play 沒有的：**副標題（Subtitle）**、**宣傳文字（Promotional Text）**、**關鍵字（Keywords）**。

這對本專案有一個具體後果：**iOS 端把「非官方」三個字放在副標**，那是商店頁第一屏就看得到的位置。
Play 沒有副標，所以那個責任整個落到**簡短說明**上——`§2` 的第一句因此必須是
「『揮汗有禮』活動的非官方工具」，順序不能調。

另外 Play 沒有 keywords 欄位，搜尋只索引名稱與說明文字。這反而讓 iOS 端
「刻意不把活動名放進 keywords」的取捨消失了——說明文字裡本來就必須提到活動名才講得清楚用途。

---

## 6. 字元數自我檢查

改文案後請重跑一次（Play 對全形／半形一視同仁，中文一字算一字元）：

```sh
# 在 docs/play-store/ 底下
python3 - <<'PY'
import re, pathlib
text = pathlib.Path("listing-zh-TW.md").read_text(encoding="utf-8")
blocks = re.findall(r"```\n(.*?)\n```", text, re.S)
limits = [("App name", 30), ("Short description", 80), ("Full description", 4000), ("What's new", 500)]
for (name, limit), body in zip(limits, blocks):
    n = len(body)
    print(f"{name:20} {n:5} / {limit}  {'OK' if n <= limit else '*** OVER ***'}")
PY
```
