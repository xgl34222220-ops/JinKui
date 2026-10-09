package com.xiguli.jinkui.data.db

/**
 * 数据库结构与逐级迁移。纯 SQL 字符串，不依赖 Android，迁移测试直接在 JVM 上跑。
 *
 *  - 版本 1（0.1.0 – 0.5.3）：cards / fields / attachments / history。
 *  - 版本 2（0.6.0）：新增账本四张表，旧表不动。
 *  - 版本 3（0.7.0）：新增周期记账规则表，旧表不动。
 *
 * 新装用户执行 [create]：版本 1 的建表语句加上之后的全部迁移，保证新装与升级得到同一套结构。
 * 迁移只允许“加”，不得丢弃或改写用户已有的数据。
 */
object VaultSchema {
    const val VERSION = 3

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

    private val MIGRATIONS: Map<Int, List<String>> = mapOf(2 to V2, 3 to V3)

    /** 新装：建出最新结构。 */
    fun create(): List<String> = V1 + upgrade(1, VERSION)

    /** 从 [from] 升到 [to] 需要依次执行的语句。 */
    fun upgrade(from: Int, to: Int): List<String> {
        require(from in 1..to && to <= VERSION) { "不支持从版本 $from 升级到 $to" }
        return (from + 1..to).flatMap { MIGRATIONS[it] ?: error("缺少版本 $it 的迁移") }
    }
}
