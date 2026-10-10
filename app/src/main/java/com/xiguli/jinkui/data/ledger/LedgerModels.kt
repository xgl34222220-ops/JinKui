package com.xiguli.jinkui.data.ledger

import java.util.UUID

/** 账户种类。[key] 写入加密数据与备份，上线后不要改动。 */
enum class AccountKind(val key: String, val label: String) {
    CASH("cash", "现金"),
    BANK("bank", "银行卡"),
    ALIPAY("alipay", "支付宝"),
    WECHAT("wechat", "微信"),
    CREDIT("credit", "信用卡"),
    OTHER("other", "其他"),
    /** 1.3 起：理财 / 余额宝等（资产）。旧版本读到时当作“其他”。 */
    INVEST("invest", "理财"),
    /** 1.3 起：借给别人的钱（应收，资产）。 */
    RECEIVABLE("receivable", "借出（应收）"),
    /** 1.3 起：向别人借的钱（应付，负债，余额为负）。 */
    PAYABLE("payable", "借入（应付）"),
    /**
     * 1.3 起：“余额调整”内部账户。账单里的“账户余额补齐”按用户选择记成它与真实账户之间的转账：
     * 只改真实账户的余额，不计收支；它自己不计入资产、负债与净资产，也不出现在记账面板里。
     */
    ADJUST("adjust", "余额调整");

    /** 是否计入资产 / 负债 / 净资产。 */
    val countsInAssets: Boolean get() = this != ADJUST

    /** 是否负债类账户（信用卡、花呗、借入）。 */
    val isLiability: Boolean get() = this == CREDIT || this == PAYABLE

    companion object {
        fun of(key: String?): AccountKind = entries.firstOrNull { it.key == key } ?: OTHER

        /** 用户在“管理账户”里可以手动选择的种类（余额调整只由导入自动建）。 */
        val userSelectable: List<AccountKind> get() = entries.filter { it != ADJUST }
    }
}

/** 收支类型。[key] 写入数据库与备份，上线后不要改动。 */
enum class TxType(val key: String, val label: String) {
    EXPENSE("expense", "支出"),
    INCOME("income", "收入"),
    TRANSFER("transfer", "转账");

    companion object {
        fun of(key: String?): TxType = entries.firstOrNull { it.key == key } ?: EXPENSE
    }
}

/**
 * 账户。余额不落库，始终由 [openingBalance] 加上全部流水算出来（[LedgerMath.balances]），
 * 所以改一笔、删一笔都不会让余额对不上。
 */
data class LedgerAccount(
    val id: String,
    val name: String,
    val kind: AccountKind,
    /** 期初余额，单位：分。信用卡欠款记为负数。 */
    val openingBalance: Long = 0L,
    /** 关联的卡包里的银行卡；只是一个引用，卡片删掉后自动失效。 */
    val cardId: String? = null,
    /** 停用的账户不再出现在记账面板里，历史流水照常统计。 */
    val archived: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** 1.4 起：信用账户的账单设置（额度、账单日、还款日）；只对 [AccountKind.CREDIT] 有意义。 */
    val credit: CreditSettings? = null,
)

/** 收支分类，最多两级：[parentId] 为 null 的是一级分类。 */
data class LedgerCategory(
    val id: String,
    val name: String,
    /** 只会是 [TxType.EXPENSE] 或 [TxType.INCOME]。 */
    val type: TxType,
    val parentId: String? = null,
    /** 图标键，界面层映射到具体图标。 */
    val icon: String = "more",
    val position: Int = 0,
    /** 隐藏的分类不再出现在记账面板里；分类从不真正删除，避免流水失去归属。 */
    val archived: Boolean = false,
    val updatedAt: Long = 0L,
)

/** 一笔流水。金额一律用“分”为单位的 Long，永远是正数，方向由 [type] 决定。 */
data class LedgerTransaction(
    val id: String,
    val type: TxType,
    val amount: Long,
    val occurredAt: Long,
    /** 支出 / 收入的账户；转账时为转出账户。 */
    val accountId: String,
    /** 只有转账才有：转入账户。 */
    val toAccountId: String? = null,
    /** 转账没有分类。 */
    val categoryId: String? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** 1.3 起：账单导入的批次（[LedgerImportBatch.id]）；手动记的为 null。只存本机，不进备份。 */
    val importBatch: String? = null,
    /** 1.4 起：这笔流水属于哪一笔分期（[LedgerInstallment.id]）或借贷（[LedgerLoan.id]）；在加密载荷里，随流水进备份。 */
    val debtId: String? = null,
)

/**
 * 一次账单导入（1.3 起，数据库版本 6）。用来在“导入记录”里列出、整批撤销。
 * 来源、文件名、笔数与自动新建的账户在 data_enc 里（字段级加密）；1.3 之前导入的流水在升级时按导入时间
 * 补成“旧版导入”批次，那些批次没有这些信息（[legacy] 为 true）。只存本机，不进备份。
 */
data class LedgerImportBatch(
    val id: String,
    val createdAt: Long,
    val source: String = "",
    val fileName: String = "",
    val count: Int = 0,
    /** 这次导入自动新建的账户；撤销时若已没有任何流水就一并删掉。 */
    val createdAccounts: List<String> = emptyList(),
    val legacy: Boolean = false,
) {
    companion object {
        const val LEGACY_PREFIX = "batch-legacy-"
    }
}

/** 每月预算。[categoryId] 为 null 表示总预算；按月循环生效。 */
data class LedgerBudget(
    val id: String,
    val categoryId: String?,
    val amount: Long,
    val updatedAt: Long = 0L,
) {
    companion object {
        const val TOTAL_ID = "budget-total"
        fun idFor(categoryId: String?): String = if (categoryId == null) TOTAL_ID else "budget-$categoryId"
    }
}

/** 账本的全部内容。解锁后整体解密到内存，锁定时丢弃。 */
data class LedgerData(
    val accounts: List<LedgerAccount> = emptyList(),
    val categories: List<LedgerCategory> = emptyList(),
    val transactions: List<LedgerTransaction> = emptyList(),
    val budgets: List<LedgerBudget> = emptyList(),
    /** 周期记账规则（0.7.0 起）。 */
    val recurring: List<LedgerRecurring> = emptyList(),
    /** 解密失败（数据损坏）而没有载入的记录数。 */
    val unreadable: Int = 0,
    /** 删除记录（0.9.0 起）：用户删掉的周期生成流水与周期规则，导入旧备份时不再加回来。 */
    val tombstones: List<LedgerTombstone> = emptyList(),
    /** 账单导入批次（1.3 起）。只存本机，不进备份。 */
    val importBatches: List<LedgerImportBatch> = emptyList(),
    /** 信用账户上的分期（1.4 起，数据库版本 7、备份 schema 5）。 */
    val installments: List<LedgerInstallment> = emptyList(),
    /** 借贷（1.4 起）。 */
    val loans: List<LedgerLoan> = emptyList(),
) {
    val isEmpty: Boolean
        get() = accounts.isEmpty() && categories.isEmpty() && transactions.isEmpty() && budgets.isEmpty() && recurring.isEmpty() &&
            installments.isEmpty() && loans.isEmpty()

    companion object {
        val EMPTY = LedgerData()
    }
}

/**
 * 一条“已删除”标记。只记两类会被旧备份或周期记账“复活”的记录：
 *  - [KIND_TX]：周期记账生成的流水（ID 由规则 + 日期决定，删掉后同一 ID 可能被再次生成或从旧备份加回），
 *    以及（1.3 起）账单导入的流水（ID 由账单内容决定，删掉后导入旧备份不再加回；重新导入同一份账单时由用户决定）；
 *  - [KIND_RECURRING]：周期记账规则本身（旧备份里还留着它，加回来会接着生成）。
 *  - [KIND_DEBT]（1.4 起）：删掉的分期与借贷（它们名下的流水同时按 [KIND_TX] 记标记）。
 * 只有 ID、种类与删除时间，不含金额、备注等内容。
 */
data class LedgerTombstone(val id: String, val kind: String, val deletedAt: Long) {
    companion object {
        const val KIND_TX = "tx"
        const val KIND_RECURRING = "recurring"
        const val KIND_DEBT = "debt"
        val KINDS = setOf(KIND_TX, KIND_RECURRING, KIND_DEBT)
    }
}

object LedgerIds {
    fun newId(): String = UUID.randomUUID().toString()
}

/**
 * 首次使用账本时预置的分类与账户。ID 固定，两台手机互相导入备份时不会出现重复分类；
 * 更新时间固定为 0，这样任何一台手机上用户改过的版本在合并时都比预置的新。
 */
object LedgerDefaults {
    private class Node(val id: String, val name: String, val icon: String, val children: List<Pair<String, String>> = emptyList())

    private val expense = listOf(
        Node("food", "餐饮", "restaurant", listOf("早餐" to "breakfast", "午餐" to "lunch", "晚餐" to "dinner", "饮品" to "cafe", "零食水果" to "snack")),
        Node("transport", "交通", "bus", listOf("公交地铁" to "subway", "打车" to "taxi", "加油" to "gas", "停车" to "parking", "火车飞机" to "train")),
        Node("shopping", "购物", "shopping", listOf("日用品" to "cart", "服饰鞋包" to "clothes", "数码电器" to "devices", "家居" to "chair")),
        Node("housing", "居住", "home", listOf("房租房贷" to "apartment", "水电燃气" to "bolt", "物业" to "home", "网费话费" to "wifi")),
        Node("fun", "娱乐", "movie", listOf("电影演出" to "movie", "游戏" to "game", "旅行" to "luggage", "运动健身" to "fitness")),
        Node("medical", "医疗", "hospital", listOf("看病" to "hospital", "药品" to "medication")),
        Node("education", "学习", "school", listOf("书籍" to "book", "课程" to "school")),
        Node("social", "人情", "gift", listOf("红包" to "redeem", "礼物" to "gift", "聚会请客" to "people")),
        Node("family", "家庭", "child", listOf("孩子" to "child", "宠物" to "pets", "长辈" to "people")),
        Node("other-expense", "其他支出", "more"),
    )

    private val income = listOf(
        Node("salary", "工资", "payments"),
        Node("bonus", "奖金", "trophy"),
        Node("invest", "理财收益", "chart"),
        Node("parttime", "兼职副业", "work"),
        Node("red-packet", "收红包", "redeem"),
        Node("refund", "退款", "refund"),
        Node("other-income", "其他收入", "more"),
    )

    fun categories(): List<LedgerCategory> {
        val result = ArrayList<LedgerCategory>()
        for ((type, nodes) in listOf(TxType.EXPENSE to expense, TxType.INCOME to income)) {
            nodes.forEachIndexed { index, node ->
                val parentId = "cat-${node.id}"
                result += LedgerCategory(parentId, node.name, type, null, node.icon, index, false, 0L)
                node.children.forEachIndexed { childIndex, (name, icon) ->
                    result += LedgerCategory("$parentId-${childIndex + 1}", name, type, parentId, icon, childIndex, false, 0L)
                }
            }
        }
        return result
    }

    const val CASH_ACCOUNT_ID = "acc-cash"

    fun accounts(createdAt: Long = 0L): List<LedgerAccount> = listOf(
        LedgerAccount(CASH_ACCOUNT_ID, "现金", AccountKind.CASH, 0L, null, false, 0, createdAt, 0L),
    )
}
