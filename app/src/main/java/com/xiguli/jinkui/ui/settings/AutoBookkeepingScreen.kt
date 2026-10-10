package com.xiguli.jinkui.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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

@Composable
fun AutoBookkeepingScreen(onBack: () -> Unit, onInbox: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val pending by container.ledger.pending.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(notificationAccessGranted(context)) }
    var explain by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = notificationAccessGranted(context) }

    fun openAccess() {
        container.lock.holdForExternalActivity()
        val intent = notificationAccessIntent(context)
        runCatching { context.startActivity(intent) }
            .recoverCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure { container.messages.show("打不开系统设置，请在“设置 → 通知 → 通知使用权”里找到金匮") }
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
            },
            onAutoSave = { on -> container.settings.update { it.copy(autoSave = on) } },
            onAccess = { openAccess() },
            onInbox = onInbox,
        ),
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
}

/** 自动记账设置（无状态）。 */
@Composable
fun AutoBookkeepingContent(
    enabled: Boolean,
    autoSave: Boolean,
    accessGranted: Boolean,
    pendingCount: Int,
    actions: AutoBookkeepingActions,
) {
    val colors = Jk.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(title = "自动记账", onBack = actions.onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2)
                .padding(top = Space.x05, bottom = Space.x5),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.card)
                    .background(colors.hero)
                    .padding(Space.x2_5),
            ) {
                Text("付完款，金匮帮你先记个草稿", style = JkType.section, color = colors.textPrimary)
                Spacer(Modifier.height(Space.x1))
                Text(
                    "打开后，金匮会读取微信、支付宝、云闪付和常见银行 App 的收付款通知，识别出金额、商户和时间，放进“待确认”。你确认（或修改）后才记进账本。默认关闭。",
                    style = JkType.small,
                    color = colors.textSecondary,
                )
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
                    title = "通知使用权",
                    subtitle = if (accessGranted) "已在系统设置里授予，可以随时收回" else "需要在系统设置里手动授予",
                    icon = Icons.Outlined.VerifiedUser,
                    value = if (accessGranted) "已授予" else "未授予",
                    onClick = actions.onAccess,
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
            Spacer(Modifier.height(Space.x1_5))
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "自动入账",
                    subtitle = "识别出来的直接记进账本，不再等你确认（解锁金匮时记入）。默认关闭；识别偶尔会出错，建议先用“待确认”观察一段时间",
                    icon = Icons.Outlined.Bolt,
                    trailing = { JkSwitch(autoSave, actions.onAutoSave, enabled = enabled) },
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

private val PRIVACY = listOf(
    "只处理微信、支付宝、云闪付和工行、建行、农行、中行、交行、招行、邮储、浦发、中信、光大、民生、兴业、平安、华夏、广发手机银行的通知，其他应用的通知一律不看。",
    "只取出金额、方向、商户和时间，通知原文不保存。金匮锁着时先用设备密钥库加密暂存，解锁后转进加密数据库；待确认不进加密备份，确认后的流水和手动记的一样备份。",
    "全部在这台手机上完成，金匮没有联网权限，什么都不会上传。",
    "“通知使用权”是系统的特殊权限：只有你能在系统设置里打开，随时可以关闭。关掉上面的开关后，金匮不再处理任何通知。",
    "小米 / 红米（HyperOS / MIUI）等系统可能会在后台停掉通知监听：如果收不到，请在系统的“应用设置 → 金匮 → 省电策略”里选“无限制”。",
)
