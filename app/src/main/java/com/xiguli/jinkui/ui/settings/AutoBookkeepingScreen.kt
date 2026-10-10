package com.xiguli.jinkui.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.xiguli.jinkui.core.auto.AutoStatus
import com.xiguli.jinkui.core.auto.ListenerRebinder
import com.xiguli.jinkui.core.auto.PaymentNotificationParser
import com.xiguli.jinkui.core.auto.UnrecognizedNotice
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.components.BotanicalCard
import com.xiguli.jinkui.ui.components.BotanicalLayout
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkTextField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AppShortcut
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.VerifiedUser
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.jinkui.core.auto.PaymentNotificationService
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSectionLabel
import com.xiguli.jinkui.ui.components.JkSwitch
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space

class AutoBookkeepingActions(
    val onBack: () -> Unit = {},
    val onToggle: (Boolean) -> Unit = {},
    val onAutoSave: (Boolean) -> Unit = {},
    val onAccess: () -> Unit = {},
    val onInbox: () -> Unit = {},
    /** 监听没连上时：请求系统重新绑定。 */
    val onReconnect: () -> Unit = {},
    /** 打开金匮的“应用详情”（自启动、省电策略在那里）。 */
    val onAppSettings: () -> Unit = {},
    /** 未识别通知 → “这是一笔记账”。 */
    val onRecord: (UnrecognizedNotice) -> Unit = {},
    val onClearUnrecognized: () -> Unit = {},
)

/** 是否已在系统设置里给金匮授予了“通知使用权”。 */
fun notificationAccessGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** 打开系统的“通知使用权”设置（Android 11 起直接到金匮这一项）。 */
fun notificationAccessIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, PaymentNotificationService::class.java).flattenToString(),
            )
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

/** 小米 / 红米 / POCO（HyperOS、MIUI）：后台限制更严，提示放在显眼的位置。 */
val isXiaomiFamily: Boolean
    get() = Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }

@Composable
fun AutoBookkeepingScreen(onBack: () -> Unit, onInbox: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val pending by container.ledger.pending.collectAsStateWithLifecycle()
    val status by container.autoStatus.flow.collectAsStateWithLifecycle()
    val unrecognized by container.unrecognized.items.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(notificationAccessGranted(context)) }
    var explain by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<UnrecognizedNotice?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = notificationAccessGranted(context)
        container.autoStatus.refreshDay()
        runCatching { container.unrecognized.refresh() }
        container.ensureListener()
    }

    fun openAccess() {
        container.lock.holdForExternalActivity()
        val intent = notificationAccessIntent(context)
        runCatching { context.startActivity(intent) }
            .recoverCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure { container.messages.show("打不开系统设置，请在“设置 → 通知 → 通知使用权”里找到金匮") }
    }

    fun openAppSettings() {
        container.lock.holdForExternalActivity()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        runCatching { context.startActivity(intent) }
            .onFailure { container.messages.show("打不开应用设置，请在系统“设置 → 应用 → 金匮”里调整") }
    }

    AutoBookkeepingContent(
        enabled = settings.autoBookkeeping,
        autoSave = settings.autoSave,
        accessGranted = granted,
        pendingCount = pending.size,
        actions = AutoBookkeepingActions(
            onBack = onBack,
            onToggle = { on ->
                container.settings.update { it.copy(autoBookkeeping = on, autoSave = if (on) it.autoSave else false) }
                if (on && !granted) explain = true
                if (on) container.ensureListener()
            },
            onAutoSave = { on -> container.settings.update { it.copy(autoSave = on) } },
            onAccess = { openAccess() },
            onInbox = onInbox,
            onReconnect = {
                ListenerRebinder.toggleComponent(context)
                container.messages.show("已请求系统重新连接，几秒后回到这里看看状态")
            },
            onAppSettings = { openAppSettings() },
            onRecord = { recording = it },
            onClearUnrecognized = { container.unrecognized.clear() },
        ),
        status = status,
        unrecognized = unrecognized,
        xiaomi = isXiaomiFamily,
    )

    if (explain) {
        JkDialog(
            title = "还差一步：授予通知使用权",
            onDismiss = { explain = false },
            confirmText = "去系统设置",
            dismissText = "稍后",
            onConfirm = {
                explain = false
                openAccess()
            },
        ) {
            Text(
                "自动记账要读取收付款通知，需要你在系统设置的“通知使用权”里打开“金匮自动记账”。这是系统的特殊权限，只有你能打开，随时可以关闭。",
                style = JkType.body,
            )
        }
    }

    recording?.let { notice ->
        RecordNoticeDialog(
            notice = notice,
            onDismiss = { recording = null },
            onConfirm = { type, amount ->
                container.recordUnrecognized(notice, type, amount)
                container.messages.show("已放进“待确认”")
                recording = null
            },
        )
    }
}

/** “这是一笔记账”：未识别通知里的数字已经抹掉，让用户补上金额与方向。 */
@Composable
private fun RecordNoticeDialog(notice: UnrecognizedNotice, onDismiss: () -> Unit, onConfirm: (TxType, Long) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    val cents = Money.parse(amount)?.takeIf { it in 1..Money.MAX_CENTS }
    JkDialog(
        title = "记成一笔待确认",
        onDismiss = onDismiss,
        confirmText = "放进待确认",
        confirmEnabled = cents != null,
        onConfirm = { cents?.let { onConfirm(type, it) } },
    ) {
        Text(notice.shape, style = JkType.small, color = Jk.colors.textSecondary)
        JkSegmented(listOf(TxType.EXPENSE to "支出", TxType.INCOME to "收入"), type, { type = it })
        JkTextField(
            value = amount,
            onValueChange = { amount = it },
            placeholder = "金额（元）",
            keyboardType = KeyboardType.Decimal,
        )
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun whenText(at: Long): String =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(timeFormat)

private fun sourceLabel(packageName: String): String =
    PaymentNotificationParser.packages[packageName]?.label ?: packageName

/** 自动记账设置（无状态）。 */
@Composable
fun AutoBookkeepingContent(
    enabled: Boolean,
    autoSave: Boolean,
    accessGranted: Boolean,
    pendingCount: Int,
    actions: AutoBookkeepingActions,
    status: AutoStatus.Snapshot = AutoStatus.Snapshot(),
    unrecognized: List<UnrecognizedNotice> = emptyList(),
    xiaomi: Boolean = false,
) {
    val colors = Jk.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(title = "自动记账", onBack = actions.onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2_5)
                .padding(top = Space.x05, bottom = Space.x5),
        ) {
            BotanicalCard(Modifier.fillMaxWidth(), layout = BotanicalLayout.SPRIG) {
                Column(Modifier.padding(Space.x2_5)) {
                    Text("付完款，金匮帮你先记个草稿", style = JkType.section, color = colors.textPrimary)
                    Spacer(Modifier.height(Space.x1))
                    Text(
                        "打开后，金匮会读取微信、支付宝、云闪付和常见银行 App 的收付款通知，识别出金额、商户和时间，放进“待确认”。你确认（或修改）后才记进账本。默认关闭。",
                        style = JkType.small,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(Space.x2))
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "自动记账",
                    subtitle = "读取收付款通知，生成待确认记录",
                    icon = Icons.Outlined.NotificationsActive,
                    trailing = { JkSwitch(enabled, actions.onToggle) },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "待确认",
                    subtitle = "确认、修改或忽略识别出来的收支",
                    icon = Icons.Outlined.Inbox,
                    value = if (pendingCount > 0) "$pendingCount 笔" else "没有",
                    onClick = actions.onInbox,
                )
            }

            JkSectionLabel("运行状态")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "通知使用权",
                    subtitle = if (accessGranted) "已在系统设置里授予，可以随时收回" else "还没授予：系统不会把通知交给金匮",
                    icon = Icons.Outlined.VerifiedUser,
                    value = if (accessGranted) "已授予" else null,
                    trailing = if (accessGranted) null else ({ JkButton("去授权", actions.onAccess, compact = true) }),
                    onClick = actions.onAccess,
                )
                JkDivider(inset = Space.x6 + Space.x05)
                val listening = when {
                    !enabled -> "开关已关闭"
                    !accessGranted -> "等待授权"
                    status.live -> "已连接" + if (status.connectedAt > 0) "（${whenText(status.connectedAt)} 起）" else ""
                    status.disconnectedAt > 0 -> "未连接（${whenText(status.disconnectedAt)} 断开）"
                    else -> "未连接"
                }
                JkRow(
                    title = "监听服务",
                    subtitle = listening,
                    icon = Icons.Outlined.Sync,
                    trailing = if (enabled && accessGranted && !status.live) ({ JkButton("重新连接", actions.onReconnect, compact = true, style = ButtonStyle.Secondary) }) else null,
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "最近收到",
                    subtitle = if (status.lastSeenAt > 0) "${sourceLabel(status.lastSeenPackage)} 的通知 · ${whenText(status.lastSeenAt)}" else "还没收到过支持的应用的通知",
                    icon = Icons.Outlined.Schedule,
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "今天",
                    subtitle = "识别 ${status.recognizedToday} 笔 · 未识别 ${status.unrecognizedToday} 条",
                    icon = Icons.Outlined.Insights,
                )
            }
            Spacer(Modifier.height(Space.x1_5))
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "自动入账",
                    subtitle = "识别出来的直接记进账本，不再等你确认（解锁金匮时记入）。默认关闭；识别偶尔会出错，建议先用“待确认”观察一段时间",
                    icon = Icons.Outlined.Bolt,
                    trailing = { JkSwitch(autoSave, actions.onAutoSave, enabled = enabled) },
                )
            }

            JkSectionLabel(if (xiaomi) "小米 / 红米手机请先设置这几项" else "收不到通知？检查这几项")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Space.x2, vertical = Space.x1_5)) {
                for ((i, line) in GUIDE.withIndex()) {
                    Row(Modifier.padding(vertical = Space.x05)) {
                        Text("${i + 1}.", style = JkType.small.copy(fontWeight = FontWeight.Bold), color = colors.accent)
                        Spacer(Modifier.width(Space.x1))
                        Text(line, style = JkType.small, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(Space.x1))
                JkButton("打开金匮的应用设置", actions.onAppSettings, style = ButtonStyle.Secondary, compact = true, icon = Icons.Outlined.OpenInNew)
            }

            JkSectionLabel("粘贴通知文字测试识别")
            ParseTester()

            if (unrecognized.isNotEmpty()) {
                JkSectionLabel("未识别通知（最近 ${unrecognized.size} 条）")
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                    unrecognized.forEachIndexed { i, notice ->
                        if (i > 0) JkDivider(inset = Space.x2)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.x2, vertical = Space.x1_5),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${notice.source.label} · ${whenText(notice.postedAt)}", style = JkType.caption, color = colors.textTertiary)
                                Spacer(Modifier.height(2.dp))
                                Text(notice.shape, style = JkType.small, color = colors.textPrimary)
                            }
                            Spacer(Modifier.width(Space.x1))
                            JkButton("这是一笔记账", { actions.onRecord(notice) }, compact = true, style = ButtonStyle.Secondary)
                        }
                    }
                    JkDivider()
                    JkRow(title = "清空未识别通知", icon = Icons.Outlined.DeleteOutline, danger = true, onClick = actions.onClearUnrecognized)
                }
                Text(
                    "这里只保存通知的“文字形状”：数字都换成了 #，加密存在这台手机上，最多 20 条。",
                    style = JkType.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = Space.x05, top = Space.x1),
                )
            }

            JkSectionLabel("隐私与权限")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Space.x2, vertical = Space.x1_5)) {
                for (line in PRIVACY) {
                    Row(Modifier.padding(vertical = Space.x05)) {
                        Text("·", style = JkType.small, color = colors.accent)
                        Spacer(Modifier.width(Space.x1))
                        Text(line, style = JkType.small, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    }
                }
            }

            JkSectionLabel("快捷记账")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Space.x2, vertical = Space.x1_5)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AppShortcut, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Space.x2))
                    Text(
                        "长按桌面上的金匮图标，选“记一笔”：解锁后直接打开记账面板。也可以把“记一笔”拖到桌面上单独放一个图标。",
                        style = JkType.small,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 测试用的几个来源（包名只用来选识别规则）。 */
private val TEST_SOURCES = listOf(
    "com.tencent.mm" to "微信",
    "com.eg.android.AlipayGphone" to "支付宝",
    "com.unionpay" to "云闪付",
    "cmb.pb" to "银行",
)

/** 粘贴一条通知的文字，看金匮能不能认出来（不保存、不记账）。 */
@Composable
private fun ParseTester() {
    val colors = Jk.colors
    var source by rememberSaveable { mutableStateOf(TEST_SOURCES.first().first) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    val outcome = remember(source, title, text) {
        if (title.isBlank() && text.isBlank()) null else PaymentNotificationParser.analyze(source, title, listOf(text), System.currentTimeMillis())
    }
    JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.x2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x05)) {
            for ((pkg, label) in TEST_SOURCES) JkChip(label, source == pkg, { source = pkg })
        }
        Spacer(Modifier.height(Space.x1))
        JkTextField(value = title, onValueChange = { title = it }, placeholder = "通知标题（例如“微信支付”）")
        Spacer(Modifier.height(Space.x1))
        JkTextField(value = text, onValueChange = { text = it }, placeholder = "通知正文（例如“已支付¥12.00”）", singleLine = false, minLines = 2)
        Spacer(Modifier.height(Space.x1_5))
        val (line, tone) = when (outcome) {
            null -> "粘贴后这里会显示识别结果；只在本机计算，不保存" to colors.textTertiary
            is PaymentNotificationParser.Outcome.Payment -> outcome.payment.let { p ->
                "认出了：${if (p.type == TxType.EXPENSE) "支出" else "收入"} ${Money.format(p.amount)}" + (if (p.merchant.isNotEmpty()) " · ${p.merchant}" else "") to colors.accent
            }
            is PaymentNotificationParser.Outcome.Unrecognized -> "没认出来：${outcome.reason}" to colors.danger
            is PaymentNotificationParser.Outcome.NotPayment -> "不会记账：${outcome.reason}" to colors.textSecondary
        }
        Text(line, style = JkType.bodyStrong, color = tone, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

private val GUIDE = listOf(
    "自启动：系统“设置 → 应用 → 金匮 → 自启动”打开（HyperOS / MIUI 在“应用设置 → 应用管理”里）。",
    "省电策略选「无限制」：同一页的“省电策略”里选“无限制”，不要让系统在后台停掉金匮。",
    "锁定后台：在最近任务里下拉（或长按）金匮的卡片，点小锁，清理后台时不会被关掉。",
    "微信：微信“我 → 设置 → 消息通知 → 新消息通知”打开，并打开“通知显示消息详情”，否则通知里看不到金额。",
    "微信支付：在“服务通知”里打开“微信支付”的通知（收付款通知由它发出）；支付宝在“我的 → 设置 → 通知”里打开“收付款通知”。",
)

private val PRIVACY = listOf(
    "只处理微信、支付宝、云闪付和工行、建行、农行、中行、交行、招行、邮储、浦发、中信、光大、民生、兴业、平安、华夏、广发手机银行的通知，其他应用的通知一律不看。",
    "只取出金额、方向、商户和时间，通知原文不保存。金匮锁着时先用设备密钥库加密暂存，解锁后转进加密数据库；待确认不进加密备份，确认后的流水和手动记的一样备份。",
    "全部在这台手机上完成，金匮没有联网权限，什么都不会上传。",
    "“通知使用权”是系统的特殊权限：只有你能在系统设置里打开，随时可以关闭。关掉上面的开关后，金匮不再处理任何通知。",
    "没认出来的支付类通知只保存“文字形状”（数字换成 #），加密存在本机，最多 20 条，可以随时清空。",
)
