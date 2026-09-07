package com.megshao.exerciserewards.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megshao.exerciserewards.AppContainer
import com.megshao.exerciserewards.BuildConfig
import com.megshao.exerciserewards.core.models.Profile
import com.megshao.exerciserewards.telemetry.AnalyticsEvent
import com.megshao.exerciserewards.telemetry.ScreenName
import com.megshao.exerciserewards.telemetry.SimpleOutcome
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.telemetry.TelemetryIssue
import com.megshao.exerciserewards.ui.appViewModel
import com.megshao.exerciserewards.ui.components.AppCard
import com.megshao.exerciserewards.ui.components.BirthDateField
import com.megshao.exerciserewards.ui.components.IconBox
import com.megshao.exerciserewards.ui.components.PrimaryButton
import com.megshao.exerciserewards.ui.components.ProfileField
import com.megshao.exerciserewards.ui.components.clickableRow
import com.megshao.exerciserewards.ui.theme.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 我的資料：3 欄位表單（身分證、生日、手機），存／讀透過 `ProfileStoring`。
 *
 * 個資最小化到登入必需：姓名／email／健保卡卡號皆不在此收集（`Profile` 保留欄位，
 * 只是 UI 不收集，存起來維持空字串）。
 *
 * 安全與隱私的所有選項（本機資料說明、一鍵清除）都直接放在這一層，
 * 不再多一層「資安中心」子頁——個資與保護個資的開關本來就該在同一個畫面看得完。
 */
@Composable
public fun ProfileScreen(onDataCleared: () -> Unit) {
    val context = LocalContext.current
    val viewModel = appViewModel { container, appContext -> ProfileViewModel(container, appContext) }
    val state by viewModel.state.collectAsState()
    val isDemo by viewModel.isDemo.collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Telemetry.screenAppeared(context, ScreenName.PROFILE)
        viewModel.load()
    }

    LaunchedEffect(state.cleared) {
        if (state.cleared) onDataCleared()
    }

    Column(modifier = Modifier.fillMaxSize().background(Tokens.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PrivacyBanner()

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileField(
                    label = "身分證號",
                    value = state.draft.idNo,
                    onValueChange = viewModel::updateIdNo,
                    placeholder = "A123456789",
                    sensitive = true,
                    isRevealed = state.isRevealed,
                    maskedText = state.maskedIdNo,
                )
                BirthDateField(
                    isoDate = state.draft.birthDate,
                    onIsoDateChange = viewModel::updateBirthDate,
                )
                ProfileField(
                    label = "手機號碼",
                    value = state.draft.phone,
                    onValueChange = viewModel::updatePhone,
                    placeholder = "09xxxxxxxx",
                    sensitive = true,
                    isRevealed = state.isRevealed,
                    maskedText = state.maskedPhone,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                )
                // email／健保卡卡號／姓名不在此收集：個資最小化到登入必需三欄。
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickableRow(enabled = true) { viewModel.toggleReveal() },
            ) {
                Icon(
                    if (state.isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = Tokens.dim,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    if (state.isRevealed) "隱藏敏感欄位" else "敏感欄位已遮罩，點這裡顯示完整內容",
                    fontSize = 12.sp,
                    color = Tokens.dim,
                )
            }

            // MARK: 安全與隱私
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "安全與隱私",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Tokens.dim,
                    modifier = Modifier.padding(start = 4.dp),
                )
                AppCard(padding = 0.dp) {
                    if (isDemo) {
                        // 只在示範模式下出現：讓審查員（或誤入的使用者）一鍵切回真實環境。
                        SettingsRow(
                            icon = Icons.Filled.Visibility,
                            iconTint = Tokens.text,
                            iconBackground = Tokens.disabledBackground,
                            title = "離開示範模式",
                            subtitle = "目前顯示的是範例資料，未連線官方網站",
                            trailing = Icons.Filled.ChevronRight,
                            onClick = viewModel::exitDemo,
                        )
                        RowDivider()
                    }

                    SettingsRow(
                        icon = Icons.Filled.Lock,
                        iconTint = Tokens.primary,
                        iconBackground = Tokens.iconTintBackground,
                        title = "本機資料",
                        subtitle = "僅這三個欄位以 AES-256-GCM 加密存於本機（金鑰在 Android Keystore）· " +
                            "無伺服器 · 不進雲端備份 · 不把個資寫入紀錄",
                    )
                    RowDivider()

                    TelemetryRow(
                        enabled = state.isTelemetryEnabled,
                        onToggle = viewModel::setTelemetryEnabled,
                    )
                    RowDivider()

                    // 隱私權政策：**Play 的 User Data 政策要求政策連結同時出現在
                    // Console 的欄位「與 App 內」**，只填 Console 是不合規的。
                    // 放在這裡而不是塞進免責聲明，是因為使用者想回頭查的時候會來設定頁找。
                    SettingsRow(
                        icon = Icons.Filled.PrivacyTip,
                        iconTint = Tokens.text,
                        iconBackground = Tokens.disabledBackground,
                        title = "隱私權政策",
                        subtitle = "收什麼、送去哪、怎麼自己查證，逐節寫清楚",
                        trailing = Icons.Filled.OpenInNew,
                        onClick = { openPrivacyPolicy(context) },
                    )
                    RowDivider()

                    // 原始碼連結：隱私宣稱要能被查證才有意義，所以把 repo 直接放進 App，
                    // 而不是只寫在商店描述裡。以外部瀏覽器開啟。
                    SettingsRow(
                        icon = Icons.Filled.Code,
                        iconTint = Tokens.text,
                        iconBackground = Tokens.disabledBackground,
                        title = "原始碼",
                        subtitle = "全部程式碼開源，這頁說的每一句都可以自己查證",
                        trailing = Icons.Filled.OpenInNew,
                        onClick = { openSourceCode(context) },
                    )
                    RowDivider()

                    SettingsRow(
                        icon = Icons.Filled.Delete,
                        iconTint = Tokens.danger,
                        iconBackground = Tokens.dangerBackground,
                        title = "立即清除本機資料",
                        titleColor = Tokens.danger,
                        subtitle = "刪除個資與登入狀態，回到初次設定",
                        trailing = Icons.Filled.ChevronRight,
                        onClick = { showClearConfirm = true },
                    )
                }
            }

            // 版本號讀 BuildConfig，不硬編碼——避免哪天送審版本改了卻忘了同步這行。
            // 名稱一律用上架名 Exercise Rewards（活動名不拿來自稱）。
            Text(
                "Exercise Rewards v${BuildConfig.VERSION_NAME} · 非官方工具\n" +
                    "個資不上雲 · 只連 500.gov.tw · 使用統計開著時會連 Firebase",
                fontSize = 11.5.sp,
                color = Tokens.dim,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.size(20.dp))
        }

        // 底部固定的儲存列
        Column(modifier = Modifier.fillMaxWidth().background(Tokens.card)) {
            HorizontalDivider(color = Tokens.line)
            Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                PrimaryButton(text = "儲存到本機", onClick = viewModel::save)
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除本機所有資料？") },
            text = {
                Text("將刪除本機儲存的個人資料與登入狀態，App 會回到初次設定畫面。此動作無法復原。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearLocalData()
                }) { Text("清除", color = Tokens.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
            containerColor = Tokens.card,
        )
    }

    state.alert?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAlert,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissAlert) { Text("好") } },
            containerColor = Tokens.card,
        )
    }
}

/**
 * 明確聲明個資的界線。
 *
 * **這段文字改過一次，原因要留著**：加了 Firebase（匿名使用統計）之後，原本第一點的
 * 「也不會提供給任何第三方」就不再是一句無條件為真的話了。個資的部分完全沒變
 * ——仍然一個位元都不外傳；變的是「會有不含個資的操作事件送給 Firebase」。
 * 所以這裡把界線拆成兩段講清楚，而不是把兩件事混在一句籠統的保證裡。
 */
@Composable
private fun PrivacyBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Tokens.shapeMedium)
            .background(Tokens.successBackground)
            .border(1.dp, Tokens.successBorder, Tokens.shapeMedium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Verified,
                contentDescription = null,
                tint = Tokens.success,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "個資不外傳，只在登入時送給官方網站",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.successText,
            )
        }
        listOf(
            "本 App 沒有伺服器也沒有後台。你的個資不會上傳雲端、不會進雲端備份、不會寫進任何紀錄，也不會給第三方——這一點沒有例外。",
            "以下三個欄位只在你登入時，由這支手機直接送到官方網站 500.gov.tw；平常以 AES-256-GCM 加密存在這支手機，可隨時用下方「立即清除本機資料」永久刪除。",
            "唯一會離開這支手機的是下方的「傳送匿名使用統計」：把「按了哪個按鈕、哪一步失敗、有沒有當機」送給 Google Firebase，用來修 bug。裡面沒有個資、沒有你上傳的截圖與券碼，不想送可以在下面關掉。",
        ).forEach { bullet ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("・", fontSize = 12.sp, color = Tokens.successText)
                Text(bullet, fontSize = 12.sp, color = Tokens.successText, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    titleColor: Color = Tokens.text,
    trailing: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickableRow(enabled = true, onClick = onClick) else Modifier)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        IconBox(icon, iconTint, iconBackground)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
            Text(subtitle, fontSize = 11.5.sp, color = Tokens.muted, lineHeight = 17.sp)
        }
        trailing?.let {
            Icon(it, contentDescription = null, tint = Tokens.chevron, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * 匿名使用統計開關。**同意免責聲明後預設開啟，使用者可隨時關掉（opt-out）**。
 *
 * 為什麼不是 opt-in：當機報告是最需要收到的東西，而藏在設定頁裡等人自己發現，
 * 實際開啟率低到樣本沒有意義。改成在**首次啟動的免責聲明**把這件事明講，
 * 使用者讀過並主動勾選同意之後才初始化 Firebase——保障放在「送之前一定先告知」，
 * 而不是「預設不送」。
 *
 * 這裡只切偏好；真正的「送不送得出去」由 `Telemetry` 的閘門決定（示範模式一律不送）。
 *
 * 副標依開關狀態換句話講：關著的時候使用者最想確認的是「現在真的沒在送吧」，
 * 開著的時候想確認的是「那到底送了什麼」。兩種狀態都要把「不含什麼」列完整，
 * 也不用行銷語氣（「協助我們做得更好」那類）淡化這是一個把資料送給第三方的開關。
 */
@Composable
private fun TelemetryRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        IconBox(Icons.Filled.BarChart, Tokens.text, Tokens.disabledBackground)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("傳送匿名使用統計", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
            Text(
                if (enabled) {
                    "開啟中 · 送出操作事件與當機報告給 Google Firebase · 不含個資、截圖、券碼 · 可隨時關掉"
                } else {
                    "已關閉 · 目前不會有任何資料送到 Google · 重新打開也不含個資、截圖、券碼"
                },
                fontSize = 11.5.sp,
                color = Tokens.muted,
                lineHeight = 17.sp,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Tokens.primary),
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 62.dp), color = Tokens.line)
}

/**
 * 開啟 Android 版的原始碼 repo。
 *
 * **這裡原本指向 `exercise_rewards_ios`** —— 從 iOS 端 port 過來時漏改的。
 * 一支 Android App 的「原始碼」按鈕開出 iOS 的 repo，等於把可查證性這件事做假：
 * 使用者照著讀完 Swift 也驗證不到手上這支 App 的任何行為。
 */
private fun openSourceCode(context: Context) {
    openUrl(context, "https://github.com/megshao/exercise_rewards_android")
}

/**
 * 開啟隱私權政策。
 *
 * 網址是 Android repo 自己的 GitHub Pages，**不是 iOS 那一份**：兩邊的技術敘述不同
 * （Keychain 對 Android Keystore、iCloud 對 allowBackup、App 隱私權報告對抓包工具），
 * 把 iOS 的政策掛給 Android 使用者看，內容會是錯的——那比沒有政策更糟。
 */
private fun openPrivacyPolicy(context: Context) {
    openUrl(context, "https://megshao.github.io/exercise_rewards_android/privacy.html")
}

/** 以外部瀏覽器開啟網址。開不起來（沒有瀏覽器）時安靜略過，不讓設定頁因此當掉。 */
private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
}

// MARK: - ViewModel

public class ProfileViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    public data class State(
        val draft: Profile = Profile(),
        val isRevealed: Boolean = false,
        val isTelemetryEnabled: Boolean = true,
        val alert: String? = null,
        val cleared: Boolean = false,
    ) {
        val maskedIdNo: String get() = mask(draft.idNo, prefix = 1, suffix = 2)
        val maskedPhone: String get() = mask(draft.phone, prefix = 4, suffix = 3)
    }

    private val _state = MutableStateFlow(State(isTelemetryEnabled = container.preferences.isTelemetryEnabled))
    public val state: StateFlow<State> = _state.asStateFlow()

    public val isDemo: StateFlow<Boolean> = container.isDemo

    public fun load() {
        try {
            container.environment.value.profileStore.load()?.let {
                _state.value = _state.value.copy(draft = it)
            }
        } catch (_: Throwable) {
            _state.value = _state.value.copy(alert = "讀取失敗，請重新輸入。")
            // 儲存層失敗走非致命錯誤，不進 Analytics；**內容與例外訊息一律不附帶**。
            Telemetry.recordNonFatal(context, TelemetryIssue.PROFILE_STORE)
        }
    }

    public fun updateIdNo(value: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(idNo = value))
    }

    public fun updateBirthDate(value: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(birthDate = value))
    }

    public fun updatePhone(value: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(phone = value))
    }

    public fun toggleReveal() {
        _state.value = _state.value.copy(isRevealed = !_state.value.isRevealed)
    }

    public fun save() {
        try {
            container.environment.value.profileStore.save(_state.value.draft)
            _state.value = _state.value.copy(alert = "已儲存到本機。")
            // 只有成功／失敗。三欄個資永遠不進遙測。
            Telemetry.logEvent(context, AnalyticsEvent.profileSave(SimpleOutcome.OK))
        } catch (_: Throwable) {
            _state.value = _state.value.copy(alert = "無法寫入本機加密儲存，請確認裝置已解鎖後再試一次。")
            Telemetry.logEvent(context, AnalyticsEvent.profileSave(SimpleOutcome.ERROR))
            Telemetry.recordNonFatal(context, TelemetryIssue.PROFILE_STORE)
        }
    }

    public fun setTelemetryEnabled(enabled: Boolean) {
        container.preferences.isTelemetryEnabled = enabled
        _state.value = _state.value.copy(isTelemetryEnabled = enabled)
        Telemetry.setUserEnabled(context, enabled)
    }

    public fun exitDemo() {
        container.exitDemo()
        load()
    }

    public fun dismissAlert() {
        _state.value = _state.value.copy(alert = null)
    }

    /** 清除本機所有資料：刪個資、快取、已使用標記、cookie、同意紀錄，回初次設定。 */
    public fun clearLocalData() {
        // 這一行必須在**最前面**：後面的 `resetPreference` 會把偏好關掉並重置 instance id，
        // 那之後就再也送不出去了。順序＝先記錄、再重置、最後回到未同意狀態。
        Telemetry.logEvent(context, AnalyticsEvent.localDataClear())

        container.exitDemo()
        runCatching { container.environment.value.profileStore.clear() }
        container.tasksCache.clear()
        // 「這張券我用過了」的本機標記也算本機資料。exitDemo() 在非示範模式是 no-op，
        // 不能靠它順手清掉，所以這裡明確再清一次。
        container.voucherUsage.clear()
        // 遙測偏好也算「本機資料」：清除後回到預設並立刻停止收集。
        Telemetry.resetPreference(context)
        // 免責聲明的同意紀錄與導覽進度也屬於「初次設定狀態」的一部分。
        container.preferences.resetToFirstRun()

        // 官方站的登入 cookie 是加密持久化、跨啟動續用的；只清個資並不會登出。
        // 不一併清掉就與這顆按鈕（與隱私說明）承諾的「清除本機所有資料」不符。
        viewModelScope.launch {
            container.environment.value.resetSession()
            _state.value = _state.value.copy(draft = Profile(), alert = "本機資料已刪除。", cleared = true)
        }
    }

    private companion object {
        fun mask(value: String, prefix: Int, suffix: Int): String {
            if (value.isEmpty()) return ""
            if (value.length <= prefix + suffix) return "●".repeat(value.length)
            return value.take(prefix) + "●".repeat(value.length - prefix - suffix) + value.takeLast(suffix)
        }
    }
}
