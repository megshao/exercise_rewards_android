package com.megshao.exerciserewards.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megshao.exerciserewards.core.models.TaskState
import com.megshao.exerciserewards.ui.theme.Tokens

// MARK: - TaskState 的 UI 對應
//
// `TaskState.showsUploadCountdown` 定義在 core 的 TaskState 上：那是「官網這個欄位在這個
// 狀態下還有沒有意義」的判斷，屬領域規則而非排版，放在 core 才有單元測試守得住
// （見 TaskStateTest）。這裡只放純排版的對應。

public val TaskState.badgeText: String
    get() = when (this) {
        TaskState.NOT_STARTED -> "尚未開始"
        TaskState.OPEN -> "未上傳"
        TaskState.PENDING_REVIEW -> "審核中"
        TaskState.REDEEMABLE -> "可兌換"
        TaskState.REDEEMED -> "已兌換"
        TaskState.UNKNOWN -> "—"
    }

public val TaskState.badgeForeground: Color
    get() = when (this) {
        TaskState.NOT_STARTED, TaskState.REDEEMED, TaskState.UNKNOWN -> Tokens.dim
        TaskState.OPEN -> Tokens.primaryDark
        TaskState.PENDING_REVIEW -> Tokens.warnText
        TaskState.REDEEMABLE -> Color.White
    }

public val TaskState.badgeBackground: Color
    get() = when (this) {
        TaskState.NOT_STARTED, TaskState.UNKNOWN -> Tokens.disabledBackground
        TaskState.REDEEMED -> Tokens.disabledBackground
        TaskState.OPEN -> Tokens.openBadgeBackground
        TaskState.PENDING_REVIEW -> Tokens.warnBackground
        TaskState.REDEEMABLE -> Tokens.success
    }

/**
 * 狀態徽章。
 *
 * @param hasEnded 上傳窗已經過完的期別。官網對這種卡片照樣回 `NOT_UPLOADED`（→ OPEN），
 *   直接顯示「未上傳」會讓使用者以為還來得及補傳，所以這裡換成「已結束」。
 */
@Composable
public fun TaskStateBadge(
    state: TaskState,
    modifier: Modifier = Modifier,
    hasEnded: Boolean = false,
) {
    if (state == TaskState.OPEN && hasEnded) {
        StatusBadge("已結束", Tokens.dim, Tokens.disabledBackground, modifier)
    } else {
        StatusBadge(state.badgeText, state.badgeForeground, state.badgeBackground, modifier)
    }
}

/**
 * 三段式進度時間軸：上傳 → 審核 → 兌換。
 *
 * 節點依狀態顯示 完成(綠勾) / 進行中(填色圖示) / 未開始(灰框)；連接線在該段完成時轉綠。
 * 對齊設計稿與 iOS 的 `TaskStepper`。
 */
@Composable
public fun TaskStepper(state: TaskState, modifier: Modifier = Modifier) {
    /** 狀態排序：未開始0 未上傳1 審核中2 可兌換3 已兌換4。 */
    val order = when (state) {
        TaskState.NOT_STARTED, TaskState.UNKNOWN -> 0
        TaskState.OPEN -> 1
        TaskState.PENDING_REVIEW -> 2
        TaskState.REDEEMABLE -> 3
        TaskState.REDEEMED -> 4
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StepNode(step = 1, order = order, label = "上傳", icon = Icons.Filled.ArrowUpward, activeColor = Tokens.primary)
        Connector(green = order >= 2, modifier = Modifier.weight(1f))
        StepNode(step = 2, order = order, label = "審核", icon = Icons.Filled.Search, activeColor = Tokens.reviewActive)
        Connector(green = order >= 3, modifier = Modifier.weight(1f))
        StepNode(step = 3, order = order, label = "兌換", icon = Icons.Filled.CardGiftcard, activeColor = Tokens.success)
    }
}

@Composable
private fun Connector(green: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // 對齊節點圓心（節點下方有文字）
            .offset(y = 11.dp)
            .height(3.dp)
            .clip(CircleShape)
            .background(if (green) Tokens.success else Tokens.line2),
    )
}

@Composable
private fun StepNode(
    step: Int,
    order: Int,
    label: String,
    icon: ImageVector,
    activeColor: Color,
) {
    val done = order > step
    val active = order == step
    val pending = !done && !active

    val circleColor = when {
        done -> Tokens.success
        active -> activeColor
        else -> Color.White
    }

    Column(
        modifier = Modifier.width(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(circleColor)
                .then(if (pending) Modifier.border(2.dp, Tokens.line2, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (done) Icons.Filled.Check else icon,
                contentDescription = null,
                tint = if (pending) Tokens.dim else Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (pending) FontWeight.Normal else FontWeight.SemiBold,
            color = if (pending) Tokens.dim else Tokens.text,
        )
    }
}

/**
 * 解析後端的「剩 X 天 Y 小時」倒數字串，並依剩餘時間換算顏色。
 *
 * 對應 iOS 的 `RemainingTime`。**這是排版用的顏色分級，不是領域規則**——
 * 「還能不能上傳」在 core 的 `TaskPeriod.canUpload`。
 */
public object RemainingTime {

    /** 上傳窗總長：每期 7 天。 */
    public const val WINDOW_HOURS: Double = 7 * 24.0

    /** 從「剩 1 天 22 小時」／「剩 22 小時」／「剩 1 天」解析出剩餘小時數；無法解析回 null。 */
    public fun hours(text: String): Double? {
        var days = 0.0
        var hrs = 0.0
        var matched = false
        firstNumber(text, "天")?.let { days = it; matched = true }
        firstNumber(text, "小時")?.let { hrs = it; matched = true }
        return if (matched) days * 24 + hrs else null
    }

    /** 顏色：> 3 天綠、1–3 天橘、< 1 天紅。 */
    public fun color(hours: Double): Color = when {
        hours >= 72 -> Tokens.success
        hours >= 24 -> Tokens.primary
        else -> Tokens.danger
    }

    private fun firstNumber(text: String, unit: String): Double? {
        val unitIndex = text.indexOf(unit)
        if (unitIndex < 0) return null
        val prefix = text.substring(0, unitIndex)
        val digits = StringBuilder()
        for (ch in prefix.reversed()) {
            when {
                ch.isDigit() -> digits.insert(0, ch)
                ch == ' ' -> continue
                digits.isNotEmpty() -> return digits.toString().toDoubleOrNull()
            }
        }
        return digits.toString().toDoubleOrNull()
    }
}

/**
 * 商家色塊 logo，依商家名稱對應設計稿的品牌色；辨識不出的商家用中性灰底。
 *
 * **名稱比對走 [com.megshao.exerciserewards.telemetry.Vendor.of]，這裡不再自己判斷一次**。
 * iOS 端曾經這支與遙測各有一份 `contains` 判斷，結果漂掉了：logo 認得萬家福／樂家康，
 * 遙測卻把它們算成 other。現在只有一份比對表。
 */
@Composable
public fun VendorLogo(vendorName: String, modifier: Modifier = Modifier) {
    val vendor = com.megshao.exerciserewards.telemetry.Vendor.of(vendorName)
    val color = when (vendor) {
        com.megshao.exerciserewards.telemetry.Vendor.FAMILY_MART -> Color(0xFF0A8F4E)
        com.megshao.exerciserewards.telemetry.Vendor.SEVEN_ELEVEN -> Color(0xFFE8501F)
        com.megshao.exerciserewards.telemetry.Vendor.HILIFE -> Color(0xFFC1121F)
        com.megshao.exerciserewards.telemetry.Vendor.PXMART -> Color(0xFFE4002B)
        com.megshao.exerciserewards.telemetry.Vendor.WANJIAFU -> Tokens.primary
        com.megshao.exerciserewards.telemetry.Vendor.OTHER -> Tokens.dim
    }
    /** 認得的商家用固定縮寫（設計稿指定）；認不出來的退成名稱前兩字。 */
    val label = when (vendor) {
        com.megshao.exerciserewards.telemetry.Vendor.FAMILY_MART -> "全家"
        com.megshao.exerciserewards.telemetry.Vendor.SEVEN_ELEVEN -> "7-11"
        com.megshao.exerciserewards.telemetry.Vendor.HILIFE -> "萊爾富"
        com.megshao.exerciserewards.telemetry.Vendor.PXMART -> "全聯"
        else -> vendorName.take(2)
    }

    Box(
        modifier = modifier
            .size(46.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(13.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}
