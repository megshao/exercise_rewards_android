package com.megshao.exerciserewards.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megshao.exerciserewards.AppContainer
import com.megshao.exerciserewards.core.models.RedeemOption
import com.megshao.exerciserewards.core.models.RedeemResult
import com.megshao.exerciserewards.core.services.SiteHandoff
import com.megshao.exerciserewards.core.services.SiteHandoffDestination
import com.megshao.exerciserewards.telemetry.AnalyticsEvent
import com.megshao.exerciserewards.telemetry.Endpoint
import com.megshao.exerciserewards.telemetry.FailReason
import com.megshao.exerciserewards.telemetry.ListOutcome
import com.megshao.exerciserewards.telemetry.RedeemOutcome
import com.megshao.exerciserewards.telemetry.ScreenName
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.telemetry.TelemetryIssue
import com.megshao.exerciserewards.telemetry.Vendor
import com.megshao.exerciserewards.ui.appViewModel
import com.megshao.exerciserewards.ui.components.AppCard
import com.megshao.exerciserewards.ui.components.InfoBanner
import com.megshao.exerciserewards.ui.components.LoadingBlock
import com.megshao.exerciserewards.ui.components.PrimaryButton
import com.megshao.exerciserewards.ui.components.SiteHandoffMessage
import com.megshao.exerciserewards.ui.components.StateMessage
import com.megshao.exerciserewards.ui.components.VendorLogo
import com.megshao.exerciserewards.ui.components.clickableRow
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 兌換好禮：列出可兌換的商家品項，點「兌換」需先二次確認警語
 * （兌換後不可更換、需簡訊驗證出示券碼）才會真的送出表單。
 *
 * 每一列另有「兌換品項」，開廠商商品頁看該通路的加碼券能換哪些商品——
 * 對應官網同一列的那顆按鈕（官網是另開瀏覽器視窗，這裡改成 App 內的頁面，
 * 使用者不會被帶離兌換流程）。
 *
 * 兌換成功後導向券碼頁——該期已經是 state=REDEEMED，要看券碼需再走一次簡訊 OTP 驗證。
 */
@Composable
public fun RedeemScreen(
    taskId: String,
    periodIndex: Int?,
    onOpenVendorIntro: (path: String, vendorName: String) -> Unit,
    onOpenVoucher: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = appViewModel(key = "redeem-$taskId") { container, appContext ->
        RedeemViewModel(container, appContext, taskId, periodIndex)
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        // 不帶 taskID。
        Telemetry.screenAppeared(context, ScreenName.REDEEM)
        if (state.options.isEmpty() && state.result == null) viewModel.load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        InfoBanner(
            "選一家合作商家後即完成兌換，每筆運動紀錄只能兌換一次，送出後不可更換。兌換後需簡訊驗證才會產生券碼。",
            background = Tokens.warnBackground,
            foreground = Tokens.warnText,
        )

        val result = state.result
        when {
            result != null -> RedeemResultCard(result = result, onOpenVoucher = onOpenVoucher)

            state.isLoading && state.options.isEmpty() -> LoadingBlock()

            // 兌換頁結構對不上（見 core 的 SiteHandoff.shouldHandoff）：說實話並交接到官網的
            // 同一期兌換頁；taskId 的驗證與退回都在 core，這裡不拼網址。
            state.siteChangeSuspected && state.options.isEmpty() -> SiteHandoffMessage(
                destination = SiteHandoffDestination.Redeem(taskId),
                onRetry = viewModel::load,
            )

            state.errorMessage != null && state.options.isEmpty() -> StateMessage(
                icon = Icons.Filled.Warning,
                iconTint = Tokens.danger,
                message = state.errorMessage.orEmpty(),
                onRetry = viewModel::load,
            )

            state.options.isEmpty() -> Text(
                "目前沒有可兌換的商家品項。",
                fontSize = 13.sp,
                color = Tokens.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.options.forEach { option ->
                    VendorRow(
                        option = option,
                        isSubmitting = state.isSubmitting,
                        onRedeem = { viewModel.selectOption(option) },
                        onIntro = { option.introPath?.let { onOpenVendorIntro(it, option.vendorName) } },
                    )
                }
            }
        }

        Spacer(Modifier.size(20.dp))
    }

    state.pendingOption?.let { option ->
        AlertDialog(
            // 滑掉／點外面等同取消。
            onDismissRequest = viewModel::cancelPending,
            title = { Text("確認兌換") },
            text = {
                Text("將兌換「${option.vendorName}．${option.itemName}」。兌換後不可更換，需簡訊驗證出示券碼。")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRedeem) {
                    Text("確認兌換", color = Tokens.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPending) { Text("取消") }
            },
            containerColor = Tokens.card,
        )
    }
}

@Composable
private fun RedeemResultCard(result: RedeemResult, onOpenVoucher: () -> Unit) {
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
            if (result.submitted) {
                PrimaryButton(text = "檢視加碼券", onClick = onOpenVoucher)
            }
        }
    }
}

/**
 * 單一商家品項列：logo 色塊、品項名、「兌換品項」與「兌換」兩顆鈕。
 *
 * 「兌換品項」只在該商家真的有介紹頁時出現（`introPath != null`）——
 * 這與官網的規則一致：靜態頁存在與否就是唯一的開關，不自己拼網址。
 */
@Composable
private fun VendorRow(
    option: RedeemOption,
    isSubmitting: Boolean,
    onRedeem: () -> Unit,
    onIntro: () -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.radiusLarge)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.04f))
            .clip(shape)
            .background(Tokens.card)
            .border(1.dp, Tokens.line2, shape)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VendorLogo(option.vendorName)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(option.vendorName, style = displayStyle(15, FontWeight.Bold), color = Tokens.text)
            Text(option.itemName, fontSize = 12.sp, color = Tokens.muted, lineHeight = 18.sp)
        }

        if (option.introPath != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tokens.card2)
                    .border(1.dp, Tokens.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickableRow(enabled = true, onClick = onIntro)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("兌換品項", style = displayStyle(13, FontWeight.Bold), color = Tokens.primary)
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSubmitting) Tokens.dim else Tokens.primary)
                .clickableRow(enabled = !isSubmitting, onClick = onRedeem)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("兌換", style = displayStyle(13, FontWeight.Bold), color = Color.White)
        }
    }
}

// MARK: - ViewModel

public class RedeemViewModel(
    private val container: AppContainer,
    private val context: Context,
    private val taskId: String,
    /** 活動週次（1–14）。遙測只送這個，不送 taskId。 */
    private val periodIndex: Int?,
) : ViewModel() {

    public data class State(
        val options: List<RedeemOption> = emptyList(),
        val isLoading: Boolean = false,
        val isSubmitting: Boolean = false,
        val errorMessage: String? = null,
        /** 最近一次載入失敗是不是「官網結構對不上」（core 的 `SiteHandoff.shouldHandoff`）。 */
        val siteChangeSuspected: Boolean = false,
        val result: RedeemResult? = null,
        val pendingOption: RedeemOption? = null,
    )

    private val _state = MutableStateFlow(State())
    public val state: StateFlow<State> = _state.asStateFlow()

    public fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val loaded = container.environment.value.redeem.options(taskId)
                _state.value = _state.value.copy(options = loaded, isLoading = false, siteChangeSuspected = false)
                // `option_count` 是官網目錄大小（全體使用者一樣），不是個人資料。
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.redeemOptions(
                        if (loaded.isEmpty()) ListOutcome.EMPTY else ListOutcome.OK,
                        null,
                        loaded.size,
                    ),
                )
                reportIntroLinkDrift(loaded)
            } catch (error: Throwable) {
                val reason = Telemetry.reportFailure(context, error, Endpoint.REDEEM)
                Telemetry.logEvent(context, AnalyticsEvent.redeemOptions(ListOutcome.ERROR, reason, 0))
                _state.value = _state.value.copy(
                    isLoading = false,
                    // 官網結構對不上時畫面走交接畫面，這句「請確認網路連線」不會被顯示出來。
                    siteChangeSuspected = SiteHandoff.shouldHandoff(error),
                    errorMessage = "無法載入兌換清單，請確認網路連線後重新整理",
                )
            }
        }
    }

    /**
     * 「解析成功但一個介紹頁連結都沒有」的警報。
     *
     * **為什麼需要它**：這是這條路徑上唯一**不會丟出錯誤**的失敗。官網目前每一家都有
     * 「兌換品項」連結；如果切列邊界對不上官網的 class 寫法，`RedeemParser` 會退回
     * 切表單的路徑——品項照樣解析成功、兌換照樣可用、`redeem_options` 照樣回報 ok，
     * 只是每一列的 `introPath` 都變成 null，按鈕靜默消失。沒有這個警報，
     * 只有使用者回報才會發現。
     *
     * 只送一個整數（品項數），不送商家名、品項名或任何路徑——官網文字一律不進遙測。
     */
    private fun reportIntroLinkDrift(loaded: List<RedeemOption>) {
        if (loaded.isEmpty() || loaded.any { it.introPath != null }) return
        Telemetry.recordNonFatal(
            context,
            TelemetryIssue.REDEEM_INTRO_MISSING,
            Endpoint.REDEEM,
            extras = mapOf("options" to com.megshao.exerciserewards.telemetry.AnalyticsValue.Count(loaded.size)),
        )
    }

    /**
     * `Vendor` 是由**公開的商家名稱**分類出來的封閉列舉，
     * **不是** `vendorId`／`itemId`（那是官網識別碼），也不是 `itemName`（官網文字）。
     */
    public fun selectOption(option: RedeemOption) {
        _state.value = _state.value.copy(pendingOption = option)
        Telemetry.logEvent(context, AnalyticsEvent.redeemSelect(Vendor.of(option.vendorName)))
    }

    /** 使用者在確認對話框按了取消（或滑掉）。 */
    public fun cancelPending() {
        val option = _state.value.pendingOption ?: return
        _state.value = _state.value.copy(pendingOption = null)
        Telemetry.logEvent(context, AnalyticsEvent.redeemCancel(Vendor.of(option.vendorName)))
    }

    public fun confirmRedeem() {
        val option = _state.value.pendingOption ?: return
        val vendor = Vendor.of(option.vendorName)
        _state.value = _state.value.copy(pendingOption = null, isSubmitting = true)
        viewModelScope.launch {
            Telemetry.logEvent(context, AnalyticsEvent.redeemSubmit(vendor, periodIndex))
            val startedAt = System.nanoTime()
            try {
                val redeemResult = container.environment.value.redeem.redeem(
                    taskId = taskId,
                    vendorId = option.vendorId,
                    item = option.itemId,
                )
                _state.value = _state.value.copy(isSubmitting = false, result = redeemResult)
                // `RedeemResult.message` 即使是 App 自己的靜態文案也不送，維持「無字串」原則。
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.redeemResult(
                        if (redeemResult.submitted) RedeemOutcome.SUBMITTED else RedeemOutcome.STAYED_ON_PAGE,
                        vendor,
                        Telemetry.elapsedMs(startedAt),
                    ),
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    result = RedeemResult(
                        submitted = false,
                        message = "兌換失敗，請稍後再試，或改用官網確認任務狀態",
                    ),
                )
                val reason = Telemetry.reportFailure(context, error, Endpoint.REDEEM)
                val outcome = when (reason) {
                    FailReason.NETWORK -> RedeemOutcome.NETWORK
                    FailReason.SITE_STATUS, FailReason.REDIRECT_LOOP,
                    FailReason.BLOCKED_EGRESS, FailReason.CSRF_MISSING,
                    -> RedeemOutcome.HTTP_ERROR

                    else -> RedeemOutcome.UNKNOWN
                }
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.redeemResult(outcome, vendor, Telemetry.elapsedMs(startedAt)),
                )
            }
        }
    }
}
