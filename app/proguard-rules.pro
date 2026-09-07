# R8 規則（release build，isMinifyEnabled + isShrinkResources 皆為 true）
#
# ## 這個檔案為什麼這麼短
#
# 2026-09-07 實測 `./gradlew :app:bundleRelease`：**在沒有任何自訂規則的情況下就通過了**，
# 而且三個「一般會被 R8 弄壞」的相依都完好。這裡把驗證方法留下來，理由是
# **下一個人最可能犯的錯是憑印象貼一堆 `-keep`**——那會讓 R8 停止裁切，
# 直接放大 APK 並掩蓋真正的問題。要加規則前，先照下面的方法確認真的壞了。
#
# ### kotlinx-serialization：不需要規則
# `kotlinx-serialization-core-jvm:1.11.0` 自己帶 R8 規則（consumer rules，
# `META-INF/com.android.tools/r8/kotlinx-serialization-*.pro`），已被合併進本次建置。
# 驗證（mapping.txt 裡有它＝沒被裁掉，只是被改名）：
#   grep "\$\$serializer" app/build/outputs/mapping/release/mapping.txt | grep megshao
# 實測命中 `Profile$$serializer -> f02` 與 `TasksCache$CachedPeriod$$serializer -> eq2`。
# 註：R8 有把 `Profile` 的 `getName()`／`getEmail()`／`getNhiCardNo()`／`componentN()` 裁掉，
# 這是對的——產生出來的 serializer 直接讀欄位，不透過 getter，所以序列化不受影響。
#
# ### Firebase（Analytics + Crashlytics）：不需要規則
# `firebase-common`／`firebase-components` 帶 consumer rules（含
# `-keep class * implements com.google.firebase.components.ComponentRegistrar`），
# 那正是 Firebase 唯一真的靠反射找的東西。
# 上面這段結論最初是在 `app/google-services.json` **還不存在**時得出的（那時兩個 plugin
# 沒被套用，Firebase 在 AAB 裡是死的，等於沒驗到）。設定檔放進來之後 release build 的組成
# 確實變了（多一個 Crashlytics 的 bytecode transform），所以**已重跑 bundleRelease 重新驗證**：
#   grep -c ComponentRegistrar app/build/outputs/mapping/release/mapping.txt   → 105
# ComponentRegistrar 的實作都還在（只是被改名），結論成立，仍然不需要自訂規則。
# 還沒做的是**在實機上跑一次 release 版、確認同意流程真的會初始化 Firebase**——
# R8 沒裁錯東西跟執行期真的能初始化是兩件事，見 docs/play-store/blockers.md。
#
# ### ZXing（com.google.zxing:core）：不需要規則
# 純 JVM、無反射；`BarcodeGenerator` 以 `when` 明確對應到 `ZxingFormat` 的列舉成員，
# 不是用字串反射查類別，所以 R8 的呼叫圖找得到它。
# 驗證：`grep "com.google.zxing" app/build/outputs/mapping/release/mapping.txt`
# 實測 `Code128Writer` 等實際用到的 writer 都在（沒用到的格式被裁掉，這是我們要的）。
#
# ### OkHttp 5：不需要規則
# 自帶 consumer rules（`-dontwarn okhttp3.internal.platform.**`／`org.conscrypt.**`／
# `org.bouncycastle.**`）。本次建置零警告。

# ---------------------------------------------------------------------------
# 唯一自己加的規則：讓當機堆疊留住真正的行號
# ---------------------------------------------------------------------------
#
# **問題**：R8 預設會做 line-number 優化——把行號重新編號成 1,2,3…，並把每個類別的
# SourceFile 換成 `r8-map-id-<hash>`。實測（dexdump base/dex/classes.dex）確認本次產物就是這樣：
#   source_file_idx : (r8-map-id-1964498...)   而且 line=1 line=2 line=3 …
# 後果是 **logcat／bugreport 上的堆疊完全沒有可用行號**，要靠那個 hash 對應到當初那一份
# `mapping.txt` 跑 retrace 才還原得回來。
#
# **為什麼對這個專案是問題**：`mapping.txt` 有 51 MB、不進版控，而封測期間最常見的除錯素材
# 是「測試者傳來的一段 logcat」。金鑰以外的東西弄丟不致命，但那份 mapping 弄丟，
# 那個版本的所有堆疊就永久讀不懂了。
#
# **這兩行的作用**：`SourceFile,LineNumberTable` 讓真實行號留在 dex 裡；
# `-renamesourcefileattribute SourceFile` 再把檔名字串抹成固定的 "SourceFile"，
# 所以**不會**因此洩漏原始碼結構（類別／方法名照樣被混淆）。
# 代價是 dex 稍微變大（實測整包 AAB 6,220,881 → 6,312,583 bytes，+91.7 KB／+1.47%），
# 換到的是「行號不依賴任何外部檔案」。加上之後實測 dexdump 顯示 source_file_idx 為
# 固定字串 "SourceFile"、行號恢復成真實值（最大 line=1261，與原始碼行數相符）。
#
# 這也是 Firebase Crashlytics 官方文件對啟用 R8 的 App 的建議設定。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# 刻意不加的規則（加了會有反效果，別貼回來）
# ---------------------------------------------------------------------------
# -keep class com.megshao.exerciserewards.** { *; }
#     整包不裁 = 放棄 R8。而且 `core` 的領域型別本來就有一半是 parser 的中間產物，
#     裁掉才對。已驗證真正需要保留的（serializer、Application、Activity）都留著了。
#
# -keep class com.google.firebase.** { *; } / -keep class com.google.zxing.** { *; }
#     上面已驗證兩者都不需要；留著只會讓「哪天真的壞了」查不出是誰的鍋。
#
# -dontobfuscate
#     這支 App 的原始碼是公開的，混淆不是為了藏程式碼；但關掉它會讓 dex 變大、
#     也讓 Play Console 上的當機叢集失去 R8 的殘差對應。沒有理由關。
