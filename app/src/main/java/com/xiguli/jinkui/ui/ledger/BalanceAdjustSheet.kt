package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.BalanceAdjustMode
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space

/** 这个账户最近的一次余额调整（可撤销）。 */
data class RecentAdjust(val txId: String, val delta: Long, val label: String)

/**
 * 1.5.0-alpha01：修改余额（参考一木记账、钱迹的“修改余额”，只借鉴交互）。
 * 填账户现在应有的余额（信用卡 / 花呗填“现在欠多少”），默认不计入收支；也可以把差额记成收入或支出。
 * 下面列出这个账户最近的余额调整，点“撤销”删掉那一笔。
 */
@Composable
fun BalanceAdjustSheet(
    account: LedgerAccount,
    current: Long,
    recent: List<RecentAdjust>,
    onDismiss: () -> Unit,
    onSave: (target: Long, mode: BalanceAdjustMode) -> Unit,
    onUndo: (txId: String) -> Unit = {},
) {
    val colors = Jk.colors
    val credit = account.kind == AccountKind.CREDIT
    // 信用账户按“现在欠多少”填正数（欠 1200 填 1200）；有溢缴款时填负数
    val shown = if (credit) -current else current
    var text by remember { mutableStateOf(Money.plain(shown)) }
    var mode by remember { mutableStateOf(BalanceAdjustMode.NOT_COUNTED) }
    var error by remember { mutableStateOf<String?>(null) }
    val parsed = Money.parse(text.trim().ifEmpty { "0" })
    val target = parsed?.let { if (credit) -it else it }
    val delta = target?.let { it - current }

    JkSheet(onDismiss = onDismiss, title = "修改余额") {
        Text(
            "${account.name} · " + if (credit) "现在欠 ${Money.format(-current)}" else "现在余额 ${Money.format(current)}",
            style = JkType.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(Space.x1_5))
        JkTextField(
            value = text,
            onValueChange = {
                text = it.take(16)
                error = null
            },
            label = if (credit) "现在欠多少（元）" else "改成多少（元）",
            placeholder = "0",
            keyboardType = KeyboardType.Decimal,
            error = error,
        )
        Spacer(Modifier.height(Space.x1))
        Text(
            when {
                delta == null -> "金额格式不对，最多两位小数"
                delta == 0L -> "和现在一样，不用改"
                else -> "将记一笔余额调整 ${if (delta > 0) "+" else "-"}${Money.format(kotlin.math.abs(delta))}"
            },
            style = JkType.small,
            color = if (delta == null) colors.danger else colors.textSecondary,
        )
        Spacer(Modifier.height(Space.x1_5))
        Text("这笔差额怎么算", style = JkType.small.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
        Spacer(Modifier.height(Space.x05))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            JkChip("不计入收支", mode == BalanceAdjustMode.NOT_COUNTED, { mode = BalanceAdjustMode.NOT_COUNTED })
            JkChip("记为收入 / 支出", mode == BalanceAdjustMode.AS_INCOME_EXPENSE, { mode = BalanceAdjustMode.AS_INCOME_EXPENSE })
        }
        Text(
            if (mode == BalanceAdjustMode.NOT_COUNTED) "只把余额改对，本月收入、支出与统计都不变（推荐）"
            else "多出来的记一笔“其他收入”，少了的记一笔“其他支出”，会算进本月收支",
            style = JkType.caption,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = Space.x05),
        )
        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(Space.x1_5))
            JkCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.x2, vertical = Space.x1)) {
                Text("最近的余额调整", style = JkType.overline, color = colors.textTertiary)
                for (r in recent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("余额调整 ${if (r.delta >= 0) "+" else "-"}${Money.format(kotlin.math.abs(r.delta))}", style = JkType.bodyStrong, color = colors.textPrimary)
                            Text(r.label, style = JkType.caption, color = colors.textTertiary)
                        }
                        JkButton("撤销", { onUndo(r.txId) }, style = ButtonStyle.Secondary, compact = true)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.x2))
        JkButton(
            text = "保存",
            onClick = {
                if (target == null) error = "金额格式不对，最多两位小数"
                else onSave(target, mode)
            },
            enabled = delta != 0L,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
