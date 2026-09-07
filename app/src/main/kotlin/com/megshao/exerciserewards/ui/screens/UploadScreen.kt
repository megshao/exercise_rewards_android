package com.megshao.exerciserewards.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megshao.exerciserewards.AppContainer
import com.megshao.exerciserewards.core.services.UploadFailure
import com.megshao.exerciserewards.core.services.UploadResult
import com.megshao.exerciserewards.data.ImageReencoder
import com.megshao.exerciserewards.telemetry.AnalyticsEvent
import com.megshao.exerciserewards.telemetry.Endpoint
import com.megshao.exerciserewards.telemetry.FailReason
import com.megshao.exerciserewards.telemetry.ScreenName
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.telemetry.TelemetryIssue
import com.megshao.exerciserewards.telemetry.UploadOutcome
import com.megshao.exerciserewards.telemetry.UploadPickOutcome
import com.megshao.exerciserewards.ui.appViewModel
import com.megshao.exerciserewards.ui.components.AppCard
import com.megshao.exerciserewards.ui.components.InfoBanner
import com.megshao.exerciserewards.ui.components.PrimaryButton
import com.megshao.exerciserewards.ui.components.SecondaryButton
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 上傳運動紀錄。
 *
 * 隱私設計：讓使用者從相簿**自選**一張運動紀錄截圖 → 預覽 → 「確認上傳」。
 * 上傳的內容完全由使用者挑選，App 不會自己產生任何圖卡。
 *
 * 選好的圖一定會先重新編碼（見 [ImageReencoder]）——那是把 EXIF／GPS 丟掉的地方，
 * 也是這一頁最重要的一行。用系統的照片挑選器，**不需要任何相簿權限**。
 */
@Composable
public fun UploadScreen(taskId: String, periodIndex: Int?) {
    val context = LocalContext.current
    val viewModel = appViewModel(key = "upload-$taskId") { container, appContext ->
        UploadViewModel(container, appContext, taskId, periodIndex)
    }
    val state by viewModel.state.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::onPicked) }

    LaunchedEffect(Unit) {
        // 不帶 taskID（期別 UUID）。要知道是第幾期，看 upload_submit 的 period_index。
        Telemetry.screenAppeared(context, ScreenName.UPLOAD)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InfoBanner(
            "每期限上傳一次截圖。請從相簿選一張這期運動紀錄的截圖（例如運動 App 的統計畫面），確認後送出。",
        )

        val result = state.result
        if (result != null) {
            ResultCard(result = result, onReset = viewModel::reset)
        } else {
            SecondaryButton(
                text = if (state.preview != null) "重新選擇截圖" else "從相簿選擇截圖",
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !state.isUploading,
                fillWidth = true,
                icon = Icons.Filled.PhotoLibrary,
            )

            state.errorMessage?.let {
                Text(it, fontSize = 12.5.sp, color = Tokens.danger, lineHeight = 19.sp)
            }

            state.preview?.let { bitmap ->
                PreviewCard(bitmap)
                // 這一行是給使用者的承諾，也是實作的說明：送出去的是重新編碼過的檔案。
                Text(
                    "送出前已重新編碼，原始檔案的 EXIF（含 GPS 位置）不會一起上傳。",
                    fontSize = 11.5.sp,
                    color = Tokens.dim,
                    lineHeight = 17.sp,
                )
                PrimaryButton(
                    text = "確認上傳",
                    onClick = viewModel::confirmUpload,
                    loading = state.isUploading,
                    icon = Icons.Filled.ArrowCircleUp,
                )
            }
        }

        Spacer(Modifier.size(20.dp))
    }
}

@Composable
private fun PreviewCard(bitmap: Bitmap) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "已選擇的截圖預覽",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .clip(Tokens.shapeMedium)
            .border(1.dp, Tokens.line2, Tokens.shapeMedium),
    )
}

@Composable
private fun ResultCard(result: UploadResult, onReset: () -> Unit) {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (result.submitted) Icons.Filled.Verified else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (result.submitted) Tokens.success else Tokens.danger,
                modifier = Modifier.size(40.dp),
            )
            Text(
                result.message,
                style = displayStyle(14, FontWeight.Medium),
                color = Tokens.text,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
            if (!result.submitted) {
                SecondaryButton(text = "重新選擇", onClick = onReset)
            }
        }
    }
}

// MARK: - ViewModel

public class UploadViewModel(
    private val container: AppContainer,
    private val context: Context,
    private val taskId: String,
    /** 活動週次（1–14）。遙測只送這個，**不送 taskId（期別 UUID）**。 */
    private val periodIndex: Int?,
) : ViewModel() {

    public data class State(
        val preview: Bitmap? = null,
        val isUploading: Boolean = false,
        val errorMessage: String? = null,
        val result: UploadResult? = null,
    )

    private val _state = MutableStateFlow(State())
    public val state: StateFlow<State> = _state.asStateFlow()

    private var imageBytes: ByteArray? = null

    /**
     * 讀取使用者從相簿選取的截圖並重新編碼。
     *
     * **只送二元結果（picked / unreadable）。** 刻意不送的東西：檔案大小、圖片尺寸、
     * 原始格式、壓縮迭代次數（迭代次數可以反推檔案大小，是衍生資訊）、相片識別碼、EXIF。
     * 使用者的照片是 User Content，關於它的任何測量值都不該離開裝置。
     * 同理，選圖失敗**不進非致命錯誤**——那是關於使用者檔案的錯誤。
     */
    public fun onPicked(uri: Uri) {
        _state.value = _state.value.copy(errorMessage = null)
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) { ImageReencoder.reencode(context, uri) }
            if (encoded == null) {
                imageBytes = null
                _state.value = _state.value.copy(
                    preview = null,
                    errorMessage = "無法讀取或處理這張圖片，請換一張再試",
                )
                Telemetry.logEvent(context, AnalyticsEvent.uploadPick(UploadPickOutcome.UNREADABLE))
                return@launch
            }
            imageBytes = encoded.bytes
            _state.value = _state.value.copy(preview = encoded.preview)
            Telemetry.logEvent(context, AnalyticsEvent.uploadPick(UploadPickOutcome.PICKED))
        }
    }

    /** 送出使用者自選的截圖（真實 multipart POST /member/upload，file 欄位 screenshot）。 */
    public fun confirmUpload() {
        val bytes = imageBytes ?: return
        _state.value = _state.value.copy(isUploading = true, errorMessage = null)
        viewModelScope.launch {
            // 只有活動週次。沒有任何檔案資訊。
            Telemetry.logEvent(context, AnalyticsEvent.uploadSubmit(periodIndex))
            val startedAt = System.nanoTime()
            try {
                val uploadResult = container.environment.value.upload.upload(
                    taskId = taskId.ifEmpty { null },
                    imageData = bytes,
                    fileName = "screenshot.jpg",
                )
                _state.value = _state.value.copy(isUploading = false, result = uploadResult)
                // 官網 `.notice--error` 的原文只留在 `uploadResult.message` 給畫面用，
                // 這裡送的是分類。
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.uploadResult(outcome(uploadResult), periodIndex, Telemetry.elapsedMs(startedAt)),
                )
                // 上傳端點回了非 200／302。「頁面沒有 file 欄位」不算錯誤（當期不可上傳是常態）。
                (uploadResult.failure as? UploadFailure.HttpError)?.let {
                    Telemetry.recordNonFatal(context, TelemetryIssue.UPLOAD, Endpoint.UPLOAD, it.status)
                }
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    isUploading = false,
                    result = UploadResult(submitted = false, message = "上傳失敗，請稍後再試"),
                )
                val reason = Telemetry.reportFailure(context, error, Endpoint.UPLOAD)
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.uploadResult(outcome(reason), periodIndex, Telemetry.elapsedMs(startedAt)),
                )
            }
        }
    }

    public fun reset() {
        imageBytes = null
        _state.value = State()
    }

    private companion object {
        /** [UploadResult] → 遙測分類。 */
        fun outcome(result: UploadResult): UploadOutcome = when (val failure = result.failure) {
            null -> if (result.submitted) UploadOutcome.SUBMITTED else UploadOutcome.UNKNOWN
            UploadFailure.WindowClosed -> UploadOutcome.WINDOW_CLOSED
            UploadFailure.CsrfMissing -> UploadOutcome.CSRF_MISSING
            UploadFailure.SiteRejected -> UploadOutcome.SITE_REJECTED
            is UploadFailure.HttpError -> UploadOutcome.HTTP_ERROR
        }

        /** throw 出來的錯誤 → 遙測分類。 */
        fun outcome(reason: FailReason): UploadOutcome = when (reason) {
            FailReason.NETWORK -> UploadOutcome.NETWORK
            FailReason.CSRF_MISSING -> UploadOutcome.CSRF_MISSING
            FailReason.SITE_STATUS, FailReason.REDIRECT_LOOP, FailReason.BLOCKED_EGRESS -> UploadOutcome.HTTP_ERROR
            FailReason.SITE_PARSE, FailReason.SESSION_PROBABLE -> UploadOutcome.SITE_REJECTED
            else -> UploadOutcome.UNKNOWN
        }
    }
}
