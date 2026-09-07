package com.megshao.exerciserewards.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 上傳截圖的專用下載器。
 *
 * **為什麼不用 Coil／Glide**（iOS 端的對應理由是「不用 AsyncImage」）：那些函式庫預設
 * 走共用的 OkHttpClient 與磁碟快取。三件事跟預設不同，每一件都是刻意的：
 *
 * - **cookie-less**：這個 client 沒有 cookie jar，所以這條路徑**真的**沒有登入 cookie，
 *   不必依賴「cookie 的 domain scope 剛好不match」這個間接保證。`Location` 指回
 *   500.gov.tw 的同源情境下，這個差別就會現形。
 * - **不落盤**：使用者的運動紀錄截圖（可能有姓名、路線）不寫進任何磁碟快取。
 * - **大小上限**：避免惡意／異常回應把整份 body 讀進記憶體。
 *
 * 網址本身另有一道關卡在 core：`TasksService.isAllowedScreenshotImageUrl`
 * （https + 官方站白名單或官方圖片儲存網域），302 `Location` 是不受信任輸入。
 */
public object ScreenshotLoader {

    /** 圖片大小上限。上傳端本來就壓在 5 MB 以內，這裡留四倍餘裕。 */
    public const val MAX_IMAGE_BYTES: Int = 20 * 1024 * 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 沒有 cookieJar：預設就是 CookieJar.NO_COOKIES。這裡明寫出來當文件。
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            // 沒有 cache：OkHttp 預設不快取，同樣明寫。
            .cache(null)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    public sealed interface Result {
        public data class Ok(val bitmap: Bitmap) : Result
        public data object Failed : Result
    }

    public fun load(url: String): Result {
        // 示範模式回傳的是本機標記，不走網路——示範模式絕不能連外。
        if (url == MockTasksService.DEMO_SCREENSHOT_URI) {
            return Result.Ok(demoScreenshot())
        }

        val request = Request.Builder().url(url).build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.Failed
                val (bytes, exceeded) = readAtMost(response.body.byteStream())
                if (exceeded) return Result.Failed
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return Result.Failed
                Result.Ok(bitmap)
            }
        }.getOrDefault(Result.Failed)
    }

    private fun readAtMost(stream: java.io.InputStream): Pair<ByteArray, Boolean> {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMAGE_BYTES) return ByteArray(0) to true
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray() to false
    }

    /**
     * 示範模式的截圖：**在裝置上畫出來**，不打包圖檔也不連外。
     *
     * iOS 端是把一張 PNG 放進 app bundle；這裡選擇畫，理由是不必為了示範模式在 repo 裡
     * 多一個二進位檔（開源專案裡每個二進位檔都是一個「這是什麼、誰放的」的問題）。
     */
    private fun demoScreenshot(): Bitmap {
        val width = 720
        val height = 1280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#FFF7EE"))

        val title = Paint().apply {
            color = Color.parseColor("#1A1D24")
            textSize = 56f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val body = Paint().apply {
            color = Color.parseColor("#697485")
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val accent = Paint().apply {
            color = Color.parseColor("#FF5A1F")
            textSize = 120f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        canvas.drawText("示範運動紀錄", width / 2f, 320f, title)
        canvas.drawText("8,432 步", width / 2f, 500f, accent)
        canvas.drawText("這是示範模式的範例畫面", width / 2f, 600f, body)
        canvas.drawText("未連線官方網站，也不是真實資料", width / 2f, 660f, body)
        return bitmap
    }
}
