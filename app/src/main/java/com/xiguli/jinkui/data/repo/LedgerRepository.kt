package com.xiguli.jinkui.data.repo

import com.xiguli.jinkui.core.crypto.WrongSecretException
import com.xiguli.jinkui.core.security.SessionKeys
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.data.db.LedgerAccountRow
import com.xiguli.jinkui.data.db.LedgerBatch
import com.xiguli.jinkui.data.db.LedgerBudgetRow
import com.xiguli.jinkui.data.db.LedgerCategoryRow
import com.xiguli.jinkui.data.db.LedgerTxRow
import com.xiguli.jinkui.data.db.VaultDatabase
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerBudget
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerJson
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerMerge
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONException

class LedgerImportSummary(val added: Int, val updated: Int, val skipped: Int)

/** 校验没通过时抛出，[message] 直接给用户看。 */
class LedgerInputException(message: String) : Exception(message)

/**
 * 账本仓库。与卡片仓库同一套做法：数据库里只有字段级密文，解锁后整体解密到内存，
 * 锁定时整体丢弃；所有读写都在 IO 线程。
 */
class LedgerRepository(
    private val database: VaultDatabase,
    private val vault: VaultManager,
) {
    private val state = MutableStateFlow(LedgerData.EMPTY)
    val data: StateFlow<LedgerData> = state.asStateFlow()

    private fun keys(): SessionKeys = vault.keys ?: throw IllegalStateException("金匮已锁定")

    suspend fun load() = withContext(Dispatchers.IO) {
        val keys = keys()
        // 第一次打开账本（新装或从 0.5.x 升级上来）：预置一套分类和一个“现金”账户。
        // 分类从不真正删除，所以这张表只会在第一次时为空。
        if (database.ledgerCategoryCount() == 0) {
            val now = System.currentTimeMillis()
            database.writeLedger(
                LedgerBatch(
                    accounts = if (database.ledgerAccounts().isEmpty()) LedgerDefaults.accounts(now).map { it.toRow(keys) } else emptyList(),
                    categories = LedgerDefaults.categories().map { it.toRow(keys) },
                ),
            )
        }
        var unreadable = 0
        fun <R, T> decodeAll(rows: List<R>, decode: (R) -> T): List<T> = rows.mapNotNull { row ->
            try {
                decode(row)
            } catch (e: WrongSecretException) {
                unreadable++
                null
            } catch (e: JSONException) {
                unreadable++
                null
            }
        }
        val accounts = decodeAll(database.ledgerAccounts()) { row ->
            val p = LedgerJson.readAccountPayload(keys.field.decrypt(row.dataEnc, accountContext(row.id)))
            LedgerAccount(row.id, p.name, p.kind, p.opening, p.cardId, row.archived, row.position, row.createdAt, row.updatedAt)
        }
        val categories = decodeAll(database.ledgerCategories()) { row ->
            val p = LedgerJson.readCategoryPayload(keys.field.decrypt(row.dataEnc, categoryContext(row.id)))
            LedgerCategory(row.id, p.name, TxType.of(row.type), row.parentId, p.icon, row.position, row.archived, row.updatedAt)
        }
        val transactions = decodeAll(database.ledgerTransactions()) { row ->
            val p = LedgerJson.readTransactionPayload(keys.field.decrypt(row.dataEnc, transactionContext(row.id)))
            LedgerTransaction(
                row.id, TxType.of(row.type), p.amount, row.occurredAt, row.accountId, row.toAccountId,
                row.categoryId, p.note, p.tags, row.createdAt, row.updatedAt,
            )
        }
        val budgets = decodeAll(database.ledgerBudgets()) { row ->
            LedgerBudget(row.id, row.categoryId, LedgerJson.readBudgetAmount(keys.field.decrypt(row.dataEnc, budgetContext(row.id))), row.updatedAt)
        }
        publish { LedgerData(accounts, categories, transactions, budgets, unreadable) }
    }

    /** 与卡片仓库相同：锁定之后完成的后台操作不能再把明文放回内存。 */
    private fun publish(transform: (LedgerData) -> LedgerData) {
        if (vault.keys == null) return
        state.value = transform(state.value)
        if (vault.keys == null) state.value = LedgerData.EMPTY
    }

    /** 锁定时调用：丢弃内存中的全部明文。 */
    fun clear() {
        state.value = LedgerData.EMPTY
    }

    // ── 流水 ──────────────────────────────────────────────────────

    suspend fun saveTransaction(tx: LedgerTransaction): LedgerTransaction = withContext(Dispatchers.IO) {
        val keys = keys()
        val current = state.value
        val normalized = tx.copy(
            note = tx.note.trim(),
            tags = tx.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            toAccountId = if (tx.type == TxType.TRANSFER) tx.toAccountId else null,
            categoryId = if (tx.type == TxType.TRANSFER) null else tx.categoryId,
        )
        LedgerMath.validate(normalized, current.accounts)?.let { throw LedgerInputException(it) }
        val now = System.currentTimeMillis()
        val previous = current.transactions.firstOrNull { it.id == tx.id }
        val saved = normalized.copy(createdAt = previous?.createdAt ?: now, updatedAt = now)
        database.saveLedgerTransaction(saved.toRow(keys))
        publish { data -> data.copy(transactions = data.transactions.filterNot { it.id == saved.id } + saved) }
        saved
    }

    suspend fun deleteTransaction(id: String) = withContext(Dispatchers.IO) {
        database.deleteLedgerTransaction(id)
        publish { data -> data.copy(transactions = data.transactions.filterNot { it.id == id }) }
    }

    // ── 账户 ──────────────────────────────────────────────────────

    suspend fun saveAccount(account: LedgerAccount): LedgerAccount = withContext(Dispatchers.IO) {
        val keys = keys()
        val name = account.name.trim()
        if (name.isEmpty()) throw LedgerInputException("请填写账户名称")
        val current = state.value
        val now = System.currentTimeMillis()
        val previous = current.accounts.firstOrNull { it.id == account.id }
        val saved = account.copy(
            name = name.take(40),
            position = previous?.position ?: ((current.accounts.maxOfOrNull { it.position } ?: -1) + 1),
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
        )
        database.saveLedgerAccount(saved.toRow(keys))
        publish { data -> data.copy(accounts = (data.accounts.filterNot { it.id == saved.id } + saved).sortedBy { it.position }) }
        saved
    }

    /** 只有没有流水的账户才能删除；有流水的请停用。 */
    suspend fun deleteAccount(id: String) = withContext(Dispatchers.IO) {
        if (LedgerMath.usageOf(id, state.value.transactions) > 0) {
            throw LedgerInputException("这个账户还有流水，不能删除，可以改为停用")
        }
        database.deleteLedgerAccount(id)
        publish { data -> data.copy(accounts = data.accounts.filterNot { it.id == id }) }
    }

    // ── 分类 ──────────────────────────────────────────────────────

    suspend fun saveCategory(category: LedgerCategory): LedgerCategory = withContext(Dispatchers.IO) {
        val keys = keys()
        val name = category.name.trim()
        if (name.isEmpty()) throw LedgerInputException("请填写分类名称")
        if (category.type == TxType.TRANSFER) throw LedgerInputException("分类只能是支出或收入")
        val current = state.value
        val parent = category.parentId?.let { id -> current.categories.firstOrNull { it.id == id } }
        if (category.parentId != null && (parent == null || parent.parentId != null)) {
            throw LedgerInputException("分类最多两级")
        }
        val previous = current.categories.firstOrNull { it.id == category.id }
        val siblings = current.categories.filter { it.parentId == category.parentId && it.type == category.type }
        val saved = category.copy(
            name = name.take(20),
            type = parent?.type ?: category.type,
            position = previous?.position ?: ((siblings.maxOfOrNull { it.position } ?: -1) + 1),
            updatedAt = System.currentTimeMillis(),
        )
        database.saveLedgerCategory(saved.toRow(keys))
        publish { data -> data.copy(categories = (data.categories.filterNot { it.id == saved.id } + saved).sortedBy { it.position }) }
        saved
    }

    // ── 预算 ──────────────────────────────────────────────────────

    /** 设置每月预算；[amount] 为 0 表示取消。[categoryId] 为 null 是总预算。 */
    suspend fun setBudget(categoryId: String?, amount: Long) = withContext(Dispatchers.IO) {
        val keys = keys()
        if (amount < 0) throw LedgerInputException("预算不能是负数")
        val id = LedgerBudget.idFor(categoryId)
        if (amount == 0L) {
            database.deleteLedgerBudget(id)
            publish { data -> data.copy(budgets = data.budgets.filterNot { it.id == id }) }
        } else {
            val budget = LedgerBudget(id, categoryId, amount, System.currentTimeMillis())
            database.saveLedgerBudget(budget.toRow(keys))
            publish { data -> data.copy(budgets = data.budgets.filterNot { it.id == id } + budget) }
        }
    }

    // ── 备份 ──────────────────────────────────────────────────────

    fun snapshot(): LedgerData {
        keys()
        return state.value.copy(unreadable = 0)
    }

    /** 合并导入（规则见 [LedgerMerge]）。需要写入的记录先全部加密好，再在一个事务里写入。 */
    suspend fun restore(incoming: LedgerData): LedgerImportSummary = withContext(Dispatchers.IO) {
        val keys = keys()
        val plan = LedgerMerge.plan(state.value, incoming)
        val write = plan.toWrite
        try {
            if (!write.isEmpty) {
                database.writeLedger(
                    LedgerBatch(
                        accounts = write.accounts.map { it.toRow(keys) },
                        categories = write.categories.map { it.toRow(keys) },
                        transactions = write.transactions.map { it.toRow(keys) },
                        budgets = write.budgets.map { it.toRow(keys) },
                    ),
                )
            }
        } finally {
            runCatching { load() }
        }
        LedgerImportSummary(plan.added, plan.updated, plan.skipped)
    }

    // ── 行转换 ────────────────────────────────────────────────────

    private fun LedgerAccount.toRow(keys: SessionKeys) = LedgerAccountRow(
        id, position, archived, createdAt, updatedAt,
        keys.field.encrypt(LedgerJson.accountPayload(this), accountContext(id)),
    )

    private fun LedgerCategory.toRow(keys: SessionKeys) = LedgerCategoryRow(
        id, parentId, type.key, position, archived, updatedAt,
        keys.field.encrypt(LedgerJson.categoryPayload(this), categoryContext(id)),
    )

    private fun LedgerTransaction.toRow(keys: SessionKeys) = LedgerTxRow(
        id, type.key, occurredAt, accountId, toAccountId, categoryId, createdAt, updatedAt,
        keys.field.encrypt(LedgerJson.transactionPayload(this), transactionContext(id)),
    )

    private fun LedgerBudget.toRow(keys: SessionKeys) = LedgerBudgetRow(
        id, categoryId, updatedAt,
        keys.field.encrypt(LedgerJson.budgetPayload(this), budgetContext(id)),
    )

    private companion object {
        // AAD：每段密文绑定到自己的记录，挪到别处无法解密。
        fun accountContext(id: String) = "ledger:account:$id"
        fun categoryContext(id: String) = "ledger:category:$id"
        fun transactionContext(id: String) = "ledger:tx:$id"
        fun budgetContext(id: String) = "ledger:budget:$id"
    }
}
