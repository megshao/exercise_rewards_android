package com.megshao.exerciserewards.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat as ZxingFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * 依 `data-format` 即時生成券碼圖。
 *
 * **條碼是純粹依號碼值算出的圖形**，不是需要下載的圖片資產——所以拿到號碼與格式之後，
 * 在本機畫出來就行，不必（也不該）為了一張條碼多開一條對外連線。
 *
 * **為什麼引 ZXing**：iOS 有 CoreImage 內建的條碼濾鏡，Android 平台**沒有**對等的東西
 * （只有相機端的掃描 API，沒有產生器）。ZXing core 是純 JVM、無 Android 相依，
 * 也是這件事的事實標準。
 *
 * 支援的 `format`（分流依據見 `VoucherFigure.format` 的原始字串）：
 * - `CODE_128` → Code 128
 * - `QR_CODE`  → QR
 * - `AZTEC`    → Aztec
 * - `PDF_417`  → PDF417
 *
 * 官網目前只用前兩種。後兩種是**預先接上**的：ZXing 本來就支援，接起來幾乎沒有成本，
 * 而它們是台灣零售券碼除了 Code 128／QR 之外最可能出現的兩種。這樣官網換券種時
 * 使用者當下就有條碼可掃，不必等一次改版上架。
 *
 * 其他未知 format、或編碼失敗，一律回傳 `null`——呼叫端（券碼頁）在拿到 `null` 時
 * **必須顯示號碼文字**，讓店員能改用手動輸入，而不是空白畫面。
 */
public object BarcodeGenerator {

    /**
     * @param value 券碼字面值（`data-value`）。
     * @param format 券碼符號集（`data-format` 原始字串，例如 "CODE_128" / "QR_CODE"）。
     */
    public fun render(value: String, format: String): ImageBitmap? {
        if (value.isEmpty()) return null
        val zxingFormat = when (format.uppercase()) {
            "CODE_128" -> ZxingFormat.CODE_128
            "QR_CODE" -> ZxingFormat.QR_CODE
            "AZTEC" -> ZxingFormat.AZTEC
            // 官網若用了 PDF417，格式字串照 ZXing 慣例會是 `PDF_417`；`PDF417` 一併容錯。
            "PDF_417", "PDF417" -> ZxingFormat.PDF_417
            else -> return null
        }

        // 一維條碼要寬扁、二維要方形。這裡給的是**編碼解析度**，實際顯示尺寸由 Compose 拉伸；
        // `filterQuality = None` 讓放大時保持硬邊，條碼才掃得過。
        val isSquare = zxingFormat == ZxingFormat.QR_CODE || zxingFormat == ZxingFormat.AZTEC
        val width = if (isSquare) 512 else 720
        val height = if (isSquare) 512 else 240

        val hints = mapOf(
            EncodeHintType.MARGIN to if (isSquare) 1 else 4,
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
        )

        val matrix = runCatching {
            MultiFormatWriter().encode(value, zxingFormat, width, height, hints)
        }.getOrNull() ?: return null

        return matrix.toBitmap().asImageBitmap()
    }

    private fun BitMatrix.toBitmap(): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
