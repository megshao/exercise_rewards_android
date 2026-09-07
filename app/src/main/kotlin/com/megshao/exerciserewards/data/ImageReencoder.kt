package com.megshao.exerciserewards.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

/**
 * 把使用者從相簿選來的圖重新編碼成 JPEG。
 *
 * ## 這支存在的唯一理由是隱私，不是格式轉換
 *
 * 相簿原檔帶著完整 EXIF，其中包含 **GPS 座標**——那是「使用者在哪裡運動」的精確位置，
 * 比運動紀錄本身更敏感，而且使用者按「確認上傳」時完全不會預期它被一起送出去。
 * 把圖解碼成 Bitmap 再重新壓成 JPEG 會把 EXIF 整段丟掉，這是本流程唯一的去識別化手段。
 *
 * **所以它不能有旁路**：編碼失敗時呼叫端必須報錯，**絕不可以退回原檔**
 * （原檔也可能是 HEIC/PNG，跟固定送出的 `screenshot.jpg` / `image/jpeg` 對不上，
 * 本來就不該當 fallback）。
 *
 * ## 與 iOS 端的一個平台差異
 *
 * iOS 的 `UIImage` 解碼時會自動套用 EXIF 方向。Android 這邊 `ImageDecoder`（API 28+）
 * 也會，但 API 26–27 只有 `BitmapFactory`，它**不套用方向**。螢幕截圖本來就沒有方向
 * 標記（是截圖器產生的，不是相機拍的），所以實務上不影響；真的遇到相機照片在舊機型上
 * 躺著，使用者自己旋轉後重選即可。**刻意不引 `androidx.exifinterface` 只為讀一個方向欄位**
 * ——那個函式庫的用途是「讀 EXIF」，而這裡的整個目的是把 EXIF 丟掉。
 */
public object ImageReencoder {

    /** 官網單檔上限 5 MB。 */
    public const val MAX_BYTES: Int = 5 * 1024 * 1024

    /** 解碼時的長邊上限。截圖不需要比這更大，也避免超大圖把記憶體吃掉。 */private const val MAX_DIMENSION = 2048

    public data class Encoded(val bytes: ByteArray, val preview: Bitmap) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * 讀取 [uri] 並重新編碼。任何一步失敗都回 null——呼叫端必須報錯而不是送原檔。
     */
    public fun reencode(context: Context, uri: Uri): Encoded? {
        val bitmap = decode(context, uri) ?: return null
        val bytes = jpegUnderLimit(bitmap) ?: return null
        return Encoded(bytes, bitmap)
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_DIMENSION) {
                    decoder.setTargetSampleSize(sampleSize(longest))
                }
                // 一律轉成可變的 ARGB_8888，後面才壓得出 JPEG。
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
    }.getOrNull()

    private fun sampleSize(longestSide: Int): Int {
        var sample = 1
        while (longestSide / sample > MAX_DIMENSION) sample *= 2
        return sample
    }

    /**
     * 把圖片編成 ≤5 MB 的 JPEG；必要時逐步降畫質。
     *
     * 回傳 null 時呼叫端必須報錯——**不可退回原檔**，重新編碼正是拿掉 EXIF 的地方。
     */
    public fun jpegUnderLimit(bitmap: Bitmap): ByteArray? {
        for (quality in 90 downTo 30 step 10) {
            val stream = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) return null
            val bytes = stream.toByteArray()
            if (bytes.size <= MAX_BYTES) return bytes
        }
        // 最低畫質還是超標就放棄：官網會擋，先在這裡講清楚比送出去被退好。
        val stream = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 30, stream)) return null
        return stream.toByteArray().takeIf { it.size <= MAX_BYTES }
    }
}
