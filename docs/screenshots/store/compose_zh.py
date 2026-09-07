#!/usr/bin/env python3
"""
Exercise Rewards — Google Play 手機截圖排版（zh-Hant）。

改寫自 iOS 端的 `../../../exercise-rewards-ios/docs/screenshots/store/compose_zh.py`
（那份又源自 skill `aso-cosmicmeta-ss` 的 compose.py）。**沿用而不重寫的理由**：
那份已經解掉中文排版的兩個問題——skill 原版用空白斷詞來換行，中文沒有空白；
以及 Heiti 的 CJK 字形幾乎填滿 em box，用自然字距排標題會糊成一團，PIL 沒有
letter-spacing，只能逐字繪製並自己插字距。這兩件事在 Android 端一模一樣。

與 iOS 版的差異只有三處：

1. **畫布 1080x1920（Apple 6.9" 是 1320x2868）。**
   這不是美觀選擇，是硬性規格：Play 要求**最長邊不得超過最短邊的兩倍**。
   原始截圖 1280x2856 的比例是 2.231，**直接上傳會被退**；1920/1080 = 1.78 才過。
   （iOS 的 1320x2868 比例 2.173 也超過 2，所以 iOS 端的成品同樣不能拿來上 Play。）
2. **裝置框換成 skill 的 `android_frame.png`。** 那是一個 900x1980、邊框 14px 的
   單純矩形框（內側沒有圓角造型，與 iPhone 框不同），所以圓角由這裡的遮罩自己加。
   它的螢幕開口比例 2.237，與我們的截圖 2.231 幾乎一致，套進去不需要裁切。
3. **字級按畫布比例縮小**（1080/1320 ≈ 0.82）。

輸出一律 1080x1920、24-bit RGB（無 alpha）——Play 只收 JPEG 或 24-bit PNG。

重跑：
    python3 docs/screenshots/store/compose_zh.py
原始圖來自 `docs/screenshots/raw/`（由 `scripts/capture-screenshots.sh` 產生）。
"""

import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SKILL_ASSETS = os.path.expanduser("~/.claude/skills/aso-cosmicmeta-ss/assets")
FRAME_FILE = os.path.join(SKILL_ASSETS, "android_frame.png")

FONT_TC = "/System/Library/Fonts/STHeiti Medium.ttc"
FONT_TC_INDEX = 0          # 0 = Heiti TC, 1 = Heiti SC

CANVAS_W, CANVAS_H = 1080, 1920

# 裝置幾何。解法與 iOS 版相同：讓 screen_y + 縮放後的截圖高度 == CANVAS_H，
# 這樣**整張原始截圖都看得到**（含底部分頁列），而機身下緣溢出畫布外。
DEVICE_W = 620
BEZEL = 10                 # 14 * (620/900) ≈ 10
SCREEN_CORNER_R = 26       # 框本身沒有圓角，這裡自己加一點，才像現代手機
FRAME_NATIVE_W = 900

# 色票：與 App 內 Theme 一致（亮橘 #F2711C 系）
BG_TOP = (255, 255, 255)
BG_BOTTOM = (255, 244, 234)
GLOW = (242, 113, 28)
EYEBROW_BG = (255, 233, 216)
EYEBROW_FG = (194, 82, 8)
VERB_FG = (232, 89, 12)
DESC_FG = (74, 74, 80)

# 字體排版（iOS 版數值 × 0.82）
EYEBROW_SIZE = 39
EYEBROW_Y = 113
EYEBROW_PAD_X = 33
EYEBROW_PAD_Y = 16
VERB_TOP = 211
VERB_SIZE_MAX = 124
VERB_SIZE_MIN = 96
VERB_STROKE = 2
# Heiti 的 CJK 字形幾乎填滿 em box，用自然字距排標題字會黏在一起、縮圖時糊掉。
# PIL 沒有 letter-spacing，所以標題逐字繪製，每個字後面插入這個比例的額外字距。
VERB_TRACK = 0.12
VERB_DESC_GAP = 46
DESC_SIZE = 52
DESC_LINE_GAP = 18
MAX_TEXT_W = int(CANVAS_W * 0.86)


def font(size):
    return ImageFont.truetype(FONT_TC, size, index=FONT_TC_INDEX)


def text_size(draw, text, f, stroke=0):
    b = draw.textbbox((0, 0), text, font=f, stroke_width=stroke)
    return b[2] - b[0], b[3] - b[1], b


def verb_width(f, text):
    """插入 VERB_TRACK 字距之後，標題的實際寬度。"""
    track = VERB_TRACK * f.size
    return sum(f.getlength(ch) for ch in text) + track * (len(text) - 1)


def fit_verb(text):
    for size in range(VERB_SIZE_MAX, VERB_SIZE_MIN - 1, -2):
        f = font(size)
        if verb_width(f, text) + 2 * VERB_STROKE <= MAX_TEXT_W:
            return f
    return font(VERB_SIZE_MIN)


def render_verb(text, f):
    """逐字繪製標題並裁到實際墨跡範圍，讓呼叫端能以墨跡上緣對齊 VERB_TOP。"""
    track = VERB_TRACK * f.size
    pad = 4 * VERB_STROKE + 8
    layer = Image.new(
        "RGBA",
        (int(verb_width(f, text) + 2 * pad + f.size), int(f.size * 2.5 + 2 * pad)),
        (0, 0, 0, 0),
    )
    d = ImageDraw.Draw(layer)
    x = float(pad)
    for ch in text:
        d.text((x, pad + f.size * 0.4), ch, font=f, fill=VERB_FG, anchor="la",
               stroke_width=VERB_STROKE, stroke_fill=VERB_FG)
        x += f.getlength(ch) + track
    return layer.crop(layer.getbbox())


def background(device_y):
    canvas = Image.new("RGB", (CANVAS_W, CANVAS_H))
    d = ImageDraw.Draw(canvas)
    for y in range(CANVAS_H):
        t = (y / (CANVAS_H - 1)) ** 0.85
        d.line([(0, y), (CANVAS_W, y)],
               fill=tuple(int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3)))

    # 裝置上緣後方的柔和橘色光暈
    glow = Image.new("L", (CANVAS_W, CANVAS_H), 0)
    ImageDraw.Draw(glow).ellipse(
        [-210, device_y - 340, CANVAS_W + 210, device_y + 570], fill=46)
    glow = glow.filter(ImageFilter.GaussianBlur(146))
    canvas.paste(Image.new("RGB", (CANVAS_W, CANVAS_H), GLOW), (0, 0), glow)
    return canvas.convert("RGBA")


def draw_eyebrow(canvas, text):
    d = ImageDraw.Draw(canvas)
    f = font(EYEBROW_SIZE)
    w, h, b = text_size(d, text, f)
    pill_w, pill_h = w + EYEBROW_PAD_X * 2, h + EYEBROW_PAD_Y * 2
    x0, y0 = (CANVAS_W - pill_w) // 2, EYEBROW_Y
    d.rounded_rectangle([x0, y0, x0 + pill_w, y0 + pill_h],
                        radius=pill_h // 2, fill=EYEBROW_BG)
    d.text((CANVAS_W // 2, y0 + EYEBROW_PAD_Y - b[1]), text,
           font=f, fill=EYEBROW_FG, anchor="mt")


def compose(shot_path, eyebrow, verb, desc, out_path):
    shot = Image.open(shot_path).convert("RGBA")
    screen_w = DEVICE_W - 2 * BEZEL
    sc_h = round(shot.height * screen_w / shot.width)
    # 反解裝置位置：整張截圖都看得到，下緣正好貼齊畫布底部。
    screen_y = CANVAS_H - sc_h
    device_y = screen_y - BEZEL
    device_x = (CANVAS_W - DEVICE_W) // 2
    screen_x = device_x + BEZEL

    canvas = background(device_y)
    draw_eyebrow(canvas, eyebrow)
    d = ImageDraw.Draw(canvas)

    vf = fit_verb(verb)
    vimg = render_verb(verb, vf)
    if vimg.width > MAX_TEXT_W:
        raise SystemExit(f"標題過寬（{vimg.width}px > {MAX_TEXT_W}）：{verb}")
    canvas.alpha_composite(vimg, ((CANVAS_W - vimg.width) // 2, VERB_TOP))
    y = VERB_TOP + vimg.height + VERB_DESC_GAP

    df = font(DESC_SIZE)
    for line in desc.split("\n"):
        lw, lh, lb = text_size(d, line, df)
        if lw > MAX_TEXT_W:
            raise SystemExit(f"說明行過寬（{lw}px > {MAX_TEXT_W}）：{line}")
        d.text((CANVAS_W // 2, y - lb[1]), line, font=df, fill=DESC_FG, anchor="mt")
        y += lh + DESC_LINE_GAP
    text_bottom = y - DESC_LINE_GAP
    # 文字壓到裝置上就是排版壞了，寧可 build 失敗也不要產出爛素材。
    if text_bottom > device_y - 30:
        raise SystemExit(f"文字與裝置重疊：text_bottom={text_bottom} device_y={device_y}")

    # 裝置陰影
    sh = Image.new("L", (CANVAS_W, CANVAS_H), 0)
    ImageDraw.Draw(sh).rounded_rectangle(
        [device_x, device_y + 16, device_x + DEVICE_W, CANVAS_H + 140],
        radius=110, fill=70)
    sh = sh.filter(ImageFilter.GaussianBlur(34))
    canvas.paste(Image.new("RGB", (CANVAS_W, CANVAS_H), (120, 70, 30)), (0, 0), sh)

    # 截圖，裁成圓角
    shot = shot.resize((screen_w, sc_h), Image.LANCZOS)
    mask = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [screen_x, screen_y, screen_x + screen_w, CANVAS_H],
        radius=SCREEN_CORNER_R, fill=255)
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(
        [screen_x, screen_y, screen_x + screen_w, CANVAS_H],
        radius=SCREEN_CORNER_R, fill=(0, 0, 0, 255))
    layer.paste(shot, (screen_x, screen_y))
    layer.putalpha(mask)
    canvas = Image.alpha_composite(canvas, layer)

    # 裝置框疊上去
    frame = Image.open(FRAME_FILE).convert("RGBA")
    fw = DEVICE_W
    fh = round(frame.height * fw / frame.width)
    frame = frame.resize((fw, fh), Image.LANCZOS)
    fl = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    fl.paste(frame, (device_x, device_y))
    canvas = Image.alpha_composite(canvas, fl)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    # Play 只收 JPEG 或 **24-bit** PNG，所以一定要 convert("RGB") 去掉 alpha。
    canvas.convert("RGB").save(out_path, "PNG")
    im = Image.open(out_path)
    print(f"  {os.path.basename(out_path):18s} {im.size[0]}x{im.size[1]}  "
          f"verb={verb}（{vf.size}px）")
    assert im.size == (CANVAS_W, CANVAS_H)


HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "..", "raw")
OUT = HERE

# eyebrow 每一張都出現，等於在每張素材上重申「非官方」——那是 listing 的界線 #1。
EYEBROW = "揮汗有禮非官方串接"

# 文案沿用 iOS 端已審過的七組（同一份設計、同一批賣點），對應的原始圖檔名在
# Android 端刻意保持一致，所以是逐項對得上的。
SET = [
    ("01", "02-login.png", "填一次，免重打",
     "身分證號、生日、手機填一次\n之後登入不用再打一遍"),
    ("02", "03-home.png", "打開就看到重點",
     "本週任務與手上的加碼券\n同一頁看完，不用到處找"),
    ("03", "04-tasks.png", "看懂每一期進度",
     "該上傳、該兌換\n14 期狀態一次標清楚"),
    ("04", "08b-vendor-intro.png", "先看能換什麼",
     "每個通路的可兌換商品分類\n挑定了再送出，不怕換錯"),
    ("05", "06-upload.png", "挑一張截圖送出",
     "從相簿選運動紀錄截圖\n直接送到當期任務"),
    ("06", "09-wallet.png", "加碼券收進券夾",
     "換到的券集中一頁\n要用時打開出示條碼"),
    ("07", "12-profile-security.png", "個資只留在手機",
     "沒有伺服器、不寫紀錄檔\n想刪隨時一鍵清光"),
]

if __name__ == "__main__":
    print(f"畫布 {CANVAS_W}x{CANVAS_H}（比例 {CANVAS_H / CANVAS_W:.3f}，Play 上限 2.0）")
    for num, raw, verb, desc in SET:
        compose(os.path.join(RAW, raw), EYEBROW, verb, desc,
                os.path.join(OUT, f"zh-Hant_{num}.png"))
    print(f"共 {len(SET)} 張")
