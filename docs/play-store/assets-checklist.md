# 商店素材檢查表

> 撰寫日期：**2026-09-07**（規格於同日查證）
> 規格來源：https://support.google.com/googleplay/android-developer/answer/9866151
> ・圖示設計規範：https://developer.android.com/distribute/google-play/resources/icon-design-specifications
> ・Metadata 政策（素材內容禁則）：https://support.google.com/googleplay/android-developer/answer/9898842
>
> **⚠️ 本文件在 2026-09-07 當天被改寫過一次。** 原始任務單寫的是
> 「App 圖示目前是佔位向量（`ic_launcher_foreground.xml` 與歡迎頁的 `AppMark` 兩處要一起換）」——
> **那個描述已經過期了。** commit `59f811f`（"use the iOS icon artwork for the launcher and store icon"）
> 已經把兩處一起換掉，佔位的那顆星形向量已不存在。下表是實測過的現況。

---

## 0. 一頁摘要

| 素材 | Play 是否必要 | 現況 | 還要做什麼 |
|---|---|---|---|
| App 圖示 512×512 | **必要** | ✅ 已有，但**格式要轉** | 用本文件產好的 `assets/play-icon-512.png`（已是 32-bit）|
| 主要宣傳圖 1024×500 | **必要**（不給就無法發布商店資訊） | ✅ **已產好可用** | 直接上傳 `assets/play-feature-graphic-1024x500.png`；正式上架前建議請設計師重做 |
| 手機截圖 ×2 起 | **必要** | ❌ **完全沒有，這是唯一真的缺口** | 見 §3。**這是素材類唯一會卡住今天封測的一項** |
| 啟動器圖示（裝機後） | 必要 | ✅ 已完成並實測過安全區 | 無 |
| 平板／Chromebook 截圖 | 選填 | ❌ 沒有 | 封測不需要，見 §4 |
| 宣傳影片 | 選填 | ❌ 沒有 | 不做 |

---

## 1. App 圖示（Play 商店那張 512×512）

### 規格與實測結果

| 規格要求 | 現有檔案 `app/ic_launcher-playstore.png` | 判定 |
|---|---|---|
| 512 × 512 px | 512 × 512 | ✅ |
| **32-bit PNG（含 alpha channel）** | **24-bit RGB（PNG colortype 2，沒有 alpha channel）** | ❌ **不符** |
| 檔案 ≤ 1024 KB | 35.2 KB | ✅ |
| 不可自己做圓角、不可自己加陰影（Play 會自己套 30% 圓角遮罩與陰影） | 是完整方形，沒有預先圓角 | ✅ |
| 避免透明（透明處會露出 Play UI 的底色） | 完全不透明 | ✅ |
| 不得含官方標誌／國徽／「官方」字樣 | 純幾何：橘色漸層底 + 黃色票券 + 白色汗滴 | ✅ |
| 不得有 emoji、badge、疊加文字、排名或價格字樣 | 無 | ✅ |

**唯一的問題是色彩格式。** Google 的規格明寫 **"32-bit PNG (with alpha)"**，而現有檔案是 24-bit RGB。
Play Console 的上傳器會擋這種格式。

> **注意這裡有個看起來矛盾的地方，但兩句話都對**：規格要求「32-bit（含 alpha）」，
> 同時又說「Avoid transparency」。意思是**要有 alpha channel，但整張圖的 alpha 應該是不透明的**。
> 所以正確的做法不是把角落挖成透明，而是**補一條全不透明的 alpha channel**。

### ✅ 已經幫你轉好了

`docs/play-store/assets/play-icon-512.png` — 512×512、**PNG colortype 6（RGBA）**、alpha 全為 255、40.0 KB。
它是 `app/ic_launcher-playstore.png` 的機械轉換，**圖案一個像素都沒動**。

**上傳這一張。** 如果你想自己重做一次，指令是：

```sh
# 方法 A：sips（macOS 內建，不用裝東西）
sips -s format png --setProperty hasAlpha true \
  app/ic_launcher-playstore.png --out /tmp/play-icon-512.png

# 方法 B：Python（本文件用的就是這個，行為最明確）
python3 -c "
from PIL import Image
im = Image.open('app/ic_launcher-playstore.png').convert('RGBA')
im.putalpha(255)                    # 補一條全不透明的 alpha
im.save('docs/play-store/assets/play-icon-512.png','PNG')"
```

驗證（`colortype=6` 才對）：

```sh
python3 -c "
import struct; d=open('docs/play-store/assets/play-icon-512.png','rb').read(33)
print('size', struct.unpack('>II', d[16:24]), 'bitdepth', d[24], 'colortype', d[25])"
```

---

## 2. 主要宣傳圖（Feature graphic）1024×500

**這一項是硬性的**：Google 原文「**You must provide a feature graphic to publish your store listing**」。
沒有它，商店資訊發布不了，封測也就發不出去。

### ✅ 已經幫你產好一張可用的

`docs/play-store/assets/play-feature-graphic-1024x500.png` — 1024×500、24-bit PNG（無 alpha，符合規格）、44.6 KB。

內容：App 自己的圖示（套上 Play 風格的 30% 圓角與陰影）+ 從圖示取樣的同一組橘色漸層 +
三行文字。**文字右緣留了 176 px 邊距**，因為 Play 在某些版位會裁切邊緣。

文字內容（已逐條對照 `listing-zh-TW.md` §0 的三條界線與 Play 的 Metadata 政策）：

| 行 | 文字 | 檢查 |
|---|---|---|
| 1 | `Exercise Rewards` | 上架名，不是活動名 ✅ |
| 2 | `「揮汗有禮」活動的非官方工具` | 活動名**緊接**「非官方」✅ |
| 3 | `個資只存在你的手機　開發者收不到` | 界線 #2 的準確說法，沒有出現「不儲存」✅ |

沒有官方標誌、沒有國徽、沒有「官方／授權／合作」字樣、沒有 emoji、
沒有 `免費`／`無廣告`／`#1`／`最佳` 這類 Metadata 政策禁止的字樣。

> **誠實的品質評價**：這張圖是**程式產生的**，夠格、乾淨、政策安全，
> **足以支撐封閉測試**，但它不是設計品。正式上架前建議請設計師重做一張
> （沿用同一組色票與同樣的三行文字結構即可）。重做時只要記得：
> **不要自己加圓角或陰影到整張圖**（那是給圖示的規則，宣傳圖本身就是滿版矩形），
> 焦點放中央、邊緣別放重要元素。

規格自我檢查：

```sh
python3 -c "
import struct,os
p='docs/play-store/assets/play-feature-graphic-1024x500.png'
d=open(p,'rb').read(33)
print(struct.unpack('>II',d[16:24]), 'colortype', d[25], '(2=RGB 無 alpha，正確)', os.path.getsize(p),'bytes')"
```

---

## 3. ❌ 手機截圖 —— 素材類唯一真的缺口

### 規格（2026-09-07 查證）

| 項目 | 要求 |
|---|---|
| 數量 | **最少 2 張**（跨所有裝置類型合計）才能發布；每種裝置類型最多 8 張 |
| 格式 | **JPEG 或 24-bit PNG（不可有 alpha）** |
| 最小邊 | ≥ 320 px |
| 最大邊 | ≤ 3840 px |
| **長寬比** | **最長邊不得超過最短邊的 2 倍** |
| Google 建議（會影響能不能被 Play 的推廣版位選中） | **≥ 4 張、短邊 ≥ 1080 px**，且為 **9:16 直向（≥1080×1920）** 或 16:9 橫向 |

### ⚠️ 兩個會讓人踩坑的地方（都跟上面那條長寬比有關）

**坑 1：iOS 端現成的商店截圖不能直接拿來用。**

iOS 端 `../exercise-rewards-ios/docs/screenshots/store/zh-Hant_01..07.png` 是
**1320 × 2868**，看起來很誘人（已經排版好、有中文標題、有合規小標）。但：

```
2868 / 1320 = 2.173  >  2      ← 違反「最長邊 ≤ 最短邊 ×2」
```

**直接上傳會被 Play 擋掉。** 那個尺寸是 Apple 6.9" iPhone 的規格，Apple 沒有 2:1 的限制。

**坑 2：直接從現代手機／模擬器截圖也常常不合格。**

現在的手機大多是 19.5:9 或 20:9。例如 1080 × 2400：

```
2400 / 1080 = 2.222  >  2      ← 同樣違反
```

→ **所以「拿手機截一張圖上傳」這條最直覺的路，多半會被退。**

### 建議做法（照這個順序，最省時間）

**方案 A（最快、最推薦）：開一台 16:9 的模擬器，讓 `DemoFlowTest` 順手把圖截出來**

`app/src/androidTest/.../DemoFlowTest.kt` 走的就是示範模式，
本來就會把 12 個畫面一一走過——那正好是商店截圖要的畫面，而且**不含任何真實個資**
（示範三碼是 `A000000000` / `1990-01-01` / `0900000000`）。

1. 建一台 **1080 × 1920（16:9）** 的 AVD。
   `1920 / 1080 = 1.78 ≤ 2` → **截出來直接就合格**，不用後製補邊。
   （API 26 以上任一版本都可以；建議用較新的以免踩舊版模擬器的怪問題。）
2. 在測試裡加截圖（`androidx.test.core.app.takeScreenshot` 或
   `UiDevice.takeScreenshot`），或跑完手動 `adb exec-out screencap -p > 03-home.png`。
3. 挑 4–6 張：**歡迎／免責聲明 → 首頁 → 任務 → 券夾 → 兌換 → 我的資料**。
   （封測階段不必套裝置框或加標題文字，原始截圖就能上傳。）

**方案 B：沿用 iOS 的排版腳本，改一下畫布尺寸**

`../exercise-rewards-ios/docs/screenshots/store/compose_zh.py` 已經寫好了
中文標題排版、合規小標膠囊、裝置框合成，只是畫布寫死 `CANVAS_W, CANVAS_H = 1320, 2868`。
把它改成 **1080 × 1920** 或 **1440 × 2560**（兩者都是 16:9），並換掉 iPhone 的框，
就能產出與 iOS 視覺一致的 Play 版截圖。

**注意**：那支腳本的裝置框是 `~/.claude/skills/aso-cosmicmeta-ss/assets/iphone_frame.png`，
在 Play 上用 iPhone 外框是**明顯的錯誤**（而且會讓人以為這是 iOS App）。要改用 Android 外框或乾脆不套框。

**方案 C（後製補邊，最後手段）**

如果已經有 20:9 的截圖不想重截，就把它補邊到 2:1 以內：

```sh
# 1080x2400 -> 1200x2400（上下不動，左右補上 App 的底色）
python3 -c "
from PIL import Image
im = Image.open('raw.png').convert('RGB')
w,h = im.size
W = max(w, (h+1)//2)          # 保證 h/W <= 2
out = Image.new('RGB', (W,h), (255,248,240))   # Tokens 的背景色系
out.paste(im, ((W-w)//2, 0))
out.save('shot.png')"
```

補邊會讓畫面兩側出現空白，視覺上不好看，所以只在趕時間時用。

### 截圖內容的禁則（Metadata 政策，與圖示同一套）

- ❌ **不可含任何真實個資**。用示範模式的三碼，截完**逐張目視確認**
  （iOS 端就是這樣做的：7 張逐張看過）。
- ❌ 不可含官方標誌、國徽、「官方」字樣。
- ❌ 不可加「#1」「最佳」「免費」「無廣告」這類文字。
- ❌ 不可放不存在的功能畫面（Play 明文要求素材要如實反映功能）。
- ✅ 如果要在截圖上加標題文字，建議**至少一張帶「非官方」字樣**——
  商店頁第一屏就看到最好，這是 Play 的政府資訊要求（「easy-to-see」）的加分做法。

---

## 4. 平板／大螢幕：封測不用做，但要知道規則變了

- **平板截圖不是發布的必要條件。** 官方頁面的措辭是「you can add a minimum of 4 screenshots」，
  是邀請而非要求，也**沒有**「沒上傳平板截圖就標記為未最佳化」這種規則。
- **但真正會影響你的是另一件事**：Google 現在跑的是 **adaptive app quality guidelines**
  （取代並擴充了舊的大螢幕指南）。判定依據是**App 的行為**（能不能自由縮放、支不支援橫向、
  有沒有被上下黑邊框住），**不是有沒有上傳平板截圖**。後果有三層：
  - 符合標準 → 商店頁拿到「**Optimized for large screens**」徽章（2026-05 Android 開發者部落格宣布）；
  - 不符合基本相容性 → **大螢幕裝置的商店頁會對使用者顯示提示**；
  - Google 會「updating our featuring and ranking logic in Play on large screen devices」——
    **大螢幕上的排名與推薦會被影響**。
- **這對本 App 是個已知的取捨**：`AndroidManifest.xml` 把 Activity 鎖成
  `android:screenOrientation="portrait"`，`README` 也明說「僅直向」。
  而 **targetSdk 36 起，在最短邊 ≥ 600dp 的螢幕上系統預設會忽略方向鎖定並讓 App 可調整大小**——
  本專案 targetSdk 是 37，所以**在平板上這個 portrait 鎖定實際上不生效**，
  App 會被拉成大視窗顯示。
  → **封測不影響**（測試者用手機）。**但正式上架前該實測一次平板／可折疊裝置上的版面**，
  否則會拿到大螢幕的使用者提示與排名扣分。已列進 `blockers.md` 的「上架前、非今天」區。

---

## 5. 啟動器圖示（裝機後在桌面上的那顆）——已完成，並且實測過

commit `59f811f` 已經整套換掉，佔位向量不存在了。實測結果：

| 檔案 | 內容 | 實測 |
|---|---|---|
| `mipmap-anydpi-v26/ic_launcher.xml` | adaptive-icon：background / foreground / **monochrome** 三層都有 | ✅ 有 monochrome，Android 13+ 的主題化圖示（themed icon）能正常運作 |
| `drawable/ic_launcher_background.xml` | 向量漸層底 | ✅ |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png` | 五個密度都有 | ✅ |
| `mipmap-{...}dpi/ic_launcher_monochrome.png` | 五個密度都有 | ✅ |
| `drawable-nodpi/app_mark.png` | 歡迎頁的 `AppMark`（512×512） | ✅ 已從佔位向量換成 iOS 原圖 |

**安全區實測**（adaptive icon 的 108dp 畫布只保證中央 72dp＝66.7% 不被遮罩裁切）：

```
mipmap-xxxhdpi/ic_launcher_foreground.png  canvas=432x432
  圖案實際邊界 = (83, 128, 352, 310)
  安全區       = (72,  72, 360, 360)
  → 圖案完全落在安全區內：YES
```

→ **圓形、方形、squircle 各種遮罩下都不會被切到票券兩端。**
`ic_launcher.xml` 的註解說「票券縮到畫布的 57.4% 重新置中」，實測含陰影的整體寬度是 62.3%，
兩者一致（差值就是陰影），**設計意圖與產物相符**。

### 兩個小觀察（都不影響封測）

1. **`mipmap-anydpi-v26/ic_launcher_round.xml` 存在，但 manifest 沒有宣告 `android:roundIcon`。**
   資源壓縮器的報告因此標它 `is not reachable`。
   minSdk 是 26，adaptive icon 已經涵蓋所有機型的遮罩需求，**`roundIcon` 在這裡是多餘的**——
   留著只是一個沒被引用的資源。要清乾淨就刪掉那個檔；不刪也沒有任何實際影響。
2. **沒有 `mipmap-*/ic_launcher.png` 這種傳統點陣圖示。**
   這是**正確的**：`minSdk = 26`，而 adaptive icon 從 API 26 就支援，
   所以 `anydpi-v26` 一份就覆蓋全部機型，不需要舊版 fallback。

---

## 6. 上傳前最後檢查

- [ ] 圖示上傳的是 **`assets/play-icon-512.png`**（32-bit），不是 `app/ic_launcher-playstore.png`（24-bit）
- [ ] 主要宣傳圖已上傳（**沒有它商店資訊發布不了**）
- [ ] 手機截圖 **≥ 2 張**（建議 4–6 張），且每一張都滿足 **最長邊 ≤ 最短邊 × 2**
- [ ] 每一張截圖都**逐張目視確認**沒有真實個資（只出現 `A000000000` / `1990-01-01` / `0900000000`）
- [ ] 所有素材都不含官方標誌、國徽、「官方／授權／合作」字樣
- [ ] 所有素材都不含 `免費`／`無廣告`／`#1`／`最佳`／`Editor's choice` 這類 Metadata 政策禁則
- [ ] 至少一張截圖或宣傳圖上帶「非官方」字樣（政府資訊政策的「easy-to-see」加分項）
