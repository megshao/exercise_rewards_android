# 內容分級問卷（IARC）逐題建議答案

> 套件名稱：`com.megshao.exercise_rewards`　撰寫日期：**2026-09-07**（政策與題目說明於同日查證）
>
> 預期結果：**全年齡／3+（IARC Generic）、ESRB Everyone、PEGI 3、USK 0**。
> 這支 App 沒有任何暴力、性、語言、藥物、賭博或恐怖內容，唯一會影響分級的是
> **§3 的「分享個人資訊」——那一題答「是」，而且必須答「是」。**

---

## 0. 依據（2026-09-07 查證）

| 主題 | 來源 |
|---|---|
| 問卷流程、分級機構、送出後會發生什麼 | https://support.google.com/googleplay/android-developer/answer/9859655 |
| 內容分級政策 | https://support.google.com/googleplay/android-developer/answer/9898843 |
| **逐題說明（本文件所有引文的出處）** | https://support.google.com/googleplay/android-developer/topic/6169337 |
| 目標對象與內容 | https://support.google.com/googleplay/android-developer/answer/9867159 |
| IARC 運作方式與申訴 | https://globalratings.com/how-iarc-works/ · https://globalratings.com/faq/ |

> ⚠️ **必須先講清楚的限制**：**Google 與 IARC 都刻意不公開問卷的題目原文與分節標題。**
> IARC 的說法是問卷由各參與分級機構共同開發、以演算法計算。
> 公開的只有 Play Console 說明中心的**逐題說明文章**（上表第三列），那些文章寫的是
> 「什麼情況該答 Yes」的判準，而不是螢幕上的題目原文。
> **所以本文件引用的是判準原文，題目名稱則是說明文章的標題。** 進了問卷若看到措辭不同，
> 請以判準為準；若某一題本文件沒有涵蓋，請照「App 裡實際有沒有這件事」誠實作答，
> 不要為了維持某個預期分級去猜。

---

## 1. 流程（進 Console 之後照這個順序）

1. Play Console → **政策與方案 › 應用程式內容（App content）** → 「內容分級」→ **開始**
2. 填 **電子郵件地址**。這個信箱是**與 IARC 通信用**的（分級證明、日後機構覆核與申訴都寄這裡），
   不是客服信箱。填 `megshao0918@gmail.com`
3. **選類別** → 見 §2
4. **填問卷** → 見 §3、§4
5. 看 **Summary 頁**上算出來的各機構分級 → **送出**

**先決條件**（Console 會擋）：同一個「應用程式內容」頁面裡的
**廣告宣告**、**應用程式存取權（App access）**、**隱私權政策**要先填好，
「目標對象與內容」才會開放。所以實務上的順序是：隱私權政策 → 廣告 → App access → 內容分級 → 目標對象。

**送出後**：IARC 的分級是**立即發出**的（「Once a developer completes the questionnaire, the ratings
are issued immediately」），不用等人審。但 Google 另外提醒：
Summary 頁上算出來的分級**不一定就是使用者最後看到的**——分級機構事後覆核可以改，
而且「App updates and submissions can be rejected for misrepresenting an app's content」。

> ⚠️ **不填的後果是移除，不是掛個標籤**（`answer/9898843` 逐字）：
> 「**We don't allow apps without a content rating on Google Play.**」
> 「You must complete the content rating questionnaire for each new app submitted to the Play
> Console… **Apps without a content rating will be removed from the Play Store.**」
> 「Misrepresentation of your app's content may result in removal or suspension…」
>
> 而且它是**封測的前置條件**：`answer/14151465` 的「Requirements to access track」表格
> 對 Closed testing 的要求是「**Complete app setup.**」，內容分級就在那份清單裡。

**日後什麼時候要重填**：只要改動的內容或功能會讓任何一題的答案改變就要重填。
對本專案來說最可能的觸發點是**加入使用者之間可見的內容或任何社群功能**（見 §3 第 1 題）。

---

## 2. 類別選擇

**選「工具、生產力、通訊或其他（Utility, Productivity, Communication, or Other）」。**

依據是這個類別的說明文章原文：

> "Developers should select this category for any app that doesn't belong to other listed categories."

而它列舉的例子裡**明白包含 fitness apps**、交通時刻表、預約類 App——本 App
（看任務進度、管理券、預約式的兌換流程）正落在這一群。

**被否決的三個類別與理由**：

| 類別 | 為什麼不選 |
|---|---|
| Reference, News, or Educational | 該類要求「Content should be presented clinically and in a neutral way」，且明白排除「apps with a significant focus on transaction capabilities」。本 App 的核心是兌換這個交易動作 |
| Social Networking, Forums, and UGC Sharing | 該類說明原文特別警告不要誤選：「should only be chosen for apps that actually meet the definition and **not for apps that simply connect to apps that meet the definition**」。本 App 沒有任何使用者之間的互動 |
| Consumer Store or Commercial Streaming Service | 該類指的是賣實體或數位商品的商店。本 App 不販售任何東西，兌換是官方活動的既有機制，且金流與核銷完全不經過本 App |

> ⚠️ **注意兩件事**：
> 1. 問卷的類別**與商店資訊的「應用程式類別」是兩份不同的清單**。
>    商店資訊那邊選「生活風格」（見 `listing-zh-TW.md` §5），這裡選「工具…或其他」，
>    **兩者不一致是正常的**，不要互相對齊。
> 2. 完整的類別選單只有在 Console 裡看得到（說明中心只有上述四篇＋遊戲類）。
>    若看到更貼切的選項，以「App 實際做什麼」為準。

---

## 3. 四題關鍵題（本 App 唯一需要動腦的部分）

### 3.1「線上互動或內容交換（Online Interaction or Content Exchange）」→ **否**

判準原文：

> "Submitters should answer 'Yes' if users can freely exchange content they have created. This
> includes the ability to communicate between users, comment on provided content, share photos, or
> exchange any other type of content created by users.
> To help define an online interaction or content exchange, developers should **only consider their
> app's native services** and not consider sharing that is accomplished by using secondary apps."

**答「否」的理由**：這一題問的是**使用者之間**能不能自由交換內容。本 App

- 沒有帳號系統（開發者端）、沒有留言、沒有按讚、沒有分享、沒有任何使用者清單；
- 使用者上傳的截圖送到**官方網站的他自己的任務紀錄**，**其他使用者看不到**
  （`ScreenshotScreen` 只能看自己上傳的那一張，而且走的是 cookie-less 專用 client）；
- 「前往官網註冊」是用 `Intent.ACTION_VIEW` 交給系統瀏覽器——正是判準裡
  「secondary apps」那一句要排除的情形。

> **這一題答錯的代價很大**：答「是」會直接把分級往上推，並可能觸發
> 使用者生成內容的額外政策義務（檢舉機制、內容審核）。本 App 完全沒有那些東西，
> 答「是」等於自找一堆做不到的義務。

### 3.2「向他人顯示使用者位置（Displays User's Location to Others）」→ **否**

判準原文：

> "Submitters should answer 'Yes' **only if** the app transmits the user's current, precise location
> to other people. … Essentially, if users of the app could find each other in the real world based
> on information provided by the app, then this question should be marked 'Yes.' …
> **Submitters should not say 'Yes' if the app merely uses the users' location but does not share it
> with others.**"

**答「否」的理由**：本 App 連「使用」位置都沒有——原始 manifest 只宣告 `INTERNET`，
**沒有任何定位權限**，程式裡也沒有任何位置 API。
（合併後的 manifest 多出 `ACCESS_NETWORK_STATE`／`WAKE_LOCK`／`BIND_GET_INSTALL_REFERRER_SERVICE`
三個 Firebase 帶進來的權限，全部與位置無關；`AD_ID` 等三個廣告權限已被
`tools:node="remove"` 移除，實測命中數為 0。）

**而且更強一層**：上傳的截圖在送出前會由 `ImageReencoder` 重新編碼，
**把 EXIF 裡的 GPS 座標整段丟掉**，且**編碼失敗不退回原檔**。
所以連「使用者在哪裡運動」這個間接位置也不會離開裝置。

> 注意這與 `data-safety.md` §3 把 **Approximate location 勾 Yes** 並不矛盾：
> 那一格是因為 GA 會在**伺服器端由連線 IP** 推導國家層級位置，
> 而這一題問的是「精確位置有沒有給別的使用者看」。**兩題問的是完全不同的事。**

### 3.3 ★「分享個人資訊（Shares Personal Information）」→ **是**

判準原文：

> "Developers should answer 'Yes' if the app shares user-provided personal information with **any
> third party (other than the app's developer or publisher)**. Examples of user-provided personal
> information include name, address, email address, phone number, **date of birth**, social security
> number, financial information, and private records.
> Unique numbers/identifiers automatically generated by the app, the mobile device or wireless
> service provider are **not** considered user-provided personal information.
> Developers should answer 'No' if users can share personal information **within** the app or if
> anonymous information is reported for analytics purposes, such as Google Analytics, and they
> don't share user-provided personal information with third parties."

**必須答「是」的理由**，逐句對照：

| 判準 | 本 App 的事實 |
|---|---|
| "user-provided personal information" | 身分證號、出生日期、手機號碼三欄，全部由使用者在 `OnboardingScreen` 的表單自己輸入。**出生日期是判準明列的例子之一** |
| "any third party (other than the app's developer or publisher)" | `500.gov.tw` 由活動主辦單位營運，**不是本 App 的開發者或發行者** |
| 「自動產生的識別碼不算」 | Firebase 的安裝編號因此**不算**，不能拿它來當答「是」的理由（但也不能拿它當答「否」的理由） |
| 「僅用於分析的匿名資訊（如 Google Analytics）應答否」 | **Firebase 那半確實不構成答「是」的理由**——判準明文把它排除了 |

→ **答「是」的唯一原因是三欄個資送到 `500.gov.tw`。** 這一點無可迴避。

> ### ⚠️ 這一題與 Data safety 的答案**故意相反**，那不是矛盾
>
> | 表單 | 「分享給第三方？」 | 為什麼 |
> |---|---|---|
> | **Data safety** | **不勾 Shared** | Google 在那份表單裡給了明文的 **user-initiated action 豁免**：「Transferring user data to a third party based on a specific user-initiated action, where the user reasonably expects the data to be shared」 |
> | **IARC 內容分級** | **答「是」** | 這份問卷的判準裡**沒有任何主動觸發的豁免**。它只問「有沒有把使用者提供的個資給第三方」，答案就是有 |
>
> **兩份表單是獨立的，答案可以合法地不一致。**
> **不要拿其中一份去「校正」另一份**——那會讓其中一份變成不實陳述。
> 填表的人若覺得哪裡怪，請回來讀這一段，而不是把答案改成一致。
>
> 這一題答「是」通常不會把分級推到全年齡以上（它是資訊揭露而非成人內容），
> 但**答錯（答否）會是實質的不實陳述**，而 Google 明文說分級不實可被拒絕或下架。

### 3.4「數位購買（Digital Purchases）」→ **否**

判準原文：

> "Digital purchases are purchases for digital goods completed directly from within the app.
> Typically, these purchases are for additional content or premiums."

**理由**：App 完全免費、沒有 IAP、沒有訂閱、不處理任何金流。
**兌換加碼券不是購買**——那是官方活動的既有機制，使用者沒有付出任何金錢，
核銷也發生在超商／賣場而不是 App 內。

---

## 4. 其餘題組一律「無／否」

| 題組 | 答案 | 理由 |
|---|---|---|
| **暴力**（Violent Material、Violence Against Anything Other Than Humans、Violence Towards Vulnerable or Defenseless Characters、Blood and Gore、Realistic or Historical War Setting、Creatures Behave Like Humans） | 全部**否／無** | App 內容只有任務清單、券夾、表單與條碼。沒有任何角色、劇情或影像素材 |
| **恐怖／驚悚**（Scary Elements、Fierce Sounds, Dark Overtones, or Disturbing Elements） | 否 | 同上。沒有音效、沒有動畫 |
| **性與裸露**（Depictions of Sexual Activity、Public Sharing of Nudity or Gore） | 否 | 同上。使用者上傳的圖不會公開給任何其他使用者（見 §3.1） |
| **語言**（Potentially Offensive Language） | 否 | 全部文案為中性說明文字，且都是寫死在資源與程式裡的字串，沒有使用者自由輸入的文字會顯示給別人 |
| **管制物質**（Illegal Drugs、Illegal or Recreational Drugs、Focus of the App） | 否 | 無相關內容或提及 |
| **賭博**（Real Gambling or Cash Payouts） | 否 | 兌換是官方活動的既有機制，**沒有隨機性、沒有下注、沒有現金給付**。使用者達標即可兌換，不是抽獎 |
| **情境修飾**（Setting、Presentation） | 依 Console 實際選項填 | 這兩題是給有敘事內容的 App／遊戲用的修飾題；本 App 無敘事，選最中性的選項 |

---

## 5. 同一頁的其他宣告（不屬於分級問卷，但一起做完比較省事）

| 項目 | 建議答案 | 理由與注意事項 |
|---|---|---|
| **廣告（Ads）** | **否，不含廣告** | App 不投放任何廣告，`google_analytics_default_allow_ad_personalization_signals=false`。答否，商店頁就不會出現「含廣告」標籤。⚠️ **注意**：這一題問的是「有沒有顯示廣告」，與 `data-safety.md` §6 的**廣告識別碼權限**是兩件不同的事——AD_ID 權限**已於 2026-09-07 從 manifest 移除**並實測驗證（merged release manifest 與 AAB 皆零命中），所以那一格現在答「不使用廣告識別碼」是真的。這段原本警告「答否不會讓權限消失」——那個前提已經不存在 |
| **應用程式存取權（App access）** | **部分功能需要登入 → 提供示範帳號** | 見下方 §5.1，這一格填錯會直接被退 |
| **政府應用程式宣告（Government apps）** | **否，非政府機關開發／非代表政府** | 2023-01-31 起所有 App 都必須填這一項。本 App 是非官方個人工具，答「否」。**這一格與商店說明的非官方聲明必須一致** |
| **健康應用程式宣告（Health apps）** | **必填，且要聲明「不提供健康功能」** | ⚠️ **這一項容易漏**：Google 明文「All developers with apps published on Google Play must complete the Health apps declaration form, **including apps that do not offer any health features** must complete this form and certify that no health features are offered」，而且條文含「**including apps on closed testing, open testing, or production tracks**」——**封測也躲不掉**。本 App 不讀任何健康資料、不提供任何健康或醫療功能 → 照實聲明「沒有」。**但要留意與 `data-safety.md` §4 的 Fitness info 決定是否看起來衝突**——那一格是「有沒有把運動紀錄截圖送出去」，這一格是「有沒有提供健康功能」，答案分別是「有送出」與「沒有功能」，並不矛盾，但**若被問到要能講清楚** |
| **財務功能宣告（Financial features）** | **無** | 加碼券不是金融商品，App 不做匯款、投資、借貸或理財建議，也不顯示任何金額餘額。條文同樣含「**including apps on closed testing, open testing, or production tracks**」，所以**即使答「無」也要把表單填掉** |
| **新聞／雜誌宣告** | 不適用 | 非新聞類 App |
| **目標對象與內容（Target audience）** | **18 歲以上**（見下方說明） | 年齡分組選項為 5 歲以下／6–8／9–12／13–15／16–17／18 歲以上 |

### 5.1 應用程式存取權（App access）——必須填，而且要用示範帳號

Google 的要求原文（重點是「隨時可用、可重複使用、不因地區失效、要用英文」）：

> "Your sign-in details must be accessible at all times, reusable, and valid regardless of user
> location." · "Your sign-in details must be provided in English." ·
> "Only provide account credentials specifically used for testing. Do not provide any production
> user's credentials."

**本 App 的正確填法**：選「部分或所有功能受限制」，然後提供**示範模式**的三碼。
示範模式（`app/src/main/kotlin/com/megshao/exerciserewards/data/DemoMode.kt`）完全不連線官方網站、
資料全部是範例，因此完美符合上面三個條件——**它不會過期、不因地區失效、也不是任何真實使用者的帳號**。

**要填進 Console 的英文說明（可直接複製）**：

```
This app is an unofficial third-party client for a Taiwanese government sports-rewards
programme (500.gov.tw). Real accounts require a Taiwanese national ID and an SMS code sent
to a Taiwanese phone number, so a real account cannot be provided for review.

The app therefore ships with a built-in DEMO MODE that requires no network access and no
real account. To enter it, type these three values into the login form on first launch:

  National ID   : A000000000
  Date of birth : 1990-01-01
  Phone number  : 0900000000

These are sentinel values (A000000000 is not a valid ROC national ID — its checksum does not
match), so a real user cannot trigger demo mode by accident. In demo mode every screen is
populated with sample data, the app makes no requests to 500.gov.tw, and no analytics events
are sent. A persistent banner marks the session as a demo.

Note on first-launch order: a disclaimer screen appears before the login form and must be
accepted to continue. The disclaimer discloses that the app sends anonymous usage statistics
and crash reports to Google Firebase, which is initialised at that moment — before demo mode
is entered. This ordering is intentional: nothing is collected before the disclosure is shown.

The app is not affiliated with, authorised by, or endorsed by any government entity. Source
code: https://github.com/megshao/exercise_rewards_android
```

> ℹ️ 上面那行 GitHub 網址已經是 Android repo 的。
> ⚠️ 但**App 內**的「查看原始碼」連結目前仍指向 iOS 的 repo，要一併修（見 `blockers.md` B-4）。

### 5.2 為什麼目標對象建議「18 歲以上」

- 活動參加需要本人身分證號與手機號碼，實務上就是成年人在用；
- 選 13 歲以下任何級距會觸發 **Play Families 政策**（額外的設計、廣告與資料規範），
  本 App 完全不是為兒童設計，沒有必要去承擔那一整套義務；
- Google 對這一項的警告是：**行銷素材看起來吸引兒童**但宣告成人向會被退。
  本 App 的圖示與截圖都是資訊型介面，沒有卡通角色或遊戲化元素，不會踩到。

---

## 6. 送出前的自我檢查

- [ ] 類別選了「工具、生產力、通訊或其他」（不是商店資訊的「生活風格」——兩者本來就不同）
- [ ] §3.3「分享個人資訊」答**是**，並且**已理解它與 Data safety 不勾 Shared 並不矛盾**
- [ ] §3.1「線上互動或內容交換」答**否**
- [ ] 廣告宣告答**否**（但別以為這樣 AD_ID 權限就處理好了——見 `data-safety.md` §6）
- [ ] **健康應用程式宣告已完成**並聲明「不提供健康功能」（最容易漏的一項）
- [ ] 政府應用程式宣告答「非政府」，且與商店說明的非官方聲明一致
- [ ] App access 已填示範帳號三碼與**英文**說明，且說明裡含免責聲明先出現的時序
- [ ] IARC 通信信箱填的是自己收得到的信箱（分級證明與日後覆核都寄那裡）
