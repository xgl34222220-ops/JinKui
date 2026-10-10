package com.xiguli.jinkui.data.db

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.xiguli.jinkui.core.crypto.wipe
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class CardRow(
    val id: String,
    val type: String,
    val title: String,
    val tags: String,
    val color: Int,
    val noteEnc: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long,
)

class FieldRow(
    val id: String,
    val cardId: String,
    val position: Int,
    val label: String,
    val valueEnc: String,
    val sensitive: Boolean,
    val kind: String,
)

class AttachmentRow(
    val id: String,
    val cardId: String,
    val name: String,
    val mime: String,
    val size: Long,
    val createdAt: Long,
)

class HistoryRow(
    val id: String,
    val cardId: String,
    val label: String,
    val valueEnc: String,
    val changedAt: Long,
)

class LedgerAccountRow(
    val id: String,
    val position: Int,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val dataEnc: String,
)

class LedgerCategoryRow(
    val id: String,
    val parentId: String?,
    val type: String,
    val position: Int,
    val archived: Boolean,
    val updatedAt: Long,
    val dataEnc: String,
)

class LedgerTxRow(
    val id: String,
    val type: String,
    val occurredAt: Long,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val dataEnc: String,
    /** 导入批次（版本 6 起），手动记的为 null。 */
    val importBatch: String? = null,
)

/** 一次账单导入（版本 6 起）。data_enc 为空串表示升级时补出来的“旧版导入”批次。 */
class LedgerImportBatchRow(val id: String, val createdAt: Long, val dataEnc: String)

class LedgerBudgetRow(
    val id: String,
    val categoryId: String?,
    val updatedAt: Long,
    val dataEnc: String,
)

class LedgerRecurringRow(
    val id: String,
    val updatedAt: Long,
    val dataEnc: String,
)

/** 一笔分期或借贷（版本 7 起）。[kind] 是 installment / loan，其余都在 data_enc 里。 */
class LedgerDebtRow(val id: String, val kind: String, val updatedAt: Long, val dataEnc: String)

/** 自动记账的一笔待确认（data_enc 是字段级密文）。 */
class LedgerPendingRow(val id: String, val createdAt: Long, val dataEnc: String)

/** 一次写入的一批账本记录（导入备份、首次预置）。 */
class LedgerTombstoneRow(val id: String, val kind: String, val deletedAt: Long)

class LedgerBatch(
    val accounts: List<LedgerAccountRow> = emptyList(),
    val categories: List<LedgerCategoryRow> = emptyList(),
    val transactions: List<LedgerTxRow> = emptyList(),
    val budgets: List<LedgerBudgetRow> = emptyList(),
    val recurring: List<LedgerRecurringRow> = emptyList(),
    val tombstones: List<LedgerTombstoneRow> = emptyList(),
    val debts: List<LedgerDebtRow> = emptyList(),
)

/**
 * SQLCipher 加密数据库。整库以保险库密钥派生出的 256 位原始密钥加密；
 * 字段值、备注在入库前还会再做一层字段级 AES-GCM，这里看到的都是密文。
 */
class VaultDatabase(private val context: Context) {
    private val lock = Any()
    private var helper: SupportSQLiteOpenHelper? = null
    private var passphrase: ByteArray? = null

    val isOpen: Boolean get() = synchronized(lock) { helper != null }

    fun open(rawKey: ByteArray) = synchronized(lock) {
        closeLocked()
        System.loadLibrary("sqlcipher")
        // x'…' 形式告诉 SQLCipher 这已是 256 位原始密钥，不必再做一次口令派生。
        val key = rawKeyLiteral(rawKey)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(Schema)
            .build()
        val created = SupportOpenHelperFactory(key).create(configuration)
        try {
            created.writableDatabase
        } catch (e: Exception) {
            runCatching { created.close() }
            key.wipe()
            throw e
        }
        helper = created
        passphrase = key
    }

    fun close() = synchronized(lock) { closeLocked() }

    /** 关闭并删除数据库文件（输错自毁 / 手动清空）。 */
    fun destroy() = synchronized(lock) {
        closeLocked()
        context.deleteDatabase(DB_NAME)
        val base = context.getDatabasePath(DB_NAME)
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            runCatching { java.io.File(base.path + suffix).delete() }
        }
    }

    fun cards(): List<CardRow> = read("SELECT id,type,title,tags,color,note_enc,pinned,created_at,updated_at,last_used_at FROM cards") {
        CardRow(
            id = it.getString(0),
            type = it.getString(1),
            title = it.getString(2),
            tags = it.getString(3),
            color = it.getInt(4),
            noteEnc = it.getString(5),
            pinned = it.getInt(6) != 0,
            createdAt = it.getLong(7),
            updatedAt = it.getLong(8),
            lastUsedAt = it.getLong(9),
        )
    }

    fun fields(): List<FieldRow> = read("SELECT id,card_id,position,label,value_enc,sensitive,kind FROM fields ORDER BY card_id, position") {
        FieldRow(
            id = it.getString(0),
            cardId = it.getString(1),
            position = it.getInt(2),
            label = it.getString(3),
            valueEnc = it.getString(4),
            sensitive = it.getInt(5) != 0,
            kind = it.getString(6),
        )
    }

    fun attachments(): List<AttachmentRow> = read("SELECT id,card_id,name,mime,size,created_at FROM attachments ORDER BY created_at") {
        AttachmentRow(
            id = it.getString(0),
            cardId = it.getString(1),
            name = it.getString(2),
            mime = it.getString(3),
            size = it.getLong(4),
            createdAt = it.getLong(5),
        )
    }

    fun history(cardId: String? = null): List<HistoryRow> {
        val sql = "SELECT id,card_id,label,value_enc,changed_at FROM history" +
            (if (cardId != null) " WHERE card_id = ?" else "") + " ORDER BY changed_at DESC"
        val args: Array<Any?> = if (cardId != null) arrayOf(cardId) else emptyArray()
        return read(sql, args) {
            HistoryRow(
                id = it.getString(0),
                cardId = it.getString(1),
                label = it.getString(2),
                valueEnc = it.getString(3),
                changedAt = it.getLong(4),
            )
        }
    }

    /** 一次事务内写入卡片、字段、附件索引与新增的历史记录。 */
    fun saveCard(
        card: CardRow,
        fields: List<FieldRow>,
        addedAttachments: List<AttachmentRow>,
        removedAttachmentIds: List<String>,
        newHistory: List<HistoryRow>,
    ) = transaction { db -> writeCard(db, card, fields, addedAttachments, removedAttachmentIds, newHistory) }

    /**
     * 整张替换（导入备份时用）：删掉这张卡片原有的字段、附件索引与历史，再写入新内容。
     * 两步在同一个事务里完成，中途失败时本机原来那张卡片原样保留。
     */
    fun replaceCard(
        card: CardRow,
        fields: List<FieldRow>,
        attachments: List<AttachmentRow>,
        history: List<HistoryRow>,
    ) = transaction { db ->
        deleteCardRows(db, card.id)
        writeCard(db, card, fields, attachments, emptyList(), history)
    }

    private fun writeCard(
        db: SupportSQLiteDatabase,
        card: CardRow,
        fields: List<FieldRow>,
        addedAttachments: List<AttachmentRow>,
        removedAttachmentIds: List<String>,
        newHistory: List<HistoryRow>,
    ) {
        db.execSQL(
            "INSERT OR REPLACE INTO cards(id,type,title,tags,color,note_enc,pinned,created_at,updated_at,last_used_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                card.id, card.type, card.title, card.tags, card.color, card.noteEnc,
                if (card.pinned) 1 else 0, card.createdAt, card.updatedAt, card.lastUsedAt,
            ),
        )
        db.execSQL("DELETE FROM fields WHERE card_id = ?", arrayOf<Any?>(card.id))
        for (field in fields) {
            db.execSQL(
                "INSERT INTO fields(id,card_id,position,label,value_enc,sensitive,kind) VALUES(?,?,?,?,?,?,?)",
                arrayOf<Any?>(
                    field.id, field.cardId, field.position, field.label, field.valueEnc,
                    if (field.sensitive) 1 else 0, field.kind,
                ),
            )
        }
        for (id in removedAttachmentIds) {
            db.execSQL("DELETE FROM attachments WHERE id = ?", arrayOf<Any?>(id))
        }
        for (attachment in addedAttachments) {
            db.execSQL(
                "INSERT OR REPLACE INTO attachments(id,card_id,name,mime,size,created_at) VALUES(?,?,?,?,?,?)",
                arrayOf<Any?>(
                    attachment.id, attachment.cardId, attachment.name, attachment.mime,
                    attachment.size, attachment.createdAt,
                ),
            )
        }
        for (entry in newHistory) {
            db.execSQL(
                "INSERT OR REPLACE INTO history(id,card_id,label,value_enc,changed_at) VALUES(?,?,?,?,?)",
                arrayOf<Any?>(entry.id, entry.cardId, entry.label, entry.valueEnc, entry.changedAt),
            )
        }
    }

    fun deleteCard(id: String) = transaction { db -> deleteCardRows(db, id) }

    private fun deleteCardRows(db: SupportSQLiteDatabase, id: String) {
        db.execSQL("DELETE FROM fields WHERE card_id = ?", arrayOf<Any?>(id))
        db.execSQL("DELETE FROM attachments WHERE card_id = ?", arrayOf<Any?>(id))
        db.execSQL("DELETE FROM history WHERE card_id = ?", arrayOf<Any?>(id))
        db.execSQL("DELETE FROM cards WHERE id = ?", arrayOf<Any?>(id))
    }

    fun setPinned(id: String, pinned: Boolean) = transaction { db ->
        db.execSQL("UPDATE cards SET pinned = ? WHERE id = ?", arrayOf<Any?>(if (pinned) 1 else 0, id))
    }

    fun touch(id: String, time: Long) = transaction { db ->
        db.execSQL("UPDATE cards SET last_used_at = ? WHERE id = ?", arrayOf<Any?>(time, id))
    }

    fun clearHistory(cardId: String) = transaction { db ->
        db.execSQL("DELETE FROM history WHERE card_id = ?", arrayOf<Any?>(cardId))
    }

    // ── 账本 ──────────────────────────────────────────────────────
    // 金额、备注等都在 data_enc 里，是字段级密文。

    fun ledgerAccounts(): List<LedgerAccountRow> =
        read("SELECT id,position,archived,created_at,updated_at,data_enc FROM ledger_accounts ORDER BY position, created_at") {
            LedgerAccountRow(it.getString(0), it.getInt(1), it.getInt(2) != 0, it.getLong(3), it.getLong(4), it.getString(5))
        }

    fun ledgerCategories(): List<LedgerCategoryRow> =
        read("SELECT id,parent_id,type,position,archived,updated_at,data_enc FROM ledger_categories ORDER BY position") {
            LedgerCategoryRow(
                it.getString(0), if (it.isNull(1)) null else it.getString(1), it.getString(2),
                it.getInt(3), it.getInt(4) != 0, it.getLong(5), it.getString(6),
            )
        }

    fun ledgerTransactions(): List<LedgerTxRow> =
        read("SELECT id,type,occurred_at,account_id,to_account_id,category_id,created_at,updated_at,data_enc,import_batch FROM ledger_transactions ORDER BY occurred_at DESC") {
            LedgerTxRow(
                it.getString(0), it.getString(1), it.getLong(2), it.getString(3),
                if (it.isNull(4)) null else it.getString(4),
                if (it.isNull(5)) null else it.getString(5),
                it.getLong(6), it.getLong(7), it.getString(8),
                if (it.isNull(9)) null else it.getString(9),
            )
        }

    // ── 账单导入批次（版本 6 起）──────────────────────────────────

    fun ledgerImportBatches(): List<LedgerImportBatchRow> =
        read("SELECT id,created_at,data_enc FROM ledger_import_batches ORDER BY created_at DESC") {
            LedgerImportBatchRow(it.getString(0), it.getLong(1), it.getString(2))
        }

    /**
     * 一个事务里删除一批流水并记下删除标记（批量删除、撤销导入、按时间段删除共用）；
     * [batchId] 不为空时同时删掉这条导入记录，[accountIds] 里已经没有任何流水引用的账户也一并删除。
     */
    fun deleteLedgerTransactions(
        ids: Collection<String>,
        tombstones: List<LedgerTombstoneRow>,
        batchId: String? = null,
        accountIds: Collection<String> = emptyList(),
    ) = transaction { db ->
        ids.forEach { db.execSQL("DELETE FROM ledger_transactions WHERE id = ?", arrayOf<Any?>(it)) }
        tombstones.forEach { writeTombstone(db, it) }
        batchId?.let { db.execSQL("DELETE FROM ledger_import_batches WHERE id = ?", arrayOf<Any?>(it)) }
        for (id in accountIds) {
            val used = db.query(
                "SELECT COUNT(*) FROM ledger_transactions WHERE account_id = ? OR to_account_id = ?",
                arrayOf<Any?>(id, id),
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            if (used == 0) db.execSQL("DELETE FROM ledger_accounts WHERE id = ?", arrayOf<Any?>(id))
        }
    }

    /** 撤销删除：把删掉的流水原样写回，并去掉它们的删除标记。 */
    fun restoreLedgerTransactions(rows: List<LedgerTxRow>) = transaction { db ->
        rows.forEach {
            writeTransaction(db, it)
            db.execSQL("DELETE FROM ledger_tombstones WHERE id = ?", arrayOf<Any?>(it.id))
        }
    }

    fun ledgerBudgets(): List<LedgerBudgetRow> =
        read("SELECT id,category_id,updated_at,data_enc FROM ledger_budgets") {
            LedgerBudgetRow(it.getString(0), if (it.isNull(1)) null else it.getString(1), it.getLong(2), it.getString(3))
        }

    fun ledgerRecurring(): List<LedgerRecurringRow> =
        read("SELECT id,updated_at,data_enc FROM ledger_recurring") {
            LedgerRecurringRow(it.getString(0), it.getLong(1), it.getString(2))
        }

    fun saveLedgerRecurring(row: LedgerRecurringRow) = transaction { db -> writeRecurring(db, row) }

    /** 只删除规则；它已经生成的流水保留。同一事务里记下删除标记，旧备份里的这条规则不会被加回来。 */
    fun deleteLedgerRecurring(id: String, tombstone: LedgerTombstoneRow? = null) = transaction { db ->
        db.execSQL("DELETE FROM ledger_recurring WHERE id = ?", arrayOf<Any?>(id))
        tombstone?.let { writeTombstone(db, it) }
    }

    fun ledgerTombstones(): List<LedgerTombstoneRow> =
        read("SELECT id,kind,deleted_at FROM ledger_tombstones") {
            LedgerTombstoneRow(it.getString(0), it.getString(1), it.getLong(2))
        }

    /**
     * 周期记账：在一个事务里写入新生成的流水并推进规则进度。
     * 流水用 INSERT OR IGNORE：同一个 ID（同一规则同一天）已经存在时不会覆盖用户改过的那一笔，也不会重复。
     */
    fun applyRecurring(transactions: List<LedgerTxRow>, rules: List<LedgerRecurringRow>) = transaction { db ->
        transactions.forEach { writeTransaction(db, it, ignoreExisting = true) }
        rules.forEach { writeRecurring(db, it) }
    }

    fun ledgerCategoryCount(): Int = read("SELECT COUNT(*) FROM ledger_categories") { it.getInt(0) }.single()

    /** 一个事务内写入整批账本记录（同 ID 覆盖）。 */
    fun writeLedger(batch: LedgerBatch) = transaction { db ->
        batch.accounts.forEach { writeAccount(db, it) }
        batch.categories.forEach { writeCategory(db, it) }
        batch.transactions.forEach { writeTransaction(db, it) }
        batch.budgets.forEach { writeBudget(db, it) }
        batch.recurring.forEach { writeRecurring(db, it) }
        batch.tombstones.forEach { writeTombstone(db, it) }
        batch.debts.forEach { writeDebt(db, it) }
    }

    // ── 欠款：分期与借贷（版本 7 起）────────────────────────────────

    fun ledgerDebts(): List<LedgerDebtRow> =
        read("SELECT id,kind,updated_at,data_enc FROM ledger_debts") {
            LedgerDebtRow(it.getString(0), it.getString(1), it.getLong(2), it.getString(3))
        }

    /**
     * 欠款的一次改动，一个事务：新建 / 更新账户（借贷的对方）、写入欠款记录与流水（还款、利息、手续费等）、
     * 删除欠款记录与流水并记下删除标记。任何一步失败整体回滚。
     */
    fun writeDebtChange(
        accounts: List<LedgerAccountRow> = emptyList(),
        debts: List<LedgerDebtRow> = emptyList(),
        transactions: List<LedgerTxRow> = emptyList(),
        deleteDebts: Collection<String> = emptyList(),
        deleteTransactions: Collection<String> = emptyList(),
        tombstones: List<LedgerTombstoneRow> = emptyList(),
    ) = transaction { db ->
        accounts.forEach { writeAccount(db, it) }
        deleteTransactions.forEach { db.execSQL("DELETE FROM ledger_transactions WHERE id = ?", arrayOf<Any?>(it)) }
        deleteDebts.forEach { db.execSQL("DELETE FROM ledger_debts WHERE id = ?", arrayOf<Any?>(it)) }
        debts.forEach { writeDebt(db, it) }
        transactions.forEach { writeTransaction(db, it) }
        tombstones.forEach { writeTombstone(db, it) }
    }

    private fun writeDebt(db: SupportSQLiteDatabase, row: LedgerDebtRow) {
        db.execSQL(
            "INSERT OR REPLACE INTO ledger_debts(id,kind,updated_at,data_enc) VALUES(?,?,?,?)",
            arrayOf<Any?>(row.id, row.kind, row.updatedAt, row.dataEnc),
        )
    }

    fun saveLedgerTransaction(row: LedgerTxRow) = transaction { db -> writeTransaction(db, row) }

    /**
     * 账单导入：一个事务里写入（可选的）新账户与全部流水。流水用 INSERT OR IGNORE，
     * 同一个导入 ID 已经存在时保留本机那一笔（用户可能改过分类）。
     */
    fun importLedger(account: LedgerAccountRow?, transactions: List<LedgerTxRow>) = importLedger(listOfNotNull(account), transactions, null)

    fun importLedger(accounts: List<LedgerAccountRow>, transactions: List<LedgerTxRow>, batch: LedgerImportBatchRow?) =
        importLedger(accounts, emptyList(), transactions, batch)

    /**
     * 账单导入（1.3）：一个事务里写入自动新建的账户、按原 App 新建的分类、全部流水与这次的导入记录。
     * 重新导入以前删掉 / 撤销过的同一笔时，去掉它的删除标记（这是用户明确的选择）。
     */
    fun importLedger(
        accounts: List<LedgerAccountRow>,
        categories: List<LedgerCategoryRow>,
        transactions: List<LedgerTxRow>,
        batch: LedgerImportBatchRow?,
    ) = transaction { db ->
        accounts.forEach { writeAccount(db, it) }
        categories.forEach { writeCategory(db, it) }
        transactions.forEach {
            writeTransaction(db, it, ignoreExisting = true)
            db.execSQL("DELETE FROM ledger_tombstones WHERE id = ?", arrayOf<Any?>(it.id))
        }
        batch?.let {
            db.execSQL(
                "INSERT OR REPLACE INTO ledger_import_batches(id,created_at,data_enc) VALUES(?,?,?)",
                arrayOf<Any?>(it.id, it.createdAt, it.dataEnc),
            )
        }
    }

    // ── 自动记账：待确认 ──────────────────────────────────────────

    fun ledgerPending(): List<LedgerPendingRow> =
        read("SELECT id,created_at,data_enc FROM ledger_pending ORDER BY created_at") {
            LedgerPendingRow(it.getString(0), it.getLong(1), it.getString(2))
        }

    fun insertPending(rows: List<LedgerPendingRow>) = transaction { db ->
        rows.forEach {
            db.execSQL(
                "INSERT OR REPLACE INTO ledger_pending(id,created_at,data_enc) VALUES(?,?,?)",
                arrayOf<Any?>(it.id, it.createdAt, it.dataEnc),
            )
        }
    }

    fun deletePending(ids: Collection<String>) = transaction { db ->
        ids.forEach { db.execSQL("DELETE FROM ledger_pending WHERE id = ?", arrayOf<Any?>(it)) }
    }

    /** 确认待确认：写入流水并删除对应的待确认，在同一个事务里（可选地顺带新建账户）。 */
    fun confirmPending(account: LedgerAccountRow?, transactions: List<LedgerTxRow>, pendingIds: Collection<String>) = transaction { db ->
        account?.let { writeAccount(db, it) }
        transactions.forEach { writeTransaction(db, it) }
        pendingIds.forEach { db.execSQL("DELETE FROM ledger_pending WHERE id = ?", arrayOf<Any?>(it)) }
    }

    /** 删除流水；[tombstone] 不为空时（周期生成的流水）在同一事务里记下删除标记。 */
    fun deleteLedgerTransaction(id: String, tombstone: LedgerTombstoneRow? = null) = transaction { db ->
        db.execSQL("DELETE FROM ledger_transactions WHERE id = ?", arrayOf<Any?>(id))
        tombstone?.let { writeTombstone(db, it) }
    }

    fun saveLedgerAccount(row: LedgerAccountRow) = transaction { db -> writeAccount(db, row) }

    /** 只删除没有任何流水引用的账户；有引用时抛异常，整个事务不生效。 */
    fun deleteLedgerAccount(id: String) = transaction { db ->
        db.query(
            "SELECT COUNT(*) FROM ledger_transactions WHERE account_id = ? OR to_account_id = ?",
            arrayOf<Any?>(id, id),
        ).use { cursor ->
            cursor.moveToFirst()
            check(cursor.getInt(0) == 0) { "这个账户还有流水，不能删除，可以改为停用" }
        }
        db.execSQL("DELETE FROM ledger_accounts WHERE id = ?", arrayOf<Any?>(id))
    }

    fun saveLedgerCategory(row: LedgerCategoryRow) = transaction { db -> writeCategory(db, row) }

    fun saveLedgerBudget(row: LedgerBudgetRow) = transaction { db -> writeBudget(db, row) }

    fun deleteLedgerBudget(id: String) = transaction { db ->
        db.execSQL("DELETE FROM ledger_budgets WHERE id = ?", arrayOf<Any?>(id))
    }

    private fun writeAccount(db: SupportSQLiteDatabase, row: LedgerAccountRow) {
        db.execSQL(
            "INSERT OR REPLACE INTO ledger_accounts(id,position,archived,created_at,updated_at,data_enc) VALUES(?,?,?,?,?,?)",
            arrayOf<Any?>(row.id, row.position, if (row.archived) 1 else 0, row.createdAt, row.updatedAt, row.dataEnc),
        )
    }

    private fun writeCategory(db: SupportSQLiteDatabase, row: LedgerCategoryRow) {
        db.execSQL(
            "INSERT OR REPLACE INTO ledger_categories(id,parent_id,type,position,archived,updated_at,data_enc) VALUES(?,?,?,?,?,?,?)",
            arrayOf<Any?>(row.id, row.parentId, row.type, row.position, if (row.archived) 1 else 0, row.updatedAt, row.dataEnc),
        )
    }

    private fun writeRecurring(db: SupportSQLiteDatabase, row: LedgerRecurringRow) {
        db.execSQL(
            "INSERT OR REPLACE INTO ledger_recurring(id,updated_at,data_enc) VALUES(?,?,?)",
            arrayOf<Any?>(row.id, row.updatedAt, row.dataEnc),
        )
    }

    private fun writeTombstone(db: SupportSQLiteDatabase, row: LedgerTombstoneRow) {
        db.execSQL(
            "INSERT OR REPLACE INTO ledger_tombstones(id,kind,deleted_at) VALUES(?,?,?)",
            arrayOf<Any?>(row.id, row.kind, row.deletedAt),
        )
    }

    private fun writeTransaction(db: SupportSQLiteDatabase, row: LedgerTxRow, ignoreExisting: Boolean = false) {
        db.execSQL(
            (if (ignoreExisting) "INSERT OR IGNORE" else "INSERT OR REPLACE") + " INTO ledger_transactions(id,type,occurred_at,account_id,to_account_id,category_id,created_at,updated_at,data_enc,import_batch) VALUES(?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                row.id, row.type, row.occurredAt, row.accountId, row.toAccountId, row.categoryId,
                row.createdAt, row.updatedAt, row.dataEnc, row.importBatch,
            ),
        )
    }

    private fun writeBudget(db: SupportSQLiteDatabase, row: LedgerBudgetRow) {
        db.execSQL(
            "INSERT OR REPLACE INTO ledger_budgets(id,category_id,updated_at,data_enc) VALUES(?,?,?,?)",
            arrayOf<Any?>(row.id, row.categoryId, row.updatedAt, row.dataEnc),
        )
    }

    private fun database(): SupportSQLiteDatabase =
        helper?.writableDatabase ?: throw IllegalStateException("金匮已锁定")

    private fun <T> read(sql: String, args: Array<Any?> = emptyArray(), map: (Cursor) -> T): List<T> =
        synchronized(lock) {
            val result = ArrayList<T>()
            database().query(sql, args).use { cursor ->
                while (cursor.moveToNext()) result += map(cursor)
            }
            result
        }

    private fun transaction(block: (SupportSQLiteDatabase) -> Unit) = synchronized(lock) {
        val db = database()
        db.beginTransaction()
        try {
            block(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun closeLocked() {
        runCatching { helper?.close() }
        helper = null
        passphrase?.wipe()
        passphrase = null
    }

    private fun rawKeyLiteral(rawKey: ByteArray): ByteArray {
        val hex = "0123456789abcdef"
        val out = ByteArray(rawKey.size * 2 + 3)
        out[0] = 'x'.code.toByte()
        out[1] = '\''.code.toByte()
        for (i in rawKey.indices) {
            val value = rawKey[i].toInt() and 0xFF
            out[2 + i * 2] = hex[value ushr 4].code.toByte()
            out[3 + i * 2] = hex[value and 0x0F].code.toByte()
        }
        out[out.size - 1] = '\''.code.toByte()
        return out
    }

    private object Schema : SupportSQLiteOpenHelper.Callback(VaultSchema.VERSION) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            VaultSchema.create().forEach { db.execSQL(it) }
        }

        /** 逐级迁移；在打开数据库的同一个事务里执行，任何一步失败都整体回滚，原数据不受影响。 */
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            VaultSchema.upgrade(oldVersion, newVersion).forEach { db.execSQL(it) }
        }
    }

    private companion object {
        const val DB_NAME = "vault.db"
    }
}
