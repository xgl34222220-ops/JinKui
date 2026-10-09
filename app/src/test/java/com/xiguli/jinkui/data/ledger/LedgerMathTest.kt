package com.xiguli.jinkui.data.ledger

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerMathTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH, openingBalance = 10_000)
    private val bank = LedgerAccount("a-bank", "招行", AccountKind.BANK, openingBalance = 500_000)
    private val credit = LedgerAccount("a-credit", "信用卡", AccountKind.CREDIT, openingBalance = -20_000)
    private val accounts = listOf(cash, bank, credit)
    private val categories = LedgerDefaults.categories()

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private var seq = 0
    private fun tx(
        type: TxType,
        amount: Long,
        time: Long,
        account: String = cash.id,
        to: String? = null,
        category: String? = null,
        note: String = "",
        tags: List<String> = emptyList(),
    ) = LedgerTransaction("t-${seq++}", type, amount, time, account, to, category, note, tags, createdAt = time, updatedAt = time)

    @Test
    fun balancesFollowEveryTransaction() {
        val txs = listOf(
            tx(TxType.EXPENSE, 1_550, at(2026, 10, 1), cash.id, category = "cat-food-2"),
            tx(TxType.INCOME, 1_000_000, at(2026, 10, 5), bank.id, category = "cat-salary"),
            tx(TxType.TRANSFER, 20_000, at(2026, 10, 6), bank.id, to = cash.id),
            // 还信用卡：从银行卡转到信用卡，欠款减少
            tx(TxType.TRANSFER, 20_000, at(2026, 10, 7), bank.id, to = credit.id),
            tx(TxType.EXPENSE, 3_000, at(2026, 10, 8), credit.id, category = "cat-shopping"),
            // 指向不存在账户的流水不影响任何余额
            tx(TxType.EXPENSE, 999, at(2026, 10, 8), "a-ghost"),
        )
        val balances = LedgerMath.balances(accounts, txs)
        assertEquals(10_000 - 1_550 + 20_000L, balances[cash.id])
        assertEquals(500_000 + 1_000_000 - 20_000 - 20_000L, balances[bank.id])
        assertEquals(-20_000 + 20_000 - 3_000L, balances[credit.id])
        assertEquals(3, balances.size)
        assertEquals(balances.values.sum(), LedgerMath.netWorth(balances))
        // 转账在账户之间守恒：净资产只受收支影响
        assertEquals(10_000 + 500_000 - 20_000 + 1_000_000 - 1_550 - 3_000L, LedgerMath.netWorth(balances))
    }

    @Test
    fun summaryIgnoresTransfers() {
        val txs = listOf(
            tx(TxType.EXPENSE, 100, at(2026, 10, 1)),
            tx(TxType.EXPENSE, 250, at(2026, 10, 2)),
            tx(TxType.INCOME, 1_000, at(2026, 10, 3)),
            tx(TxType.TRANSFER, 5_000, at(2026, 10, 3), bank.id, to = cash.id),
        )
        val summary = LedgerMath.summary(txs)
        assertEquals(350L, summary.expense)
        assertEquals(1_000L, summary.income)
        assertEquals(650L, summary.balance)
        assertEquals(Summary.ZERO, LedgerMath.summary(emptyList()))
    }

    @Test
    fun monthBoundariesUseLocalTime() {
        val lastSecond = at(2026, 9, 30, 23, 59)
        val firstMinute = at(2026, 10, 1, 0, 0)
        val txs = listOf(tx(TxType.EXPENSE, 1, lastSecond), tx(TxType.EXPENSE, 2, firstMinute))
        assertEquals(listOf(2L), LedgerMath.inMonth(txs, YearMonth.of(2026, 10), zone).map { it.amount })
        assertEquals(listOf(1L), LedgerMath.inMonth(txs, YearMonth.of(2026, 9), zone).map { it.amount })
        assertEquals(YearMonth.of(2026, 10), LedgerMath.monthOf(firstMinute, zone))
    }

    @Test
    fun groupsByDayNewestFirstWithDailyTotals() {
        val txs = listOf(
            tx(TxType.EXPENSE, 100, at(2026, 10, 1, 8)),
            tx(TxType.EXPENSE, 200, at(2026, 10, 3, 9)),
            tx(TxType.INCOME, 1_000, at(2026, 10, 3, 18)),
            tx(TxType.EXPENSE, 50, at(2026, 10, 1, 20)),
        )
        val days = LedgerMath.groupByDay(txs, zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1)), days.map { it.date })
        assertEquals(Summary(200, 1_000), days[0].summary)
        assertEquals(Summary(150, 0), days[1].summary)
        // 同一天里晚的在前
        assertEquals(listOf(50L, 100L), days[1].items.map { it.amount })
    }

    @Test
    fun filtersByAccountCategoryAndNote() {
        val lunch = tx(TxType.EXPENSE, 2_500, at(2026, 10, 1), cash.id, category = "cat-food-2", note = "公司楼下 牛肉面", tags = listOf("工作日"))
        val coffee = tx(TxType.EXPENSE, 3_000, at(2026, 10, 2), bank.id, category = "cat-food-4", note = "拿铁")
        val taxi = tx(TxType.EXPENSE, 4_200, at(2026, 10, 2), bank.id, category = "cat-transport-2", note = "加班打车", tags = listOf("报销"))
        val move = tx(TxType.TRANSFER, 10_000, at(2026, 10, 3), bank.id, to = cash.id)
        val all = listOf(lunch, coffee, taxi, move)

        assertEquals(all, LedgerMath.filter(all, TxFilter(), categories))
        // 账户筛选：转入转出都算
        assertEquals(listOf(lunch, move), LedgerMath.filter(all, TxFilter(accountId = cash.id), categories))
        // 选一级分类时包含它的子分类
        assertEquals(listOf(lunch, coffee), LedgerMath.filter(all, TxFilter(categoryId = "cat-food"), categories))
        assertEquals(listOf(coffee), LedgerMath.filter(all, TxFilter(categoryId = "cat-food-4"), categories))
        // 搜备注与标签，多个词同时满足
        assertEquals(listOf(lunch), LedgerMath.filter(all, TxFilter(query = "牛肉"), categories))
        assertEquals(listOf(taxi), LedgerMath.filter(all, TxFilter(query = "报销"), categories))
        assertEquals(listOf(lunch), LedgerMath.filter(all, TxFilter(query = "公司 工作日"), categories))
        assertEquals(emptyList<LedgerTransaction>(), LedgerMath.filter(all, TxFilter(accountId = cash.id, query = "拿铁"), categories))
        assertTrue(TxFilter(query = "x").isActive)
        assertFalse(TxFilter(query = "  ").isActive)
    }

    @Test
    fun breakdownRollsChildrenIntoTopLevel() {
        val txs = listOf(
            tx(TxType.EXPENSE, 1_000, at(2026, 10, 1), category = "cat-food-1"),
            tx(TxType.EXPENSE, 2_000, at(2026, 10, 1), category = "cat-food-3"),
            tx(TxType.EXPENSE, 1_000, at(2026, 10, 1), category = "cat-food"),
            tx(TxType.EXPENSE, 5_000, at(2026, 10, 2), category = "cat-transport-2"),
            tx(TxType.EXPENSE, 2_000, at(2026, 10, 2), category = null),
            tx(TxType.EXPENSE, 1_000, at(2026, 10, 2), category = "cat-deleted"),
            tx(TxType.INCOME, 99_999, at(2026, 10, 2), category = "cat-salary"),
        )
        val shares = LedgerMath.categoryBreakdown(txs, categories, TxType.EXPENSE)
        assertEquals(listOf("cat-transport", "cat-food", ""), shares.map { it.categoryId })
        val food = shares.first { it.categoryId == "cat-food" }
        assertEquals(4_000L, food.amount)
        assertEquals(3, food.count)
        assertEquals(0.3333f, food.ratio, 0.001f)
        // 未分类与找不到的分类合在一起
        assertEquals(3_000L, shares.first { it.categoryId == "" }.amount)
        assertEquals(1f, shares.sumOf { it.ratio.toDouble() }.toFloat(), 0.0001f)
        val income = LedgerMath.categoryBreakdown(txs, categories, TxType.INCOME)
        assertEquals(listOf("cat-salary"), income.map { it.categoryId })
    }

    @Test
    fun trendCoversEveryMonthEvenWhenEmpty() {
        val txs = listOf(
            tx(TxType.EXPENSE, 100, at(2026, 8, 15)),
            tx(TxType.EXPENSE, 300, at(2026, 10, 1)),
            tx(TxType.INCOME, 1_000, at(2026, 10, 31, 23, 59)),
            tx(TxType.TRANSFER, 7_777, at(2026, 10, 2), bank.id, to = cash.id),
            tx(TxType.EXPENSE, 5_000, at(2026, 4, 30)),
            tx(TxType.EXPENSE, 5_000, at(2026, 11, 1, 0, 0)),
        )
        val points = LedgerMath.trend(txs, YearMonth.of(2026, 10), 6, zone)
        assertEquals((5..10).map { YearMonth.of(2026, it) }, points.map { it.month })
        assertEquals(listOf(0L, 0L, 0L, 100L, 0L, 300L), points.map { it.summary.expense })
        assertEquals(1_000L, points.last().summary.income)
    }

    @Test
    fun monthOverMonthChange() {
        assertEquals(0.5f, LedgerMath.change(150, 100)!!, 0.0001f)
        assertEquals(-0.25f, LedgerMath.change(75, 100)!!, 0.0001f)
        assertNull(LedgerMath.change(100, 0))
    }

    @Test
    fun budgetCountsCategoryAndChildren() {
        val budgets = listOf(
            LedgerBudget(LedgerBudget.idFor("cat-food"), "cat-food", 50_000),
            LedgerBudget(LedgerBudget.TOTAL_ID, null, 300_000),
            LedgerBudget(LedgerBudget.idFor("cat-fun"), "cat-fun", 10_000),
            LedgerBudget(LedgerBudget.idFor("cat-medical"), "cat-medical", 0),
        )
        val txs = listOf(
            tx(TxType.EXPENSE, 20_000, at(2026, 10, 1), category = "cat-food-2"),
            tx(TxType.EXPENSE, 35_000, at(2026, 10, 2), category = "cat-food"),
            tx(TxType.EXPENSE, 4_000, at(2026, 10, 3), category = "cat-fun-1"),
            tx(TxType.EXPENSE, 1_000, at(2026, 10, 3), category = "cat-transport"),
            tx(TxType.INCOME, 900_000, at(2026, 10, 3), category = "cat-salary"),
            tx(TxType.TRANSFER, 900_000, at(2026, 10, 3), bank.id, to = cash.id),
        )
        val progress = LedgerMath.budgetProgress(budgets, txs, categories)
        // 总预算在前，金额为 0 的视为未设置
        assertEquals(listOf(null, "cat-food", "cat-fun"), progress.map { it.budget.categoryId })
        val total = progress[0]
        assertEquals(60_000L, total.spent)
        assertEquals(240_000L, total.remaining)
        assertEquals(0.2f, total.ratio, 0.0001f)
        assertFalse(total.over)
        val food = progress[1]
        assertEquals(55_000L, food.spent)
        assertTrue(food.over)
        assertEquals(-5_000L, food.remaining)
        assertEquals(4_000L, progress[2].spent)
    }

    @Test
    fun validationRejectsBadInput() {
        val ok = tx(TxType.EXPENSE, 100, at(2026, 10, 1))
        assertNull(LedgerMath.validate(ok, accounts))
        assertNotNull(LedgerMath.validate(ok.copy(amount = 0), accounts))
        assertNotNull(LedgerMath.validate(ok.copy(amount = -5), accounts))
        assertNotNull(LedgerMath.validate(ok.copy(amount = Money.MAX_CENTS + 1), accounts))
        assertNotNull(LedgerMath.validate(ok.copy(accountId = "nope"), accounts))
        val transfer = tx(TxType.TRANSFER, 100, at(2026, 10, 1), bank.id, to = cash.id)
        assertNull(LedgerMath.validate(transfer, accounts))
        assertNotNull(LedgerMath.validate(transfer.copy(toAccountId = null), accounts))
        assertNotNull(LedgerMath.validate(transfer.copy(toAccountId = bank.id), accounts))
    }

    @Test
    fun accountUsageAndTags() {
        val txs = listOf(
            tx(TxType.EXPENSE, 1, at(2026, 10, 1), cash.id),
            tx(TxType.TRANSFER, 1, at(2026, 10, 1), bank.id, to = credit.id),
        )
        assertEquals(1, LedgerMath.usageOf(cash.id, txs))
        assertEquals(1, LedgerMath.usageOf(credit.id, txs))
        assertEquals(0, LedgerMath.usageOf("a-new", txs))
        assertEquals(listOf("早饭", "公司", "报销"), LedgerMath.parseTags(" #早饭 公司，报销、公司 "))
        assertEquals(emptyList<String>(), LedgerMath.parseTags("   "))
    }

    @Test
    fun defaultCategoriesAreTwoLevelWithStableIds() {
        val byId = categories.associateBy { it.id }
        assertEquals(categories.size, byId.size)
        for (category in categories) {
            assertTrue(category.id.matches(Regex("[A-Za-z0-9-]{1,64}")))
            assertTrue(category.type == TxType.EXPENSE || category.type == TxType.INCOME)
            val parent = category.parentId?.let(byId::getValue)
            if (parent != null) {
                assertNull("最多两级", parent.parentId)
                assertEquals(parent.type, category.type)
            }
        }
        assertTrue(categories.any { it.type == TxType.INCOME })
        assertEquals("餐饮", byId.getValue("cat-food").name)
        assertEquals(LedgerDefaults.categories(), LedgerDefaults.categories())
        assertTrue(categories.all { it.updatedAt == 0L })
    }

    @Test
    fun editedDefaultsWinOverFreshSeedWhenRestoring() {
        // 新手机刚预置好默认分类，导入旧手机的备份：旧手机上改过名的分类要生效，没改过的跳过
        val fresh = LedgerData(accounts = LedgerDefaults.accounts(createdAt = 1_000), categories = categories)
        val renamed = categories.first { it.id == "cat-food" }.copy(name = "吃饭", updatedAt = 5)
        val incoming = LedgerData(accounts = LedgerDefaults.accounts(createdAt = 1), categories = categories.map { if (it.id == renamed.id) renamed else it })
        val plan = LedgerMerge.plan(fresh, incoming)
        assertEquals(listOf(renamed), plan.toWrite.categories)
        assertTrue(plan.toWrite.accounts.isEmpty())
        assertEquals(1, plan.updated)
        assertEquals(0, plan.added)
    }

    @Test
    fun mergeKeepsNewerAndNeverDeletes() {
        val localTx = tx(TxType.EXPENSE, 100, at(2026, 10, 1)).copy(id = "t-shared", updatedAt = 10)
        val localOnly = tx(TxType.EXPENSE, 200, at(2026, 10, 1)).copy(id = "t-local", updatedAt = 10)
        val local = LedgerData(accounts = listOf(cash.copy(updatedAt = 5)), transactions = listOf(localTx, localOnly))

        val newer = localTx.copy(amount = 999, updatedAt = 20)
        val incoming = LedgerData(
            accounts = listOf(cash.copy(name = "旧名字", updatedAt = 1), bank.copy(updatedAt = 1)),
            transactions = listOf(newer, newer.copy(amount = 1, updatedAt = 15), tx(TxType.INCOME, 5, at(2026, 10, 2)).copy(id = "t-new")),
            budgets = listOf(LedgerBudget(LedgerBudget.TOTAL_ID, null, 100, 3)),
        )
        val plan = LedgerMerge.plan(local, incoming)
        assertEquals(listOf(bank.id), plan.toWrite.accounts.map { it.id })
        assertEquals(setOf("t-shared", "t-new"), plan.toWrite.transactions.map { it.id }.toSet())
        assertEquals(999L, plan.toWrite.transactions.first { it.id == "t-shared" }.amount)
        assertEquals(1, plan.toWrite.budgets.size)
        assertEquals(3, plan.added)
        assertEquals(1, plan.updated)
        assertEquals(1, plan.skipped)
        // 再导入一次同一份：全部跳过
        val merged = LedgerData(
            accounts = local.accounts + plan.toWrite.accounts,
            transactions = local.transactions.filterNot { it.id == "t-shared" } + plan.toWrite.transactions,
            budgets = plan.toWrite.budgets,
        )
        val again = LedgerMerge.plan(merged, incoming)
        assertTrue(again.toWrite.isEmpty)
        assertEquals(0, again.added + again.updated)
    }

    // ── 0.7.0 复查补充 ──────────────────────────────────────────

    @Test
    fun usageCountsSinglePassMatchesUsageOf() {
        val txs = listOf(
            tx(TxType.EXPENSE, 100, at(2026, 10, 1), cash.id),
            tx(TxType.TRANSFER, 100, at(2026, 10, 2), bank.id, to = cash.id),
            tx(TxType.TRANSFER, 100, at(2026, 10, 3), credit.id, to = credit.id),
        )
        val counts = LedgerMath.usageCounts(txs)
        assertEquals(2, counts[cash.id])
        assertEquals(1, counts[bank.id])
        // 异常数据：同一账户转给自己只算一笔
        assertEquals(1, counts[credit.id])
        for (account in accounts) assertEquals(LedgerMath.usageOf(account.id, txs), counts[account.id] ?: 0)
    }

    @Test
    fun latestMonthFindsFutureRecords() {
        assertNull(LedgerMath.latestMonth(emptyList(), zone))
        val txs = listOf(tx(TxType.EXPENSE, 1, at(2026, 10, 1)), tx(TxType.EXPENSE, 1, at(2027, 2, 1)))
        assertEquals(YearMonth.of(2027, 2), LedgerMath.latestMonth(txs, zone))
    }

    @Test
    fun monthBoundaryUsesLocalMidnightNotUtc() {
        // 上海 11 月 1 日 00:30 是 UTC 10 月 31 日 16:30，应算 11 月
        val justAfter = at(2026, 11, 1, 0, 30)
        val justBefore = at(2026, 10, 31, 23, 59)
        val txs = listOf(tx(TxType.EXPENSE, 1, justAfter), tx(TxType.EXPENSE, 2, justBefore))
        assertEquals(listOf(1L), LedgerMath.inMonth(txs, YearMonth.of(2026, 11), zone).map { it.amount })
        assertEquals(listOf(2L), LedgerMath.inMonth(txs, YearMonth.of(2026, 10), zone).map { it.amount })
        // 换到 UTC 看，两笔都落在 10 月
        assertEquals(2, LedgerMath.inMonth(txs, YearMonth.of(2026, 10), ZoneId.of("UTC")).size)
    }

    @Test
    fun dailyAllowanceOnlyForCurrentMonthWithRemaining() {
        val budget = LedgerBudget(LedgerBudget.TOTAL_ID, null, 31_00)
        val october = YearMonth.of(2026, 10)
        // 10 月 1 日：31 天
        assertEquals(DailyAllowance(31, 100), LedgerMath.dailyAllowance(BudgetProgress(budget, 0), october, LocalDate.of(2026, 10, 1)))
        // 最后一天：剩余全给今天
        assertEquals(DailyAllowance(1, 1_000), LedgerMath.dailyAllowance(BudgetProgress(budget, 21_00), october, LocalDate.of(2026, 10, 31)))
        // 除不尽向下取整到分
        assertEquals(DailyAllowance(3, 33), LedgerMath.dailyAllowance(BudgetProgress(LedgerBudget("b", null, 100), 0), october, LocalDate.of(2026, 10, 29)))
        // 不是本月、刚好用完、超支：不显示
        assertNull(LedgerMath.dailyAllowance(BudgetProgress(budget, 0), october, LocalDate.of(2026, 11, 1)))
        assertNull(LedgerMath.dailyAllowance(BudgetProgress(budget, 31_00), october, LocalDate.of(2026, 10, 5)))
        assertNull(LedgerMath.dailyAllowance(BudgetProgress(budget, 40_00), october, LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun budgetWithNoDataIsZeroSpent() {
        val progress = LedgerMath.budgetProgress(listOf(LedgerBudget(LedgerBudget.TOTAL_ID, null, 1_000)), emptyList(), categories)
        assertEquals(1, progress.size)
        assertEquals(0L, progress[0].spent)
        assertEquals(0f, progress[0].ratio)
        assertFalse(progress[0].over)
    }

    @Test
    fun largeSumsDoNotOverflow() {
        val txs = (0 until 1_000).map { tx(TxType.INCOME, Money.MAX_CENTS, at(2026, 10, 1)) }
        assertEquals(Money.MAX_CENTS * 1_000, LedgerMath.summary(txs).income)
        assertTrue(Money.format(Money.MAX_CENTS * 1_000).startsWith("¥"))
    }
}
