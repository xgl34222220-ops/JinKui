package com.xiguli.jinkui.data.ledger

import com.xiguli.jinkui.core.auto.PaySource
import com.xiguli.jinkui.data.backup.BackupJson
import com.xiguli.jinkui.data.importer.BillRecord
import com.xiguli.jinkui.data.importer.BillSource
import com.xiguli.jinkui.data.importer.CandidateFlag
import com.xiguli.jinkui.data.importer.ImportCandidate
import com.xiguli.jinkui.data.importer.ImportClass
import com.xiguli.jinkui.data.importer.ImportPreview
import com.xiguli.jinkui.data.importer.ImportRules
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.0-alpha04 自动分类规则与记账模板：匹配顺序、收支与金额限定、停用与失效的分类 / 账户、套用、
 * 模板生成流水，自动记账与导入账单的挂钩，以及备份 schema 6 往返与删除标记。合成数据。
 */
class RuleMathTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val categories = LedgerDefaults.categories()
    private val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH, position = 0)
    private val wechat = LedgerAccount("a-wx", "微信", AccountKind.WECHAT, position = 1)
    private val old = LedgerAccount("a-old", "旧卡", AccountKind.BANK, archived = true, position = 2)
    private val accounts = listOf(cash, wechat, old)

    private val takeout = LedgerRule("r-takeout", listOf("美团", "饿了么"), TxType.EXPENSE, categoryId = "cat-food-3", tags = listOf("外卖"), position = 0)
    private val bigTakeout = LedgerRule("r-big", listOf("美团"), TxType.EXPENSE, minAmount = 100_00, categoryId = "cat-social-3", position = 1)
    private val subway = LedgerRule("r-subway", listOf("地铁"), categoryId = "cat-transport-1", accountId = wechat.id, position = 2)
    private val off = LedgerRule("r-off", listOf("咖啡"), TxType.EXPENSE, categoryId = "cat-food-4", enabled = false, position = 3)
    private val rules = listOf(bigTakeout.copy(position = 5), subway, takeout, off)

    private fun match(type: TxType, text: String, amount: Long?) = RuleMath.match(rules, type, listOf(text), amount, categories, accounts)

    @Test
    fun firstMatchByPositionWins() {
        // 位置靠前的“外卖”先命中（大额规则排在后面）
        assertEquals("r-takeout", match(TxType.EXPENSE, "美团外卖 黄焖鸡", 3_000_00)?.rule?.id)
        // 把大额规则挪到最前：只有金额达到 100 才命中，没有金额时（还没输）跳过
        val reordered = listOf(bigTakeout.copy(position = -1)) + rules
        assertEquals("r-big", RuleMath.match(reordered, TxType.EXPENSE, listOf("美团"), 120_00, categories, accounts)?.rule?.id)
        assertEquals("r-takeout", RuleMath.match(reordered, TxType.EXPENSE, listOf("美团"), 30_00, categories, accounts)?.rule?.id)
        assertEquals("r-takeout", RuleMath.match(reordered, TxType.EXPENSE, listOf("美团"), null, categories, accounts)?.rule?.id)
    }

    @Test
    fun typeCaseDisabledAndTransfers() {
        assertEquals("cat-food-3", match(TxType.EXPENSE, "ELEME 饿了么", null)?.categoryId)
        // 支出规则不作用于收入；转账永远不套规则
        assertNull(match(TxType.INCOME, "美团 退款", null))
        assertNull(match(TxType.TRANSFER, "地铁", null))
        // 停用的规则不生效
        assertNull(match(TxType.EXPENSE, "咖啡", 20_00))
        // 不区分大小写
        val latin = LedgerRule("r-latin", listOf("Starbucks"), categoryId = "cat-food-4")
        assertNotNull(RuleMath.match(listOf(latin), TxType.EXPENSE, listOf("STARBUCKS 星巴克"), null, categories, accounts))
        // “都可以”的地铁规则：收支都命中，但分类是支出分类，收入时分类一项忽略、账户照换
        val income = match(TxType.INCOME, "地铁补贴", null)
        assertEquals("r-subway", income?.rule?.id)
        assertNull(income?.categoryId)
        assertEquals(wechat.id, income?.accountId)
        assertNull(match(TxType.EXPENSE, "", 10_00))
    }

    @Test
    fun missingCategoryOrArchivedAccountIsIgnored() {
        val ghost = LedgerRule("r-ghost", listOf("房租"), categoryId = "cat-nope", accountId = old.id)
        // 分类不存在、账户已归档、也没有标签：整条不算命中
        assertNull(RuleMath.match(listOf(ghost), TxType.EXPENSE, listOf("十月房租"), null, categories, accounts))
        val tagged = ghost.copy(tags = listOf("房子"))
        val hit = RuleMath.match(listOf(tagged), TxType.EXPENSE, listOf("十月房租"), null, categories, accounts)!!
        assertNull(hit.categoryId)
        assertNull(hit.accountId)
        assertEquals(listOf("房子"), hit.tags)
    }

    @Test
    fun applyReplacesCategoryAccountAndAppendsTags() {
        val tx = LedgerTransaction("t", TxType.EXPENSE, 6_00, 1L, cash.id, categoryId = "cat-other-expense", note = "地铁", tags = listOf("通勤"))
        val hit = match(TxType.EXPENSE, tx.note, tx.amount)!!
        val applied = RuleMath.apply(tx, hit)
        assertEquals("cat-transport-1", applied.categoryId)
        assertEquals(wechat.id, applied.accountId)
        assertEquals(listOf("通勤"), applied.tags)
        assertEquals(cash.id, RuleMath.apply(tx, hit, changeAccount = false).accountId)
        val withTags = RuleMath.apply(tx, match(TxType.EXPENSE, "美团", 20_00)!!)
        assertEquals(listOf("通勤", "外卖"), withTags.tags)
    }

    @Test
    fun ruleProblemsAndKeywordParsing() {
        assertEquals(listOf("美团", "饿了么", "外卖"), RuleMath.parseKeywords(" 美团  饿了么，外卖、美团 "))
        assertEquals("请至少填一个关键词", LedgerRule("x", emptyList(), categoryId = "cat-food").problem())
        assertEquals("规则只对支出或收入生效", LedgerRule("x", listOf("a"), TxType.TRANSFER, categoryId = "cat-food").problem())
        assertEquals("最低金额不能大于最高金额", LedgerRule("x", listOf("a"), minAmount = 10, maxAmount = 5, categoryId = "cat-food").problem())
        assertEquals("请至少选择分类、账户或标签中的一项", LedgerRule("x", listOf("a")).problem())
        assertNull(takeout.problem())
        assertEquals("美团 / 饿了么 · 支出", takeout.summary)
        assertEquals("美团 · 支出 · ≥ ¥100.00", bigTakeout.summary)
    }

    @Test
    fun templatesBuildTransactions() {
        val breakfast = LedgerTemplate("tp-1", "早餐", amount = 8_00, categoryId = "cat-food-1", note = "包子")
        val tx = breakfast.toTransaction("id-1", 42L, accounts, fallbackAccount = cash.id)!!
        assertEquals(8_00L, tx.amount)
        assertEquals(cash.id, tx.accountId)
        assertEquals("cat-food-1", tx.categoryId)
        assertEquals("包子", tx.note)
        // 不带金额的模板要传入金额；账户已归档时用备用账户
        val rent = LedgerTemplate("tp-2", "房租", accountId = old.id, categoryId = "cat-housing-1")
        assertNull(rent.toTransaction("id-2", 1L, accounts, cash.id))
        assertEquals(cash.id, rent.toTransaction("id-2", 1L, accounts, cash.id, amountOverride = 3_000_00)!!.accountId)
        val topUp = LedgerTemplate("tp-3", "充饭卡", TxType.TRANSFER, 100_00, cash.id, wechat.id, categoryId = "cat-food")
        val t = topUp.toTransaction("id-3", 1L, accounts, null)!!
        assertEquals(wechat.id, t.toAccountId)
        assertNull(t.categoryId)
        assertEquals("转账模板要选转出和转入账户", LedgerTemplate("x", "转", TxType.TRANSFER, accountId = cash.id).problem())
        assertEquals("模板名称最多 12 个字", LedgerTemplate("x", "一二三四五六七八九十一二三").problem())
    }

    @Test
    fun pendingSuggestionUsesRulesFirst() {
        val data = LedgerData(accounts = accounts, categories = categories, rules = rules)
        val entry = PendingEntry("p-1", PaySource.WECHAT, TxType.EXPENSE, 32_00, "美团外卖", 1_000L, 1_000L)
        val tx = PendingMath.suggestion(entry, data, zone)!!
        assertEquals("cat-food-3", tx.categoryId)
        assertEquals(listOf("外卖"), tx.tags)
        // 没有规则时与以前一样
        assertTrue(PendingMath.suggestion(entry, data.copy(rules = emptyList()), zone)!!.tags.isEmpty())
    }

    @Test
    fun importPreviewHookOnlyTouchesNormalRows() {
        val data = LedgerData(accounts = accounts, categories = categories, rules = rules)
        fun record(party: String, type: TxType = TxType.EXPENSE) =
            BillRecord(BillSource.WECHAT, LocalDateTime.of(2026, 9, 30, 12, 0), type, 25_00, counterparty = party)
        val normal = ImportCandidate("c-1", record("美团"), "cat-other-expense", cash.id, true, CandidateFlag.NEW)
        val fee = ImportCandidate("c-2", record("美团"), "cat-other-expense", cash.id, true, CandidateFlag.NEW, klass = ImportClass.FEE)
        val transfer = ImportCandidate("c-3", record("地铁"), null, cash.id, true, CandidateFlag.NEW, txType = TxType.TRANSFER, toAccountId = wechat.id)
        val preview = ImportPreview(BillSource.WECHAT, listOf(normal, fee, transfer), emptyMap(), 0)
        val out = ImportRules.applyToPreview(preview, data)
        assertEquals("cat-food-3", out.candidates[0].categoryId)
        assertEquals(fee, out.candidates[1])
        assertEquals(transfer, out.candidates[2])
        // 记账 App 导出一比一，不套规则；没有规则时原样返回
        val app = preview.copy(source = BillSource.APP)
        assertSame(app, ImportRules.applyToPreview(app, data))
        assertSame(preview, ImportRules.applyToPreview(preview, data.copy(rules = emptyList())))
        // 写入时追加规则里的标签
        val txs = listOf(LedgerTransaction("c-1", TxType.EXPENSE, 25_00, 1L, cash.id, categoryId = "cat-food-3"))
        assertEquals(listOf("外卖"), ImportRules.tag(txs, out, data).single().tags)
    }

    @Test
    fun rulesAndTemplatesRoundTripThroughBackup() {
        val template = LedgerTemplate("tp-1", "早餐", amount = 8_00, categoryId = "cat-food-1", tags = listOf("早"), position = 0, updatedAt = 7)
        val data = LedgerData(
            accounts = listOf(cash, wechat), categories = categories,
            rules = listOf(takeout.copy(updatedAt = 3), subway.copy(updatedAt = 4)), templates = listOf(template),
            tombstones = listOf(LedgerTombstone("r-gone", LedgerTombstone.KIND_RULE, 9)),
        )
        val back = BackupJson.decodeAll(BackupJson.encode(emptyList(), 1L, data)).ledger
        assertEquals(data.rules, back.rules)
        assertEquals(data.templates, back.templates)
        assertEquals(data.tombstones, back.tombstones)
        // 删掉的规则：导入一份还带着它的旧备份也不会回来
        val incoming = data.copy(rules = data.rules + LedgerRule("r-gone", listOf("x"), categoryId = "cat-food", updatedAt = 1), tombstones = emptyList())
        val plan = LedgerMerge.plan(LedgerData(tombstones = data.tombstones), incoming)
        assertTrue(plan.toWrite.rules.none { it.id == "r-gone" })
        // schema 5 的备份没有规则与模板
        val old = """{"app":"jinkui","schema":5,"cards":[],"ledger":{"accounts":[],"transactions":[]}}"""
        val oldLedger = BackupJson.decodeAll(old.toByteArray()).ledger
        assertTrue(oldLedger.rules.isEmpty() && oldLedger.templates.isEmpty())
    }
}
