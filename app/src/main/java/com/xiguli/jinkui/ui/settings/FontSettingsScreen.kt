package com.xiguli.jinkui.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.jinkui.data.repo.FontImportException
import com.xiguli.jinkui.data.repo.FontMode
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkBadge
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkSectionLabel
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.launch

/** 字体页的操作；默认空实现，方便截图测试。 */
class FontSettingsActions(
    val onBack: () -> Unit = {},
    val onSelect: (FontMode) -> Unit = {},
    val onImport: () -> Unit = {},
    val onReset: () -> Unit = {},
)

/** 设置 → 外观 → 字体。 */
@Composable
fun FontSettingsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            try {
                val name = container.fonts.import(uri)
                container.settings.update {
                    it.copy(fontMode = FontMode.CUSTOM, customFontName = name, customFontStamp = System.currentTimeMillis())
                }
                container.messages.show("已换成「$name」")
            } catch (e: FontImportException) {
                container.messages.show(e.message ?: "导入失败")
            } catch (e: Exception) {
                container.messages.show("导入失败")
            } finally {
                importing = false
            }
        }
    }
    FontSettingsContent(
        mode = settings.fontMode,
        customName = settings.customFontName.takeIf { it.isNotEmpty() && container.fonts.hasCustom },
        importing = importing,
        actions = FontSettingsActions(
            onBack = onBack,
            onSelect = { mode ->
                if (mode == FontMode.CUSTOM && !container.fonts.hasCustom) {
                    container.lock.holdForExternalActivity()
                    picker.launch(arrayOf("*/*"))
                } else {
                    container.settings.update { it.copy(fontMode = mode) }
                }
            },
            onImport = {
                if (!importing) {
                    container.lock.holdForExternalActivity()
                    picker.launch(arrayOf("*/*"))
                }
            },
            onReset = {
                container.fonts.reset()
                container.settings.update {
                    it.copy(
                        fontMode = if (it.fontMode == FontMode.CUSTOM) FontMode.SYSTEM else it.fontMode,
                        customFontName = "",
                        customFontStamp = System.currentTimeMillis(),
                    )
                }
                container.messages.show("已移除导入的字体，恢复为系统字体")
            },
        ),
    )
}

/** 字体页内容（无状态）。整页本身就是预览：选中哪种字体，这一页（和整个金匮）立即换成它。 */
@Composable
fun FontSettingsContent(
    mode: FontMode,
    customName: String?,
    importing: Boolean,
    actions: FontSettingsActions,
) {
    val colors = Jk.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(title = "字体", onBack = actions.onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2_5)
                .padding(top = Space.x05, bottom = Space.x5),
        ) {
            // 预览
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.x2_5)) {
                Text("本月支出", style = JkType.overline, color = colors.textTertiary)
                Text("¥ 3,862.40", style = JkType.amountHero, color = colors.textPrimary)
                Spacer(Modifier.height(Space.x05))
                Text("2026年10月11日　12:30　日均 ¥ 386.24", style = JkType.date, color = colors.textSecondary)
                Spacer(Modifier.height(Space.x1_5))
                Text("金匮把卡片和账本都锁在这台手机里。", style = JkType.body, color = colors.textPrimary)
                Text("ABC abc 0123456789 •••• 5972", style = JkType.digitsSmall, color = colors.textTertiary)
            }

            JkSectionLabel("字体")
            JkCard(Modifier.fillMaxWidth().selectableGroup(), padding = PaddingValues(vertical = Space.x05)) {
                FontOption(
                    title = FontMode.SYSTEM.label,
                    subtitle = "中文、正文和数字都用系统字体；在手机主题里换的字体（例如小米 / 红米的 HyperOS 字体）也会生效",
                    selected = mode == FontMode.SYSTEM,
                    badge = "默认",
                    onClick = { actions.onSelect(FontMode.SYSTEM) },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                FontOption(
                    title = FontMode.ROUNDED.label,
                    subtitle = "金额、日期、卡号用圆角方正的 Oxanium 数字，其余跟随系统字体",
                    selected = mode == FontMode.ROUNDED,
                    onClick = { actions.onSelect(FontMode.ROUNDED) },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                FontOption(
                    title = FontMode.CUSTOM.label,
                    subtitle = if (customName != null) "「$customName」用于全部文字" else "还没有导入。选一个 .ttf 或 .otf 字体文件",
                    selected = mode == FontMode.CUSTOM && customName != null,
                    onClick = { actions.onSelect(FontMode.CUSTOM) },
                )
            }
            Spacer(Modifier.height(Space.x2))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                JkButton(
                    text = if (importing) "正在导入…" else if (customName != null) "换一个字体文件" else "导入字体文件",
                    onClick = actions.onImport,
                    style = ButtonStyle.Secondary,
                    icon = Icons.Outlined.FileOpen,
                    loading = importing,
                    modifier = Modifier.weight(1f),
                )
                if (customName != null) {
                    JkButton(
                        text = "移除",
                        onClick = actions.onReset,
                        style = ButtonStyle.Ghost,
                        icon = Icons.Outlined.RestartAlt,
                    )
                }
            }
            Text(
                "字号跟随系统的“字体大小”设置（支持 1.3 倍及以上）。导入的字体只复制到金匮的私有目录，不上传、不进加密备份；" +
                    "字体里没有的字（例如只含英文数字的字体遇到中文）会自动用系统字体显示。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x1_5),
            )
        }
    }
}

@Composable
private fun FontOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, badge: String? = null) {
    val colors = Jk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.x2, vertical = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textTertiary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(Space.x2))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = JkType.bodyStrong, color = colors.textPrimary)
                if (badge != null) {
                    Spacer(Modifier.width(Space.x1))
                    JkBadge(badge)
                }
            }
            Text(subtitle, style = JkType.small, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
