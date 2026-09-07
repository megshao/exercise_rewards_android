package com.megshao.exerciserewards.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megshao.exerciserewards.AppContainer
import com.megshao.exerciserewards.core.models.LoginCredentials
import com.megshao.exerciserewards.core.models.LoginOutcome
import com.megshao.exerciserewards.core.models.Profile
import com.megshao.exerciserewards.telemetry.AnalyticsEvent
import com.megshao.exerciserewards.telemetry.Endpoint
import com.megshao.exerciserewards.telemetry.FailReason
import com.megshao.exerciserewards.telemetry.LoginTrigger
import com.megshao.exerciserewards.telemetry.OnboardingField
import com.megshao.exerciserewards.telemetry.ScreenName
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.ui.appViewModel
import com.megshao.exerciserewards.ui.components.BirthDateField
import com.megshao.exerciserewards.ui.components.PrimaryButton
import com.megshao.exerciserewards.ui.components.ProfileField
import com.megshao.exerciserewards.ui.components.SecondaryButton
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首次啟動導覽的**個資填寫**那一步。
 *
 * 歡迎頁與免責聲明排在這之前（順序見 `AppNavigation`），所以走到這裡時使用者一定已經
 * 同意過聲明、遙測也已經初始化——`tutorial_begin` 因此在這一頁出現時才送。
 *
 * 這一頁做的事：
 * 1. 強制填 3 欄位（身分證、生日、手機——個資最小化到登入必需，姓名／健保卡卡號／email
 *    皆不在此收集）。
 * 2. 「送出並驗證」呼叫 `AuthServicing.login`（內部就是 `/access` 分流 + login）：
 *    - 成功 → 存 Profile 到加密儲存 → 直接完成，進主畫面。
 *    - 三碼不符 → 停在表單，提示。
 *    - 未註冊 → **App 不做註冊**：顯示提示＋「前往官網註冊」按鈕，用外部瀏覽器開官網；
 *      App 內完全不實作任何註冊步驟、不碰健保卡。
 *
 * App 不使用任何生物辨識：個資本來就只存在這支手機，手機本身的鎖屏已經是同一層保護，
 * 再加一層只是重複擋自己人。
 */
@Composable
public fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val viewModel = appViewModel { container, appContext -> OnboardingViewModel(container, appContext) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        Telemetry.screenAppeared(context, ScreenName.ONBOARDING_FORM)
        // 導覽漏斗的第一步。**刻意在這裡而不是「開始使用」那顆按鈕上**——
        // 歡迎頁排在免責聲明之前，那時遙測還沒初始化，埋在那裡送不出去。
        viewModel.formDidAppear()
    }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text2("填寫個人資料", displayStyle(20, FontWeight.ExtraBold))
            androidx.compose.material3.Text(
                "首次使用需完整填寫以下資料以驗證身分。",
                fontSize = 13.sp,
                color = Tokens.muted,
            )
        }

        // 使用者正要輸入身分證號，這是全 App 最需要把話說準的一刻。
        // 「不會寫入紀錄檔」在 Crashlytics 存在後語意變模糊，改成「不會把個資寫進任何紀錄」
        // ——這句話即使在遙測開啟時也仍然為真。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Tokens.shapeMedium)
                .background(Tokens.successBackground)
                .padding(14.dp),
        ) {
            androidx.compose.material3.Text(
                "這三欄只加密存在這支手機，登入時直接送給 500.gov.tw。App 不會把個資寫進任何紀錄，也不會給第三方。",
                fontSize = 12.5.sp,
                color = Tokens.successText,
                lineHeight = 19.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileField(
                label = "身分證號",
                value = state.draft.idNo,
                onValueChange = viewModel::updateIdNo,
                placeholder = "A123456789",
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
                keyboardType = KeyboardType.Phone,
            )
        }

        if (state.isNotRegistered) {
            // App 不做註冊，只提示使用者先到官網完成註冊，按鈕以外部瀏覽器開啟。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Tokens.shapeMedium)
                    .background(Tokens.card)
                    .border(1.dp, Tokens.line2, Tokens.shapeMedium)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.Text(
                    "此身分證尚未在官網註冊，請先至官網完成註冊後再回來登入。",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Tokens.text,
                    lineHeight = 19.sp,
                )
                SecondaryButton(
                    text = "前往官網註冊",
                    onClick = {
                        // 無參數：開啟的是固定 URL，不含任何使用者輸入。
                        Telemetry.logEvent(context, AnalyticsEvent.registerRedirect())
                        openRegisterPage(context)
                    },
                )
            }
        } else if (state.errorMessage != null) {
            androidx.compose.material3.Text(
                state.errorMessage.orEmpty(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Tokens.danger,
            )
        }

        PrimaryButton(
            text = "送出並驗證",
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )

        Box(Modifier.padding(bottom = 20.dp))
    }
}

@Composable
private fun Text2(text: String, style: androidx.compose.ui.text.TextStyle) {
    androidx.compose.material3.Text(text, style = style, color = Tokens.text)
}

/** 官網的註冊入口。固定 URL，不含任何使用者輸入。 */
private fun openRegisterPage(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://500.gov.tw/registrant/access"))
    runCatching { context.startActivity(intent) }
}

public class OnboardingViewModel(
    private val container: AppContainer,
    private val context: Context,
) : ViewModel() {

    public data class State(
        /** 主表單只收 3 欄（身分證／生日／手機）——個資最小化到登入必需。 */
        val draft: Profile = Profile(),
        val isSubmitting: Boolean = false,
        val errorMessage: String? = null,
        /** App 不做註冊，只顯示提示＋「前往官網註冊」外部連結。 */
        val isNotRegistered: Boolean = false,
        val finished: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    public val state: StateFlow<State> = _state.asStateFlow()

    private var didReportBegin = false

    public fun updateIdNo(value: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(idNo = value))
    }

    public fun updateBirthDate(value: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(birthDate = value))
    }

    public fun updatePhone(value: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(phone = value))
    }

    /**
     * 表單畫面出現時呼叫（只送一次）。
     *
     * `tutorial_begin` 原本綁在歡迎頁的「開始使用」上。歡迎頁移到免責聲明**之前**之後，
     * 那個時機還在同意前——閘門會把事件丟掉，漏斗第一步就永遠是 0。
     * 因此改成「表單出現」＝使用者真的開始填資料，這也更貼近 `tutorial_begin` 的語意。
     */
    public fun formDidAppear() {
        if (didReportBegin) return
        didReportBegin = true
        Telemetry.logEvent(context, AnalyticsEvent.tutorialBegin())
    }

    public fun submit() {
        val draft = _state.value.draft
        val failure = validationFailure(draft)
        if (failure != null) {
            _state.value = _state.value.copy(isNotRegistered = false, errorMessage = failure.second)
            // 只送「哪個欄位格式不對」。
            Telemetry.logEvent(context, AnalyticsEvent.onboardingValidationFailed(failure.first))
            return
        }

        val credentials = LoginCredentials(
            idNo = draft.idNo.trim(),
            birthDate = draft.birthDate.trim(),
            phone = draft.phone.trim(),
        )

        // 示範帳號（商店審查用）：不連線、不寫加密儲存，直接切進示範環境。
        if (container.enterDemoIfSentinel(credentials)) {
            finish()
            return
        }

        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null, isNotRegistered = false)
        viewModelScope.launch {
            val environment = container.environment.value
            // 只量「按下送出到拿到結果」的耗時。憑證本身完全不經過 Telemetry。
            val startedAt = System.nanoTime()
            try {
                when (environment.auth.login(credentials)) {
                    LoginOutcome.SUCCESS -> {
                        // 只有 method（常數）／trigger／耗時，沒有 cookie、沒有 CSRF、沒有三碼。
                        Telemetry.logEvent(
                            context,
                            AnalyticsEvent.login(LoginTrigger.ONBOARDING, Telemetry.elapsedMs(startedAt)),
                        )
                        environment.profileStore.save(draft)
                        _state.value = _state.value.copy(isSubmitting = false)
                        finish()
                    }

                    LoginOutcome.INVALID_CREDENTIALS -> {
                        // 「三碼不符」只是官網 302 回 /login 的分類，我們不知道是哪一碼錯，也不想知道。
                        Telemetry.logEvent(
                            context,
                            AnalyticsEvent.loginFailed(
                                LoginTrigger.ONBOARDING,
                                FailReason.INVALID_CREDENTIALS,
                                Telemetry.elapsedMs(startedAt),
                            ),
                        )
                        _state.value = _state.value.copy(
                            isSubmitting = false,
                            errorMessage = "身分證號、出生日期或手機號碼有誤，請確認後再試一次",
                        )
                    }

                    LoginOutcome.NOT_REGISTERED -> {
                        Telemetry.logEvent(
                            context,
                            AnalyticsEvent.loginFailed(
                                LoginTrigger.ONBOARDING,
                                FailReason.NOT_REGISTERED,
                                Telemetry.elapsedMs(startedAt),
                            ),
                        )
                        _state.value = _state.value.copy(isSubmitting = false, isNotRegistered = true)
                    }
                }
            } catch (error: Throwable) {
                // `/access` 與 `/login` 是公開頁：這裡失敗不可能是 session 過期，一律當改版訊號看。
                val reason = Telemetry.reportFailure(context, error, Endpoint.LOGIN)
                Telemetry.logEvent(
                    context,
                    AnalyticsEvent.loginFailed(LoginTrigger.ONBOARDING, reason, Telemetry.elapsedMs(startedAt)),
                )
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = "網路連線異常，請確認網路後再試一次",
                )
            }
        }
    }

    private fun finish() {
        // 真實登入成功路徑。示範帳號也會走到這裡，但 `enterDemoIfSentinel` 已經先把
        // 示範模式打開，遙測的示範模式閘門會把它擋掉——順序是對的。
        Telemetry.logEvent(context, AnalyticsEvent.tutorialComplete())
        container.preferences.hasCompletedOnboarding = true
        _state.value = _state.value.copy(finished = true)
    }

    public companion object {
        /**
         * 3 欄位皆須非空、格式基本正確才允許送出。回傳 null 代表通過。
         *
         * 遙測只拿欄位名：**不送長度、不送第一碼、不送使用者輸入的任何字元**。
         * 四個值（empty / id_no / birth_date / phone）都無法反推出任何內容。
         */
        public fun validationFailure(profile: Profile): Pair<OnboardingField, String>? {
            val idNo = profile.idNo.trim()
            val birth = profile.birthDate.trim()
            val phone = profile.phone.trim()

            if (idNo.isEmpty() || birth.isEmpty() || phone.isEmpty()) {
                return OnboardingField.EMPTY to "請完整填寫身分證號、生日與手機號碼"
            }
            if (!ID_NO.matches(idNo)) return OnboardingField.ID_NO to "身分證字號格式不正確"
            if (!BIRTH_DATE.matches(birth)) return OnboardingField.BIRTH_DATE to "出生日期格式不正確"
            if (!PHONE.matches(phone)) return OnboardingField.PHONE to "手機號碼格式不正確"
            return null
        }

        private val ID_NO = Regex("^[A-Za-z][0-9]{9}$")
        private val BIRTH_DATE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
        private val PHONE = Regex("^09[0-9]{8}$")
    }
}
