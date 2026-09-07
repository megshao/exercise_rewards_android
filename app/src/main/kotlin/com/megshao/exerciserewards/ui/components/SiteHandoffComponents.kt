package com.megshao.exerciserewards.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megshao.exerciserewards.core.services.SiteHandoff
import com.megshao.exerciserewards.core.services.SiteHandoffDestination
import com.megshao.exerciserewards.data.DemoMode
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.ui.rememberAppContainer
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle
import kotlinx.coroutines.delay

/*
 * 官網改版時的「交接」UI。
 *
 * 這支 App 全靠手寫 regex 解析官網 HTML，官網一改版所有已安裝版本同時失效，要等修好、送審、上架。
 * 空窗期裡使用者原本看到的是「請確認網路連線」——錯誤歸因，他會去重開 Wi-Fi。這一組元件不修
 * parser、不繞過改版，只負責**說實話**（是官網改版，不是你的網路壞了）與**交接**（外開系統
 * 瀏覽器到官網對應頁面，並提供「複製身分證號」讓他少打一個欄位）。
 *
 * 要不要走這條路、要開哪個網址，都由 core 的 [SiteHandoff] 決定；這裡只呈現與開啟，不拼字串。
 * 為什麼不直接自動登入，見 [SiteHandoff] 的說明。
 */

/** 全屏錯誤狀態的預設說明。券碼頁會換成貼合櫃檯情境的另一句（見 `VoucherScreen`）。 */
public const val SITE_HANDOFF_DESCRIPTION: String =
    "這支 App 讀不到官網這個頁面的資料，可能是官網改版了。你可以先到官網完成，我們會盡快修正。"

/**
 * 沒有任何可顯示資料時的全屏錯誤狀態，取代原本 `StateMessage` 的那句「請確認網路連線」。
 *
 * 版面沿用 [StateMessage]（圖示、置中文字、次要按鈕），多出「前往官網」與「複製身分證號」；
 * `onRetry` 仍然保留——改版不是唯一可能，使用者也該有重新整理的路。
 */
@Composable
public fun SiteHandoffMessage(
    destination: SiteHandoffDestination,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = SITE_HANDOFF_DESCRIPTION,
    topPadding: Dp = 40.dp,
) {
    val context = LocalContext.current
    val opensBrowser = rememberOpensBrowser()
    val idNo = rememberHandoffIdNo()

    Column(
        modifier = modifier.fillMaxWidth().padding(top = topPadding, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Tokens.warnText, modifier = Modifier.size(30.dp))
        Text("官網可能已改版", style = displayStyle(16, FontWeight.Bold), color = Tokens.text)
        Text(
            description,
            fontSize = 13.sp,
            color = Tokens.muted,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )

        if (opensBrowser) {
            PrimaryButton(
                text = "前往官網",
                onClick = { openSite(context, destination) },
                icon = Icons.Filled.OpenInNew,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (idNo != null) {
            CopyIdNoButton(idNo = idNo)
            // 剪貼簿是整支手機共用的，這是個資的一條新路徑，必須在按鈕旁邊就講清楚。
            Text(
                "複製後身分證號會留在剪貼簿，貼到官網後建議再複製別的內容覆蓋。",
                fontSize = 11.5.sp,
                color = Tokens.dim,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
            )
        }

        SecondaryButton(text = "重新整理", onClick = onRetry)
    }
}

/**
 * 有快取可顯示時的頂端橫幅：照舊顯示先前抓到的資料，但講明白它可能是舊的。
 *
 * 樣式沿用 [InfoBanner] 的黃底警示（兌換頁、券碼頁的警語就是這一種），不另創視覺語言。
 * 可以關掉（`onDismiss`），但**關掉只是這一次**——下次 parse 再失敗要重新出現，那由呼叫端的
 * ViewModel 決定，這裡不記狀態。
 */
@Composable
public fun SiteHandoffBanner(
    destination: SiteHandoffDestination,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val opensBrowser = rememberOpensBrowser()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Tokens.shapeSmall)
            .background(Tokens.warnBackground)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "官網可能已改版，以下是先前抓到的資料",
            fontSize = 12.5.sp,
            color = Tokens.warnText,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
        if (opensBrowser) {
            Text(
                "前往官網",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.warnText,
                modifier = Modifier
                    .clickableRow(enabled = true, onClick = { openSite(context, destination) })
                    .padding(vertical = 6.dp),
            )
        }
        Icon(
            Icons.Filled.Close,
            contentDescription = "關閉",
            tint = Tokens.warnText,
            modifier = Modifier
                .clickableRow(enabled = true, onClick = onDismiss)
                .padding(6.dp)
                .size(16.dp),
        )
    }
}

/**
 * 「複製身分證號」。只複製這一個欄位——生日與手機使用者自己知道，刻意不一次把三碼都攤在剪貼簿上。
 *
 * 回饋照券碼頁「重新發送 (60s)」那種**換按鈕文字**的方式：按下後兩秒內顯示「已複製」，不另開對話框。
 * Android 13 起系統自己也會跳一個「已複製」的提示，兩者並存無妨。
 */
@Composable
private fun CopyIdNoButton(idNo: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MILLIS)
            copied = false
        }
    }

    SecondaryButton(
        text = if (copied) "已複製" else "複製身分證號",
        onClick = {
            copyIdNo(context, idNo)
            copied = true
        },
        icon = Icons.Filled.ContentCopy,
        fillWidth = true,
    )
}

/**
 * 要不要顯示「前往官網」。示範模式與截圖模式下**不顯示**（而不是顯示一顆按了沒反應的按鈕）：
 * 示範模式的定義就是「未連線官方網站」，把審查員送去官網用一組不存在的帳號登入沒有意義；
 * 截圖模式則不能讓測試流程被外開的瀏覽器打斷。真實使用者兩種模式都碰不到，按鈕一定在。
 */
@Composable
private fun rememberOpensBrowser(): Boolean {
    val isDemo by rememberAppContainer().isDemo.collectAsState()
    return !isDemo && !Telemetry.isScreenshotMode
}

/**
 * 從既有的加密儲存讀身分證號。讀不到（還沒填、Keystore 失敗）就回 null，**按鈕不顯示**。
 *
 * 示範模式下也回 null：哨兵值 `A000000000` 不該進剪貼簿。`isDemo` 已經擋了一層，
 * 再用 [DemoMode.matches] 對 profile 本身檢查一次，兩層互為保險。
 */
@Composable
private fun rememberHandoffIdNo(): String? {
    val container = rememberAppContainer()
    val isDemo by container.isDemo.collectAsState()
    return remember(isDemo) {
        if (isDemo) return@remember null
        val profile = runCatching { container.environment.value.profileStore.load() }.getOrNull()
            ?: return@remember null
        if (DemoMode.matches(profile)) return@remember null
        profile.idNo.trim().takeIf { it.isNotEmpty() }
    }
}

/**
 * 外開系統瀏覽器到官網。網址只能來自 [SiteHandoff.url]，做法照 `OnboardingScreen` 的
 * 「前往官網註冊」：開不起來（沒有瀏覽器）時安靜略過，不讓畫面因此當掉。
 */
private fun openSite(context: Context, destination: SiteHandoffDestination) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SiteHandoff.url(destination)))
    runCatching { context.startActivity(intent) }
}

/**
 * 把身分證號放進系統剪貼簿。
 *
 * label 刻意留空：剪貼簿管理器與鍵盤會把 label 秀出來，寫「身分證號」等於替旁邊看的人標註
 * 這串是什麼。Android 13 起另外標成敏感內容（`EXTRA_IS_SENSITIVE`），系統的剪貼簿預覽會遮罩。
 */
private fun copyIdNo(context: Context, idNo: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("", idNo)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    runCatching { clipboard.setPrimaryClip(clip) }
}

private const val COPIED_FEEDBACK_MILLIS = 2_000L
