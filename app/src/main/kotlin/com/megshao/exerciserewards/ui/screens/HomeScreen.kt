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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megshao.exerciserewards.AppContainer
import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.models.LoginCredentials
import com.megshao.exerciserewards.core.models.LoginOutcome
import com.megshao.exerciserewards.core.models.TaskPeriod
import com.megshao.exerciserewards.core.models.TaskState
import com.megshao.exerciserewards.core.models.canUpload
import com.megshao.exerciserewards.core.models.current
import com.megshao.exerciserewards.telemetry.AnalyticsEvent
import com.megshao.exerciserewards.telemetry.Endpoint
import com.megshao.exerciserewards.telemetry.FailReason
import com.megshao.exerciserewards.telemetry.LoginTrigger
import com.megshao.exerciserewards.telemetry.ScreenName
import com.megshao.exerciserewards.telemetry.TasksSource
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.ui.appViewModel
import com.megshao.exerciserewards.ui.components.AppCard
import com.megshao.exerciserewards.ui.components.LoadingBlock
import com.megshao.exerciserewards.ui.components.PillButton
import com.megshao.exerciserewards.ui.components.PrimaryButton
import com.megshao.exerciserewards.ui.components.RemainingTime
import com.megshao.exerciserewards.ui.components.StatusBadge
import com.megshao.exerciserewards.ui.components.TaskStateBadge
import com.megshao.exerciserewards.ui.components.TaskStepper
import com.megshao.exerciserewards.ui.components.clickableRow
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** 首頁：一鍵登入 CTA、本週任務摘要卡、加碼券清單（最多五列）。 */
@Composable
public fun HomeScreen(
    onOpenProfile: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenWallet: () -> Unit,
    onRedeem: (TaskPeriod) -> Unit,
    onVoucher: (TaskPeriod) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = appViewModel { container, appContext -> HomeViewModel(container, appContext) }
    val state by viewModel.state.collectAsState()
    val usedIds by viewModel.usedIds.collectAsState()

    LaunchedEffect(Unit) {
        Telemetry.screenAppeared(context, ScreenName.HOME)
        viewModel.bootstrap()
    }

    // 上傳／兌換完成後官網會改狀態，但 App 不會自己知道。
    // 收到失效訊號就強制重抓（忽略節流）——見 AppContainer.invalidateTasks。
    val revision by viewModel.tasksRevision.collectAsState()
    LaunchedEffect(revision) {
        if (revision > 0) viewModel.refresh(force = true)
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        // 下拉是明確意圖，忽略節流（理由同任務頁）。
        onRefresh = { viewModel.refresh(force = true) },
        modifier = Modifier.fillMaxSize().background(Tokens.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HomeHeader(greeting = state.greeting, onOpenProfile = onOpenProfile)

            LoginCta(state = state, onRetryLogin = viewModel::loginTapped, onOpenProfile = onOpenProfile)

            WeeklyTaskSection(
                task = state.currentWeekTask,
                isLoading = state.isLoadingSummary,
                onOpenTasks = onOpenTasks,
            )

            VoucherSection(
                rows = viewModel.voucherRows(usedIds),
                totalCount = viewModel.allVoucherRows(usedIds).size,
                isLoading = state.isLoadingSummary,
                isUsed = { usedIds.contains(it.id) },
                onOpenWallet = onOpenWallet,
                onRedeem = onRedeem,
                onVoucher = onVoucher,
            )

            Spacer(Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HomeHeader(greeting: String, onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(greeting, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Tokens.muted)
            Row {
                Text("揮汗", style = displayStyle(24, FontWeight.ExtraBold), color = Tokens.text)
                Text("有禮", style = displayStyle(24, FontWeight.ExtraBold), color = Tokens.primary)
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Tokens.card)
                .border(1.dp, Tokens.line2, CircleShape)
                .clickableRow(enabled = true, onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = "我的資料",
                tint = Tokens.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 登入狀態列。整塊靠左對齊，與首頁其他區塊同一條左邊界；只有全寬按鈕本來就滿版。
 */
@Composable
private fun LoginCta(
    state: HomeViewModel.State,
    onRetryLogin: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        when {
            // 個資未填齊：引導去填寫，不顯示登入按鈕。
            !state.isProfileComplete -> PrimaryButton(
                text = "前往「我的資料」填寫個資",
                onClick = onOpenProfile,
                icon = Icons.Filled.Person,
            )

            // 自動登入中：顯示載入狀態，不需按鈕。
            state.isLoggingIn -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Tokens.primary)
                Text("登入中…", fontSize = 14.sp, color = Tokens.muted)
            }

            // 已自動登入：不顯示登入按鈕。
            state.hasLoggedIn -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = Tokens.success,
                    modifier = Modifier.size(15.dp),
                )
                Text("已登入，任務資料已同步", fontSize = 13.sp, color = Tokens.muted)
            }

            // 自動登入失敗：提供手動重試。
            state.loginResultIsError -> PrimaryButton(
                text = "重新登入",
                onClick = onRetryLogin,
                loading = state.isLoggingIn,
                icon = Icons.Filled.Refresh,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = Tokens.dim,
                modifier = Modifier.size(14.dp),
            )
            // 這行是首頁的信任徽章。加了 Firebase 之後，「不會上傳雲端」單獨看會被讀成
            // 「這支 App 什麼都不上傳」，所以改成把範圍講明白：講的是個資，去的是官方站。
            // 使用統計那條界線在「我的資料」的隱私聲明裡完整交代。
            Text("個資只存這支手機 · 只在登入時送給 500.gov.tw", fontSize = 12.sp, color = Tokens.dim)
        }

        state.loginResultMessage?.let { message ->
            Text(
                message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (state.loginResultIsError) Tokens.danger else Tokens.success,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun WeeklyTaskSection(
    task: TaskPeriod?,
    isLoading: Boolean,
    onOpenTasks: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("本週任務", style = displayStyle(16, FontWeight.Bold), color = Tokens.text, modifier = Modifier.weight(1f))
            Text(
                "全部 14 期 ›",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tokens.primary,
                modifier = Modifier.clickableRow(enabled = true, onClick = onOpenTasks),
            )
        }

        when {
            isLoading -> LoadingBlock(topPadding = 20.dp)
            task != null -> TaskSummaryCard(task)
            else -> Text("目前沒有任務資料，下拉重新整理試試。", fontSize = 13.sp, color = Tokens.muted)
        }
    }
}

/** 首頁用的本週任務摘要卡（精簡版，完整卡片見任務頁）。 */
@Composable
private fun TaskSummaryCard(task: TaskPeriod, now: Instant = Instant.now()) {
    val hasEnded = task.hasEnded(now)
    AppCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("第 ${task.index} 期", style = displayStyle(15, FontWeight.Bold), color = Tokens.text)
                Text("${task.startDate} ~ ${task.endDate}", fontSize = 12.sp, color = Tokens.muted)
            }
            TaskStateBadge(state = task.state, hasEnded = hasEnded)
        }

        Spacer(Modifier.size(12.dp))

        // 進度時間軸：上傳 → 審核 → 兌換（依狀態上色）。取代舊的百分比條，
        // 已審核／已兌換等完成狀態不再顯示剩餘時間條。
        TaskStepper(task.state)

        Spacer(Modifier.size(12.dp))

        TaskStatusLine(task = task, now = now)
    }
}

@Composable
private fun TaskStatusLine(task: TaskPeriod, now: Instant) {
    when (task.state) {
        TaskState.OPEN -> {
            // 與任務頁的上傳鈕走同一條規則（core 的 `TaskPeriod.canUpload`，由
            // UploadWindowTest 守著），兩頁不會再各自寫一次「OPEN 且未過期」的合取。
            if (!task.canUpload(now)) {
                // 官網對過期未上傳的卡片仍會回倒數字串，照著顯示等於告訴使用者「還有時間」。
                Text("本期已結束，未上傳運動紀錄", fontSize = 12.sp, color = Tokens.dim)
            } else {
                val remaining = task.remainingText
                val hours = remaining?.let(RemainingTime::hours)
                if (remaining != null && hours != null) {
                    Text(
                        remaining,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RemainingTime.color(hours),
                    )
                } else {
                    Text("尚未上傳，請於上傳期間內送出運動紀錄", fontSize = 12.sp, color = Tokens.muted)
                }
            }
        }

        TaskState.PENDING_REVIEW -> Text("審核中 · 約 5 個工作日", fontSize = 12.sp, color = Tokens.muted)

        TaskState.REDEEMABLE -> Text(
            "任務完成，可兌換超商加碼券",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Tokens.success,
        )

        TaskState.REDEEMED -> Text("已兌換 · 券已存入券夾", fontSize = 12.sp, color = Tokens.muted)

        TaskState.NOT_STARTED, TaskState.UNKNOWN -> {
            if (task.state.showsUploadCountdown) {
                task.remainingText?.let { Text(it, fontSize = 12.sp, color = Tokens.muted) }
            }
        }
    }
}

/**
 * 加碼券區塊：把「可兌換」與「已兌換」兩種期別合成同一份清單。
 *
 * 排序與截斷的規則都在 [HomeViewModel]，這裡只負責畫。
 * 超過五列時顯示「查看全部」導向券夾，避免首頁被 14 期塞滿。
 */
@Composable
private fun VoucherSection(
    rows: List<TaskPeriod>,
    totalCount: Int,
    isLoading: Boolean,
    isUsed: (TaskPeriod) -> Boolean,
    onOpenWallet: () -> Unit,
    onRedeem: (TaskPeriod) -> Unit,
    onVoucher: (TaskPeriod) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("加碼券", style = displayStyle(16, FontWeight.Bold), color = Tokens.text, modifier = Modifier.weight(1f))
            if (totalCount > HomeViewModel.VOUCHER_ROW_LIMIT) {
                Text(
                    "查看全部 $totalCount 張 ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Tokens.primary,
                    modifier = Modifier.clickableRow(enabled = true, onClick = onOpenWallet),
                )
            }
        }

        when {
            isLoading -> LoadingBlock(topPadding = 20.dp)
            rows.isEmpty() -> Text(
                "目前沒有加碼券。完成任務並兌換後，加碼券會出現在這裡。",
                fontSize = 13.sp,
                color = Tokens.muted,
                lineHeight = 19.sp,
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { period ->
                    VoucherRowCard(
                        period = period,
                        isUsed = isUsed(period),
                        onRedeemTap = { onRedeem(period) },
                        onVoucherTap = { onVoucher(period) },
                    )
                }
            }
        }
    }
}

/**
 * 首頁的加碼券單列：一列一期，左側期別／兌換內容，右側動作鈕。
 *
 * - 尚未兌換 → 「去兌換」（二次確認在兌換頁）。
 * - 已兌換 → 「顯示條碼」（每次都要重走一次簡訊 OTP）。
 * - 已兌換且**使用者自己標記過已使用** → 整列轉灰、標「已使用」、**不放任何按鈕**。
 *   官網沒有這個狀態（見 `VoucherUsageStore`），所以只能靠使用者告訴 App；
 *   要還原請到券夾或券碼頁——首頁只負責讓人一眼看出「哪幾張還沒用」。
 */
@Composable
private fun VoucherRowCard(
    period: TaskPeriod,
    isUsed: Boolean,
    onRedeemTap: () -> Unit,
    onVoucherTap: () -> Unit,
) {
    val isRedeemed = period.state == TaskState.REDEEMED
    val shape = RoundedCornerShape(Tokens.radiusLarge)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isUsed) Tokens.mutedCard else Tokens.card)
            .border(1.dp, Tokens.line, shape)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val iconTint = when {
            isUsed -> Tokens.dim
            isRedeemed -> Tokens.primary
            else -> Tokens.success
        }
        val iconBackground = when {
            isUsed -> Tokens.disabledBackground
            isRedeemed -> Tokens.card2
            else -> Tokens.successBackground
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    isUsed -> Icons.Filled.Check
                    isRedeemed -> Icons.Filled.ConfirmationNumber
                    else -> Icons.Filled.CardGiftcard
                },
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "第 ${period.index} 期加碼券",
                    style = displayStyle(15, FontWeight.Bold),
                    color = if (isUsed) Tokens.muted else Tokens.text,
                )
                if (isUsed) {
                    StatusBadge("已使用", Tokens.dim, Tokens.disabledBackground)
                }
            }
            // 官網卡片上的「兌換內容：通路／品項」。是官網原文，只顯示、不進遙測。
            period.voucherSummary?.let {
                Text(it, fontSize = 11.5.sp, color = Tokens.muted, maxLines = 2, lineHeight = 17.sp)
            }
            Text(
                when {
                    isUsed -> "已使用 · 本機紀錄，以條碼能否使用為主"
                    isRedeemed -> "已兌換 · 可出示條碼使用"
                    else -> "任務完成 · 尚未兌換"
                },
                fontSize = 11.5.sp,
                color = if (isUsed) Tokens.dim else if (isRedeemed) Tokens.muted else Tokens.success,
            )
        }

        if (!isUsed) {
            PillButton(
                text = if (isRedeemed) "顯示條碼" else "去兌換",
                onClick = if (isRedeemed) onVoucherTap else onRedeemTap,
                background = if (isRedeemed) Tokens.primary else Tokens.success,
            )
        }
    }
}

// MARK: - ViewModel

public class HomeViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    public data class State(
        val isLoggingIn: Boolean = false,
        val loginResultMessage: String? = null,
        val loginResultIsError: Boolean = false,
        val hasLoggedIn: Boolean = false,
        val isProfileComplete: Boolean = false,
        val isLoadingSummary: Boolean = false,
        val isRefreshing: Boolean = false,
        /** 首頁的加碼券區塊要跨期別排序，因此保留整份清單而非只留高亮那一期。 */
        val periods: List<TaskPeriod> = emptyList(),
    ) {
        /** 本週要高亮的那一期。規則見 core 的 `TaskPeriod.current`。 */
        val currentWeekTask: TaskPeriod?
            get() = TaskPeriod.current(periods, Instant.now())

        val greeting: String
            get() = when (Instant.now().atZone(ZoneId.systemDefault()).hour) {
                in 5..11 -> "早安，動起來！"
                in 12..17 -> "午安，動起來！"
                else -> "晚安，今天運動了嗎？"
            }
    }

    private val _state = MutableStateFlow(State())
    public val state: StateFlow<State> = _state.asStateFlow()

    public val usedIds: StateFlow<Set<String>> = container.voucherUsage.usedIds

    public val tasksRevision: StateFlow<Int> = container.tasksRevision

    private var didAutoLogin = false

    /** 排序後但**未截斷**的完整清單。長度用來判斷要不要顯示「查看全部」入口。 */
    public fun allVoucherRows(usedIds: Set<String>): List<TaskPeriod> =
        sortedVoucherRows(_state.value.periods, usedIds)

    public fun voucherRows(usedIds: Set<String>): List<TaskPeriod> =
        allVoucherRows(usedIds).take(VOUCHER_ROW_LIMIT)

    /**
     * 進入 App 時的啟動流程（只做一次）：
     * 1. 先**沿用持久化 session** 試抓任務——若成功代表上次登入的 cookie 還有效，直接免登入。
     * 2. 只有在 session 失效（抓不到任務）時，才用加密儲存裡的個資自動登入。
     *
     * 這樣一般冷啟動不會每次都重新登入。
     */
    public fun bootstrap() {
        refreshProfileState()
        // 本地優先：先秀快取的本週任務（有快取代表先前登入過，樂觀視為已登入）。
        var hadCache = false
        container.tasksCache.load()?.let { cached ->
            _state.value = _state.value.copy(periods = cached, hasLoggedIn = true)
            hadCache = true
        }

        if (!_state.value.isProfileComplete || didAutoLogin) return
        didAutoLogin = true
        // 節流：距上次更新未滿 60 秒且已有快取，就不發請求。
        if (!container.tasksCache.canRefresh() && _state.value.periods.isNotEmpty()) return

        viewModelScope.launch {
            val startedAt = System.nanoTime()
            try {
                val fetched = container.environment.value.tasks.fetchTasks()
                if (fetched.isEmpty()) throw AppError.Parsing("empty task list")
                _state.value = _state.value.copy(periods = fetched, hasLoggedIn = true)
                container.tasksCache.save(fetched)
                reportFetchSuccess(TasksSource.HOME_BOOTSTRAP, fetched, hadCache, startedAt)
            } catch (error: Throwable) {
                // 冷啟動的第一次抓取失敗**幾乎都是 session 過期**（官網 302 到登入頁，
                // 回的是登入頁 HTML，解析器丟出的錯誤跟官網改版一模一樣）。
                // 因此歸類為 session_probable，不送非致命錯誤——不然每個使用者每天冷啟動
                // 都會產生一筆假的「官網改版」警報。
                reportFetchFailure(TasksSource.HOME_BOOTSTRAP, error, hadCache, startedAt, sessionProbable = true)
                performLogin(silent = true)
            }
        }
    }

    /** 使用者手動點「重新登入」。 */
    public fun loginTapped() {
        viewModelScope.launch { performLogin(silent = false) }
    }

    public fun refresh(force: Boolean) {
        refreshProfileState()
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            loadWeeklySummary(force)
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    /**
     * 本地優先 + 節流：先秀快取；距上次更新未滿 60 秒（且已有資料）就不發 request。
     *
     * `force == true` 只發生在「剛登入成功」或使用者下拉之後，因此遙測來源標成 post_login
     * ——這是用來分辨「官網改版」與「session 過期」的關鍵：剛登入完還解析失敗，
     * 就不可能是 session 過期了。
     */
    private suspend fun loadWeeklySummary(force: Boolean) {
        if (_state.value.periods.isEmpty()) {
            container.tasksCache.load()?.let { _state.value = _state.value.copy(periods = it) }
        }
        if (!force && !container.tasksCache.canRefresh() && _state.value.periods.isNotEmpty()) return

        val hadCache = _state.value.periods.isNotEmpty()
        _state.value = _state.value.copy(isLoadingSummary = _state.value.periods.isEmpty())
        val startedAt = System.nanoTime()
        val source = if (force) TasksSource.POST_LOGIN else TasksSource.HOME_REFRESH
        try {
            val fetched = container.environment.value.tasks.fetchTasks()
            _state.value = _state.value.copy(periods = fetched, isLoadingSummary = false)
            container.tasksCache.save(fetched)
            reportFetchSuccess(source, fetched, hadCache, startedAt)
        } catch (error: Throwable) {
            _state.value = _state.value.copy(isLoadingSummary = false)
            // post_login 的解析失敗才是改版訊號；一般刷新可能只是 session 剛過期。
            reportFetchFailure(source, error, hadCache, startedAt, sessionProbable = !force)
            // 有快取就沿用，不清掉。
        }
    }

    /** 依加密儲存裡的個資判斷登入必要欄位是否齊全。 */
    public fun refreshProfileState() {
        val profile = runCatching { container.environment.value.profileStore.load() }.getOrNull()
        val complete = profile != null &&
            profile.idNo.isNotEmpty() && profile.birthDate.isNotEmpty() && profile.phone.isNotEmpty()
        _state.value = _state.value.copy(isProfileComplete = complete)
    }

    /** 執行登入。silent = 自動登入（成功不顯示提示，只在失敗時提示）。 */
    private suspend fun performLogin(silent: Boolean) {
        val environment = container.environment.value
        _state.value = _state.value.copy(isLoggingIn = true, loginResultMessage = null)
        val trigger = if (silent) LoginTrigger.AUTO else LoginTrigger.MANUAL
        val startedAt = System.nanoTime()

        try {
            val profile = environment.profileStore.load()
            if (profile == null || profile.idNo.isEmpty() || profile.birthDate.isEmpty() || profile.phone.isEmpty()) {
                _state.value = _state.value.copy(
                    isLoggingIn = false,
                    isProfileComplete = false,
                    loginResultIsError = true,
                    loginResultMessage = "請先到「我的資料」填寫身分證號、出生日期與手機號碼",
                )
                return
            }

            val credentials = LoginCredentials(profile.idNo, profile.birthDate, profile.phone)

            // 示範帳號（商店審查用）：不連線，改用示範環境的任務資料。
            if (container.enterDemoIfSentinel(credentials)) {
                val demoPeriods = runCatching { container.environment.value.tasks.fetchTasks() }.getOrNull()
                _state.value = _state.value.copy(
                    isLoggingIn = false,
                    hasLoggedIn = true,
                    loginResultIsError = false,
                    loginResultMessage = if (silent) null else "示範模式已啟用，顯示的是範例資料",
                    periods = demoPeriods ?: _state.value.periods,
                )
                return
            }

            when (environment.auth.login(credentials)) {
                LoginOutcome.SUCCESS -> {
                    _state.value = _state.value.copy(
                        isLoggingIn = false,
                        hasLoggedIn = true,
                        loginResultIsError = false,
                        loginResultMessage = if (silent) null else "登入成功，正在載入我的任務",
                    )
                    Telemetry.logEvent(context, AnalyticsEvent.login(trigger, Telemetry.elapsedMs(startedAt)))
                    // 剛登入，強制抓一次最新並寫入快取
                    loadWeeklySummary(force = true)
                }

                LoginOutcome.NOT_REGISTERED -> {
                    _state.value = _state.value.copy(
                        isLoggingIn = false,
                        loginResultIsError = true,
                        loginResultMessage = "這組身分證號尚未在「揮汗有禮」官網註冊，請先至官網完成註冊",
                    )
                    Telemetry.logEvent(
                        context,
                        AnalyticsEvent.loginFailed(trigger, FailReason.NOT_REGISTERED, Telemetry.elapsedMs(startedAt)),
                    )
                }

                LoginOutcome.INVALID_CREDENTIALS -> {
                    _state.value = _state.value.copy(
                        isLoggingIn = false,
                        loginResultIsError = true,
                        loginResultMessage = "身分證號、出生日期或手機號碼有誤，請至「我的資料」確認後再試一次",
                    )
                    Telemetry.logEvent(
                        context,
                        AnalyticsEvent.loginFailed(
                            trigger,
                            FailReason.INVALID_CREDENTIALS,
                            Telemetry.elapsedMs(startedAt),
                        ),
                    )
                }
            }
        } catch (error: Throwable) {
            _state.value = _state.value.copy(
                isLoggingIn = false,
                loginResultIsError = true,
                loginResultMessage = messageFor(error),
            )
            val reason = Telemetry.reportFailure(context, error, Endpoint.LOGIN)
            Telemetry.logEvent(context, AnalyticsEvent.loginFailed(trigger, reason, Telemetry.elapsedMs(startedAt)))
        }
    }

    private fun reportFetchSuccess(
        source: TasksSource,
        periods: List<TaskPeriod>,
        hadCache: Boolean,
        startedAt: Long,
    ) {
        Telemetry.logEvent(
            context,
            AnalyticsEvent.tasksFetched(
                source = source,
                periodCount = periods.size,
                currentPeriod = Telemetry.currentPeriodIndex(periods),
                hadCache = hadCache,
                durationMs = Telemetry.elapsedMs(startedAt),
            ),
        )
    }

    private fun reportFetchFailure(
        source: TasksSource,
        error: Throwable,
        hadCache: Boolean,
        startedAt: Long,
        sessionProbable: Boolean,
    ) {
        val reason = Telemetry.reportFailure(context, error, Endpoint.TASKS, sessionProbable)
        Telemetry.logEvent(
            context,
            AnalyticsEvent.tasksFetchFailed(source, reason, hadCache, Telemetry.elapsedMs(startedAt)),
        )
    }

    public companion object {
        /** 首頁加碼券清單最多顯示幾列。超過的部分請使用者去券夾看完整清單。 */
        public const val VOUCHER_ROW_LIMIT: Int = 5

        /**
         * 可兌換（尚未兌換）與已兌換的期別，依三段優先序排序：
         *
         * 1. **尚未兌換**（REDEEMABLE）——還要動手才拿得到券，最該先看到
         * 2. **已兌換、還沒用掉**（REDEEMED 且未標記）——手上真正能用的券
         * 3. **已標記使用完畢**——只是留著給使用者對帳，排最後
         *
         * 同一段內再依建立時間由舊到新。期別 index 就是建立順序（第 1 期最早），
         * 所以用 index 遞增即可，不需要解析日期字串。
         *
         * **為什麼「已使用」要排最後**：首頁只有五列。用過的券如果照原順序卡在前面，
         * 就會把還沒用的券擠出畫面——而那正是使用者打開 App 想找的東西。
         */
        public fun sortedVoucherRows(periods: List<TaskPeriod>, usedIds: Set<String>): List<TaskPeriod> {
            fun rank(period: TaskPeriod): Int = when {
                period.state == TaskState.REDEEMABLE -> 0
                usedIds.contains(period.id) -> 2
                else -> 1
            }

            return periods
                .filter { it.state == TaskState.REDEEMABLE || it.state == TaskState.REDEEMED }
                // 非當期的卡片後端沒有 UUID，點進兌換／券碼頁都會失敗，因此不列出來。
                .filter { it.id.isNotEmpty() }
                .sortedWith(compareBy({ rank(it) }, { it.index }))
        }

        internal fun messageFor(error: Throwable): String = when (error) {
            is AppError.Network -> "網路連線異常，請檢查網路後再試一次"
            is AppError.CsrfNotFound, is AppError.UnexpectedResponse,
            is AppError.Parsing, is AppError.ResponseTooLarge,
            -> "官網回應異常，請稍後再試"

            is AppError.NotLoggedIn -> "尚未登入，請先完成一鍵登入"
            is AppError.BlockedEgress -> "偵測到非官方網域連線，已阻擋"
            else -> "發生未知錯誤，請稍後再試一次"
        }
    }
}
