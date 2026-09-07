package com.megshao.exerciserewards

import android.content.Context
import com.megshao.exerciserewards.core.models.LoginCredentials
import com.megshao.exerciserewards.core.networking.HTTPClienting
import com.megshao.exerciserewards.core.networking.OkHttpHttpClient
import com.megshao.exerciserewards.core.security.ProfileStoring
import com.megshao.exerciserewards.core.services.AuthService
import com.megshao.exerciserewards.core.services.AuthServicing
import com.megshao.exerciserewards.core.services.RedeemService
import com.megshao.exerciserewards.core.services.RedeemServicing
import com.megshao.exerciserewards.core.services.TasksService
import com.megshao.exerciserewards.core.services.TasksServicing
import com.megshao.exerciserewards.core.services.UploadService
import com.megshao.exerciserewards.core.services.UploadServicing
import com.megshao.exerciserewards.core.services.VoucherService
import com.megshao.exerciserewards.core.services.VoucherServicing
import com.megshao.exerciserewards.data.AppPreferences
import com.megshao.exerciserewards.data.DemoMode
import com.megshao.exerciserewards.data.EncryptedCookieJar
import com.megshao.exerciserewards.data.InMemoryProfileStore
import com.megshao.exerciserewards.data.KeystoreProfileStore
import com.megshao.exerciserewards.data.MockAuthService
import com.megshao.exerciserewards.data.MockRedeemService
import com.megshao.exerciserewards.data.MockTasksService
import com.megshao.exerciserewards.data.MockUploadService
import com.megshao.exerciserewards.data.MockVoucherService
import com.megshao.exerciserewards.data.TasksCache
import com.megshao.exerciserewards.data.VoucherUsageStore
import com.megshao.exerciserewards.telemetry.Telemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 目前生效的服務組合。正式為真實 service；審查員輸入示範帳號後整組換成 Mock。
 *
 * 所有 service 共用**同一個** [HTTPClienting]，因為它們共用同一份 cookie jar——
 * 那份 cookie 就是登入 session。開第二個 client 等於開第二個登入狀態。
 */
public class AppEnvironment(
    public val auth: AuthServicing,
    public val tasks: TasksServicing,
    public val redeem: RedeemServicing,
    public val voucher: VoucherServicing,
    public val upload: UploadServicing,
    public val profileStore: ProfileStoring,
    private val http: HTTPClienting?,
) {
    /**
     * 清掉官方站的登入 session（cookie）。「立即清除本機資料」與登出都要呼叫，
     * 否則 cookie 會留在加密儲存裡跨啟動續用，等於沒真的清乾淨。
     *
     * 示範環境沒有真實 client，這裡是 no-op。
     */
    public suspend fun resetSession() {
        http?.resetSession()
    }
}

/**
 * 手動的依賴組裝，兼「示範模式／真實模式」的切換器。
 *
 * **刻意不引入 DI 框架**：整個 App 只有一組 service、一個 HTTP client、一個個資儲存，
 * 用一個類別就講得完；多一層 DI 只會讓「誰持有 session」這件事更難讀。
 */
public class AppContainer(private val context: Context) {

    public val preferences: AppPreferences = AppPreferences(context)
    public val tasksCache: TasksCache = TasksCache(context)
    public val voucherUsage: VoucherUsageStore = VoucherUsageStore(context)

    /**
     * 任務資料的「該重抓了」訊號。
     *
     * **為什麼需要它**：上傳與兌換都會讓官網那一期換狀態（→ UNDER_REVIEW／REDEEMED），
     * 但 App 這邊不會自己知道。沒有這個訊號，徽章會一直停在舊狀態——而且因為首頁與任務頁
     * 共用同一個 60 秒節流時鐘，連下拉刷新都可能被擋掉，使用者無從自救。
     *
     * 上傳／兌換頁離開時 bump 一次，三個分頁看到數字變了就強制重抓（忽略節流）。
     */
    private val _tasksRevision = MutableStateFlow(0)
    public val tasksRevision: StateFlow<Int> = _tasksRevision.asStateFlow()

    public fun invalidateTasks() {
        _tasksRevision.value += 1
    }

    private val _isDemo = MutableStateFlow(preferences.isDemoMode)
    public val isDemo: StateFlow<Boolean> = _isDemo.asStateFlow()

    private val _environment = MutableStateFlow(
        if (preferences.isDemoMode) makeDemoEnvironment() else makeRealEnvironment(),
    )
    public val environment: StateFlow<AppEnvironment> = _environment.asStateFlow()

    /** 憑證命中示範帳號就切進示範模式。回傳是否命中。 */
    public fun enterDemoIfSentinel(credentials: LoginCredentials): Boolean {
        if (!DemoMode.matches(credentials)) return false
        enterDemo()
        return true
    }

    public fun enterDemo() {
        if (_isDemo.value) return
        preferences.isDemoMode = true
        // 真實任務快取不能留在示範畫面上（反之亦然），兩個方向都清。
        // 「已使用」標記也一樣：它是綁期別 UUID 的，示範資料與真實資料不可混用。
        tasksCache.clear()
        voucherUsage.clear()
        _environment.value = makeDemoEnvironment()
        _isDemo.value = true
        // 示範模式一律不送遙測與當機報告，SDK 層也一起關掉（不只靠 Telemetry 的閘門）。
        Telemetry.isDemoMode = true
        Telemetry.applyDemoMode(context)
    }

    public fun exitDemo() {
        if (!_isDemo.value) return
        preferences.isDemoMode = false
        tasksCache.clear()
        voucherUsage.clear()
        _environment.value = makeRealEnvironment()
        _isDemo.value = false
        // 離開示範模式後，收集狀態回到使用者自己的偏好。
        Telemetry.isDemoMode = false
        Telemetry.applyDemoMode(context)
    }

    private fun makeRealEnvironment(): AppEnvironment {
        val http = OkHttpHttpClient(cookieJar = EncryptedCookieJar(context))
        return AppEnvironment(
            auth = AuthService(http),
            tasks = TasksService(http),
            redeem = RedeemService(http),
            voucher = VoucherService(http),
            upload = UploadService(http),
            profileStore = KeystoreProfileStore(context),
            http = http,
        )
    }

    private fun makeDemoEnvironment(): AppEnvironment = AppEnvironment(
        auth = MockAuthService(),
        tasks = MockTasksService(),
        redeem = MockRedeemService(),
        voucher = MockVoucherService(),
        upload = MockUploadService(),
        profileStore = InMemoryProfileStore(seed = DemoMode.profile),
        http = null,
    )
}
