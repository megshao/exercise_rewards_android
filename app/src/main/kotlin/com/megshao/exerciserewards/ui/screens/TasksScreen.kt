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
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megshao.exerciserewards.AppContainer
import com.megshao.exerciserewards.core.models.TaskPeriod
import com.megshao.exerciserewards.core.models.TaskState
import com.megshao.exerciserewards.core.models.canUpload
import com.megshao.exerciserewards.core.models.current
import com.megshao.exerciserewards.core.services.SiteHandoff
import com.megshao.exerciserewards.core.services.SiteHandoffDestination
import com.megshao.exerciserewards.telemetry.AnalyticsEvent
import com.megshao.exerciserewards.telemetry.Endpoint
import com.megshao.exerciserewards.telemetry.ScreenName
import com.megshao.exerciserewards.telemetry.TasksSource
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.ui.appViewModel
import com.megshao.exerciserewards.ui.components.LoadingBlock
import com.megshao.exerciserewards.ui.components.PrimaryButton
import com.megshao.exerciserewards.ui.components.SecondaryButton
import com.megshao.exerciserewards.ui.components.SiteHandoffBanner
import com.megshao.exerciserewards.ui.components.SiteHandoffMessage
import com.megshao.exerciserewards.ui.components.StateMessage
import com.megshao.exerciserewards.ui.components.TaskStateBadge
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

/** 我的任務儀表板：垂直卡片列出 14 期，本週置頂高亮，下拉刷新。 */
@Composable
public fun TasksScreen(
    onUpload: (TaskPeriod) -> Unit,
    onRedeem: (TaskPeriod) -> Unit,
    onScreenshot: (TaskPeriod) -> Unit,
    onVoucher: (TaskPeriod) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = appViewModel { container, appContext -> TasksViewModel(container, appContext) }
    val state by viewModel.state.collectAsState()
    val usedIds by viewModel.usedIds.collectAsState()

    LaunchedEffect(Unit) {
        Telemetry.screenAppeared(context, ScreenName.TASKS)
        if (state.periods.isEmpty()) viewModel.refresh(force = false)
    }

    // 上傳成功後官網會把該期改成 UNDER_REVIEW，但 App 不會自己知道。
    // 沒有這一段，徽章會一直停在「未上傳」——而且因為首頁與這裡共用同一個節流時鐘，
    // 連下拉刷新都可能被擋掉，使用者無從自救。
    val revision by viewModel.tasksRevision.collectAsState()
    LaunchedEffect(revision) {
        if (revision > 0) viewModel.refresh(force = true)
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        // 下拉是使用者的明確意圖，一律真的打網路。節流只該擋自動觸發的抓取——
        // 否則轉圈動畫照跑、正常結束，看起來像刷新過了，實際什麼都沒做。
        onRefresh = { viewModel.refresh(force = true) },
        modifier = Modifier.fillMaxSize().background(Tokens.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "每期七天，完成運動上傳並通過審核，即可換一張加碼券",
                fontSize = 12.5.sp,
                color = Tokens.muted,
                lineHeight = 19.sp,
            )

            when {
                state.isLoading && state.periods.isEmpty() -> LoadingBlock()

                // 官網結構對不上（見 core 的 SiteHandoff.shouldHandoff）：不是網路問題，
                // 不能再顯示「請確認網路連線」。說實話並交接到官網。
                state.siteChangeSuspected && state.periods.isEmpty() -> SiteHandoffMessage(
                    destination = SiteHandoffDestination.Tasks,
                    onRetry = { viewModel.refresh(force = true) },
                )

                state.errorMessage != null && state.periods.isEmpty() -> StateMessage(
                    icon = Icons.Filled.Warning,
                    iconTint = Tokens.danger,
                    message = state.errorMessage.orEmpty(),
                    onRetry = { viewModel.refresh(force = true) },
                )

                else -> {
                    // 有快取時照舊顯示，但不再靜默：頂端講明白這可能是舊資料。
                    if (state.showsSiteChangeBanner) {
                        SiteHandoffBanner(
                            destination = SiteHandoffDestination.Tasks,
                            onDismiss = viewModel::dismissSiteChangeBanner,
                        )
                    }
                    state.periods.forEach { period ->
                        TaskPeriodCard(
                            period = period,
                            isHighlighted = period.index == state.highlightedIndex,
                            // 標記過已使用的券不再提供「檢視加碼券」（見 VoucherUsageStore）。
                            isVoucherUsed = usedIds.contains(period.id) && period.id.isNotEmpty(),
                            // 兌換本身在兌換頁有「確認兌換」二次確認，這裡不再多一道驗證。
                            onRedeemTap = { onRedeem(period) },
                            onScreenshotTap = { onScreenshot(period) },
                            onVoucherTap = { onVoucher(period) },
                            onUploadTap = { onUpload(period) },
                        )
                    }
                }
            }

            Spacer(Modifier.size(20.dp))
        }
    }
}

/** 單一期別卡片，本週（highlighted）用琥珀色雙邊線 + 陰影強調。 */
@Composable
private fun TaskPeriodCard(
    period: TaskPeriod,
    isHighlighted: Boolean,
    isVoucherUsed: Boolean,
    onRedeemTap: () -> Unit,
    onScreenshotTap: () -> Unit,
    onVoucherTap: () -> Unit,
    onUploadTap: () -> Unit,
    now: Instant = Instant.now(),
) {
    /**
     * 上傳窗已經過完。官網對這種卡片照樣回 `NOT_UPLOADED`（→ OPEN）且照樣附倒數字串，
     * 所以「還能不能上傳」不能只看 state——見 core 的 `TaskPeriod.hasEnded`。
     */
    val hasEnded = period.hasEnded(now)

    /** 這張卡要不要在右上角顯示倒數：只有本週高亮、該狀態的倒數還有意義、且還沒過完。 */
    val showsRemaining = isHighlighted && period.state.showsUploadCountdown && !hasEnded

    /**
     * 非當期的卡片後端沒有 UUID，「立即兌換」／「看截圖」都需要打帶 UUID 的端點，
     * 因此一律先檢查才顯示這兩顆按鈕。
     */
    val hasId = period.id.isNotEmpty()

    val shape = RoundedCornerShape(Tokens.radiusLarge)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isHighlighted) 12.dp else 5.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            )
            .clip(shape)
            .background(if (period.state == TaskState.NOT_STARTED) Tokens.mutedCard else Tokens.card)
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = if (isHighlighted) Tokens.amber else Tokens.line,
                shape = shape,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "第 ${period.index} 期",
                    style = displayStyle(15, FontWeight.Bold),
                    color = if (period.state == TaskState.NOT_STARTED) Tokens.dim else Tokens.text,
                )
                Text("${period.startDate} ~ ${period.endDate}", fontSize = 12.sp, color = Tokens.muted)
            }
            // 官網對已走完審核的期別（可兌換／已兌換）照樣回傳上傳窗倒數，
            // 顯示出來會讓人以為還有東西要上傳——見 core 的 `TaskState.showsUploadCountdown`。
            val remaining = period.remainingText
            if (showsRemaining && remaining != null) {
                Text(remaining, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Tokens.primaryDark)
            } else {
                TaskStateBadge(state = period.state, hasEnded = hasEnded)
            }
        }

        // 上面那格被倒數佔走時，徽章補在下一行；沒被佔走就不用重複顯示。
        if (showsRemaining && period.remainingText != null) {
            TaskStateBadge(state = period.state, hasEnded = hasEnded)
        }

        // 官網卡片上的「兌換內容：通路／品項」。官網原文，只顯示、不進遙測。
        period.voucherSummary?.let {
            Text(
                "兌換內容：$it",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tokens.text,
                lineHeight = 18.sp,
            )
        }

        if (isVoucherUsed) {
            Text(
                "你已在 App 內標記為使用完畢（本機紀錄顯示，使用與否以條碼能否使用為主）。",
                fontSize = 11.5.sp,
                color = Tokens.dim,
                lineHeight = 17.sp,
            )
        }

        val uploadedAt = period.uploadedAt
        if (uploadedAt != null) {
            val reviewedAt = period.reviewedAt
            Text(
                if (reviewedAt != null) "上傳 $uploadedAt · 審核 $reviewedAt" else "上傳 $uploadedAt",
                fontSize = 12.sp,
                color = Tokens.muted,
            )
        } else if (period.state == TaskState.PENDING_REVIEW && period.remainingText != null && !isHighlighted) {
            Text(period.remainingText.orEmpty(), fontSize = 12.sp, color = Tokens.dim)
        }

        // 動作列
        when (period.state) {
            TaskState.REDEEMABLE -> if (hasId) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        PrimaryButton(text = "立即兌換", onClick = onRedeemTap)
                    }
                    SecondaryButton(text = "看截圖", onClick = onScreenshotTap)
                }
            }

            TaskState.OPEN ->
                // 「還能不能上傳」是狀態與日曆的合取，規則與踩過的坑都在 core 的
                // `TaskPeriod.canUpload`——那條規則刻意放在 core，因為寫在這個卡片裡的判斷
                // 沒有任何單元測試搆得到（示範資料裡也沒有「已過期但仍 OPEN」的期別，
                // UI 測試同樣驗不到）。這裡只負責二選一。
                if (period.canUpload(now)) {
                    PrimaryButton(
                        text = "上傳運動紀錄",
                        onClick = onUploadTap,
                        icon = Icons.Filled.ArrowCircleUp,
                    )
                } else {
                    Text("本期上傳期間已結束", fontSize = 12.sp, color = Tokens.dim)
                }

            TaskState.REDEEMED, TaskState.PENDING_REVIEW ->
                // 已兌換／審核中：只提供「看截圖」回顧當時上傳的內容，不再提供「立即兌換」
                // （已兌換過的期別無法重複兌換；審核中則尚未進入可兌換狀態）。
                if (hasId && (period.state == TaskState.REDEEMED || period.uploadedAt != null)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(text = "看截圖", onClick = onScreenshotTap)
                        // 標記過已使用的券不再給「檢視加碼券」——按了也只是再走一次 OTP
                        // 看一張自己已經用掉的條碼。
                        if (period.state == TaskState.REDEEMED && !isVoucherUsed) {
                            Box(Modifier.weight(1f)) {
                                PrimaryButton(text = "檢視加碼券", onClick = onVoucherTap)
                            }
                        }
                    }
                }

            TaskState.NOT_STARTED, TaskState.UNKNOWN -> Unit
        }
    }
}

// MARK: - ViewModel

public class TasksViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    public data class State(
        val periods: List<TaskPeriod> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
        /**
         * 最近一次抓取失敗是不是「官網結構對不上」（core 的 `SiteHandoff.shouldHandoff`）。
         * 沒有資料時決定畫交接畫面而不是「請確認網路連線」；抓成功就歸零。
         */
        val siteChangeSuspected: Boolean = false,
        /**
         * 有快取時要不要在頂端掛「官網可能已改版」橫幅。使用者可以關掉，
         * 但下一次 parse 再失敗會重新掛上——關掉的是這一次，不是這個功能。
         */
        val showsSiteChangeBanner: Boolean = false,
    ) {
        /**
         * 本週要置頂高亮的那一期 index。
         * **用 index 而非 id**：非當期的卡片後端沒有 UUID，id 會是空字串而彼此相同。
         */
        val highlightedIndex: Int?
            get() = TaskPeriod.current(periods, Instant.now())?.index
    }

    private val _state = MutableStateFlow(State())
    public val state: StateFlow<State> = _state.asStateFlow()

    public val usedIds: StateFlow<Set<String>> = container.voucherUsage.usedIds

    public val tasksRevision: StateFlow<Int> = container.tasksRevision

    /**
     * 本地優先：先秀快取；距上次更新未滿 60 秒則不再發 request。
     *
     * @param force 忽略節流。**使用者主動觸發的刷新（下拉、重試鈕、上傳後）一律傳 true**；
     *   false 只給畫面出現時的自動抓取用。
     *
     *   節流的時鐘是 [com.megshao.exerciserewards.data.TasksCache] 的單一時戳，首頁與本頁
     *   共用它但各存各的資料——所以首頁刷新過就會把這裡的 60 秒重新計時。這是為什麼
     *   「等超過 60 秒再下拉」對使用者不是可靠的自救方式，也是為什麼下拉一定要 force。
     */
    public fun refresh(force: Boolean) {
        viewModelScope.launch {
            if (_state.value.periods.isEmpty()) {
                container.tasksCache.load()?.let {
                    _state.value = _state.value.copy(periods = sorted(it))
                }
            }
            if (!force && !container.tasksCache.canRefresh() && _state.value.periods.isNotEmpty()) return@launch

            val hadCache = _state.value.periods.isNotEmpty()
            _state.value = _state.value.copy(
                isLoading = _state.value.periods.isEmpty(),
                isRefreshing = force,
                errorMessage = null,
            )
            val startedAt = System.nanoTime()
            try {
                val fetched = container.environment.value.tasks.fetchTasks()
                _state.value = _state.value.copy(
                    periods = sorted(fetched),
                    isLoading = false,
                    isRefreshing = false,
                    siteChangeSuspected = false,
                    showsSiteChangeBanner = false,
                )
                container.tasksCache.save(fetched)
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.tasksFetched(
                        source = TasksSource.TASKS_TAB,
                        periodCount = fetched.size,
                        currentPeriod = Telemetry.currentPeriodIndex(fetched),
                        hadCache = hadCache,
                        durationMs = Telemetry.elapsedMs(startedAt),
                    ),
                )
            } catch (error: Throwable) {
                // 分頁本身可能是冷啟動後第一個被打開的畫面，解析失敗同樣可能只是 session 過期，
                // 因此這裡也走 sessionProbable，不把它當成官網改版警報。
                val reason = Telemetry.reportFailure(context, error, Endpoint.TASKS, sessionProbable = true)
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.tasksFetchFailed(
                        TasksSource.TASKS_TAB,
                        reason,
                        hadCache,
                        Telemetry.elapsedMs(startedAt),
                    ),
                )
                // 「官網結構對不上」與其他失敗分開處理：前者不是網路問題，畫面上不能再叫人
                // 檢查網路；有快取時照舊沿用，但頂端掛橫幅講明白，不再靜默。
                val siteChange = SiteHandoff.shouldHandoff(error)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    siteChangeSuspected = siteChange,
                    showsSiteChangeBanner = siteChange && _state.value.periods.isNotEmpty(),
                    // 有快取就沿用；完全沒資料才顯示錯誤。
                    errorMessage = if (_state.value.periods.isEmpty()) {
                        "無法載入任務資料，請確認網路連線後重新整理"
                    } else {
                        null
                    },
                )
            }
        }
    }

    /** 使用者關掉頂端的改版橫幅。只關這一次——下次 parse 再失敗會重新出現。 */
    public fun dismissSiteChangeBanner() {
        _state.value = _state.value.copy(showsSiteChangeBanner = false)
    }

    private companion object {
        /**
         * 當期置頂，其餘依 index 遞增。
         *
         * **用 index 過濾，不能用 id**：非當期（NOT_STARTED／NOT_UPLOADED）後端沒有 UUID，
         * id 皆為空字串，用 id 比對會把所有空 id 的期別一起濾掉，導致只剩高亮那一張。
         */
        fun sorted(periods: List<TaskPeriod>): List<TaskPeriod> {
            val highlighted = TaskPeriod.current(periods, Instant.now())
                ?: return periods.sortedBy { it.index }
            val rest = periods.filter { it.index != highlighted.index }.sortedBy { it.index }
            return listOf(highlighted) + rest
        }
    }
}
