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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megshao.exerciserewards.AppContainer
import com.megshao.exerciserewards.core.models.TaskPeriod
import com.megshao.exerciserewards.core.models.TaskState
import com.megshao.exerciserewards.telemetry.AnalyticsEvent
import com.megshao.exerciserewards.telemetry.Endpoint
import com.megshao.exerciserewards.telemetry.ScreenName
import com.megshao.exerciserewards.telemetry.TasksSource
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.ui.appViewModel
import com.megshao.exerciserewards.ui.components.AppCard
import com.megshao.exerciserewards.ui.components.LoadingBlock
import com.megshao.exerciserewards.ui.components.PrimaryButton
import com.megshao.exerciserewards.ui.components.SecondaryButton
import com.megshao.exerciserewards.ui.components.SectionTitle
import com.megshao.exerciserewards.ui.components.StateMessage
import com.megshao.exerciserewards.ui.components.StatusBadge
import com.megshao.exerciserewards.ui.components.clickableRow
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 我的券夾：列出已兌換（可使用）的加碼券與任務完成待兌換的期別。
 *
 * - 已兌換 → 點「檢視券碼」（每次都要 OTP 驗證後才顯示條碼）。
 * - 已兌換且使用者標記過「已使用」→ 移到最下方的「已使用」區，**不再顯示「檢視券碼」**。
 *   官網沒有這個狀態，只能靠使用者自己標記（見 `VoucherUsageStore`）。
 * - 待兌換 → 點「去兌換」（兌換的二次確認在兌換頁內）。
 */
@Composable
public fun WalletScreen(
    onVoucher: (TaskPeriod) -> Unit,
    onRedeem: (TaskPeriod) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = appViewModel { container, appContext -> WalletViewModel(container, appContext) }
    val state by viewModel.state.collectAsState()
    val usedIds by viewModel.usedIds.collectAsState()

    LaunchedEffect(Unit) {
        Telemetry.screenAppeared(context, ScreenName.WALLET)
        if (state.redeemed.isEmpty() && state.redeemable.isEmpty()) viewModel.refresh()
    }

    // 兌換完成後那一期會變成 REDEEMED，券夾要跟著長出新的一張。
    val revision by viewModel.tasksRevision.collectAsState()
    LaunchedEffect(revision) {
        if (revision > 0) viewModel.refresh()
    }

    val unused = state.redeemed.filter { !usedIds.contains(it.id) }
    val used = state.redeemed.filter { usedIds.contains(it.id) }
    val isEmpty = state.redeemed.isEmpty() && state.redeemable.isEmpty()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().background(Tokens.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("已取得的加碼券，最多可獲得 14 張", fontSize = 12.5.sp, color = Tokens.muted)

            when {
                state.isLoading && isEmpty -> LoadingBlock()

                state.errorMessage != null && isEmpty -> StateMessage(
                    icon = Icons.Filled.Warning,
                    iconTint = Tokens.danger,
                    message = state.errorMessage.orEmpty(),
                    onRetry = viewModel::refresh,
                    topPadding = 80.dp,
                )

                isEmpty -> StateMessage(
                    icon = Icons.Outlined.ConfirmationNumber,
                    message = "目前沒有加碼券。\n完成任務並兌換後，加碼券會出現在這裡。",
                    topPadding = 80.dp,
                )

                else -> {
                    if (state.redeemable.isNotEmpty()) {
                        SectionTitle("可兌換")
                        state.redeemable.forEach { RedeemableCard(it) { onRedeem(it) } }
                    }
                    if (unused.isNotEmpty()) {
                        SectionTitle("可使用的加碼券")
                        unused.forEach { period ->
                            VoucherCard(
                                period = period,
                                onViewCode = { onVoucher(period) },
                                onToggleUsed = { viewModel.toggleUsed(period) },
                            )
                        }
                    }
                    if (used.isNotEmpty()) {
                        SectionTitle("已使用")
                        used.forEach { period ->
                            UsedVoucherCard(period) { viewModel.toggleUsed(period) }
                        }
                    }
                }
            }

            Spacer(Modifier.size(20.dp))
        }
    }
}

/** 已兌換的券卡：可檢視券碼（走 OTP）。 */
@Composable
private fun VoucherCard(
    period: TaskPeriod,
    onViewCode: () -> Unit,
    onToggleUsed: () -> Unit,
) {
    AppCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.ConfirmationNumber,
                    contentDescription = null,
                    tint = Tokens.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text("第 ${period.index} 期加碼券", style = displayStyle(15, FontWeight.Bold), color = Tokens.text)
            }
            StatusBadge("已兌換", Tokens.dim, Tokens.disabledBackground)
        }

        Spacer(Modifier.size(12.dp))
        Text("${period.startDate} ~ ${period.endDate}", fontSize = 12.sp, color = Tokens.muted)

        // 官網卡片上的「兌換內容：通路／品項」。官網原文，只顯示、不進遙測。
        period.voucherSummary?.let {
            Spacer(Modifier.size(6.dp))
            Text(it, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text, lineHeight = 18.sp)
        }

        Spacer(Modifier.size(12.dp))
        PrimaryButton(text = "檢視券碼", onClick = onViewCode, icon = Icons.Filled.QrCode)

        Spacer(Modifier.size(10.dp))
        Text(
            "標記為已使用",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Tokens.muted,
            modifier = Modifier
                .clickableRow(enabled = true, onClick = onToggleUsed)
                .padding(vertical = 4.dp),
        )
    }
}

/**
 * 已標記為使用完畢的券：整張轉灰、**不提供「檢視券碼」**，只留「還原」。
 * 標記是本機備忘，所以卡片上要講清楚它不影響官網。
 */
@Composable
private fun UsedVoucherCard(period: TaskPeriod, onToggleUsed: () -> Unit) {
    val shape = RoundedCornerShape(Tokens.radiusLarge)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.mutedCard)
            .border(1.dp, Tokens.line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Tokens.dim,
                    modifier = Modifier.size(18.dp),
                )
                Text("第 ${period.index} 期加碼券", style = displayStyle(15, FontWeight.Bold), color = Tokens.muted)
            }
            StatusBadge("已使用", Tokens.dim, Tokens.disabledBackground)
        }
        period.voucherSummary?.let {
            Text(it, fontSize = 12.sp, color = Tokens.muted, lineHeight = 18.sp)
        }
        Text(
            "你在 App 內標記為已使用（本機紀錄顯示，使用與否以條碼能否使用為主）。",
            fontSize = 11.5.sp,
            color = Tokens.dim,
            lineHeight = 17.sp,
        )
        SecondaryButton(text = "還原成未使用", onClick = onToggleUsed)
    }
}

/** 任務完成待兌換：去兌換。 */
@Composable
private fun RedeemableCard(period: TaskPeriod, onRedeem: () -> Unit) {
    AppCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "第 ${period.index} 期",
                style = displayStyle(15, FontWeight.Bold),
                color = Tokens.text,
                modifier = Modifier.weight(1f),
            )
            StatusBadge("可兌換", androidx.compose.ui.graphics.Color.White, Tokens.success)
        }
        Spacer(Modifier.size(12.dp))
        Text("任務完成 · 尚未兌換", fontSize = 12.sp, color = Tokens.muted)
        Spacer(Modifier.size(12.dp))
        // 兌換本身在兌換頁有「確認兌換」二次確認，這裡不再多一道驗證。
        PrimaryButton(text = "去兌換", onClick = onRedeem, icon = Icons.Filled.CardGiftcard)
    }
}

// MARK: - ViewModel

public class WalletViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    public data class State(
        val redeemed: List<TaskPeriod> = emptyList(),
        val redeemable: List<TaskPeriod> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _state = MutableStateFlow(State())
    public val state: StateFlow<State> = _state.asStateFlow()

    public val usedIds: StateFlow<Set<String>> = container.voucherUsage.usedIds

    public val tasksRevision: StateFlow<Int> = container.tasksRevision

    public fun refresh() {
        viewModelScope.launch {
            val hadCache = _state.value.redeemed.isNotEmpty() || _state.value.redeemable.isNotEmpty()
            _state.value = _state.value.copy(isLoading = true, isRefreshing = hadCache, errorMessage = null)
            val startedAt = System.nanoTime()
            try {
                val periods = container.environment.value.tasks.fetchTasks()
                _state.value = _state.value.copy(
                    redeemed = periods.filter { it.state == TaskState.REDEEMED },
                    redeemable = periods.filter { it.state == TaskState.REDEEMABLE },
                    isLoading = false,
                    isRefreshing = false,
                )
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.tasksFetched(
                        source = TasksSource.WALLET,
                        periodCount = periods.size,
                        currentPeriod = Telemetry.currentPeriodIndex(periods),
                        hadCache = hadCache,
                        durationMs = Telemetry.elapsedMs(startedAt),
                    ),
                )
            } catch (error: Throwable) {
                val reason = Telemetry.reportFailure(context, error, Endpoint.TASKS, sessionProbable = true)
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.tasksFetchFailed(TasksSource.WALLET, reason, hadCache, Telemetry.elapsedMs(startedAt)),
                )
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "無法載入券夾，請先回首頁登入，或稍後重試。",
                )
            }
        }
    }

    /** 切換「已使用」標記。寫進共用 store，三個分頁會一起重畫。 */
    public fun toggleUsed(period: TaskPeriod) {
        val used = container.voucherUsage.toggle(period.id)
        // 只送方向，不帶期別 UUID／期數／兌換內容。
        Telemetry.logEvent(context, AnalyticsEvent.voucherMarkUsed(used))
    }
}
