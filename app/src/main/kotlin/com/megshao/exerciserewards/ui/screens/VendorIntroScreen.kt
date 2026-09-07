package com.megshao.exerciserewards.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megshao.exerciserewards.AppContainer
import com.megshao.exerciserewards.core.models.VendorIntro
import com.megshao.exerciserewards.core.models.VendorIntroCategory
import com.megshao.exerciserewards.core.models.VendorIntroLayout
import com.megshao.exerciserewards.telemetry.AnalyticsValue
import com.megshao.exerciserewards.telemetry.Endpoint
import com.megshao.exerciserewards.telemetry.ScreenName
import com.megshao.exerciserewards.telemetry.Telemetry
import com.megshao.exerciserewards.telemetry.TelemetryIssue
import com.megshao.exerciserewards.ui.appViewModel
import com.megshao.exerciserewards.ui.components.LoadingBlock
import com.megshao.exerciserewards.ui.components.StateMessage
import com.megshao.exerciserewards.ui.components.clickableRow
import com.megshao.exerciserewards.ui.theme.Tokens
import com.megshao.exerciserewards.ui.theme.displayStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 廠商可兌換商品：從兌換頁每一列的「兌換品項」進來，列出該通路的商品分類與品項。
 *
 * 官網那幾頁有兩種版型（見 core 的 `VendorIntroParser`），這裡照著資料自己有的東西畫：
 * - 有逐項清單（`items`）→ 可展開的分類卡，卡上顯示官網標的「N 項」。
 * - 只有舉例（`examples`）→ 分類名 + 一行舉例文字，**明講「舉例」**，
 *   不假裝那是完整清單。
 *
 * 搜尋會同時比對分類名與品項名；只有舉例的分類則比對舉例文字。
 */
@Composable
public fun VendorIntroScreen(introPath: String, vendorName: String) {
    val context = LocalContext.current
    val viewModel = appViewModel(key = "intro-$introPath") { container, appContext ->
        VendorIntroViewModel(container, appContext, introPath)
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        // 不帶 introPath，也不帶商家名（官網文字一律不進遙測）。
        Telemetry.screenAppeared(context, ScreenName.VENDOR_INTRO)
        viewModel.load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val intro = state.intro
        when {
            state.isLoading && intro == null -> LoadingBlock()

            state.errorMessage != null && intro == null -> StateMessage(
                icon = Icons.Filled.Warning,
                iconTint = Tokens.danger,
                message = state.errorMessage.orEmpty(),
                onRetry = viewModel::load,
            )

            intro != null -> {
                intro.subtitle?.let {
                    Text(it, fontSize = 12.5.sp, color = Tokens.muted, lineHeight = 19.sp)
                }

                SearchField(query = state.query, onQueryChange = viewModel::updateQuery)

                val categories = state.filteredCategories
                if (categories.isEmpty()) {
                    Text(
                        "找不到符合「${state.query}」的商品。",
                        fontSize = 13.sp,
                        color = Tokens.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    )
                } else {
                    categories.forEach { category ->
                        CategoryCard(
                            category = category,
                            // 搜尋中一律展開：使用者要看的就是命中的那幾項，
                            // 讓他再一張一張點開等於白搜。
                            isExpanded = state.isSearching || state.expanded.contains(category.id),
                            canCollapse = !state.isSearching,
                            isSearching = state.isSearching,
                            onToggle = { viewModel.toggle(category.id) },
                        )
                    }
                }

                if (intro.notices.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("兌換注意事項", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tokens.text)
                        intro.notices.forEach {
                            Text(it, fontSize = 11.5.sp, color = Tokens.muted, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.size(20.dp))
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Tokens.card)
            .border(1.dp, Tokens.line2, CircleShape)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = Tokens.dim,
            modifier = Modifier.size(16.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(fontSize = 14.sp, color = Tokens.text),
                cursorBrush = SolidColor(Tokens.primary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text("搜尋商品名稱", fontSize = 14.sp, color = Tokens.dim)
            }
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = "清除搜尋",
                tint = Tokens.dim,
                modifier = Modifier
                    .size(18.dp)
                    .clickableRow(enabled = true) { onQueryChange("") },
            )
        }
    }
}

/** 一張分類卡。逐項版可展開／收合；只有舉例的版本沒有東西可展開，直接把舉例攤在卡上。 */
@Composable
private fun CategoryCard(
    category: VendorIntroCategory,
    isExpanded: Boolean,
    canCollapse: Boolean,
    isSearching: Boolean,
    onToggle: () -> Unit,
) {
    val hasItems = category.items.isNotEmpty()
    val rotation by animateFloatAsState(if (isExpanded) 0f else -90f, label = "chevron")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Tokens.shapeLarge)
            .background(if (category.isAllItems) Tokens.card2 else Tokens.card)
            .border(
                1.dp,
                if (category.isAllItems) Tokens.amber else Tokens.line,
                Tokens.shapeLarge,
            )
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hasItems && canCollapse) {
                        Modifier.clickableRow(enabled = true, onClick = onToggle)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                category.name,
                style = displayStyle(15, FontWeight.Bold),
                color = Tokens.text,
                modifier = Modifier.weight(1f, fill = false),
            )

            countText(category, isSearching)?.let { count ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Tokens.warnBackground)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(count, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Tokens.warnText)
                }
            }

            Box(Modifier.weight(1f))

            if (hasItems) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = Tokens.primary,
                    modifier = Modifier.size(16.dp).rotate(rotation),
                )
            }
        }

        if (hasItems && isExpanded) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Tokens.line)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                category.items.forEach {
                    Text(it, fontSize = 13.sp, color = Tokens.text, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        category.examples?.let { examples ->
            Text(
                examples,
                fontSize = 12.5.sp,
                color = Tokens.muted,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            )
            Text(
                "以上為舉例，實際品項以門市現場為準。",
                fontSize = 11.sp,
                color = Tokens.dim,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 搜尋時顯示的是**篩選後**的筆數，跟官網標的總數不是同一件事，
 * 因此只在沒有搜尋時才拿官網的 `statedCount` 來顯示。
 */
private fun countText(category: VendorIntroCategory, isSearching: Boolean): String? {
    if (category.items.isEmpty()) return null
    if (isSearching) return "${category.items.size} 項符合"
    return "${category.statedCount ?: category.items.size} 項"
}

// MARK: - ViewModel

public class VendorIntroViewModel(
    private val container: AppContainer,
    private val context: Context,
    private val introPath: String,
) : ViewModel() {

    public data class State(
        val intro: VendorIntro? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val query: String = "",
        /** 使用者手動展開的分類 id。預設全部收合，跟官網一樣。 */
        val expanded: Set<String> = emptySet(),
    ) {
        val trimmedQuery: String get() = query.trim()
        val isSearching: Boolean get() = trimmedQuery.isNotEmpty()

        /**
         * 依搜尋字串篩選：命中分類名時整個分類留下，否則只留下命中的品項。
         * 只有舉例的分類則比對舉例文字。
         */
        val filteredCategories: List<VendorIntroCategory>
            get() {
                val categories = intro?.categories ?: return emptyList()
                val needle = trimmedQuery
                if (needle.isEmpty()) return categories

                return categories.mapNotNull { category ->
                    if (category.name.contains(needle, ignoreCase = true)) return@mapNotNull category

                    val matched = category.items.filter { it.contains(needle, ignoreCase = true) }
                    if (matched.isNotEmpty()) return@mapNotNull category.copy(items = matched)

                    if (category.examples?.contains(needle, ignoreCase = true) == true) category else null
                }
            }
    }

    private val _state = MutableStateFlow(State())
    public val state: StateFlow<State> = _state.asStateFlow()

    public fun updateQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    public fun toggle(id: String) {
        val expanded = _state.value.expanded
        _state.value = _state.value.copy(
            expanded = if (expanded.contains(id)) expanded - id else expanded + id,
        )
    }

    public fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val loaded = container.environment.value.redeem.vendorIntro(introPath)
                _state.value = _state.value.copy(intro = loaded, isLoading = false)
                reportLayoutDrift(loaded)
            } catch (error: Throwable) {
                // 這一頁是純資訊，載不到不影響兌換本身，因此只提示、不擋流程。
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "無法載入可兌換商品清單，請稍後再試。",
                )
                Telemetry.reportFailure(context, error, Endpoint.VENDOR_INTRO)
            }
        }
    }

    /**
     * 版型認不出來時的警報。
     *
     * **為什麼要在這裡發**：`VendorIntroParser` 兩種版型都對不上時不再丟例外，
     * 而是退到純文字給出最小可用結果（讓使用者至少看得到東西）。那個決定的代價是
     * **失敗不再自動變成例外**，所以「官網換版型了」這件事必須由呼叫端自己回報，
     * 否則就變成一個沒人知道的降級。
     *
     * 只送 layout 這個封閉列舉，不送標題、分類名或任何官網文字。
     */
    private fun reportLayoutDrift(intro: VendorIntro) {
        if (intro.layout != VendorIntroLayout.UNRECOGNISED) return
        Telemetry.recordNonFatal(
            context,
            TelemetryIssue.VENDOR_INTRO_LAYOUT,
            Endpoint.VENDOR_INTRO,
            extras = mapOf("layout" to AnalyticsValue.Code(intro.layout.raw)),
        )
    }
}
