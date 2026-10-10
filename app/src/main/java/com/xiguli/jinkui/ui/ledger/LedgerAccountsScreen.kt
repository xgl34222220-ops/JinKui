package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerIds
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.repo.LedgerInputException
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSectionLabel
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkSwitch
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsUi(
    val accounts: List<LedgerAccount>,
    val balances: Map<String, Long>,
    val netWorth: Long,
    val usage: Map<String, Int>,
)

internal fun buildAccountsUi(data: LedgerData): AccountsUi {
    val balances = LedgerMath.balances(data.accounts, data.transactions)
    return AccountsUi(
        accounts = data.accounts.sortedWith(compareBy<LedgerAccount> { it.archived }.thenBy { it.position }),
        balances = balances,
        netWorth = LedgerMath.netWorth(balances),
        usage = LedgerMath.usageCounts(data.transactions),
    )
}

class LedgerAccountsViewModel(private val container: AppContainer) : ViewModel() {
    val ui: StateFlow<AccountsUi> = container.ledger.data
        .map { buildAccountsUi(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildAccountsUi(container.ledger.data.value))

    fun save(account: LedgerAccount, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                container.ledger.saveAccount(account)
                onDone()
            } catch (e: LedgerInputException) {
                container.messages.show(e.message ?: "保存失败")
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun delete(account: LedgerAccount, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                container.ledger.deleteAccount(account.id)
                container.messages.show("已删除「${account.name}」")
                onDone()
            } catch (e: Exception) {
                container.messages.show(e.message ?: "删除失败")
            }
        }
    }
}

@Composable
fun LedgerAccountsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { LedgerAccountsViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val cards by container.repository.cards.collectAsStateWithLifecycle()
    val colors = Jk.colors
    var editing by remember { mutableStateOf<LedgerAccount?>(null) }

    LedgerAccountsContent(
        ui = ui,
        cards = cards,
        onBack = onBack,
        onAdd = { editing = LedgerAccount(LedgerIds.newId(), "", AccountKind.BANK) },
        onEdit = { editing = it },
    )

    editing?.let { account ->
        AccountEditSheet(
            account = account,
            isNew = ui.accounts.none { it.id == account.id },
            usage = ui.usage[account.id] ?: 0,
            bankCards = cards.filter { it.type == CardType.BANK },
            onDismiss = { editing = null },
            onSave = { viewModel.save(it) { editing = null } },
            onDelete = { viewModel.delete(account) { editing = null } },
        )
    }
}

@Composable
private fun AccountEditSheet(
    account: LedgerAccount,
    isNew: Boolean,
    usage: Int,
    bankCards: List<Card>,
    onDismiss: () -> Unit,
    onSave: (LedgerAccount) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Jk.colors
    var name by remember { mutableStateOf(account.name) }
    var kind by remember { mutableStateOf(account.kind) }
    var opening by remember { mutableStateOf(if (account.openingBalance == 0L) "" else Money.plain(account.openingBalance)) }
    var cardId by remember { mutableStateOf(account.cardId) }
    var archived by remember { mutableStateOf(account.archived) }
    var openingError by remember { mutableStateOf<String?>(null) }

    JkSheet(onDismiss = onDismiss, title = if (isNew) "添加账户" else "编辑账户") {
        Column(
            Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                items(AccountKind.entries) { k ->
                    JkChip(k.label, kind == k, {
                        kind = k
                        if (name.isBlank() || AccountKind.entries.any { it.label == name }) name = k.label
                    })
                }
            }
            Spacer(Modifier.height(Space.x1_5))
            JkTextField(value = name, onValueChange = { name = it.take(40) }, label = "名称", placeholder = "例如：招行储蓄卡")
            Spacer(Modifier.height(Space.x1_5))
            JkTextField(
                value = opening,
                onValueChange = {
                    opening = it.take(16)
                    openingError = null
                },
                label = if (kind == AccountKind.CREDIT) "期初余额（欠款填负数，如 -1200）" else "期初余额",
                placeholder = "0",
                keyboardType = KeyboardType.Decimal,
                error = openingError,
            )
            if (kind == AccountKind.BANK || kind == AccountKind.CREDIT) {
                JkSectionLabel("关联卡包里的银行卡（可选）")
                if (bankCards.isEmpty()) {
                    Text("卡包里还没有银行卡。", style = JkType.small, color = colors.textTertiary)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                        item { JkChip("不关联", cardId == null, { cardId = null }) }
                        items(bankCards, key = { it.id }) { card ->
                            JkChip("${card.title} ${card.faceHint}".trim(), cardId == card.id, { cardId = card.id })
                        }
                    }
                }
            }
            if (!isNew) {
                Spacer(Modifier.height(Space.x1))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("停用", style = JkType.body, color = colors.textPrimary)
                        Text("停用后记账时不再出现，历史流水照常统计", style = JkType.caption, color = colors.textTertiary)
                    }
                    JkSwitch(archived, { archived = it })
                }
            }
        }
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            if (!isNew) {
                JkButton(
                    text = if (usage > 0) "有 $usage 笔流水" else "删除",
                    onClick = onDelete,
                    style = ButtonStyle.Danger,
                    enabled = usage == 0,
                    modifier = Modifier.weight(1f),
                )
            }
            JkButton(
                text = "保存",
                onClick = {
                    val cents = if (opening.isBlank()) 0L else Money.parse(opening)
                    if (cents == null) {
                        openingError = "金额格式不对，最多两位小数"
                    } else {
                        onSave(
                            account.copy(
                                name = name,
                                kind = kind,
                                openingBalance = cents,
                                cardId = if (kind == AccountKind.BANK || kind == AccountKind.CREDIT) cardId else null,
                                archived = archived,
                            ),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 账户与余额（无状态）：净资产卡 + 账户分组列表。界面测试直接渲染它。 */
@Composable
fun LedgerAccountsContent(
    ui: AccountsUi,
    cards: List<com.xiguli.jinkui.data.model.Card>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (LedgerAccount) -> Unit,
) {
    val colors = Jk.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        JkTopBar("账户与余额", onBack = onBack) {
            JkIconButton(Icons.Outlined.Add, "添加账户", onAdd)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.x2_5, end = Space.x2_5, bottom = Space.x6),
        ) {
            item {
                JkCard(Modifier.fillMaxWidth()) {
                    Text("净资产", style = JkType.overline, color = colors.textTertiary)
                    RollingAmount(
                        ui.netWorth,
                        JkType.amountLarge,
                        if (ui.netWorth < 0) colors.danger else colors.textPrimary,
                    )
                    Text("余额 = 期初余额 + 全部收支与转账，随流水自动更新", style = JkType.caption, color = colors.textTertiary)
                }
                Spacer(Modifier.height(Space.x2))
            }
            item {
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                    if (ui.accounts.isEmpty()) {
                        Text("还没有账户，点右上角添加。", style = JkType.small, color = colors.textTertiary, modifier = Modifier.padding(Space.x2))
                    }
                    ui.accounts.forEachIndexed { index, account ->
                        if (index > 0) JkDivider(inset = Space.x6 + Space.x05)
                        val linked = account.cardId?.let { id -> cards.firstOrNull { it.id == id } }
                        val balance = ui.balances[account.id] ?: 0L
                        JkRow(
                            title = account.name,
                            subtitle = buildString {
                                append(account.kind.label)
                                if (linked != null) append(" · ").append(linked.title).append(" ").append(linked.faceHint)
                                if (account.archived) append(" · 已停用")
                            },
                            icon = accountIcon(account.kind),
                            onClick = { onEdit(account) },
                            trailing = {
                                Text(
                                    Money.format(balance),
                                    style = JkType.amount,
                                    color = when {
                                        balance < 0 -> colors.danger
                                        account.archived -> colors.textTertiary
                                        else -> colors.textPrimary
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
