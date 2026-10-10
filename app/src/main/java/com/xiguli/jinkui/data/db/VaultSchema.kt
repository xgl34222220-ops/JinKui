package com.xiguli.jinkui.data.db

/**
 * 数据库结构与逐级迁移。纯 SQL 字符串，不依赖 Android，迁移测试直接在 JVM 上跑。
 *
 *  - 版本 1（0.1.0 – 0.5.3）：cards / fields / attachments / history。
 *  - 版本 2（0.6.0）：新增账本四张表，旧表不动。
 *  - 版本 3（0.7.0）：新增周期记账规则表，旧表不动。
 *  - 版本 4（0.9.0）：新增账本删除标记表，旧表不动。
 *  - 版本 5（1.2.0）：新增自动记账的“待确认”表，旧表不动。
 *  - 版本 6（1.3.0）：流水加一列导入批次 import_batch，新增导入批次表；已导入的流水按导入时间补上批次。
 *  - 版本 7（1.4.0）：新增欠款表 ledger_debts（信用账户的分期、借贷），旧表不动。
 *
 * 新装用户执行 [create]：版本 1 的建表语句加上之后的全部迁移，保证新装与升级得到同一套结构。
 * 迁移只允许“加”，不得丢弃或改写用户已有的数据。
 */
object VaultSchema {
    const val VERSION = 7

    private val V1 = listOf(
        """CREATE TABLE cards(
            id TEXT PRIMARY KEY NOT NULL,
            type TEXT NOT NULL,
            title TEXT NOT NULL,
            tags TEXT NOT NULL,
            color INTEGER NOT NULL,
            note_enc TEXT NOT NULL,
            pinned INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            last_used_at INTEGER NOT NULL
        )""",
        """CREATE TABLE fields(
            id TEXT PRIMARY KEY NOT NULL,
            card_id TEXT NOT NULL,
            position INTEGER NOT NULL,
            label TEXT NOT NULL,
            value_enc TEXT NOT NULL,
            sensitive INTEGER NOT NULL,
            kind TEXT NOT NULL
        )""",
        "CREATE INDEX fields_card ON fields(card_id)",
        """CREATE TABLE attachments(
            id TEXT PRIMARY KEY NOT NULL,
            card_id TEXT NOT NULL,
            name TEXT NOT NULL,
            mime TEXT NOT NULL,
            size INTEGER NOT NULL,
            created_at INTEGER NOT NULL
        )""",
        "CREATE INDEX attachments_card ON attachments(card_id)",
        """CREATE TABLE history(
            id TEXT PRIMARY KEY NOT NULL,
            card_id TEXT NOT NULL,
            label TEXT NOT NULL,
            value_enc TEXT NOT NULL,
            changed_at INTEGER NOT NULL
        )""",
        "CREATE INDEX history_card ON history(card_id)",
    )

    /**
     * 1 → 2：账本。金额、备注、标签、账户名与分类名都在 data_enc 里（字段级 AES-GCM 密文），
     * 明文列只有 ID、类型、时间与关联 ID，用于排序与关联。
     */
    private val V2 = listOf(
        """CREATE TABLE ledger_accounts(
            id TEXT PRIMARY KEY NOT NULL,
            position INTEGER NOT NULL,
            archived INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            data_enc TEXT NOT NULL
        )""",
        """CREATE TABLE ledger_categories(
            id TEXT PRIMARY KEY NOT NULL,
            parent_id TEXT,
            type TEXT NOT NULL,
            position INTEGER NOT NULL,
            archived INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            data_enc TEXT NOT NULL
        )""",
        """CREATE TABLE ledger_transactions(
            id TEXT PRIMARY KEY NOT NULL,
            type TEXT NOT NULL,
            occurred_at INTEGER NOT NULL,
            account_id TEXT NOT NULL,
            to_account_id TEXT,
            category_id TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            data_enc TEXT NOT NULL
        )""",
        "CREATE INDEX ledger_tx_time ON ledger_transactions(occurred_at)",
        """CREATE TABLE ledger_budgets(
            id TEXT PRIMARY KEY NOT NULL,
            category_id TEXT,
            updated_at INTEGER NOT NULL,
            data_enc TEXT NOT NULL
        )""",
    )

    /**
     * 2 → 3：周期记账规则。金额、账户、分类、频率、起止日期、备注和生成进度全部在 data_enc 里，
     * 明文列只有 ID 与更新时间。
     */
    private val V3 = listOf(
        """CREATE TABLE ledger_recurring(
            id TEXT PRIMARY KEY NOT NULL,
            updated_at INTEGER NOT NULL,
            data_enc TEXT NOT NULL
        )""",
    )

    /**
     * 3 → 4：删除标记。用户删掉的周期生成流水与周期规则在这里留一个 ID，
     * 之后补记周期记账、导入旧备份时都不会把它们加回来。只有 ID、种类与删除时间，没有任何内容。
     */
    private val V4 = listOf(
        """CREATE TABLE ledger_tombstones(
            id TEXT PRIMARY KEY NOT NULL,
            kind TEXT NOT NULL,
            deleted_at INTEGER NOT NULL
        )""",
    )

    /**
     * 4 → 5：自动记账的“待确认”。来源、方向、金额、商户与时间整条在 data_enc 里（字段级 AES-GCM），
     * 明文列只有 ID 与创建时间。不进加密备份。
     */
    private val V5 = listOf(
        """CREATE TABLE ledger_pending(
            id TEXT PRIMARY KEY NOT NULL,
            created_at INTEGER NOT NULL,
            data_enc TEXT NOT NULL
        )""",
    )

    /**
     * 5 → 6：账单导入批次（“导入记录”与“撤销导入”）。
     *  - ledger_transactions 加一列 import_batch（明文，只是随机 ID；手动记的为 NULL）；
     *  - ledger_import_batches：来源、文件名、笔数、自动新建的账户在 data_enc 里（字段级加密），明文只有 ID 与时间；
     *  - 回填：1.2 / 1.3 测试版导入的流水 ID 一律是 "imp-" + 账单内容摘要，同一次导入的 created_at 相同，
     *    所以按 created_at 分组补成“旧版导入”批次（ID = batch-legacy-<created_at>，data_enc 为空串表示没有详情），
     *    升级前导入的账单同样可以整批撤销。只加不改：流水内容与其余列都不动。
     */
    private val V6 = listOf(
        "ALTER TABLE ledger_transactions ADD COLUMN import_batch TEXT",
        "CREATE INDEX ledger_tx_batch ON ledger_transactions(import_batch)",
        """CREATE TABLE ledger_import_batches(
            id TEXT PRIMARY KEY NOT NULL,
            created_at INTEGER NOT NULL,
            data_enc TEXT NOT NULL
        )""",
        """INSERT INTO ledger_import_batches(id, created_at, data_enc)
            SELECT 'batch-legacy-' || created_at, created_at, '' FROM ledger_transactions
            WHERE id LIKE 'imp-%' GROUP BY created_at""",
        "UPDATE ledger_transactions SET import_batch = 'batch-legacy-' || created_at WHERE id LIKE 'imp-%' AND import_batch IS NULL",
    )

    /**
     * 6 → 7：欠款 / 账单（1.4）。分期与借贷的金额、日期、对方账户、备注全部在 data_enc 里（字段级加密），
     * 明文列只有 ID、种类（installment / loan）与更新时间。信用账户的账单日、还款日、额度放在账户自己的加密载荷里，
     * 流水归属哪一笔欠款放在流水的加密载荷里，都不需要改表。
     * 1.3 导入的一木借出 / 借入（每人一个应收 / 应付账户）不需要迁移：读取时按账户合成“导入的借贷”。
     */
    private val V7 = listOf(
        """CREATE TABLE ledger_debts(
            id TEXT PRIMARY KEY NOT NULL,
            kind TEXT NOT NULL,
            updated_at INTEGER NOT NULL,
            data_enc TEXT NOT NULL
        )""",
    )

    private val MIGRATIONS: Map<Int, List<String>> = mapOf(2 to V2, 3 to V3, 4 to V4, 5 to V5, 6 to V6, 7 to V7)

    /** 新装：建出最新结构。 */
    fun create(): List<String> = V1 + upgrade(1, VERSION)

    /** 从 [from] 升到 [to] 需要依次执行的语句。 */
    fun upgrade(from: Int, to: Int): List<String> {
        require(from in 1..to && to <= VERSION) { "不支持从版本 $from 升级到 $to" }
        return (from + 1..to).flatMap { MIGRATIONS[it] ?: error("缺少版本 $it 的迁移") }
    }
}
