package com.megshao.exerciserewards.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.megshao.exerciserewards.core.models.TaskPeriod
import com.megshao.exerciserewards.data.DisclaimerConsent
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.telemetry.VoucherSource
import com.megshao.exerciserewards.ui.components.DemoModeBanner
import com.megshao.exerciserewards.ui.screens.DisclaimerScreen
import com.megshao.exerciserewards.ui.screens.HomeScreen
import com.megshao.exerciserewards.ui.screens.OnboardingScreen
import com.megshao.exerciserewards.ui.screens.ProfileScreen
import com.megshao.exerciserewards.ui.screens.RedeemScreen
import com.megshao.exerciserewards.ui.screens.ScreenshotScreen
import com.megshao.exerciserewards.ui.screens.TasksScreen
import com.megshao.exerciserewards.ui.screens.UploadScreen
import com.megshao.exerciserewards.ui.screens.VendorIntroScreen
import com.megshao.exerciserewards.ui.screens.VoucherScreen
import com.megshao.exerciserewards.ui.screens.WalletScreen
import com.megshao.exerciserewards.ui.screens.WelcomeScreen
import com.megshao.exerciserewards.ui.theme.Tokens

/**
 * App 根導覽。首次啟動的順序：**歡迎 → 免責聲明 → 個資填寫 → 主畫面**。
 *
 * **為什麼歡迎頁排在免責聲明之前**：免責聲明是一整頁條款，第一次開 App 就直接撞上它，
 * 使用者連「這是什麼 App」都還不知道就要決定同不同意。先給一頁「這是什麼、誰做的」，
 * 按下「開始使用」表示願意繼續，再請他讀條款——同意才是有前提的。
 *
 * **這個順序對遙測的影響**：`Telemetry.configure()` 仍然只在同意的那一刻被呼叫，
 * 所以歡迎頁完全在「Firebase 一行都還沒執行」的階段（那句話仍然為真）。
 * 代價是歡迎頁不能埋任何事件——漏斗第一步 `tutorial_begin` 因此移到表單出現時才送。
 */
@Composable
public fun AppRoot() {
    val container = rememberAppContainer()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDemo by container.isDemo.collectAsState()

    // 這三個旗標決定首次啟動走到哪一步。用 state 保存，改了才會重畫。
    var hasSeenWelcome by remember { mutableStateOf(container.preferences.hasSeenWelcome) }
    var agreedVersion by remember { mutableStateOf(container.preferences.disclaimerAgreedVersion) }
    var hasCompletedOnboarding by remember { mutableStateOf(container.preferences.hasCompletedOnboarding) }

    // enableEdgeToEdge() 之後內容會畫到狀態列底下。主畫面由 Scaffold 處理 insets，
    // 但首次啟動的三頁沒有 Scaffold，得自己讓開——否則標題會被時鐘蓋住（實測過）。
    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // 截圖模式會把橫幅收起來，讓上架素材的畫面上緣乾淨。真機使用者與商店審查員都不可能
        // 觸發截圖模式（見 Telemetry.isScreenshotMode），他們進示範模式時橫幅一定照常出現。
        if (isDemo && !Telemetry.isScreenshotMode) DemoModeBanner()

        when {
            !hasSeenWelcome -> WelcomeScreen(
                onStart = {
                    container.preferences.hasSeenWelcome = true
                    hasSeenWelcome = true
                },
            )

            agreedVersion < DisclaimerConsent.CURRENT_VERSION -> DisclaimerScreen(
                onAgree = {
                    container.preferences.disclaimerAgreedVersion = DisclaimerConsent.CURRENT_VERSION
                    container.preferences.disclaimerAgreedAt = System.currentTimeMillis()
                    // **這是整支 App 第一次執行 Firebase 程式碼的時機。**
                    // 免責聲明畫面是使用統計被揭露的地方，所以初始化綁在這裡——在使用者
                    // 讀到說明並按下同意之前，`FirebaseApp.initializeApp` 不會被呼叫。
                    Telemetry.configure(context, userEnabled = container.preferences.isTelemetryEnabled)
                    // 同意後的第一個事件，必須在 configure 之後。
                    Telemetry.logEvent(
                        context,
                        com.megshao.exerciserewards.telemetry.AnalyticsEvent.consentGranted(),
                    )
                    agreedVersion = DisclaimerConsent.CURRENT_VERSION
                },
            )

            !hasCompletedOnboarding -> OnboardingScreen(
                onFinish = { hasCompletedOnboarding = true },
            )

            else -> MainNavigation(
                onDataCleared = {
                    // 「清除本機所有資料」承諾的是回到初次設定，所以三個旗標一起歸零
                    // ——只重設 onboarding 會讓使用者直接落在免責聲明頁，跟第一次安裝不一樣。
                    hasSeenWelcome = false
                    agreedVersion = 0
                    hasCompletedOnboarding = false
                },
            )
        }
    }
}

/** 主要 3 個分頁：首頁、任務、券夾；其餘畫面推在上面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavigation(onDataCleared: () -> Unit) {
    val navController = rememberNavController()
    val container = rememberAppContainer()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    val isTab = route in setOf(Route.HOME, Route.TASKS, Route.WALLET)

    Scaffold(
        containerColor = Tokens.background,
        topBar = {
            // 分頁自己有標題區塊（首頁的問候語、任務頁的說明），只有推進來的畫面需要 app bar。
            if (!isTab && route != null) {
                TopAppBar(
                    title = { Text(titleFor(route, backStackEntry?.arguments), fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Tokens.card,
                        titleContentColor = Tokens.text,
                        navigationIconContentColor = Tokens.text,
                    ),
                )
            }
        },
        bottomBar = {
            if (isTab) {
                NavigationBar(containerColor = Tokens.card) {
                    TabItem(navController, route, Route.HOME, "首頁", Icons.Filled.Home)
                    TabItem(navController, route, Route.TASKS, "任務", Icons.Filled.Verified)
                    TabItem(navController, route, Route.WALLET, "券夾", Icons.Filled.ConfirmationNumber)
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = navController,
            startDestination = Route.HOME,
            modifier = Modifier.padding(insets),
        ) {
            composable(Route.HOME) {
                HomeScreen(
                    onOpenProfile = { navController.navigate(Route.PROFILE) },
                    onOpenTasks = { navController.navigate(Route.TASKS) },
                    onOpenWallet = { navController.navigate(Route.WALLET) },
                    onRedeem = { navController.navigate(Route.redeem(it)) },
                    onVoucher = { navController.navigate(Route.voucher(it, VoucherSource.WALLET)) },
                )
            }

            composable(Route.TASKS) {
                TasksScreen(
                    onUpload = { navController.navigate(Route.upload(it)) },
                    onRedeem = { navController.navigate(Route.redeem(it)) },
                    onScreenshot = { navController.navigate(Route.screenshot(it)) },
                    onVoucher = { navController.navigate(Route.voucher(it, VoucherSource.TASKS)) },
                )
            }

            composable(Route.WALLET) {
                WalletScreen(
                    onVoucher = { navController.navigate(Route.voucher(it, VoucherSource.WALLET)) },
                    onRedeem = { navController.navigate(Route.redeem(it)) },
                )
            }

            composable(Route.PROFILE) {
                ProfileScreen(onDataCleared = onDataCleared)
            }

            detailRoute(Route.UPLOAD) { taskId, index ->
                UploadScreen(taskId = taskId, periodIndex = index)
            }

            composable(
                "${Route.SCREENSHOT}/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
            ) { entry ->
                ScreenshotScreen(taskId = entry.arguments?.getString("taskId").orEmpty())
            }

            detailRoute(Route.REDEEM) { taskId, index ->
                RedeemScreen(
                    taskId = taskId,
                    periodIndex = index,
                    onOpenVendorIntro = { path, vendor ->
                        navController.navigate(Route.vendorIntro(path, vendor))
                    },
                    onOpenVoucher = {
                        navController.navigate(Route.voucher(taskId, VoucherSource.REDEEM_RESULT, index))
                    },
                )
            }

            composable(
                "${Route.VENDOR_INTRO}?path={path}&vendor={vendor}",
                arguments = listOf(
                    navArgument("path") { type = NavType.StringType },
                    navArgument("vendor") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                VendorIntroScreen(
                    introPath = Uri.decode(entry.arguments?.getString("path").orEmpty()),
                    vendorName = Uri.decode(entry.arguments?.getString("vendor").orEmpty()),
                )
            }

            composable(
                "${Route.VOUCHER}/{taskId}?source={source}&index={index}",
                arguments = listOf(
                    navArgument("taskId") { type = NavType.StringType },
                    navArgument("source") { type = NavType.StringType; defaultValue = VoucherSource.WALLET.raw },
                    navArgument("index") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { entry ->
                val sourceRaw = entry.arguments?.getString("source") ?: VoucherSource.WALLET.raw
                VoucherScreen(
                    taskId = entry.arguments?.getString("taskId").orEmpty(),
                    source = VoucherSource.entries.firstOrNull { it.raw == sourceRaw } ?: VoucherSource.WALLET,
                    periodIndex = entry.arguments?.getInt("index")?.takeIf { it > 0 },
                )
            }
        }
    }

    // 上傳／兌換頁離開時讓任務資料失效（見 AppContainer.invalidateTasks 的說明）。
    androidx.compose.runtime.DisposableEffect(route) {
        onDispose {
            if (route != null && (route.startsWith(Route.UPLOAD) || route.startsWith(Route.REDEEM))) {
                container.invalidateTasks()
            }
        }
    }
}

/** 帶 `taskId` 與選用 `index` 的細節頁。 */
private fun NavGraphBuilder.detailRoute(
    base: String,
    content: @Composable (taskId: String, index: Int?) -> Unit,
) {
    composable(
        "$base/{taskId}?index={index}",
        arguments = listOf(
            navArgument("taskId") { type = NavType.StringType },
            navArgument("index") { type = NavType.IntType; defaultValue = -1 },
        ),
    ) { entry ->
        content(
            entry.arguments?.getString("taskId").orEmpty(),
            entry.arguments?.getInt("index")?.takeIf { it > 0 },
        )
    }
}

@Composable
private fun RowScope.TabItem(
    navController: NavHostController,
    currentRoute: String?,
    route: String,
    label: String,
    icon: ImageVector,
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = {
            if (currentRoute != route) {
                navController.navigate(route) {
                    popUpTo(Route.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontSize = 11.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Tokens.primary,
            selectedTextColor = Tokens.primary,
            unselectedIconColor = Tokens.dim,
            unselectedTextColor = Tokens.dim,
            indicatorColor = Tokens.card2,
        ),
    )
}

private fun titleFor(route: String, arguments: android.os.Bundle?): String {
    val index = arguments?.getInt("index")?.takeIf { it > 0 }
    val suffix = index?.let { " · 第 $it 期" } ?: ""
    return when {
        route.startsWith(Route.PROFILE) -> "我的資料"
        route.startsWith(Route.UPLOAD) -> "上傳運動紀錄$suffix"
        route.startsWith(Route.SCREENSHOT) -> "上傳截圖"
        route.startsWith(Route.REDEEM) -> "兌換好禮$suffix"
        route.startsWith(Route.VENDOR_INTRO) -> "可兌換商品"
        route.startsWith(Route.VOUCHER) -> "我的加碼券"
        else -> ""
    }
}

private object Route {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val WALLET = "wallet"
    const val PROFILE = "profile"
    const val UPLOAD = "upload"
    const val SCREENSHOT = "screenshot"
    const val REDEEM = "redeem"
    const val VENDOR_INTRO = "vendorIntro"
    const val VOUCHER = "voucher"

    fun upload(period: TaskPeriod): String = "$UPLOAD/${period.id.ifEmpty { "-" }}?index=${period.index}"

    fun screenshot(period: TaskPeriod): String = "$SCREENSHOT/${period.id.ifEmpty { "-" }}"

    fun redeem(period: TaskPeriod): String = "$REDEEM/${period.id}?index=${period.index}"

    fun voucher(period: TaskPeriod, source: VoucherSource): String =
        "$VOUCHER/${period.id}?source=${source.raw}&index=${period.index}"

    fun voucher(taskId: String, source: VoucherSource, index: Int?): String =
        "$VOUCHER/$taskId?source=${source.raw}&index=${index ?: -1}"

    /** `path` 含 `/`，一定要編碼；商家名可能含中文與符號，同樣編碼。 */
    fun vendorIntro(path: String, vendorName: String): String =
        "$VENDOR_INTRO?path=${Uri.encode(path)}&vendor=${Uri.encode(vendorName)}"
}
