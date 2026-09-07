package com.megshao.exerciserewards.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle

/**
 * 單一欄位輸入元件。敏感欄位在「未聚焦且已有值、且未展開」時以遮罩顯示，
 * 點一下即可聚焦編輯真實內容。對應 iOS 的 `ProfileField`。
 */
@Composable
public fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    sensitive: Boolean = false,
    isRevealed: Boolean = true,
    maskedText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    /** 是否要蓋上遮罩（僅敏感欄位、未展開、未聚焦、且已有值時）。 */
    val showMasked = sensitive && !isRevealed && !focused && value.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Tokens.muted)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Tokens.shapeSmall)
                .background(Tokens.card)
                .border(
                    width = if (focused) 1.5.dp else 1.dp,
                    color = if (focused) Tokens.primary else Tokens.line2,
                    shape = Tokens.shapeSmall,
                )
                .clickableRow(enabled = true) { focusRequester.requestFocus() }
                .padding(14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    // UI 測試用欄位標籤定位輸入框（對應 iOS 的 accessibilityIdentifier）。
                    // placeholder 是另一個 Text 節點，對它 performTextInput 會失敗。
                    .testTag("field.$label")
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    // 顯式指定文字色：不靠主題，避免哪天深色模式漏鎖時變成白底白字。
                    color = if (showMasked) Color.Transparent else Tokens.text,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(Tokens.primary),
                singleLine = true,
            )
            if (showMasked) {
                Text(maskedText.orEmpty(), fontSize = 15.sp, color = Tokens.text)
            } else if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, fontSize = 15.sp, color = Tokens.dim)
            }
        }
    }
}

/**
 * 出生日期的純資料處理：ISO 字串 ↔ 年月日、民國換算、每月天數。
 *
 * **全部用格里曆固定計算，不依賴裝置的行事曆／語系設定**，顯示文字一律繁體中文
 * ——使用者把系統切成民國曆或英文時，這裡的行為完全不變。
 */
public object BirthDate {

    public data class Parts(val year: Int, val month: Int, val day: Int)

    /** 可選年份範圍對齊官網：民國元年（1912）至 2009。 */
    public val years: List<Int> = (1912..2009).toList()
    public val defaultParts: Parts = Parts(1990, 1, 1)

    /** 解析 `yyyy-MM-dd`；格式不符或日期不存在（例如 2001-02-30）皆回 null。 */
    public fun parse(iso: String): Parts? {
        val pieces = iso.split("-")
        if (pieces.size != 3 || pieces[0].length != 4 || pieces[1].length != 2 || pieces[2].length != 2) {
            return null
        }
        val y = pieces[0].toIntOrNull() ?: return null
        val m = pieces[1].toIntOrNull() ?: return null
        val d = pieces[2].toIntOrNull() ?: return null
        if (y !in years || m !in 1..12 || d !in 1..daysIn(y, m)) return null
        return Parts(y, m, d)
    }

    public fun iso(parts: Parts): String = "%04d-%02d-%02d".format(parts.year, parts.month, parts.day)

    /** 「1990 年 5 月 20 日」 */
    public fun display(parts: Parts): String = "${parts.year} 年 ${parts.month} 月 ${parts.day} 日"

    /** 「民國 79 年」——1912 為民國元年。 */
    public fun rocYearText(year: Int): String = "民國 ${year - 1911} 年"

    public fun daysIn(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeap(year)) 29 else 28
        else -> 31
    }

    public fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

/**
 * 出生日期欄位：點一下開啟「年／月／日」三欄滾輪，可切換西元／民國，全繁體中文。
 * 對外仍以 ISO `yyyy-MM-dd` 字串存回 Profile（與後端契約一致）。
 */
@Composable
public fun BirthDateField(
    isoDate: String,
    onIsoDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val parts = BirthDate.parse(isoDate)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("出生日期", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Tokens.muted)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("field.birthDate")
                .clip(Tokens.shapeSmall)
                .background(Tokens.card)
                .border(1.dp, Tokens.line2, Tokens.shapeSmall)
                .clickableRow(enabled = true) { showPicker = true }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = Tokens.primary,
                modifier = Modifier.size(18.dp),
            )
            if (parts != null) {
                Text(BirthDate.display(parts), fontSize = 15.sp, color = Tokens.text)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Tokens.disabledBackground)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        BirthDate.rocYearText(parts.year),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Tokens.muted,
                    )
                }
            } else {
                Text("請選擇出生日期", fontSize = 15.sp, color = Tokens.dim)
            }
            Box(Modifier.weight(1f))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Tokens.chevron,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (showPicker) {
        BirthDatePickerDialog(
            initial = parts ?: BirthDate.defaultParts,
            onDismiss = { showPicker = false },
            onConfirm = {
                onIsoDateChange(BirthDate.iso(it))
                showPicker = false
            },
        )
    }
}

/**
 * 出生日期滾輪：年／月／日三欄，可切換西元／民國。
 *
 * **刻意不用系統的 DatePicker**：Material 的日曆會跟著裝置語系走，裝置是英文時整個
 * 日曆變英文，而且沒有民國年。這裡自己畫三個滾輪，所有文字自備繁體中文。
 */
@Composable
private fun BirthDatePickerDialog(
    initial: BirthDate.Parts,
    onDismiss: () -> Unit,
    onConfirm: (BirthDate.Parts) -> Unit,
) {
    var useRoc by remember { mutableStateOf(true) }
    var year by remember { mutableStateOf(initial.year) }
    var month by remember { mutableStateOf(initial.month) }
    var day by remember { mutableStateOf(initial.day) }

    // 換月／換年後把超出當月天數的日期收回來（例如 3/31 → 2 月時變 2/28）。
    LaunchedEffect(year, month) {
        val maxDay = BirthDate.daysIn(year, month)
        if (day > maxDay) day = maxDay
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(Tokens.shapeLarge)
                .background(Tokens.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("選擇出生日期", style = displayStyle(17, FontWeight.Bold), color = Tokens.text)

            // 民國／西元切換
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Tokens.shapeSmall)
                    .background(Tokens.disabledBackground)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                EraTab("民國", selected = useRoc, modifier = Modifier.weight(1f)) { useRoc = true }
                EraTab("西元", selected = !useRoc, modifier = Modifier.weight(1f)) { useRoc = false }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WheelPicker(
                    items = BirthDate.years,
                    selected = year,
                    onSelected = { year = it },
                    label = { if (useRoc) "民國 ${it - 1911} 年" else "西元 $it 年" },
                    modifier = Modifier.weight(1.4f),
                )
                WheelPicker(
                    items = (1..12).toList(),
                    selected = month,
                    onSelected = { month = it },
                    label = { "$it 月" },
                    modifier = Modifier.weight(1f),
                )
                WheelPicker(
                    items = (1..BirthDate.daysIn(year, month)).toList(),
                    selected = day,
                    onSelected = { day = it },
                    label = { "$it 日" },
                    modifier = Modifier.weight(1f),
                )
            }

            // 下方摘要固定同時顯示兩種年份，讓使用者一眼確認選對了。
            Text(
                "${BirthDate.display(BirthDate.Parts(year, month, day))}（${BirthDate.rocYearText(year)}）",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tokens.muted,
                modifier = Modifier.fillMaxWidth(),
            )

            PrimaryButton(text = "完成", onClick = { onConfirm(BirthDate.Parts(year, month, day)) })
            SecondaryButton(text = "取消", onClick = onDismiss, fillWidth = true)
        }
    }
}

@Composable
private fun EraTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Tokens.card else Color.Transparent)
            .clickableRow(enabled = true, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Tokens.text else Tokens.muted,
        )
    }
}

/**
 * 極簡滾輪：LazyColumn + snap，中間那一格就是選取值。
 *
 * Compose 沒有內建的 wheel picker（iOS 有 `.pickerStyle(.wheel)`），自己畫比引一個
 * 第三方元件庫划算——這裡只需要「捲動、吸附、回報中間那一項」三件事。
 */
@Composable
private fun <T> WheelPicker(
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 36.dp
    val visibleCount = 5
    val initialIndex = items.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    /**
     * 中央那一項的 index。
     *
     * **這裡踩過坑（2026-09-07 實測）**：原本寫 `firstVisibleItemIndex + visibleCount / 2`，
     * 但 contentPadding 已經把第一項推到中央那一格了，於是又多加兩格——畫面上的高亮框停在
     * 「民國 79 年」，下方摘要卻顯示 1992 年（民國 81 年），而且一開螢幕就會把選取值改掉。
     *
     * 改成直接問 layoutInfo「哪一項的中心離視窗中心最近」：不管 contentPadding 怎麼給、
     * 每項多高，這個算法都是對的。
     */
    val centerIndex by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - viewportCenter) }
                ?.index
                ?: initialIndex
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                items.getOrNull(centerIndex)?.let(onSelected)
            }
        }
    }

    Box(modifier = modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        // 中央選取框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Tokens.card2),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items) { item ->
                val isCenter = item == items.getOrNull(centerIndex)
                Box(
                    modifier = Modifier.fillMaxWidth().height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(item),
                        fontSize = if (isCenter) 15.sp else 14.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter) Tokens.text else Tokens.dim,
                    )
                }
            }
        }
    }
}

/**
 * 共用 6 格 OTP 輸入元件（樣式對齊設計稿）。
 *
 * 底層蓋一個透明的輸入框承接鍵盤輸入與 focus，上層畫出方格樣式；只允許數字、最多 6 碼。
 */
@Composable
public fun OtpCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    val length = 6
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            repeat(length) { index ->
                val character = code.getOrNull(index)?.toString().orEmpty()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (character.isEmpty()) Tokens.background else Tokens.card2)
                        .border(
                            width = if (character.isEmpty()) 1.dp else 1.5.dp,
                            color = if (character.isEmpty()) Tokens.line2 else Tokens.primary,
                            shape = RoundedCornerShape(13.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(character, style = displayStyle(20, FontWeight.Bold), color = Tokens.text)
                }
            }
        }

        BasicTextField(
            value = code,
            onValueChange = { raw -> onCodeChange(raw.filter(Char::isDigit).take(length)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("field.otp")
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            // 透明字與透明游標：真正的字由上面那排方格畫。
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            singleLine = true,
        )
    }
}

/** 鍵盤收起（送出前呼叫，避免鍵盤蓋住結果）。 */
@Composable
public fun rememberKeyboardDismiss(): () -> Unit {
    val focusManager = LocalFocusManager.current
    return remember(focusManager) { { focusManager.clearFocus() } }
}
