package com.xiguli.jinkui.ui.ledger

import androidx.compose.runtime.Immutable
import com.xiguli.jinkui.data.ledger.BudgetProgress
import com.xiguli.jinkui.data.ledger.EntryDefaults
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerClock
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.Summary
import com.xiguli.jinkui.data.ledger.TxFilter
import com.xiguli.jinkui.data.ledger.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 流水列表里的一行，所有要显示的文字都在后台线程拼好。
 * 列表滚动时只做布局与绘制，不在组合里查表、格式化时间或金额。
 */
@Immutable
data class TxRowUi(
    val id: String,
    val type: TxType,
    val title: String,
    val subtitle: String,
    /** 分类图标键；转账为 null（用转账图标）。 */
    val iconKey: String?,
    val amountText: String,
    val tx: LedgerTransaction,
)

/** 一天的分组头与这一天的流水。 */
@Immutable
data class DayUi(
    val date: LocalDate,
    val label: String,
    val weekday: String,
    val totals: String,
    val rows: List<TxRowUi>,
    /** 当日支出 / 收入（分），日历视图按它着色。 */
    val expense: Long = 0L,
    val income: Long = 0L,
)

/** 月度视图需要的一切，在后台线程一次算好。 */
@Immutable
data class LedgerUi(
    val month: YearMonth,
    val summary: Summary,
    val days: List<DayUi>,
    val totalBudget: BudgetProgress?,
    val accounts: List<LedgerAccount>,
    val accountsById: Map<String, LedgerAccount>,
    val categoriesById: Map<String, LedgerCategory>,
    val filterActive: Boolean,
    val monthCount: Int,
    val unreadable: Int,
    /** 流水里最晚的月份，允许月份切换翻到将来日期的记录。 */
    val latestMonth: YearMonth?,
    /** 日均支出；将来的月份为 null。 */
    val dailyAverage: Long?,
    /** 第一次在后台算好之前为 true：此时不显示空状态，免得一闪而过。 */
    val loading: Boolean = false,
    /** “快速记一笔”的常用支出分类（最近用得多的在前）。 */
    val quickCategories: List<LedgerCategory> = emptyList(),
    /** 今天的日期（日历视图标出今天）。 */
    val today: LocalDate? = null,
) {
    companion object {
        fun placeholder(month: YearMonth) = LedgerUi(
            month = month,
            summary = Summary.ZERO,
            days = emptyList(),
            totalBudget = null,
            accounts = emptyList(),
            accountsById = emptyMap(),
            categoriesById = emptyMap(),
            filterActive = false,
            monthCount = 0,
            unreadable = 0,
            latestMonth = null,
            dailyAverage = null,
            loading = true,
        )
    }
}

fun buildLedgerUi(
    data: LedgerData,
    month: YearMonth,
    filter: TxFilter,
    zone: ZoneId,
    today: LocalDate = LocalDate.now(zone),
): LedgerUi {
    val inMonth = LedgerMath.inMonth(data.transactions, month, zone)
    val filtered = LedgerMath.filter(inMonth, filter, data.categories)
    val accountsById = data.accounts.associateBy { it.id }
    val categoriesById = data.categories.associateBy { it.id }
    // 汇总跟随筛选：筛了某个分类，就看这个分类的收支
    val summary = LedgerMath.summary(filtered)
    val days = LedgerMath.groupByDay(filtered, zone).map { day ->
        DayUi(
            date = day.date,
            label = shortDayLabel(day.date, today),
            weekday = weekdayLabel(day.date),
            totals = dayTotals(day.summary),
            rows = day.items.map { txRow(it, accountsById, categoriesById, zone) },
            expense = day.summary.expense,
            income = day.summary.income,
        )
    }
    return LedgerUi(
        month = month,
        summary = summary,
        days = days,
        totalBudget = LedgerMath.budgetProgress(data.budgets.filter { it.categoryId == null }, inMonth, data.categories).firstOrNull(),
        accounts = data.accounts,
        accountsById = accountsById,
        categoriesById = categoriesById,
        filterActive = filter.isActive,
        monthCount = inMonth.size,
        unreadable = data.unreadable,
        latestMonth = LedgerMath.latestMonth(data.transactions, zone),
        dailyAverage = LedgerClock.dailyAverage(summary.expense, month, today),
        quickCategories = EntryDefaults.frequentCategories(
            data.transactions,
            data.categories,
            nowMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        ),
        today = today,
    )
}

/** 当日收支：“支 ¥12.00　收 ¥3.00”，没有的一项不写。 */
fun dayTotals(summary: Summary): String = buildList {
    if (summary.expense > 0) add("支 ${Money.format(summary.expense)}")
    if (summary.income > 0) add("收 ${Money.format(summary.income)}")
}.joinToString("　")

fun txRow(
    tx: LedgerTransaction,
    accountsById: Map<String, LedgerAccount>,
    categoriesById: Map<String, LedgerCategory>,
    zone: ZoneId,
): TxRowUi {
    val account = accountsById[tx.accountId]
    val title: String
    val where: String
    val iconKey: String?
    if (tx.type == TxType.TRANSFER) {
        val to = tx.toAccountId?.let { accountsById[it] }
        title = "转账"
        where = "${account?.name ?: "未知账户"} → ${to?.name ?: "未知账户"}"
        iconKey = null
    } else {
        val category = tx.categoryId?.let { categoriesById[it] }
        title = categoryLabel(tx.categoryId, categoriesById)
        where = account?.name ?: "未知账户"
        iconKey = category?.icon ?: category?.parentId?.let { categoriesById[it]?.icon }
    }
    val detail = listOf(tx.note, tx.tags.joinToString(" ") { "#$it" }).filter { it.isNotBlank() }.joinToString("　")
    val time = Instant.ofEpochMilli(tx.occurredAt).atZone(zone).toLocalTime()
    val subtitle = "%02d:%02d　%s".format(time.hour, time.minute, where) + if (detail.isNotEmpty()) "　$detail" else ""
    val amountText = when (tx.type) {
        TxType.EXPENSE -> "-" + Money.format(tx.amount)
        TxType.INCOME -> "+" + Money.format(tx.amount)
        TxType.TRANSFER -> Money.format(tx.amount)
    }
    return TxRowUi(tx.id, tx.type, title, subtitle, iconKey, amountText, tx)
}
