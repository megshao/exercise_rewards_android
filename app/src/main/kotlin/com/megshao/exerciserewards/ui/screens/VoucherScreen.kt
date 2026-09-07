package com.megshao.exerciserewards.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megshao.exerciserewards.AppContainer
import com.megshao.exerciserewards.core.models.Voucher
import com.megshao.exerciserewards.core.models.VoucherFigure
import com.megshao.exerciserewards.core.models.VoucherOtpResult
import com.megshao.exerciserewards.telemetry.AnalyticsEvent
import com.megshao.exerciserewards.telemetry.AnalyticsValue
import com.megshao.exerciserewards.telemetry.BarcodeFormat
import com.megshao.exerciserewards.telemetry.Endpoint
import com.megshao.exerciserewards.telemetry.FailReason
import com.megshao.exerciserewards.telemetry.OtpSendOutcome
import com.megshao.exerciserewards.telemetry.OtpVerifyOutcome
import com.megshao.exerciserewards.telemetry.ScreenName
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.telemetry.TelemetryIssue
import com.megshao.exerciserewards.telemetry.VoucherRevealOutcome
import com.megshao.exerciserewards.telemetry.VoucherSource
import com.megshao.exerciserewards.ui.appViewModel
import com.megshao.exerciserewards.ui.components.AppCard
import com.megshao.exerciserewards.ui.components.BarcodeGenerator
import com.megshao.exerciserewards.ui.components.InfoBanner
import com.megshao.exerciserewards.ui.components.OtpCodeField
import com.megshao.exerciserewards.ui.components.PrimaryButton
import com.megshao.exerciserewards.ui.components.SecondaryButton
import com.megshao.exerciserewards.ui.components.clickableRow
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 檢視加碼券（券夾的券卡樣式 + 兌換頁的 OTP 輸入）。
 *
 * 每次進入畫面都要重新走一次簡訊 OTP 驗證才會顯示券碼——依合規要求「須本人帳號即時畫面
 * 抵用、不得截圖」，本畫面（與底層的 `VoucherServicing`）**完全不快取券碼**，
 * 一律從 `NeedsOtp` 開始，離開後再進來就要重新驗證一次。
 */
@Composable
public fun VoucherScreen(
    taskId: String,
    source: VoucherSource,
    periodIndex: Int?,
) {
    val context = LocalContext.current
    val viewModel = appViewModel(key = "voucher-$taskId") { container, appContext ->
        VoucherViewModel(container, appContext, taskId)
    }
    val state by viewModel.state.collectAsState()
    val usedIds by viewModel.usedIds.collectAsState()

    LaunchedEffect(Unit) {
        // 都不帶 taskID。來源由呼叫端傳入。
        Telemetry.screenAppeared(context, ScreenName.VOUCHER)
        Telemetry.logEvent(context, AnalyticsEvent.voucherOpen(source))
    }

    DisposableEffect(Unit) {
        onDispose {
            // 離開畫面就丟棄倒數計時器；下次進來是全新的 ViewModel，
            // 狀態機一律從 NeedsOtp 重來，不會殘留上次的券碼。
            viewModel.stopCountdown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val stage = state.stage) {
            VoucherViewModel.Stage.NeedsOtp -> NeedsOtpCard(
                errorMessage = state.needsOtpError,
                isSending = state.isSendingOtp,
                onSend = { viewModel.sendOtp(isResend = false) },
            )

            VoucherViewModel.Stage.EnterCode -> EnterCodeCard(
                otp = state.otp,
                onOtpChange = viewModel::updateOtp,
                errorMessage = state.enterCodeError,
                resendCountdown = state.resendCountdown,
                isBusy = state.isVerifying || state.isLoadingVoucher,
                isSending = state.isSendingOtp,
                onResend = { viewModel.sendOtp(isResend = true) },
                onVerify = viewModel::verify,
            )

            is VoucherViewModel.Stage.Showing -> VoucherContent(
                voucher = stage.voucher,
                isMarkedUsed = usedIds.contains(taskId),
                onToggleUsed = viewModel::toggleUsed,
            )
        }

        Spacer(Modifier.size(20.dp))
    }
}

// MARK: - Stage 1: needsOtp

@Composable
private fun NeedsOtpCard(errorMessage: String?, isSending: Boolean, onSend: () -> Unit) {
    AppCard {
        Text(
            "為避免加碼券被截圖轉傳，每次檢視都需要重新完成手機簡訊驗證，驗證通過後才會顯示券碼。",
            fontSize = 13.sp,
            color = Tokens.muted,
            lineHeight = 20.sp,
        )
        errorMessage?.let {
            Spacer(Modifier.size(12.dp))
            Text(it, fontSize = 12.5.sp, color = Tokens.danger, lineHeight = 19.sp)
        }
        Spacer(Modifier.size(14.dp))
        PrimaryButton(text = "發送簡訊驗證碼", onClick = onSend, loading = isSending)
    }
}

// MARK: - Stage 2: enterCode

@Composable
private fun EnterCodeCard(
    otp: String,
    onOtpChange: (String) -> Unit,
    errorMessage: String?,
    resendCountdown: Int,
    isBusy: Boolean,
    isSending: Boolean,
    onResend: () -> Unit,
    onVerify: () -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("簡訊驗證出示券碼", style = displayStyle(17, FontWeight.Bold), color = Tokens.text)
            Text("驗證碼已發送至您登記的手機門號", fontSize = 12.5.sp, color = Tokens.muted)
        }

        Spacer(Modifier.size(16.dp))
        OtpCodeField(code = otp, onCodeChange = onOtpChange)

        errorMessage?.let {
            Spacer(Modifier.size(12.dp))
            Text(it, fontSize = 12.5.sp, color = Tokens.danger, lineHeight = 19.sp)
        }

        Spacer(Modifier.size(14.dp))
        Text(
            if (resendCountdown > 0) "重新發送 (${resendCountdown}s)" else "重新發送",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (resendCountdown > 0) Tokens.dim else Tokens.primary,
            modifier = Modifier
                .clickableRow(enabled = resendCountdown == 0 && !isSending, onClick = onResend)
                .padding(vertical = 4.dp),
        )

        Spacer(Modifier.size(14.dp))
        PrimaryButton(
            text = "檢視券碼",
            onClick = onVerify,
            enabled = otp.length == 6 && !isBusy,
            loading = isBusy,
        )
    }
}

// MARK: - Stage 3: showing

@Composable
private fun VoucherContent(
    voucher: Voucher,
    isMarkedUsed: Boolean,
    onToggleUsed: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 券頭：橘色漸層
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Tokens.shapeLarge)
                .background(Tokens.primaryGradient)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(voucher.itemName, style = displayStyle(17, FontWeight.Bold), color = Color.White)
            Text("兌換通路：${voucher.vendorName}", fontSize = 13.sp, color = Color.White)
            if (voucher.expiry.isNotEmpty()) {
                Text("兌換期限：${voucher.expiry}", fontSize = 13.sp, color = Color.White)
            }
        }

        if (voucher.figures.size > 1) {
            InfoBanner(
                "這是兩段式加碼券，請店員分別掃描下方每一段條碼，缺一不可。",
                background = Tokens.warnBackground,
                foreground = Tokens.warnText,
            )
        }

        voucher.figures.forEach { VoucherFigureCard(it) }

        if (voucher.notices.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text("注意事項", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tokens.text)
                voucher.notices.forEachIndexed { index, notice ->
                    Text(
                        "${index + 1}. $notice",
                        fontSize = 11.5.sp,
                        color = Tokens.muted,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        MarkUsedCard(isMarkedUsed = isMarkedUsed, onToggleUsed = onToggleUsed)

        Text(
            "離開此頁後，如需再次查看券碼，請重新完成簡訊驗證。",
            fontSize = 11.5.sp,
            color = Tokens.dim,
        )
    }
}

/**
 * 單一段券碼：caption + 條碼圖 + 號碼文字 fallback。
 *
 * 條碼產不出來時（未知 format 或編碼失敗）只顯示錯誤提示文字，**號碼文字仍照樣顯示**，
 * 對齊官網 `.voucher-figure__fallback` 的設計——掃不過還能手動輸入。
 */
@Composable
private fun VoucherFigureCard(figure: VoucherFigure) {
    val context = LocalContext.current
    val isQrCode = figure.format.uppercase() == "QR_CODE"
    val barcode = BarcodeGenerator.render(figure.value, figure.format)

    LaunchedEffect(figure.format, barcode == null) {
        if (barcode == null) {
            // 只送 format 分類。**券碼絕不送。**
            // 出現未知 format＝官網換了新券種，是最直接的改版訊號。
            val format = BarcodeFormat.of(figure.format)
            Telemetry.logEvent(context, AnalyticsEvent.barcodeRenderFailed(format))
            Telemetry.recordNonFatal(
                context,
                TelemetryIssue.BARCODE,
                extras = mapOf("format" to AnalyticsValue.Code(format.raw)),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Tokens.shapeLarge)
            .background(Tokens.card)
            .border(1.dp, Tokens.line2, Tokens.shapeLarge)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (figure.caption.isNotEmpty()) {
            Text(figure.caption, style = displayStyle(14, FontWeight.Bold), color = Tokens.text)
        }

        if (barcode != null) {
            Image(
                bitmap = barcode,
                contentDescription = "券碼條碼",
                contentScale = ContentScale.Fit,
                // 條碼放大時必須保持硬邊，插值會讓黑白邊界糊掉、掃不過。
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isQrCode) 190.dp else 100.dp)
                    .clip(Tokens.shapeSmall)
                    .background(Color.White)
                    .padding(if (isQrCode) 12.dp else 8.dp),
            )
        } else {
            Text(
                "條碼無法顯示，請店員手動輸入下方號碼。",
                fontSize = 12.sp,
                color = Tokens.danger,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }

        Text(
            figure.value,
            style = displayStyle(16, FontWeight.Bold),
            color = Tokens.text,
            letterSpacing = 1.5.sp,
        )
    }
}

/**
 * 「用掉了嗎」——出示完條碼之後才問，這是使用者唯一知道答案的時機。
 *
 * **為什麼要有這個**：官網沒有「已使用／已核銷」狀態（實測見 `VoucherUsageStore`），
 * 所以 App 無從得知這張券是否已在門市抵用。標記純粹是本機紀錄，
 * **券到底還能不能用，以現場條碼掃得過為準**——這一點必須在畫面上講清楚，
 * 否則使用者會以為按了就等於作廢、或反過來把標記當成券的真實狀態。
 */
@Composable
private fun MarkUsedCard(isMarkedUsed: Boolean, onToggleUsed: () -> Unit) {
    AppCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                if (isMarkedUsed) Icons.Filled.CheckCircle else Icons.Outlined.HelpOutline,
                contentDescription = null,
                tint = if (isMarkedUsed) Tokens.success else Tokens.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                if (isMarkedUsed) "已標記為使用完畢" else "已經在門市用掉了嗎？",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.text,
            )
        }

        Spacer(Modifier.size(9.dp))
        Text(
            if (isMarkedUsed) {
                "這張券在 App 內會顯示「已使用」，清單上也不再出現「顯示條碼」。" +
                    "這是本機紀錄，使用與否以條碼能否使用為主。"
            } else {
                "標記之後，這張券在 App 內會變成「已使用」、清單上不再顯示「顯示條碼」，" +
                    "方便你分辨哪幾張還沒用。\n這是本機紀錄，使用與否以條碼能否使用為主，隨時可以還原。"
            },
            fontSize = 11.5.sp,
            color = Tokens.muted,
            lineHeight = 17.sp,
        )

        Spacer(Modifier.size(9.dp))
        SecondaryButton(
            text = if (isMarkedUsed) "還原成未使用" else "標記為已使用",
            onClick = onToggleUsed,
            fillWidth = true,
        )
    }
}

// MARK: - ViewModel

public class VoucherViewModel(
    private val container: AppContainer,
    private val context: Context,
    private val taskId: String,
) : ViewModel() {

    public sealed interface Stage {
        public data object NeedsOtp : Stage
        public data object EnterCode : Stage
        public data class Showing(val voucher: Voucher) : Stage
    }

    public data class State(
        /** 一律從 [Stage.NeedsOtp] 開始：合規要求每次進入都要重新走一次 OTP。 */
        val stage: Stage = Stage.NeedsOtp,
        val otp: String = "",
        val isSendingOtp: Boolean = false,
        val isVerifying: Boolean = false,
        val isLoadingVoucher: Boolean = false,
        val needsOtpError: String? = null,
        val enterCodeError: String? = null,
        val resendCountdown: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    public val state: StateFlow<State> = _state.asStateFlow()

    public val usedIds: StateFlow<Set<String>> = container.voucherUsage.usedIds

    private var countdownJob: kotlinx.coroutines.Job? = null

    public fun updateOtp(value: String) {
        _state.value = _state.value.copy(otp = value)
    }

    /**
     * 發送 OTP。`isResend` 分辨「第一次發」與「重新發送」。
     * 發送對象的手機門號在官網 session 裡，App 從頭到尾沒碰過，自然也送不出去。
     */
    public fun sendOtp(isResend: Boolean) {
        if (_state.value.isSendingOtp) return
        if (isResend && _state.value.resendCountdown > 0) return
        _state.value = _state.value.copy(isSendingOtp = true, needsOtpError = null)
        viewModelScope.launch {
            try {
                container.environment.value.voucher.sendOtp(taskId)
                _state.value = _state.value.copy(
                    isSendingOtp = false,
                    otp = "",
                    enterCodeError = null,
                    stage = Stage.EnterCode,
                )
                Telemetry.logEvent(context, AnalyticsEvent.voucherOtpSend(OtpSendOutcome.OK, null, isResend))
                startCountdown()
            } catch (error: Throwable) {
                val reason = Telemetry.reportFailure(context, error, Endpoint.VOUCHER_RESEND)
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.voucherOtpSend(OtpSendOutcome.ERROR, reason, isResend),
                )
                _state.value = _state.value.copy(
                    isSendingOtp = false,
                    needsOtpError = "驗證碼發送失敗，請確認網路連線後重試",
                )
            }
        }
    }

    public fun verify() {
        val otp = _state.value.otp
        if (otp.length != 6 || _state.value.isVerifying) return
        _state.value = _state.value.copy(isVerifying = true, enterCodeError = null)
        viewModelScope.launch {
            try {
                when (val result = container.environment.value.voucher.verifyOtp(taskId, otp)) {
                    VoucherOtpResult.Success -> {
                        Telemetry.logEvent(
                            context,
                            AnalyticsEvent.voucherOtpVerify(OtpVerifyOutcome.SUCCESS, null),
                        )
                        _state.value = _state.value.copy(isVerifying = false)
                        loadVoucher()
                    }

                    is VoucherOtpResult.WrongCode -> {
                        val remaining = result.remaining
                        val message: String
                        val outcome: OtpVerifyOutcome
                        when {
                            remaining != null && remaining <= 0 -> {
                                message = "驗證碼錯誤次數已用罄，請重新發送驗證碼"
                                outcome = OtpVerifyOutcome.EXHAUSTED
                            }

                            remaining != null -> {
                                message = "驗證碼錯誤，還可以再試 $remaining 次"
                                outcome = OtpVerifyOutcome.WRONG_CODE
                            }

                            else -> {
                                message = "驗證碼錯誤，請再試一次"
                                outcome = OtpVerifyOutcome.WRONG_CODE
                            }
                        }
                        Telemetry.logEvent(
                            context,
                            AnalyticsEvent.voucherOtpVerify(
                                outcome,
                                remaining?.takeIf { it > 0 },
                            ),
                        )
                        _state.value = _state.value.copy(isVerifying = false, otp = "", enterCodeError = message)
                    }

                    is VoucherOtpResult.Failed -> {
                        // ⚠️ `result.message` 是官網 OTP 頁的原文，**可能含遮罩後的手機門號**
                        // （「已發送至 09xx***」）。只顯示在畫面上，絕不進遙測——只送分類。
                        Telemetry.logEvent(
                            context,
                            AnalyticsEvent.voucherOtpVerify(OtpVerifyOutcome.FAILED, null),
                        )
                        _state.value = _state.value.copy(
                            isVerifying = false,
                            otp = "",
                            enterCodeError = result.message,
                        )
                    }
                }
            } catch (error: Throwable) {
                Telemetry.reportFailure(context, error, Endpoint.VOUCHER)
                Telemetry.logEvent(context, AnalyticsEvent.voucherOtpVerify(OtpVerifyOutcome.ERROR, null))
                _state.value = _state.value.copy(
                    isVerifying = false,
                    enterCodeError = "驗證失敗，請確認網路連線後重試",
                )
            }
        }
    }

    private suspend fun loadVoucher() {
        _state.value = _state.value.copy(isLoadingVoucher = true)
        try {
            val fetched = container.environment.value.voucher.fetchVoucher(taskId)
            stopCountdown()
            _state.value = _state.value.copy(isLoadingVoucher = false, stage = Stage.Showing(fetched))
            // `figure_count` 與 `format` 描述的是**券種結構**（萊爾富是兩段式），不是券碼本身。
            // 券碼、expiry、vendorName、itemName、notices 一律不送。
            Telemetry.logEvent(
                context,
                AnalyticsEvent.voucherReveal(
                    VoucherRevealOutcome.OK,
                    fetched.figures.size,
                    BarcodeFormat.of(fetched.figures),
                ),
            )
        } catch (error: Throwable) {
            // 使用者正站在櫃檯前，這一段壞掉最該立刻知道。
            val reason = Telemetry.reportFailure(context, error, Endpoint.VOUCHER_VIEW)
            Telemetry.logEvent(
                context,
                AnalyticsEvent.voucherReveal(
                    if (reason == FailReason.SITE_PARSE) {
                        VoucherRevealOutcome.PARSE_ERROR
                    } else {
                        VoucherRevealOutcome.ERROR
                    },
                    0,
                    BarcodeFormat.OTHER,
                ),
            )
            _state.value = _state.value.copy(
                isLoadingVoucher = false,
                enterCodeError = "驗證成功，但券碼載入失敗，請重新整理",
            )
        }
    }

    public fun toggleUsed() {
        val used = container.voucherUsage.toggle(taskId)
        // 只送方向，不帶期別 UUID／期數。
        Telemetry.logEvent(context, AnalyticsEvent.voucherMarkUsed(used))
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        _state.value = _state.value.copy(resendCountdown = RESEND_COOLDOWN_SECONDS)
        countdownJob = viewModelScope.launch {
            while (_state.value.resendCountdown > 0) {
                delay(1_000)
                _state.value = _state.value.copy(
                    resendCountdown = (_state.value.resendCountdown - 1).coerceAtLeast(0),
                )
            }
        }
    }

    public fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}
