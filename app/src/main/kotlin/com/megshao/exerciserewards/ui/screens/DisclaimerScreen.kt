package com.megshao.exerciserewards.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megshao.exerciserewards.ui.components.AppCard
import com.megshao.exerciserewards.ui.components.IconBox
import com.megshao.exerciserewards.ui.components.PrimaryButton
import com.megshao.exerciserewards.ui.components.clickableRow
import com.megshao.exerciserewards.ui.theme.Tokens

/**
 * 首次啟動的免責聲明同意畫面。擋在個資填寫之前。
 *
 * **為什麼要主動同意而不是被動告知**：這支 App 會把身分證號送到官方網站、顯示的任務與
 * 券況也全部來自官方網站，使用者必須在填第一個欄位之前就知道「出了事該找誰」——
 * 所以改成必須主動勾選才能繼續。
 *
 * **這個畫面同時是遙測的揭露點**：按下同意時才會呼叫 `Telemetry.configure()`——
 * 那是整支 App 第一次執行 Firebase 程式碼的時機。所以「匿名使用統計」那一條
 * **不能從這裡拿掉**，拿掉就變成「使用者沒讀到就開始送」。
 *
 * **文案用語的界線**（改文案前務必讀）：
 * - 不可寫「不儲存個資」。三欄個資確實存在裝置上（Keystore 加密），寫「不儲存」與實作不符，
 *   而且會和隱私權政策、App 內其他文案互相矛盾。準確的說法是
 *   「只存在你的手機，開發者收不到」。
 * - 不可寫「串接官方 API」。官方網站沒有公開 API，本 App 是以一般瀏覽器的身分
 *   提交同一份網頁表單。寫成 API 會暗示某種官方授權或合作關係。
 */
@Composable
public fun DisclaimerScreen(onAgree: () -> Unit) {
    var hasAgreed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Tokens.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "使用前請先確認",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tokens.text,
                )
                Text(
                    "這是個人開發的非官方工具。開始使用前，有三件事想先跟你說清楚。",
                    fontSize = 14.sp,
                    color = Tokens.muted,
                    lineHeight = 21.sp,
                )
            }

            DisclaimerSection(
                icon = Icons.Outlined.Info,
                iconTint = Tokens.warnText,
                iconBackground = Tokens.warnBackground,
                title = "這不是官方 App",
                bullets = listOf(
                    "本 App 由個人開發，與運動部及任何政府機關沒有隸屬、合作、贊助或授權關係，也不代表活動主辦單位。",
                    "活動規則、資格認定、審核結果與獎品權益，一律以官方公告為準。",
                ),
            )

            DisclaimerSection(
                icon = Icons.Filled.Lock,
                iconTint = Tokens.success,
                iconBackground = Tokens.successBackground,
                title = "你的資料只在你手機裡",
                bullets = listOf(
                    "開發者沒有任何伺服器，收不到也看不到你的身分證號、生日與手機號碼。",
                    "這三個欄位以 AES-256-GCM 加密保存在這支手機（金鑰由 Android Keystore 保管），不進雲端備份、不隨裝置轉移，可隨時在「我的資料」一鍵永久刪除。",
                    "你按下登入時，資料由這支手機直接送到官方網站，中間不經過開發者或任何其他服務。",
                    "為了修 bug，App 會把匿名的操作紀錄與當機報告送給 Google Firebase：看了哪個畫面、哪一步失敗、當機在哪。裡面沒有你的個資，也沒有你上傳的截圖與券碼。不想送可以到「我的資料 › 安全與隱私」關掉。",
                ),
            )

            DisclaimerSection(
                icon = Icons.Filled.Sync,
                iconTint = Tokens.primary,
                iconBackground = Tokens.card2,
                title = "畫面上的內容來自官方網站",
                bullets = listOf(
                    "本 App 只是幫你把官方網站的資料整理得比較好看。任務狀態、審核結果、券的效期與可否使用，全部由官方網站決定，本 App 不做任何判定，也無法代為修改。",
                    "官方網站改版或維護時，本 App 可能會顯示不正確或暫時無法使用。",
                    "活動或帳號有問題，請直接聯繫官方客服——本 App 無法代為處理，也查不到你的帳號狀態。",
                    "App 本身的問題（畫面錯誤、當機）才需要找開發者，聯絡方式在「我的資料」頁。",
                ),
            )
        }

        // 底部固定的同意列
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Tokens.card)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("disclaimer.agree")
                    .clickableRow(enabled = true) { hasAgreed = !hasAgreed },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    if (hasAgreed) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = if (hasAgreed) "已勾選" else "未勾選",
                    tint = if (hasAgreed) Tokens.primary else Tokens.dim,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    "我已閱讀並理解上述說明，了解這是非官方工具、活動權益以官方公告為準，並同意傳送不含個資的匿名使用統計（可隨時關閉）。",
                    fontSize = 13.sp,
                    color = Tokens.text,
                    lineHeight = 19.sp,
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth().then(
                    if (hasAgreed) Modifier else Modifier.clip(Tokens.shapeMedium),
                ),
            ) {
                PrimaryButton(
                    text = "同意並開始使用",
                    onClick = onAgree,
                    enabled = hasAgreed,
                )
            }
        }
    }
}

@Composable
private fun DisclaimerSection(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    bullets: List<String>,
) {
    AppCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBox(icon, iconTint, iconBackground, size = 32.dp)
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
        }
        Box(Modifier.size(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bullets.forEach { bullet ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("・", fontSize = 13.sp, color = Tokens.dim)
                    Text(bullet, fontSize = 13.sp, color = Tokens.muted, lineHeight = 19.sp)
                }
            }
        }
    }
}
