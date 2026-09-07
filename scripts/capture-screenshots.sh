#!/usr/bin/env bash
# 產生上架素材用的原始截圖。
#
# 對應 iOS 端的 `xcodebuild test -scheme ExerciseRewardsScreenshots`。
# Android 多了兩件 iOS 不用做的事，所以這裡寫成腳本而不是只留在註解裡：
#   1. 狀態列要自己淨化（iOS 有 `simctl status_bar override`，Android 得走 SystemUI demo mode）
#   2. 測試程序寫不到 host 路徑，拍完要 adb pull（iOS 模擬器與 host 共用檔案系統）
#
# 用法：
#   scripts/capture-screenshots.sh                 # 自動選第一台模擬器
#   ANDROID_SERIAL=emulator-5554 scripts/…         # 指定裝置
#
# **請用模擬器，不要用實機**：實機拍出來會帶自己的機型狀態列與導覽列高度，
# 而且螢幕解析度不一定符合 Play 的截圖要求（需要短邊 ≥1080）。
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
APP_ID="com.megshao.exercise_rewards.debug"   # debug 版有 .debug 後綴
CLASS="com.megshao.exerciserewards.ScreenshotTest"
DEST="docs/screenshots/raw"

# 沒指定就挑第一台模擬器。刻意不挑實機——理由見檔頭。
if [ -z "${ANDROID_SERIAL:-}" ]; then
  ANDROID_SERIAL="$("$ADB" devices | awk '/^emulator-.*[[:space:]]device$/{print $1; exit}')"
  [ -n "$ANDROID_SERIAL" ] || { echo "找不到已就緒的模擬器（狀態必須是 device，不是 offline）。"; "$ADB" devices; exit 1; }
fi
export ANDROID_SERIAL
echo "▶ 裝置：$ANDROID_SERIAL"
A=("$ADB" -s "$ANDROID_SERIAL")

# 開機未完成時 pm/am 都會回「device is still booting」，那個錯誤訊息看不出真正原因，先擋。
if [ "$("${A[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; then
  echo "✗ $ANDROID_SERIAL 還在開機（sys.boot_completed != 1）。等開完再跑。"; exit 1
fi

# **這三段設定不是潔癖，是這支流程能不能跑完的前提。**
#
# 踩過的坑：模擬器上 SystemUI 很容易 ANR，然後彈出一個「System UI isn't responding」
# 對話框**壓在畫面正中央**。後果有三個，而且都不會直接告訴你原因：
#   1. 截圖全部被對話框污染（拍出來直接不能用）
#   2. 焦點被對話框搶走 → Espresso 之類需要視窗焦點的操作丟
#      RootViewWithoutFocusException（訊息完全看不出是 ANR 造成的）
#   3. 對話框壓著 → 捲動無效，前後兩張截圖會**位元組完全相同**
# `hide_error_dialogs` 讓系統不再彈這種對話框（ANR 本身還是會發生，但不再擋畫面）。
echo "▶ 關掉系統錯誤對話框與動畫"
"${A[@]}" shell settings put global hide_error_dialogs 1
# 動畫關掉有兩個好處：降低模擬器負載（ANR 的根因），以及不會拍到動畫中途的畫面。
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  "${A[@]}" shell settings put global "$k" 0
done

echo "▶ 淨化狀態列（SystemUI demo mode）"
"${A[@]}" shell settings put global sysui_demo_allowed 1
demo() { "${A[@]}" shell am broadcast -a com.android.systemui.demo "$@" >/dev/null; }
demo -e command enter
demo -e command clock -e hhmm 0941
demo -e command battery -e level 100 -e plugged false
demo -e command network -e wifi show -e level 4 -e fully true
# mobile 一併 show 會讓狀態列同時出現行動訊號與「3G」字樣，跟 wifi 圖示疊在一起很雜。
# 商店截圖只留 wifi 滿格就好。
demo -e command network -e mobile hide
demo -e command notifications -e visible false

# 每次都清掉舊的，避免這次沒拍成功的畫面留著上一輪的圖，讓人誤以為拍到了。
echo "▶ 清掉裝置上的舊截圖"
"${A[@]}" shell "rm -rf /sdcard/Android/data/$APP_ID/files/screenshots" || true

echo "▶ 建置兩個 APK"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -q

# **刻意不用 `connectedDebugAndroidTest`。** 踩過的坑，理由留著：
#
# 那個 task 會自己裝、跑、然後**把 App 卸掉**。一旦某次跑失敗，卸載會留下不一致的狀態
# （實測是 `DELETE_FAILED_INTERNAL_ERROR`），而下一次 Gradle 判定所有任務 `UP-TO-DATE`
# ——它以為 APK 還在機器上，於是**跳過安裝**，instrumentation 就找不到目標：
#   INSTRUMENTATION_STATUS: Error=Unable to find instrumentation info for: ...
#
# 最糟的是這種情況下 **Gradle 仍然回報 `BUILD SUCCESSFUL`**（AGP 把 INSTRUMENTATION_FAILED
# 吞掉了）。只看 exit code 會以為跑成功，然後對著空的輸出目錄困惑很久。
# 自己裝、自己跑，錯誤訊息才是真的，而且沒有那個卸載步驟。
echo "▶ 安裝 APK"
APK_APP=app/build/outputs/apk/debug/app-debug.apk
APK_TEST=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"${A[@]}" install -r -t "$APK_APP"  | tail -1 | sed 's/^/  app:  /'
"${A[@]}" install -r -t "$APK_TEST" | tail -1 | sed 's/^/  test: /'

echo "▶ 跑截圖測試"
INSTR_LOG="$(mktemp -t exercise-rewards-instr)"
"${A[@]}" shell am instrument -w -r -e class "$CLASS" \
  "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" > "$INSTR_LOG" 2>&1 || true
tr -d '\r' < "$INSTR_LOG" | grep -aE "INSTRUMENTATION_CODE|Error=|FAILURES|OK \(|^Time:" | sed 's/^/  /' || true
# **成敗判準不是 INSTRUMENTATION_CODE。** 踩過的坑：那個值成功與失敗都是 `-1`，
# 因為 -1 就是 `Activity.RESULT_OK`——它只代表「instrumentation 本身跑完了」，
# 不代表測試通過。照它判會在測試明明 `OK (1 test)` 時報失敗。
# 真正的訊號是 JUnit 那兩行：通過印 `OK (N tests)`，失敗印 `FAILURES!!!`。
if grep -aq "FAILURES!!!" "$INSTR_LOG" || ! grep -aq "OK (" "$INSTR_LOG"; then
  echo "✗ instrumentation 沒有成功（完整輸出：${INSTR_LOG}）"
  tr -d '\r' < "$INSTR_LOG" | tail -25 | sed 's/^/    /'
  exit 1
fi

echo "▶ 拉回 $DEST"
mkdir -p "$DEST"
"${A[@]}" pull "/sdcard/Android/data/$APP_ID/files/screenshots/." "$DEST"

echo "▶ 還原狀態列"
demo -e command exit

# 取景捲動的失敗報告由測試寫成檔案（println 只進 logcat，這條管線看不到）。
if [ -f "$DEST/_framing-failures.txt" ]; then
  echo "⚠️ 取景捲動有失敗（不影響畫面正確性，但可能造成重複的截圖）："
  sed 's/^/  /' "$DEST/_framing-failures.txt"
  rm -f "$DEST/_framing-failures.txt"
fi

echo "▶ 產出："
python3 - "$DEST" <<'REPORT_PY'
import glob, hashlib, os, struct, sys
d = sys.argv[1]
files = sorted(glob.glob(os.path.join(d, "*.png")))
if not files:
    print("  一張都沒有"); sys.exit(1)

# **重複偵測。** 取景捲動沒生效時（例如目標本來就在畫面上，performScrollTo 成了 no-op），
# 這一張會與另一張位元組完全相同，而且**沒有任何錯誤訊息**。拿去商店等於少一張素材。
seen, dups, small = {}, [], []
for path in files:
    raw = open(path, "rb").read()
    w, ht = struct.unpack(">II", raw[16:24])
    colour = {0: "gray", 2: "RGB", 3: "palette", 4: "gray+A", 6: "RGBA"}.get(raw[25], raw[25])
    name = os.path.basename(path)
    digest = hashlib.sha256(raw).hexdigest()
    note = ""
    if digest in seen:
        note = "  <-- 與 %s 完全相同" % seen[digest]
        dups.append((name, seen[digest]))
    seen.setdefault(digest, name)
    if min(w, ht) < 1080:
        small.append(name)
    print("  %-28s %dx%-6d %9s bytes  %s%s" % (name, w, ht, format(len(raw), ","), colour, note))

print("  共 %d 張，唯一內容 %d 種" % (len(files), len(seen)))

# Play 的硬性規格：最長邊不得超過最短邊的兩倍。原始截圖幾乎一定違反（現代手機是 20:9），
# 所以它是**中間產物**，要經美化流程合成到 1080x1920 才能上傳。這裡只提醒，不當成錯誤。
w, ht = struct.unpack(">II", open(files[0], "rb").read(24)[16:24])
ratio = max(w, ht) / float(min(w, ht))
if ratio > 2:
    print("  [note] 長寬比 %.3f > 2.0：原始圖不可直接上傳 Play，須經美化流程合成到 1080x1920" % ratio)
if small:
    print("  [warn] 短邊 <1080（Play 推廣版位可能不選）：%s" % ", ".join(small))
if dups:
    print("  [warn] 有重複的截圖，請檢查對應的取景捲動：")
    for a, b in dups:
        print("      %s == %s" % (a, b))
    sys.exit(1)
REPORT_PY
