package com.megshao.exerciserewards.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle

/**
 * 共用卡片樣式：白底、圓角、柔和淺灰陰影、細邊線。
 * 對應 iOS 的 `CardModifier` / `.cardStyle()`。
 */
@Composable
public fun AppCard(
    modifier: Modifier = Modifier,
    radius: Dp = Tokens.radiusLarge,
    padding: Dp = 18.dp,
    highlighted: Boolean = false,
    background: Color = Tokens.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (highlighted) 10.dp else 5.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            )
            .clip(shape)
            .background(background)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) Tokens.amber else Tokens.line,
                shape = shape,
            )
            .padding(padding),
        content = content,
    )
}

/**
 * 大按鈕、橘色漸層、hit target ≥ 48dp。對應 iOS 的 `PrimaryButtonStyle`。
 *
 * 刻意不用 Material 的 `Button`：它的 elevation／ripple／形狀都得逐項覆寫才對得上設計稿，
 * 自己畫比覆寫短，也不會在 Material 改版時漂掉。
 */
@Composable
public fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val active = enabled && !loading
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(Tokens.shapeMedium)
            .background(if (active) Tokens.primaryGradient else Tokens.disabledGradient)
            .clickableRow(enabled = active, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Box(Modifier.size(width = 10.dp, height = 0.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Box(Modifier.size(width = 8.dp, height = 0.dp))
        }
        Text(text, style = displayStyle(17, FontWeight.Bold), color = Color.White)
    }
}

/** 次要按鈕：白底、邊線。對應 iOS 的 `SecondaryButtonStyle`。 */
@Composable
public fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 44.dp)
            .clip(Tokens.shapeSmall)
            .background(Tokens.card)
            .border(1.dp, Tokens.line2, Tokens.shapeSmall)
            .clickableRow(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Tokens.text, modifier = Modifier.size(16.dp))
            Box(Modifier.size(width = 6.dp, height = 0.dp))
        }
        Text(
            text,
            style = displayStyle(15, FontWeight.SemiBold),
            color = if (enabled) Tokens.text else Tokens.dim,
        )
    }
}

/** 小顆的膠囊按鈕（首頁券列的「去兌換」／「顯示條碼」）。 */
@Composable
public fun PillButton(
    text: String,
    onClick: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .clickableRow(enabled = true, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, style = displayStyle(13, FontWeight.Bold), color = Color.White)
    }
}

/** 狀態徽章。對應 iOS 的 `StatusBadge`。 */
@Composable
public fun StatusBadge(
    text: String,
    foreground: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = foreground)
    }
}

/** 區塊小標（券夾的「可兌換」「已使用」）。 */
@Composable
public fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 4.dp),
        style = displayStyle(16, FontWeight.Bold),
        color = Tokens.text,
    )
}

/** 說明橫幅（暖色底／黃底警示／綠底隱私聲明三種）。 */
@Composable
public fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Tokens.card2,
    foreground: Color = Tokens.muted,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(Tokens.shapeSmall)
            .background(background)
            .padding(12.dp),
    ) {
        Text(text, fontSize = 12.5.sp, color = foreground, lineHeight = 18.sp)
    }
}

/** 載入中。 */
@Composable
public fun LoadingBlock(modifier: Modifier = Modifier, topPadding: Dp = 60.dp) {
    Box(
        modifier = modifier.fillMaxWidth().padding(top = topPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Tokens.primary)
    }
}

/** 錯誤／空狀態。`onRetry` 為 null 時不畫按鈕。 */
@Composable
public fun StateMessage(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    iconTint: Color = Tokens.dim,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "重新載入",
    topPadding: Dp = 60.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = topPadding, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(30.dp))
        Text(
            message,
            fontSize = 13.sp,
            color = Tokens.muted,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
        if (onRetry != null) {
            SecondaryButton(text = retryLabel, onClick = onRetry)
        }
    }
}

/** 帶圓角底色的小圖示方塊（「我的資料」列首）。 */
@Composable
public fun IconBox(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * 示範模式常駐橫幅：讓審查員（與任何誤入的使用者）隨時知道畫面上是範例資料。
 *
 * 截圖模式會把它收起來，讓上架素材的畫面上緣乾淨——真機使用者與審查員都不可能觸發截圖模式
 * （見 `Telemetry.isScreenshotMode`），他們進示範模式時橫幅一定照常出現。
 */
@Composable
public fun DemoModeBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Tokens.text)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "示範模式 · 畫面為範例資料，未連線官方網站",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

/** 讓 Row/Box 可點擊而不帶 Material 的預設外觀。 */
internal fun Modifier.clickableRow(enabled: Boolean, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, onClick = onClick)
