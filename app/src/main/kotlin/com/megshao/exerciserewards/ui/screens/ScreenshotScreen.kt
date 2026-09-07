package com.megshao.exerciserewards.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.megshao.exerciserewards.data.ScreenshotLoader
import com.megshao.exerciserewards.telemetry.AnalyticsEvent
import com.megshao.exerciserewards.telemetry.Endpoint
import com.megshao.exerciserewards.telemetry.ScreenName
import com.megshao.exerciserewards.telemetry.ScreenshotOutcome
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.ui.components.LoadingBlock
import com.megshao.exerciserewards.ui.components.StateMessage
import com.megshao.exerciserewards.ui.rememberAppContainer
import com.megshao.exerciserewards.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 看截圖：讀取 `TasksServicing.screenshotImageUrl` 解析出的圖片網址，用**專用的
 * cookie-less client** 下載後顯示（見 [ScreenshotLoader]）。
 *
 * 刻意的範圍例外：這裡顯示的圖片只會是「使用者本人上傳到官方站儲存」的簽章網址
 * （網址自帶簽章、無需登入即可讀取），網域由官方站決定。core 的 500.gov.tw 白名單只擋
 * 「HTTP client 主動發出」的請求，所以這條路徑另外有兩道自己的關卡：
 * 1. `TasksService.isAllowedScreenshotImageUrl`：302 `Location` 必須是 https，
 *    且 host 在官方站白名單或官方圖片儲存網域內，否則丟 BlockedEgress。
 * 2. [ScreenshotLoader]：cookie-less、不落盤的專用 client。
 */
@Composable
public fun ScreenshotScreen(taskId: String) {
    val context = LocalContext.current
    val container = rememberAppContainer()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // 縮放狀態：預設 fit（scale 1），雙指可放大、雙擊還原。
    var scale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(taskId) {
        Telemetry.screenAppeared(context, ScreenName.SCREENSHOT)

        /**
         * **圖片網址、它的 host 與查詢字串一律不送**——簽章網址內含 bucket 名與簽章，
         * 是官方站識別碼。只送四選一的結果分類。
         */
        fun report(outcome: ScreenshotOutcome) {
            Telemetry.logEvent(context, AnalyticsEvent.screenshotView(outcome))
        }

        if (taskId.isEmpty()) {
            isLoading = false
            errorMessage = "找不到這期任務的截圖"
            report(ScreenshotOutcome.NO_ID)
            return@LaunchedEffect
        }

        val url = try {
            container.environment.value.tasks.screenshotImageUrl(taskId)
        } catch (error: Throwable) {
            isLoading = false
            errorMessage = "無法載入截圖，請確認網路連線後重試"
            Telemetry.reportFailure(context, error, Endpoint.SCREENSHOT)
            report(ScreenshotOutcome.URL_FAILED)
            return@LaunchedEffect
        }

        when (val result = withContext(Dispatchers.IO) { ScreenshotLoader.load(url) }) {
            is ScreenshotLoader.Result.Ok -> {
                bitmap = result.bitmap
                isLoading = false
                report(ScreenshotOutcome.OK)
            }

            ScreenshotLoader.Result.Failed -> {
                isLoading = false
                errorMessage = "圖片載入失敗，請稍後再試"
                // 圖片下載失敗**不進 reportFailure**：這條路徑的對面是官方站指定的
                // 第三方圖床，它的網路錯誤不是「官網改版」訊號，只送結果分類。
                report(ScreenshotOutcome.IMAGE_FAILED)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Tokens.background),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        when {
            image != null -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "上傳的運動紀錄截圖",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2f })
                    },
            )

            isLoading -> LoadingBlock(topPadding = 0.dp)

            else -> StateMessage(
                icon = Icons.Filled.Warning,
                iconTint = Tokens.danger,
                message = errorMessage ?: "無法載入截圖",
                topPadding = 0.dp,
            )
        }
    }
}
