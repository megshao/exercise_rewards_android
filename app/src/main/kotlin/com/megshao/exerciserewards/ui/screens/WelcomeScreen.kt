package com.megshao.exerciserewards.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megshao.exerciserewards.R
import com.megshao.exerciserewards.ui.components.PrimaryButton
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle

/**
 * App 的第一個畫面：圖示 ＋ 大標題 ＋「開始使用」。
 *
 * ## 為什麼它排在免責聲明**之前**
 *
 * 免責聲明是一整頁的條款，第一次開 App 就直接撞上它，使用者連「這是什麼 App」都還不知道
 * 就要決定同不同意。先給一頁「這是什麼、誰做的」，按下「開始使用」表示願意繼續，
 * 再請他讀條款——這個順序讓同意是有前提的，而不是被迫點掉一個障礙。
 *
 * ## 這一頁不會送出任何遙測
 *
 * 它在同意之前，Firebase 根本還沒初始化（那正是「同意前 Firebase 一行程式碼都不執行」
 * 這句話的來源）。因此**不要在這裡埋任何事件**——埋了也送不出去，只會讓人誤以為有資料。
 * 導覽漏斗的第一步 `tutorial_begin` 改在表單出現時才送（見 [OnboardingScreen]）。
 */
@Composable
public fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppMark()

            // 主標一律用上架名稱 Exercise Rewards：刻意不拿活動名「揮汗有禮」自稱，
            // 避免被誤認為官方 App；活動名只出現在說明用途的副標裡。
            Text("Exercise Rewards", style = displayStyle(28, FontWeight.ExtraBold), color = Tokens.text)

            Text(
                "協助你參加運動部「揮汗有禮」活動的非官方小工具\n每週達標，就能換一張超商加碼券",
                fontSize = 15.sp,
                color = Tokens.muted,
                textAlign = TextAlign.Center,
                lineHeight = 23.sp,
            )
        }

        Box(Modifier.size(24.dp))

        // 非官方聲明（App 內三處揭露之一：啟動頁／我的資料／商店描述）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Tokens.shapeMedium)
                .background(Tokens.card2)
                .padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = Tokens.muted,
                modifier = Modifier.size(15.dp),
            )
            Text(
                "本 App 由個人開發，是非官方工具，與運動部沒有任何隸屬或授權關係。",
                fontSize = 12.sp,
                color = Tokens.muted,
                lineHeight = 18.sp,
            )
        }

        Text(
            "下一步會先請你看一次使用說明與免責聲明。",
            fontSize = 12.sp,
            color = Tokens.dim,
            textAlign = TextAlign.Center,
        )

        PrimaryButton(text = "開始使用", onClick = onStart)
    }
}

/**
 * App 標記——**沿用 iOS 版本的圖示本體**（`app_mark.png` 就是 iOS 的 `icon_1024.png` 縮放而來）。
 *
 * 第一個畫面上的圖案要與桌面上那顆圖示一致，使用者才對得起來「我點的就是這個 App」
 * ——這也是 iOS 端刻意用 app icon 本體、而不是隨便一個 symbol 的理由。
 *
 * 這裡可以直接用原圖，因為它不會被任何遮罩裁切；桌面那顆圖示則必須重新排版
 * （理由見 `res/mipmap-anydpi-v26/ic_launcher.xml` 的註解）。
 *
 * 圓角比例 22.37% 對齊 iOS 圖示的 continuous 圓角。
 */
@Composable
private fun AppMark() {
    val shape = RoundedCornerShape((96 * 0.2237f).dp)
    Image(
        painter = painterResource(R.drawable.app_mark),
        contentDescription = null,
        modifier = Modifier
            .size(96.dp)
            .clip(shape)
            .border(1.dp, Color.Black.copy(alpha = 0.06f), shape),
    )
}
