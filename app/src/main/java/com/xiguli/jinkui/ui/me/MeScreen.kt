package com.xiguli.jinkui.ui.me

import com.xiguli.jinkui.ui.theme.jkSurface

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BrandMark
import com.xiguli.jinkui.ui.components.BotanicalCard
import com.xiguli.jinkui.ui.components.BotanicalLayout
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.MintGlyph
import com.xiguli.jinkui.ui.components.MintIcon
import com.xiguli.jinkui.ui.components.pressScale
import com.xiguli.jinkui.ui.components.rememberJkPress
import com.xiguli.jinkui.ui.generator.GeneratorSheet
import com.xiguli.jinkui.ui.nav.LocalTabBarInset
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkFonts
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** “我的”头图上的两个数。 */
@Immutable
data class MeUi(
    /** 从第一笔记账到今天的天数（含当天）；还没记过为 null。 */
    val days: Int?,
    val records: Int,
    val cards: Int,
    val issues: Int = 0,
    val version: String = "",
    /** 回收站里的卡片数（1.4.0-alpha04 起）。 */
    val trash: Int = 0,
)

fun buildMeUi(data: LedgerData, cards: Int, today: LocalDate, zone: ZoneId, version: String = ""): MeUi {
    val first = data.transactions.minOfOrNull { it.occurredAt }?.let { LedgerMath.dateOf(it, zone) }
    val days = first?.let { (ChronoUnit.DAYS.between(it, today) + 1).toInt().coerceAtLeast(1) }
    return MeUi(days, data.transactions.size, cards, version = version)
}

class MeActions(
    val onRecurring: () -> Unit = {},
    val onBudget: () -> Unit = {},
    val onAssets: () -> Unit = {},
    val onAccounts: () -> Unit = {},
    val onCategories: () -> Unit = {},
    val onOrganize: () -> Unit = {},
    val onBackup: () -> Unit = {},
    val onLock: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onHealth: () -> Unit = {},
    val onGenerator: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onImportHistory: () -> Unit = {},
    val onAuto: () -> Unit = {},
    /** 欠款 / 账单（1.4 起）。 */
    val onDebts: () -> Unit = {},
    /** 报销（1.4.0-alpha04 起）。 */
    val onReimburse: () -> Unit = {},
    /** 规则与模板（1.4.0-alpha04 起）。 */
    val onRules: () -> Unit = {},
    /** 卡包回收站（1.4.0-alpha04 起）。 */
    val onTrash: () -> Unit = {},
)

@Composable
fun MeScreen(actions: MeActions) {
    val container = LocalContainer.current
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val cards by container.repository.cards.collectAsStateWithLifecycle()
    val trash by container.repository.trash.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val zone = ZoneId.systemDefault()
    val ui = remember(data, cards.size, trash.size) { buildMeUi(data, cards.size, LocalDate.now(zone), zone, version).copy(trash = trash.size) }
    var showGenerator by remember { mutableStateOf(false) }
    MeContent(
        ui,
        MeActions(
            onRecurring = actions.onRecurring,
            onBudget = actions.onBudget,
            onAssets = actions.onAssets,
            onAccounts = actions.onAccounts,
            onCategories = actions.onCategories,
            onOrganize = actions.onOrganize,
            onBackup = actions.onBackup,
            onLock = { container.vault.lock() },
            onSettings = actions.onSettings,
            onHealth = actions.onHealth,
            onGenerator = { showGenerator = true },
            onImport = actions.onImport,
            onImportHistory = actions.onImportHistory,
            onAuto = actions.onAuto,
            onDebts = actions.onDebts,
            onReimburse = actions.onReimburse,
            onRules = actions.onRules,
            onTrash = actions.onTrash,
        ),
    )
    if (showGenerator) GeneratorSheet(onDismiss = { showGenerator = false })
}

/** “我的”（无状态）：植物插画头图（应用字标、记账天数与条数）→ 4 × 2 功能宫格（账本常用）→ 账本工具 / 卡包 / 数据与安全 / 关于。 */
@Composable
fun MeContent(ui: MeUi, actions: MeActions) {
    val colors = Jk.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Space.x2)
            .padding(top = Space.x2, bottom = LocalTabBarInset.current + Space.x2),
    ) {
        Box(Modifier.fillMaxWidth()) {
            BotanicalCard(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp),
                layout = BotanicalLayout.BOTH,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        // 左右让出叶丛的位置，下边让出远山与叶尖：文字只落在中间那块干净的浅薄荷上
                        .padding(top = 48.dp, bottom = 52.dp, start = 56.dp, end = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("金匮", style = JkType.title.copy(fontWeight = FontWeight.Bold), color = colors.accent, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(Space.x1))
                    val first = if (ui.days == null) "今天开始记第一笔" else "坚持记账的第 ${ui.days} 天"
                    Text(first, style = JkType.bodyStrong.copy(fontFamily = JkFonts.numeral, letterSpacing = 1.sp), color = colors.textSecondary, textAlign = TextAlign.Center)
                    for (line in listOf("已记录 ${ui.records} 条账单", "卡包 ${ui.cards} 张")) {
                        Text(
                            line,
                            style = JkType.bodyStrong.copy(fontFamily = JkFonts.numeral, letterSpacing = 1.sp),
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            // 1.4.0-alpha03：换成与桌面图标同一枚 B 款标志（原来是线条“匣子”字标）
            BrandMark(size = 72.dp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
        }
        Spacer(Modifier.height(Space.x2))
        FeatureGrid(actions)
        Spacer(Modifier.height(Space.x2))
        // 1.5.0-alpha01：按“账本工具 / 卡包 / 数据与安全 / 关于”分组（参考钱迹设置页与一木记账“我的”）；常用的都在上面宫格里
        RowGroup("账本工具") {
            MeRow(MintGlyph.ADD_NOTE, "规则与模板", "按关键词自动分类；常记的一笔一点就记", actions.onRules)
            JkDivider(inset = 56.dp)
            MeRow(MintGlyph.RECURRING, "导入记录", "每次导入了多少笔，可整批撤销", actions.onImportHistory)
            JkDivider(inset = 56.dp)
            MeRow(MintGlyph.CARDS, "账户与余额", "添加、停用账户，修改余额", actions.onAccounts)
        }
        Spacer(Modifier.height(Space.x2))
        RowGroup("卡包") {
            MeRow(MintGlyph.KEY, "密码生成器", "生成不容易被猜到的密码", actions.onGenerator)
            JkDivider(inset = 56.dp)
            MeRow(MintGlyph.SHIELD, "安全体检", "弱密码、重复密码与快到期的卡", actions.onHealth)
            JkDivider(inset = 56.dp)
            MeRow(MintGlyph.TAG, "标签整理", "给卡片的标签改名、合并", actions.onOrganize)
            JkDivider(inset = 56.dp)
            MeRow(MintGlyph.CARDS, "回收站", if (ui.trash > 0) "${ui.trash} 张删除的卡片，30 天内可以还原" else "删除的卡片保留 30 天，可以还原", actions.onTrash)
        }
        Spacer(Modifier.height(Space.x2))
        RowGroup("数据与安全") {
            MeRow(MintGlyph.BACKUP, "加密备份与恢复", "导出 / 导入 .jkbak，只用主密码加密", actions.onBackup)
            JkDivider(inset = 56.dp)
            MeRow(MintGlyph.LOCK, "立即锁定", "清掉内存里的明文，回到解锁页", actions.onLock)
            JkDivider(inset = 56.dp)
            MeRow(MintGlyph.SETTINGS, "设置", "解锁方式、外观与字体、提醒", actions.onSettings)
        }
        Spacer(Modifier.height(Space.x2))
        RowGroup("关于") {
            MeRow(MintGlyph.INFO, "关于金匮", "全程离线，不申请联网权限" + if (ui.version.isNotEmpty()) " · ${ui.version}" else "", null)
        }
    }
}

private data class Feature(val glyph: MintGlyph, val label: String, val onClick: () -> Unit)

@Composable
private fun FeatureGrid(actions: MeActions) {
    val colors = Jk.colors
    val features = listOf(
        // 1.5.0-alpha01：宫格只放账本最常用的 8 个（参考一木记账“我的”）；标签、备份、设置挪到下面分组里
        Feature(MintGlyph.ASSETS, "资产账户", actions.onAssets),
        Feature(MintGlyph.BILL, "借钱欠款", actions.onDebts),
        Feature(MintGlyph.BUDGET, "预算", actions.onBudget),
        Feature(MintGlyph.RECURRING, "周期记账", actions.onRecurring),
        Feature(MintGlyph.CATEGORY, "分类管理", actions.onCategories),
        Feature(MintGlyph.BILL, "报销", actions.onReimburse),
        Feature(MintGlyph.ADD_NOTE, "导入账单", actions.onImport),
        Feature(MintGlyph.RECURRING, "自动记账", actions.onAuto),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(colors.surface)
            .padding(vertical = Space.x1_5, horizontal = Space.x05),
    ) {
        for (row in features.chunked(4)) {
            Row(Modifier.fillMaxWidth()) {
                for (f in row) {
                    val press = rememberJkPress(0.94f)
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 88.dp)
                            .pressScale(press)
                            .clip(Radius.control)
                            .clickable(interactionSource = press.interaction, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = f.onClick)
                            .padding(vertical = Space.x1_5),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        MintIcon(f.glyph, size = 32.dp, tint = colors.textPrimary, filled = true)
                        Spacer(Modifier.height(Space.x1))
                        Text(f.label, style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = colors.textPrimary, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowGroup(title: String, content: @Composable () -> Unit) {
    val colors = Jk.colors
    Column(
        Modifier
            .fillMaxWidth()
            .jkSurface(Jk.surfaces.card, Radius.card)
            .padding(vertical = Space.x1),
    ) {
        Text(
            title,
            style = JkType.small.copy(fontWeight = FontWeight.Medium),
            color = colors.textTertiary,
            modifier = Modifier
                .padding(horizontal = Space.x2, vertical = Space.x1)
                .semantics { heading() },
        )
        content()
    }
}

@Composable
private fun MeRow(glyph: MintGlyph, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    val colors = Jk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.x2, vertical = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MintIcon(glyph, size = 24.dp, tint = colors.textPrimary)
        Spacer(Modifier.width(Space.x2))
        Column(Modifier.weight(1f)) {
            Text(title, style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = JkType.caption, color = colors.textTertiary)
        }
        if (onClick != null) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(22.dp).offset(x = 4.dp))
        }
    }
}
