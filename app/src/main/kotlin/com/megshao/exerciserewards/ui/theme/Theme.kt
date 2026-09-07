package com.megshao.exerciserewards.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 設計系統：白底亮橘・運動風。
 *
 * Token 來源與 iOS 端 `App/Sources/Views/Theme.swift` **同一份 9 畫面設計稿的 CSS variables**，
 * 兩邊的色值必須逐字一致——那是「UI 拉齊」在程式碼裡唯一可以檢查的地方。
 * 改色請兩邊一起改。
 *
 * **本 App 鎖淺色單一主題**（與 iOS 的 `preferredColorScheme(.light)` 一致）：
 * 設計稿是白底，跟著系統切深色會出現白底白字。
 */
public object Tokens {

    // MARK: - Colors（與 iOS Theme.Colors 逐一對應）

    /** 背景 #F5F6F8 */
    public val background: Color = Color(0xFFF5F6F8)

    /** 卡片 #FFFFFF */
    public val card: Color = Color(0xFFFFFFFF)

    /** 卡片次要（暖色底）#FFF7EE */
    public val card2: Color = Color(0xFFFFF7EE)

    /** 主色橘 #FF5A1F */
    public val primary: Color = Color(0xFFFF5A1F)

    /** 深橘 #E8480F */
    public val primaryDark: Color = Color(0xFFE8480F)

    /** 亮黃 #FFC24D */
    public val amber: Color = Color(0xFFFFC24D)

    /** 文字 #1A1D24 */
    public val text: Color = Color(0xFF1A1D24)

    /** 次要文字 #697485 */
    public val muted: Color = Color(0xFF697485)

    /** 淡 #9AA1AC */
    public val dim: Color = Color(0xFF9AA1AC)

    /** 成功綠 #12A150 */
    public val success: Color = Color(0xFF12A150)
    public val successBackground: Color = Color(0xFFE6F6EE)
    public val successText: Color = Color(0xFF186C3E)
    public val successBorder: Color = Color(0xFFB7E5CB)

    /** 危險 #E5443B */
    public val danger: Color = Color(0xFFE5443B)
    public val dangerBackground: Color = Color(0xFFFDECEB)

    /** 邊線 */
    public val line: Color = Color(0xFFECEDF1)
    public val line2: Color = Color(0xFFE0E3E9)

    /** 審核中警示（黃底文字） */
    public val warnText: Color = Color(0xFF9A6400)
    public val warnBackground: Color = Color(0xFFFFF4D6)

    /** 尚未開始（灰底灰字） */
    public val disabledBackground: Color = Color(0xFFEEF0F3)

    /** 未開始／已使用卡片的底色 #FAFBFC */
    public val mutedCard: Color = Color(0xFFFAFBFC)

    /** 列尾的 chevron #C3C8D0 */
    public val chevron: Color = Color(0xFFC3C8D0)

    /** 「未上傳」徽章底 #FFECE1 */
    public val openBadgeBackground: Color = Color(0xFFFFECE1)

    /** 圖示底色（橘系）#FFF2E8 */
    public val iconTintBackground: Color = Color(0xFFFFF2E8)

    /** 審核節點的進行中色 #E6A100 */
    public val reviewActive: Color = Color(0xFFE6A100)

    /** 主色 CTA 漸層（左上到右下，橘 → 深橘） */
    public val primaryGradient: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFFFF7A2B), Color(0xFFFF5109)),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    public val disabledGradient: Brush = Brush.linearGradient(listOf(dim, dim))

    // MARK: - Radii

    public val radiusSmall: androidx.compose.ui.unit.Dp = 14.dp
    public val radiusMedium: androidx.compose.ui.unit.Dp = 18.dp
    public val radiusLarge: androidx.compose.ui.unit.Dp = 22.dp
    public val radiusXLarge: androidx.compose.ui.unit.Dp = 26.dp

    public val shapeSmall: RoundedCornerShape = RoundedCornerShape(radiusSmall)
    public val shapeMedium: RoundedCornerShape = RoundedCornerShape(radiusMedium)
    public val shapeLarge: RoundedCornerShape = RoundedCornerShape(radiusLarge)
}

/**
 * 標題字級。
 *
 * **與 iOS 的一個已知差異**：iOS 端 `Theme.displayFont` 用的是 SF Rounded（設計稿的
 * Baloo 2 圓體替代品），Android 沒有對應的系統圓體。這裡先用系統預設 sans 的粗體，
 * 視覺重量對得上、字形不同。要完全一致得打包一份字型（Baloo 2 有 OFL 授權），
 * 那是設計稿定案後的事。
 */
public fun displayStyle(size: Int, weight: FontWeight = FontWeight.Bold): TextStyle =
    TextStyle(fontSize = size.sp, fontWeight = weight)

public fun bodyStyle(size: Int, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontSize = size.sp, fontWeight = weight)

private val LightColors = lightColorScheme(
    primary = Tokens.primary,
    onPrimary = Color.White,
    secondary = Tokens.amber,
    background = Tokens.background,
    onBackground = Tokens.text,
    surface = Tokens.card,
    onSurface = Tokens.text,
    surfaceVariant = Tokens.card2,
    onSurfaceVariant = Tokens.muted,
    error = Tokens.danger,
    outline = Tokens.line2,
)

private val AppTypography = Typography(
    bodyLarge = bodyStyle(15),
    bodyMedium = bodyStyle(13),
    bodySmall = bodyStyle(12),
    titleLarge = displayStyle(20, FontWeight.ExtraBold),
    titleMedium = displayStyle(16),
    titleSmall = displayStyle(14, FontWeight.SemiBold),
)

/**
 * **鎖淺色**：不接 `isSystemInDarkTheme()`。設計為白底單一主題，
 * 跟著系統切深色會出現白底白字（iOS 端同樣鎖 `.light`）。
 */
@Composable
public fun ExerciseRewardsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
