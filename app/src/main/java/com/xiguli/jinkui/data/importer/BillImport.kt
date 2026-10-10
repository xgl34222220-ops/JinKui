package com.xiguli.jinkui.data.importer

import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerIds
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.TxType
import java.security.MessageDigest
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 预览里一行的状态。 */
enum class CandidateFlag {
    /** 新的一笔，默认勾选。 */
    NEW,
    /** 账本里已经有一笔同样金额、时间相差三分钟内的记录（可能是手动或自动记账记过的），默认不勾。 */
    SIMILAR,
    /** 退款收入，文件里找不到对应的原支出，默认不勾。 */
    REFUND,
}

/**
 * 导入时给每一笔分的类（1.3）。预览里每一类是一组，可以整组打开 / 关闭，显示笔数与金额。
 *
 * 记账 App（例如一木记账）的导出里，很多行并不是真正的收支：
 *  - “账户余额补齐”是那个 App 用来把余额对上的校准记录；
 *  - “余额宝-单次转入”、理财支出、零钱提现是自己账户之间挪钱；
 *  - “借出 / 借入”是借贷；
 *  - 账户为“花呗”的消费是信用账户上的欠款。
 * 把它们当收入 / 支出导入，统计会虚高、账户里会凭空多出大笔“存款”、欠款也看不到。
 */
enum class ImportClass(val label: String, val hint: String) {
    NORMAL("收支", "按类别归到金匮的分类，计入统计"),
    CREDIT("花呗消费", "记到“花呗”信用账户，欠款在资产里看得到"),
    FEE("手续费", "转账、提现手续费，记为其他支出"),
    INTERNAL("内部转账", "自己账户之间挪钱（余额宝转入、理财、提现），记为转账，不计收支"),
    LOAN("借贷往来", "借出记到“借出款（应收）”，借入记到“借入款（应付）”，不计收支"),
    ADJUSTMENT("余额校准", "原 App 的“账户余额补齐”，不是收入或支出。默认不导入；打开即“用作账户余额校准”：只改账户余额，不计收支"),
}

/** 预览里的一行：要写入的流水 ID 固定由账单内容算出，同一笔重复导入会被认出来。 */
data class ImportCandidate(
    val id: String,
    val record: BillRecord,
    val categoryId: String?,
    val accountId: String,
    val checked: Boolean,
    val flag: CandidateFlag,
    val klass: ImportClass = ImportClass.NORMAL,
    /** 实际写入的类型：转账类（内部转账、借贷、余额校准）为 [TxType.TRANSFER]。 */
    val txType: TxType = record.type,
    /** 转账的转入账户。 */
    val toAccountId: String? = null,
)

/** 预览里一组的汇总。[net] 是“收入 − 支出”方向上的净额（按账单原来的收 / 支方向）。 */
data class ImportGroup(
    val klass: ImportClass,
    val count: Int,
    val checkedCount: Int,
    val incomeTotal: Long,
    val expenseTotal: Long,
) {
    val net: Long get() = incomeTotal - expenseTotal
    val allChecked: Boolean get() = count > 0 && checkedCount == count
}

/**
 * 导入预览。[newAccounts] 是导入可能顺带新建的账户（例如“微信”“余额宝”“花呗”“借出款（应收）”），
 * 实际只新建被勾选的流水用到的那些。[defaultAccountId] 是认不出账户的行记到哪里（预览里可以改）。
 */
data class ImportPreview(
    val source: BillSource,
    val candidates: List<ImportCandidate>,
    val skipped: Map<SkipReason, Int>,
    /** 以前已经导入过的笔数（ID 已经在账本里），不再列出。 */
    val alreadyImported: Int,
    val newAccounts: List<LedgerAccount> = emptyList(),
    val defaultAccountId: String? = null,
    val fileName: String = "",
    /** 记账 App 导出：按原 App 的“类别 / 二级分类”一比一新建的分类（账本里还没有同名的才建）。 */
    val newCategories: List<LedgerCategory> = emptyList(),
) {
    val selected: List<ImportCandidate> get() = candidates.filter { it.checked }
    val expenseTotal: Long get() = selected.filter { it.txType == TxType.EXPENSE }.sumOf { it.record.amount }
    val incomeTotal: Long get() = selected.filter { it.txType == TxType.INCOME }.sumOf { it.record.amount }

    /** 被勾选的流水实际会用到、需要新建的账户。 */
    val usedNewAccounts: List<LedgerAccount>
        get() {
            val used = HashSet<String>()
            selected.forEach { used += it.accountId; it.toAccountId?.let(used::add) }
            return newAccounts.filter { it.id in used }
        }

    /** 被勾选的流水实际会用到、需要新建的分类（连同它们的一级分类）。 */
    val usedNewCategories: List<LedgerCategory>
        get() {
            val byId = newCategories.associateBy { it.id }
            val used = HashSet<String>()
            selected.forEach { c -> c.categoryId?.let { id -> used += id; byId[id]?.parentId?.let(used::add) } }
            return newCategories.filter { it.id in used }
        }

    /** 兼容 1.2：第一个要新建的账户。 */
    val newAccount: LedgerAccount? get() = usedNewAccounts.firstOrNull()

    /** 按类分组（只列有内容的组，顺序同 [ImportClass]）。 */
    val groups: List<ImportGroup>
        get() = ImportClass.entries.mapNotNull { k ->
            val rows = candidates.filter { it.klass == k }
            if (rows.isEmpty()) null else ImportGroup(
                klass = k,
                count = rows.size,
                checkedCount = rows.count { it.checked },
                incomeTotal = rows.filter { it.record.type == TxType.INCOME }.sumOf { it.record.amount },
                expenseTotal = rows.filter { it.record.type == TxType.EXPENSE }.sumOf { it.record.amount },
            )
        }

    fun withGroup(klass: ImportClass, checked: Boolean): ImportPreview =
        copy(candidates = candidates.map { if (it.klass == klass) it.copy(checked = checked) else it })
}

/** 一个账户在这次导入里的余额变化。 */
data class BalanceChange(val accountId: String, val name: String, val before: Long, val after: Long, val created: Boolean) {
    val delta: Long get() = after - before
}

/** 导入完成后的结果。 */
data class ImportResult(
    val imported: Int,
    val expenseCount: Int,
    val expenseTotal: Long,
    val incomeCount: Int,
    val incomeTotal: Long,
    val unchecked: Int,
    val alreadyImported: Int,
    val skipped: Map<SkipReason, Int>,
    val createdAccount: String?,
    /** 转账类（内部转账、借贷、余额校准）笔数：不计收支。 */
    val transferCount: Int = 0,
    /** 每个账户余额的变化（不含“余额调整”内部账户），让用户一眼看出有没有凭空多出来的钱。 */
    val balanceChanges: List<BalanceChange> = emptyList(),
    /** 这次导入的批次，用于“撤销本次导入”。 */
    val batchId: String? = null,
)

/** 账单导入的预览与落账计算。纯函数，便于测试。 */
object BillImport {
    /** 去重键：有交易单号用单号；记账 App 导出用“分钟 + 金额 + 备注”；否则用“分钟 + 金额 + 交易对方”。 */
    fun dedupeKey(r: BillRecord): String {
        val number = r.txNo.trim()
        if (r.source == BillSource.APP) {
            return "app:${r.time.truncatedTo(ChronoUnit.MINUTES)}:${r.type.key}:${r.amount}:${r.note.trim()}"
        }
        return if (number.isNotEmpty() && number != "/") {
            "no:${r.source.name}:$number"
        } else {
            "k:${r.time.truncatedTo(ChronoUnit.MINUTES)}:${r.type.key}:${r.amount}:${r.counterparty.trim()}"
        }
    }

    /** 由去重键算出的流水 ID（"imp-" + 32 位十六进制），满足账本 ID 规则，与周期记账生成的 ID 不会混淆。 */
    fun transactionId(r: BillRecord): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(dedupeKey(r).toByteArray(Charsets.UTF_8))
        return "imp-" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    fun isImportedId(id: String): Boolean = id.startsWith("imp-")

    // ── 分类（1.3）──────────────────────────────────────────────────

    /** 内部转账的去向。 */
    enum class Move { TO_YUEBAO, FROM_YUEBAO, TO_INVEST, WITHDRAW, REPAY_CREDIT }

    /** 这一笔属于哪一类。只对记账 App 导出做（微信 / 支付宝账单本身已经把不计收支的行排除了）。 */
    fun classify(r: BillRecord): ImportClass {
        if (r.source != BillSource.APP) return ImportClass.NORMAL
        val note = r.note
        val sub = r.item.trim()
        return when {
            "账户余额补齐" in note || "余额补齐" in note -> ImportClass.ADJUSTMENT
            move(r) != null -> ImportClass.INTERNAL
            sub == "借出" || sub == "借入" -> ImportClass.LOAN
            "手续费" in note || sub == "手续费" -> ImportClass.FEE
            "花呗" in r.method -> ImportClass.CREDIT
            else -> ImportClass.NORMAL
        }
    }

    fun move(r: BillRecord): Move? {
        val note = r.note
        val sub = r.item.trim()
        return when {
            "余额宝-单次转入" in note || "转入余额宝" in note || "转入到余额宝" in note -> Move.TO_YUEBAO
            "余额宝-转出" in note || "余额宝-单次转出" in note || "余额宝转出" in note -> Move.FROM_YUEBAO
            sub == "理财支出" -> Move.TO_INVEST
            "提现" in note && "手续费" !in note -> Move.WITHDRAW
            "还款" in note && ("花呗" in note || "信用卡" in note) -> Move.REPAY_CREDIT
            else -> null
        }
    }

    /**
     * 导入要用到的账户：已有的按种类 / 名字找，没有的才新建（只建一次，记在 [created]）。
     * “余额调整”账户建成停用状态，不出现在记账面板。
     */
    class Accounts(existing: List<LedgerAccount>, private val now: Long, preferred: String?) {
        private val active = existing.filter { !it.archived || it.kind == AccountKind.ADJUST }
        val created = ArrayList<LedgerAccount>()
        private val all get() = active + created

        private fun findOrCreate(match: (LedgerAccount) -> Boolean, make: () -> LedgerAccount): String =
            (all.firstOrNull(match) ?: make().also { created += it }).id

        private fun new(name: String, kind: AccountKind, archived: Boolean = false) =
            LedgerAccount(LedgerIds.newId(), name, kind, createdAt = now, archived = archived)

        val defaultId: String = preferred?.let { id -> active.firstOrNull { it.id == id && it.kind.countsInAssets }?.id }
            ?: active.firstOrNull { it.kind in EVERYDAY }?.id
            ?: active.firstOrNull { it.kind.countsInAssets }?.id
            ?: findOrCreate({ false }) { new("现金", AccountKind.CASH) }

        fun alipay() = findOrCreate({ it.kind == AccountKind.ALIPAY }) { new("支付宝", AccountKind.ALIPAY) }
        fun wechat() = findOrCreate({ it.kind == AccountKind.WECHAT }) { new("微信", AccountKind.WECHAT) }
        fun yuebao() = findOrCreate({ "余额宝" in it.name && it.kind.countsInAssets }) { new("余额宝", AccountKind.INVEST) }
        fun invest() = findOrCreate({ it.kind == AccountKind.INVEST && "余额宝" !in it.name }) { new("理财", AccountKind.INVEST) }
        /** 借出：备注里认得出对方时每人一个应收账户（“借出 · 小李子”），否则记到“借出款（应收）”。 */
        fun lent(person: String? = null): String {
            val name = person?.let { "借出 · $it" } ?: "借出款（应收）"
            return findOrCreate({ it.kind == AccountKind.RECEIVABLE && it.name == name }) { new(name, AccountKind.RECEIVABLE) }
        }

        /** 借入：同上，每人一个应付账户（“借入 · 老王”）。 */
        fun borrowed(person: String? = null): String {
            val name = person?.let { "借入 · $it" } ?: "借入款（应付）"
            return findOrCreate({ it.kind == AccountKind.PAYABLE && it.name == name }) { new(name, AccountKind.PAYABLE) }
        }
        fun huabei() = findOrCreate({ "花呗" in it.name && it.kind.countsInAssets }) { new("花呗", AccountKind.CREDIT) }
        fun bank() = findOrCreate({ it.kind == AccountKind.BANK }) { new("银行卡", AccountKind.BANK) }
        fun adjust() = findOrCreate({ it.kind == AccountKind.ADJUST }) { new("余额调整", AccountKind.ADJUST, archived = true) }

        /** “账户”列写明的账户名：先按名字找已有账户。 */
        fun named(name: String): String? {
            if (name.isBlank()) return null
            if ("花呗" in name) return huabei()
            all.filter { it.kind.countsInAssets && it.name.length >= 2 }.firstOrNull { name.contains(it.name) || it.name.contains(name) }?.let { return it.id }
            return when {
                "支付宝" in name -> alipay()
                "微信" in name -> wechat()
                else -> null
            }
        }

        companion object {
            private val EVERYDAY = setOf(AccountKind.CASH, AccountKind.BANK, AccountKind.ALIPAY, AccountKind.WECHAT, AccountKind.OTHER)
        }
    }

    /**
     * 按原 App 的分类树一比一建分类（1.3）：支出是“类别 / 二级分类”两级，二级为空时记在类别本身；
     * 收入的类别都是“收入”，所以二级分类直接作为一级收入分类（为空时是“其他”）。
     * 先按名字找账本里已有的（同方向、同上级），没有才新建；新分类的 ID 由方向与名字算出，重复导入不会重复建。
     */
    class Categories(existing: List<LedgerCategory>, private val now: Long) {
        private val all = existing.toMutableList()
        val created = ArrayList<LedgerCategory>()

        fun resolve(type: TxType, main: String, sub: String): String {
            val m = main.trim().take(20)
            val c = sub.trim().take(20)
            val (parentName, childName) = when {
                type == TxType.INCOME && (m == "收入" || m.isEmpty()) -> (c.ifEmpty { "其他" }) to ""
                m.isEmpty() -> (c.ifEmpty { "其他" }) to ""
                else -> m to c
            }
            val parent = find(type, parentName, null) ?: make(type, parentName, null)
            if (childName.isEmpty() || childName == parentName) return parent.id
            return (find(type, childName, parent.id) ?: make(type, childName, parent.id)).id
        }

        private fun find(type: TxType, name: String, parentId: String?): LedgerCategory? =
            all.firstOrNull { it.type == type && it.name == name && it.parentId == parentId && !it.archived }
                ?: all.firstOrNull { it.type == type && it.name == name && it.parentId == parentId }

        private fun make(type: TxType, name: String, parentId: String?): LedgerCategory {
            val key = "${type.key}:${parentId.orEmpty()}:$name"
            val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            val id = "cat-imp-" + digest.take(12).joinToString("") { "%02x".format(it) }
            val siblings = all.filter { it.type == type && it.parentId == parentId }
            val parentIcon = parentId?.let { pid -> all.firstOrNull { it.id == pid }?.icon }
            val category = LedgerCategory(
                id = id,
                name = name,
                type = type,
                parentId = parentId,
                icon = iconFor(name) ?: parentIcon ?: "more",
                position = (siblings.maxOfOrNull { it.position } ?: -1) + 1,
                updatedAt = now,
            )
            all += category
            created += category
            return category
        }

        companion object {
            /** 只是挑一个金匮已有的线条图标，名字里有这些字就用对应的。 */
            private val ICONS = listOf(
                "餐" to "restaurant", "饮料" to "cafe", "酒水" to "cafe", "零食" to "snack", "生鲜" to "cart", "早餐" to "breakfast",
                "午餐" to "lunch", "晚餐" to "dinner", "交通" to "bus", "打车" to "taxi", "公共交通" to "subway", "加油" to "gas",
                "购物" to "shopping", "数码" to "devices", "服饰" to "clothes", "家居" to "chair", "电器" to "devices", "美妆" to "shopping",
                "居家" to "home", "话费" to "wifi", "宽带" to "wifi", "电费" to "bolt", "水费" to "bolt", "家政" to "home",
                "娱乐" to "movie", "演出" to "movie", "旅游" to "luggage", "棋牌" to "game", "充值" to "game", "医疗" to "hospital",
                "医院" to "hospital", "药" to "medication", "保健" to "medication", "教育" to "school", "学费" to "school", "书" to "book",
                "人情" to "gift", "红包" to "redeem", "礼" to "gift", "宠物" to "pets", "母婴" to "child", "借" to "people",
                "工资" to "payments", "奖金" to "trophy", "理财" to "chart", "兼职" to "work", "中奖" to "trophy", "补贴" to "payments",
                "二手" to "refund", "退款" to "refund", "其他" to "more",
            )

            fun iconFor(name: String): String? = ICONS.firstOrNull { name.contains(it.first) }?.second
        }
    }

    private val PERSON = Regex("""[^\s，,。；;：:（）()\-—·#0-9]{1,12}""")

    /** 从借贷备注里认出对方（“转账给小李子”“借给老王”“向小张借”“转账收款 老王”）；认不出返回 null。 */
    fun counterparty(note: String): String? {
        val patterns = listOf(
            Regex("""(?:转账给|借给|还给|转给)\s*(${PERSON.pattern})"""),
            Regex("""向\s*(${PERSON.pattern}?)\s*借"""),
            Regex("""(?:转账收款|收款|来自)\s*[-：:]?\s*(${PERSON.pattern})"""),
        )
        for (p in patterns) {
            val name = p.find(note)?.groupValues?.get(1)?.trim().orEmpty()
            if (name.isNotEmpty() && name !in NOT_PEOPLE) return name
        }
        return null
    }

    private val NOT_PEOPLE = setOf("到余额宝", "余额宝", "支付宝", "微信", "零钱", "银行卡", "对方")

    private val ALIPAY_WORDS = listOf("支付宝", "余额宝", "花呗", "收钱码")
    private val WECHAT_WORDS = listOf("微信", "零钱", "收款")

    /**
     * 记账 App 导出这一笔的钱在哪个账户：“账户”列有值用它；否则看备注里的关键词
     * （支付宝 / 余额宝 / 花呗 / 收钱码 → 支付宝；微信 / 零钱 / 微信钱包 / 收款 → 微信，谁先出现算谁）；
     * 都没有时用预览里选的默认账户。“转账收款到余额宝”是别人转来、直接进了余额宝的钱，记到“余额宝”。
     */
    fun appAccount(r: BillRecord, accounts: Accounts): String {
        accounts.named(r.method.takeUnless { it == "支付宝" || it == "微信" }.orEmpty())?.let { return it }
        val note = r.note
        if (r.type == TxType.INCOME && "到余额宝" in note) return accounts.yuebao()
        val ali = ALIPAY_WORDS.mapNotNull { w -> note.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val wx = WECHAT_WORDS.mapNotNull { w -> note.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        return when {
            ali != null && (wx == null || ali <= wx) -> accounts.alipay()
            wx != null -> accounts.wechat()
            r.method == "支付宝" -> accounts.alipay()
            r.method == "微信" -> accounts.wechat()
            else -> accounts.defaultId
        }
    }

    // ── 1.2 的微信 / 支付宝 / 通用表格逻辑 ─────────────────────────

    /** 这笔账默认记到哪个账户；[fallback] 是来源对应的账户（微信 / 支付宝），通用表格是用户选的账户。 */
    fun accountFor(r: BillRecord, accounts: List<LedgerAccount>, fallback: String): String {
        val method = r.method
        if (method.isNotBlank()) {
            val active = accounts.filter { !it.archived && it.name.length >= 2 && it.kind.countsInAssets }
            // 支付方式里写着账户名（“招商银行储蓄卡(1234)”→ 账户“招商银行”；“花呗”→ 账户“花呗”）
            active.firstOrNull { method.contains(it.name) }?.let { return it.id }
            if ("花呗" in method) active.firstOrNull { it.kind == AccountKind.CREDIT && "花呗" in it.name }?.let { return it.id }
            if (method == "支付宝") active.firstOrNull { it.kind == AccountKind.ALIPAY }?.let { return it.id }
            if (method == "微信") active.firstOrNull { it.kind == AccountKind.WECHAT }?.let { return it.id }
            if ("信用卡" in method) {
                val bank = method.substringBefore("信用卡").take(4)
                active.firstOrNull { it.kind == AccountKind.CREDIT && bank.isNotEmpty() && it.name.contains(bank) }?.let { return it.id }
            }
            if ("储蓄卡" in method || "借记卡" in method) {
                val bank = method.substringBefore("储蓄卡").substringBefore("借记卡").take(4)
                active.firstOrNull { it.kind == AccountKind.BANK && bank.isNotEmpty() && it.name.contains(bank) }?.let { return it.id }
            }
        }
        return fallback
    }

    /**
     * 来源对应的默认账户：微信账单记到第一个“微信”账户，支付宝记到第一个“支付宝”账户；
     * 还没有时返回一个待新建的账户。通用表格用 [preferred]（用户在预览里选的），否则第一个可用账户。
     */
    fun defaultAccount(source: BillSource, accounts: List<LedgerAccount>, preferred: String? = null, now: Long = 0L): Pair<String, LedgerAccount?> {
        val active = accounts.filter { !it.archived && it.kind.countsInAssets }
        preferred?.let { id -> active.firstOrNull { it.id == id }?.let { return it.id to null } }
        val kind = when (source) {
            BillSource.WECHAT -> AccountKind.WECHAT
            BillSource.ALIPAY -> AccountKind.ALIPAY
            BillSource.CSV, BillSource.APP -> null
        }
        if (kind != null) {
            active.firstOrNull { it.kind == kind }?.let { return it.id to null }
            val created = LedgerAccount(
                id = LedgerIds.newId(),
                name = if (kind == AccountKind.WECHAT) "微信" else "支付宝",
                kind = kind,
                createdAt = now,
            )
            return created.id to created
        }
        val first = active.firstOrNull() ?: run {
            val created = LedgerAccount(LedgerIds.newId(), "现金", AccountKind.CASH, createdAt = now)
            return created.id to created
        }
        return first.id to null
    }

    fun preview(
        parsed: ParsedBill,
        data: LedgerData,
        zone: ZoneId,
        preferredAccount: String? = null,
        now: Long = System.currentTimeMillis(),
        fileName: String = "",
    ): ImportPreview {
        val existingIds = data.transactions.mapTo(HashSet()) { it.id }
        // 按金额建索引，查“三分钟内同金额”的已有记录
        val byAmount = data.transactions.filter { it.type != TxType.TRANSFER }.groupBy { it.amount to it.type }
        val app = parsed.source == BillSource.APP
        val pool = Accounts(data.accounts, now, preferredAccount)
        val cats = Categories(data.categories, now)
        val (fallback, legacyCreated) = if (app) pool.defaultId to null else defaultAccount(parsed.source, data.accounts, preferredAccount, now)
        val legacyAccounts = data.accounts + listOfNotNull(legacyCreated)
        var already = 0
        val candidates = ArrayList<ImportCandidate>()
        for (r in parsed.records) {
            val id = transactionId(r)
            if (id in existingIds) {
                already++
                continue
            }
            val at = r.time.atZone(zone).toInstant().toEpochMilli()
            val klass = classify(r)
            val similar = klass in NORMAL_LIKE &&
                byAmount[r.amount to r.type]?.any { kotlin.math.abs(it.occurredAt - at) <= SIMILAR_WINDOW_MS } == true
            val flag = when {
                r.refund -> CandidateFlag.REFUND
                similar -> CandidateFlag.SIMILAR
                else -> CandidateFlag.NEW
            }
            val base = if (app) appAccount(r, pool) else accountFor(r, legacyAccounts, fallback)
            var from = base
            var to: String? = null
            when (klass) {
                ImportClass.NORMAL, ImportClass.FEE -> Unit
                ImportClass.CREDIT -> from = pool.huabei()
                ImportClass.ADJUSTMENT -> if (r.type == TxType.INCOME) { from = pool.adjust(); to = base } else { to = pool.adjust() }
                ImportClass.LOAN -> {
                    val person = counterparty(r.note)
                    if (r.item.trim() == "借出") { to = pool.lent(person) } else { from = pool.borrowed(person); to = base }
                }
                ImportClass.INTERNAL -> when (move(r)) {
                    Move.TO_YUEBAO -> { from = pool.alipay(); to = pool.yuebao() }
                    Move.FROM_YUEBAO -> { from = pool.yuebao(); to = pool.alipay() }
                    Move.TO_INVEST -> to = pool.invest()
                    Move.WITHDRAW -> { from = if (base == pool.defaultId) pool.wechat() else base; to = pool.bank() }
                    Move.REPAY_CREDIT -> to = pool.huabei()
                    null -> Unit
                }
            }
            if (to != null && to == from) to = null // 同一个账户之间转账没有意义：退回原来的收支
            val transfer = to != null
            candidates += ImportCandidate(
                id = id,
                record = r,
                categoryId = if (transfer) null else if (app) {
                    // 与原 App 一比一：用它自己的类别 / 二级分类，缺的就建
                    cats.resolve(r.type, r.kind, r.item)
                } else {
                    BillCategorizer.categorize(r.type, listOf(r.counterparty, r.item, r.note), data.categories, r.time.toLocalTime(), r.kind)
                },
                accountId = from,
                checked = flag == CandidateFlag.NEW && klass != ImportClass.ADJUSTMENT,
                flag = flag,
                klass = if (klass in TRANSFER_LIKE && !transfer) ImportClass.NORMAL else klass,
                txType = if (transfer) TxType.TRANSFER else r.type,
                toAccountId = to,
            )
        }
        val created = if (app) pool.created.toList() else listOfNotNull(legacyCreated)
        return ImportPreview(parsed.source, candidates, parsed.skipped, already, created, fallback, fileName, cats.created.toList())
    }

    /** 勾选的行 → 流水。备注是“交易对方 · 商品”（记账 App 用它自己的备注，借贷的对方也在里面）。 */
    fun transactions(preview: ImportPreview, zone: ZoneId, now: Long, batchId: String? = null): List<LedgerTransaction> =
        preview.selected.map { c ->
            val r = c.record
            LedgerTransaction(
                id = c.id,
                type = c.txType,
                amount = r.amount,
                occurredAt = r.time.atZone(zone).toInstant().toEpochMilli(),
                accountId = c.accountId,
                toAccountId = c.toAccountId.takeIf { c.txType == TxType.TRANSFER },
                categoryId = c.categoryId.takeIf { c.txType != TxType.TRANSFER },
                note = r.summary,
                createdAt = now,
                updatedAt = now,
                importBatch = batchId,
            )
        }

    /** 导入前后每个账户的余额（只列这次动到的账户；“余额调整”内部账户不列）。 */
    fun balanceChanges(before: LedgerData, imported: List<LedgerTransaction>, newAccounts: List<LedgerAccount>): List<BalanceChange> {
        val accounts = before.accounts + newAccounts.filter { a -> before.accounts.none { it.id == a.id } }
        val touched = LinkedHashSet<String>()
        imported.forEach { touched += it.accountId; it.toAccountId?.let(touched::add) }
        val old = LedgerMath.balances(accounts, before.transactions)
        val new = LedgerMath.balances(accounts, before.transactions + imported)
        val createdIds = newAccounts.mapTo(HashSet()) { it.id }
        return accounts.filter { it.id in touched && it.kind.countsInAssets }.map {
            BalanceChange(it.id, it.name, old[it.id] ?: 0L, new[it.id] ?: 0L, it.id in createdIds && before.accounts.none { a -> a.id == it.id })
        }
    }

    fun result(
        preview: ImportPreview,
        imported: List<LedgerTransaction>,
        before: LedgerData? = null,
        batchId: String? = null,
    ): ImportResult = ImportResult(
        imported = imported.size,
        expenseCount = imported.count { it.type == TxType.EXPENSE },
        expenseTotal = imported.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
        incomeCount = imported.count { it.type == TxType.INCOME },
        incomeTotal = imported.filter { it.type == TxType.INCOME }.sumOf { it.amount },
        unchecked = preview.candidates.size - preview.selected.size,
        alreadyImported = preview.alreadyImported,
        skipped = preview.skipped,
        createdAccount = preview.newAccounts
            .filter { a -> a.kind.countsInAssets && imported.any { it.accountId == a.id || it.toAccountId == a.id } }
            .takeIf { it.isNotEmpty() }?.joinToString("、") { it.name },
        transferCount = imported.count { it.type == TxType.TRANSFER },
        balanceChanges = before?.let { balanceChanges(it, imported, preview.newAccounts) }.orEmpty(),
        batchId = batchId,
    )

    const val SIMILAR_WINDOW_MS = 3 * 60 * 1000L
}

private val NORMAL_LIKE = setOf(ImportClass.NORMAL, ImportClass.CREDIT, ImportClass.FEE)
private val TRANSFER_LIKE = setOf(ImportClass.INTERNAL, ImportClass.LOAN, ImportClass.ADJUSTMENT)
