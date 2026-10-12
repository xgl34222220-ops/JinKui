package com.xiguli.jinkui.data.repo

import com.xiguli.jinkui.core.crypto.WrongSecretException
import com.xiguli.jinkui.core.security.SessionKeys
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.data.db.LedgerAccountRow
import com.xiguli.jinkui.data.db.LedgerBatch
import com.xiguli.jinkui.data.db.LedgerBudgetRow
import com.xiguli.jinkui.data.db.LedgerCategoryRow
import com.xiguli.jinkui.data.db.LedgerRecurringRow
import com.xiguli.jinkui.data.db.LedgerPendingRow
import com.xiguli.jinkui.core.auto.CapturedPayment
import com.xiguli.jinkui.data.ledger.LedgerIds
import com.xiguli.jinkui.data.ledger.PendingEntry
import com.xiguli.jinkui.data.ledger.PendingMath
import com.xiguli.jinkui.data.db.LedgerTombstoneRow
import com.xiguli.jinkui.data.db.LedgerTxRow
import com.xiguli.jinkui.data.db.LedgerImportBatchRow
import com.xiguli.jinkui.data.db.LedgerDebtRow
import com.xiguli.jinkui.data.db.LedgerRuleRow
import com.xiguli.jinkui.data.ledger.LedgerRule
import com.xiguli.jinkui.data.ledger.LedgerTemplate
import com.xiguli.jinkui.data.ledger.Reimburse
import com.xiguli.jinkui.data.ledger.RefundMath
import com.xiguli.jinkui.data.ledger.RuleMath
import com.xiguli.jinkui.data.ledger.CreditSettings
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.LedgerInstallment
import com.xiguli.jinkui.data.ledger.LedgerLoan
import com.xiguli.jinkui.data.ledger.LoanDirection
import java.time.LocalDate
import com.xiguli.jinkui.data.importer.BillImport
import com.xiguli.jinkui.data.ledger.LedgerImportBatch
import com.xiguli.jinkui.data.db.VaultDatabase
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerBudget
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerJson
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerMerge
import com.xiguli.jinkui.data.ledger.LedgerRecurring
import com.xiguli.jinkui.data.ledger.LedgerTombstone
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.RecurringMath
import com.xiguli.jinkui.data.ledger.Money
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.xiguli.jinkui.data.ledger.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONException

class LedgerImportSummary(val added: Int, val updated: Int, val skipped: Int, val suppressed: Int = 0)

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

    /** 自动记账的“待确认”（1.2 起）。与账本同一把字段密钥加密；锁定时丢弃；不进备份。 */
    private val pendingState = MutableStateFlow<List<PendingEntry>>(emptyList())
    val pending: StateFlow<List<PendingEntry>> = pendingState.asStateFlow()

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
            } catch (e: IllegalArgumentException) {
                // 包括 NumberFormatException：载荷能解密但内容不合法，同样按损坏略过，而不是让整个账本打不开
                unreadable++
                null
            }
        }
        val accounts = decodeAll(database.ledgerAccounts()) { row ->
            val p = LedgerJson.readAccountPayload(keys.field.decrypt(row.dataEnc, accountContext(row.id)))
            LedgerAccount(row.id, p.name, p.kind, p.opening, p.cardId, row.archived, row.position, row.createdAt, row.updatedAt, p.credit)
        }
        val categories = decodeAll(database.ledgerCategories()) { row ->
            val p = LedgerJson.readCategoryPayload(keys.field.decrypt(row.dataEnc, categoryContext(row.id)))
            LedgerCategory(row.id, p.name, TxType.of(row.type), row.parentId, p.icon, row.position, row.archived, row.updatedAt)
        }
        val transactions = decodeAll(database.ledgerTransactions()) { row ->
            val p = LedgerJson.readTransactionPayload(keys.field.decrypt(row.dataEnc, transactionContext(row.id)))
            LedgerTransaction(
                row.id, TxType.of(row.type), p.amount, row.occurredAt, row.accountId, row.toAccountId,
                row.categoryId, p.note, p.tags, row.createdAt, row.updatedAt, row.importBatch, p.debtId,
                p.refundOf, p.reimburse, p.reimburseOf,
            )
        }
        val batchCounts = transactions.groupingBy { it.importBatch }.eachCount()
        val importBatches = decodeAll(database.ledgerImportBatches()) { row ->
            if (row.dataEnc.isEmpty()) {
                // 升级时由 1.3 之前的导入补出来的批次：没有来源等详情
                LedgerImportBatch(row.id, row.createdAt, count = batchCounts[row.id] ?: 0, legacy = true)
            } else {
                LedgerJson.readImportBatchPayload(row.id, row.createdAt, keys.field.decrypt(row.dataEnc, batchContext(row.id)))
            }
        }
        val budgets = decodeAll(database.ledgerBudgets()) { row ->
            LedgerBudget(row.id, row.categoryId, LedgerJson.readBudgetAmount(keys.field.decrypt(row.dataEnc, budgetContext(row.id))), row.updatedAt)
        }
        val recurring = decodeAll(database.ledgerRecurring()) { row ->
            LedgerJson.readRecurringPayload(row.id, row.updatedAt, keys.field.decrypt(row.dataEnc, recurringContext(row.id)))
        }
        // 删除标记没有密文，只是 ID；种类不认识的（更新版本写入的）原样保留在库里，这里不用
        val tombstones = database.ledgerTombstones()
            .filter { it.kind in LedgerTombstone.KINDS }
            .map { LedgerTombstone(it.id, it.kind, it.deletedAt) }
        // 分期与借贷（1.4 起）；不认识的种类（更新版本写入的）原样留在库里，这里不用
        val debtRows = database.ledgerDebts()
        val installments = decodeAll(debtRows.filter { it.kind == LedgerJson.DEBT_INSTALLMENT }) { row ->
            LedgerJson.readInstallment(row.id, row.updatedAt, org.json.JSONObject(keys.field.decrypt(row.dataEnc, debtContext(row.id))))
        }
        val loans = decodeAll(debtRows.filter { it.kind == LedgerJson.DEBT_LOAN }) { row ->
            LedgerJson.readLoan(row.id, row.updatedAt, org.json.JSONObject(keys.field.decrypt(row.dataEnc, debtContext(row.id))))
        }
        // 自动分类规则与记账模板（1.4.0-alpha04 起）；不认识的种类原样留在库里
        val ruleRows = database.ledgerRules()
        val rules = decodeAll(ruleRows.filter { it.kind == RuleMath.KIND_RULE }) { row ->
            RuleMath.readRule(row.id, row.updatedAt, org.json.JSONObject(keys.field.decrypt(row.dataEnc, ruleContext(row.id))))
        }.sortedBy { it.position }
        val templates = decodeAll(ruleRows.filter { it.kind == RuleMath.KIND_TEMPLATE }) { row ->
            RuleMath.readTemplate(row.id, row.updatedAt, org.json.JSONObject(keys.field.decrypt(row.dataEnc, ruleContext(row.id))))
        }.sortedBy { it.position }
        val pendingEntries = decodeAll(database.ledgerPending()) { row ->
            PendingMath.read(row.id, row.createdAt, keys.field.decrypt(row.dataEnc, pendingContext(row.id)))
        }
        publish { LedgerData(accounts, categories, transactions, budgets, recurring, unreadable, tombstones, importBatches, installments, loans, rules, templates) }
        publishPending { pendingEntries }
        // 解锁后立刻补记到期的周期记账；失败不影响账本打开
        runCatching { runRecurring() }
    }

    // ── 周期记账 ──────────────────────────────────────────────────

    private val recurringLock = Mutex()

    /**
     * 生成到期的周期记账（解锁时、回到账本页时调用）。不需要后台任务，也不联网。
     * 新流水与规则进度在一个事务里写入；同一规则同一天的流水 ID 固定，绝不会重复生成。
     * 返回新生成的笔数。
     */
    suspend fun runRecurring(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val batch = RecurringMath.generate(state.value, now, zone)
            if (batch.isEmpty) return@withLock 0
            database.applyRecurring(batch.transactions.map { it.toRow(keys) }, batch.rules.map { it.toRow(keys) })
            val newIds = batch.transactions.mapTo(HashSet()) { it.id }
            val advanced = batch.rules.associateBy { it.id }
            publish { data ->
                data.copy(
                    transactions = data.transactions.filterNot { it.id in newIds } + batch.transactions,
                    recurring = data.recurring.map { advanced[it.id] ?: it },
                )
            }
            batch.transactions.size
        }
    }

    suspend fun saveRecurring(rule: LedgerRecurring): LedgerRecurring = withContext(Dispatchers.IO) {
        val saved = recurringLock.withLock {
            val keys = keys()
            val current = state.value
            val normalized = rule.copy(
                note = rule.note.trim().take(500),
                tags = rule.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                toAccountId = if (rule.type == TxType.TRANSFER) rule.toAccountId else null,
                categoryId = if (rule.type == TxType.TRANSFER) null else rule.categoryId,
            )
            RecurringMath.validate(normalized, current.accounts)?.let { throw LedgerInputException(it) }
            val previous = current.recurring.firstOrNull { it.id == rule.id }
            val now = System.currentTimeMillis()
            val saved = normalized.copy(
                nextIndex = RecurringMath.indexAfterEdit(previous, normalized),
                createdAt = previous?.createdAt ?: now,
                updatedAt = now,
            )
            database.saveLedgerRecurring(saved.toRow(keys))
            publish { data -> data.copy(recurring = data.recurring.filterNot { it.id == saved.id } + saved) }
            saved
        }
        runRecurring()
        state.value.recurring.firstOrNull { it.id == saved.id } ?: saved
    }

    /** 删除规则；它已经生成的流水保留，余额不受影响。记下删除标记，导入旧备份时不会把规则加回来。 */
    suspend fun deleteRecurring(id: String) = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            keys()
            val tombstone = LedgerTombstone(id, LedgerTombstone.KIND_RECURRING, System.currentTimeMillis())
            database.deleteLedgerRecurring(id, tombstone.toRow())
            publish { data ->
                data.copy(
                    recurring = data.recurring.filterNot { it.id == id },
                    tombstones = data.tombstones.filterNot { it.id == id } + tombstone,
                )
            }
        }
    }

    /** 与卡片仓库相同：锁定之后完成的后台操作不能再把明文放回内存。 */
    private fun publish(transform: (LedgerData) -> LedgerData) {
        if (vault.keys == null) return
        state.value = transform(state.value)
        if (vault.keys == null) state.value = LedgerData.EMPTY
    }

    private fun publishPending(transform: (List<PendingEntry>) -> List<PendingEntry>) {
        if (vault.keys == null) return
        pendingState.value = transform(pendingState.value)
        if (vault.keys == null) pendingState.value = emptyList()
    }

    /** 锁定时调用：丢弃内存中的全部明文。 */
    fun clear() {
        state.value = LedgerData.EMPTY
        pendingState.value = emptyList()
    }

    // ── 账单导入 ──────────────────────────────────────────────────

    /**
     * 写入导入的流水、需要新建的账户与这次的导入记录（[batch]），一个事务。已经存在的 ID 跳过，不覆盖。
     * 每笔流水都带上批次 ID，之后可以在“导入记录”里整批撤销。返回真正写入的流水。
     */
    suspend fun importTransactions(
        transactions: List<LedgerTransaction>,
        newAccounts: List<LedgerAccount>,
        batch: LedgerImportBatch? = null,
        newCategories: List<LedgerCategory> = emptyList(),
    ): List<LedgerTransaction> =
        withContext(Dispatchers.IO) {
            recurringLock.withLock {
                val keys = keys()
                val current = state.value
                val existing = current.transactions.mapTo(HashSet()) { it.id }
                val now = System.currentTimeMillis()
                var position = (current.accounts.maxOfOrNull { a -> a.position } ?: -1) + 1
                val created = newAccounts
                    .filter { a -> current.accounts.none { it.id == a.id } && transactions.any { it.accountId == a.id || it.toAccountId == a.id } }
                    .map { it.copy(position = position++, updatedAt = now) }
                val accounts = current.accounts + created
                val fresh = transactions.filter { it.id !in existing }
                    .map { it.copy(note = it.note.trim().take(500), importBatch = batch?.id ?: it.importBatch) }
                fresh.forEach { tx -> LedgerMath.validate(tx, accounts)?.let { throw LedgerInputException(it) } }
                if (fresh.isEmpty()) return@withLock emptyList()
                val savedBatch = batch?.copy(count = fresh.size, createdAccounts = created.map { it.id })
                // 分类从不删除：已经存在的同 ID 分类不覆盖（用户可能改过名字或图标）
                val categories = newCategories.filter { c -> current.categories.none { it.id == c.id } }
                database.importLedger(created.map { it.toRow(keys) }, categories.map { it.toRow(keys) }, fresh.map { it.toRow(keys) }, savedBatch?.toRow(keys))
                val freshIds = fresh.mapTo(HashSet()) { it.id }
                publish { data ->
                    data.copy(
                        accounts = if (created.isEmpty()) data.accounts else (data.accounts + created).sortedBy { it.position },
                        categories = if (categories.isEmpty()) data.categories else (data.categories + categories).sortedBy { it.position },
                        transactions = data.transactions + fresh,
                        tombstones = data.tombstones.filterNot { it.id in freshIds },
                        importBatches = if (savedBatch == null) data.importBatches else listOf(savedBatch) + data.importBatches,
                    )
                }
                fresh
            }
        }

    /** 兼容 1.2 的调用：没有导入记录、最多新建一个账户。 */
    suspend fun importTransactions(transactions: List<LedgerTransaction>, newAccount: LedgerAccount?): List<LedgerTransaction> =
        importTransactions(transactions, listOfNotNull(newAccount), null)

    /**
     * 撤销一次导入：删掉这一批的全部流水（记下删除标记，导入旧备份不会加回来；重新导入同一份账单仍可以选回），
     * 删掉这条导入记录，以及这次自动新建、已经没有任何流水的账户。返回删掉的笔数。
     */
    suspend fun undoImport(batchId: String): Int = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            keys()
            val current = state.value
            val batch = current.importBatches.firstOrNull { it.id == batchId }
            val doomed = current.transactions.filter { it.importBatch == batchId }
            val now = System.currentTimeMillis()
            val tombstones = doomed.map { LedgerTombstone(it.id, LedgerTombstone.KIND_TX, now) }
            val accountIds = batch?.createdAccounts.orEmpty()
            database.deleteLedgerTransactions(doomed.map { it.id }, tombstones.map { it.toRow() }, batchId, accountIds)
            val ids = doomed.mapTo(HashSet()) { it.id }
            publish { data ->
                val left = data.transactions.filterNot { it.id in ids }
                data.copy(
                    transactions = left,
                    accounts = data.accounts.filterNot { a -> a.id in accountIds && left.none { it.accountId == a.id || it.toAccountId == a.id } },
                    tombstones = data.tombstones.filterNot { it.id in ids } + tombstones,
                    importBatches = data.importBatches.filterNot { it.id == batchId },
                )
            }
            doomed.size
        }
    }

    /**
     * 一次删除多笔流水（多选删除、按时间段删除），一个事务。周期生成与账单导入的流水记下删除标记。
     * 返回删掉的流水（原样），用于“撤销”。
     */
    suspend fun deleteTransactions(ids: Collection<String>): List<LedgerTransaction> = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            keys()
            val wanted = ids.toHashSet()
            val doomed = state.value.transactions.filter { it.id in wanted }
            if (doomed.isEmpty()) return@withLock emptyList()
            val now = System.currentTimeMillis()
            val tombstones = doomed.filter { needsTombstone(it.id) }.map { LedgerTombstone(it.id, LedgerTombstone.KIND_TX, now) }
            database.deleteLedgerTransactions(doomed.map { it.id }, tombstones.map { it.toRow() })
            val gone = doomed.mapTo(HashSet()) { it.id }
            publish { data ->
                data.copy(
                    transactions = data.transactions.filterNot { it.id in gone },
                    tombstones = data.tombstones.filterNot { it.id in gone } + tombstones,
                )
            }
            doomed
        }
    }

    /** 删除 [from, until) 这段时间里的全部流水（导入出错时的兜底清理）。返回删掉的流水。 */
    suspend fun deleteRange(from: Long, until: Long): List<LedgerTransaction> =
        deleteTransactions(state.value.transactions.filter { it.occurredAt in from until until }.map { it.id })

    /** 撤销删除：把 [deleted] 原样写回（账户已经不在的跳过），并去掉删除标记。 */
    suspend fun restoreTransactions(deleted: List<LedgerTransaction>) = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val current = state.value
            val accountIds = current.accounts.mapTo(HashSet()) { it.id }
            val existing = current.transactions.mapTo(HashSet()) { it.id }
            val back = deleted.filter { it.id !in existing && it.accountId in accountIds && (it.toAccountId == null || it.toAccountId in accountIds) }
            if (back.isEmpty()) return@withLock
            database.restoreLedgerTransactions(back.map { it.toRow(keys) })
            val ids = back.mapTo(HashSet()) { it.id }
            publish { data -> data.copy(transactions = data.transactions + back, tombstones = data.tombstones.filterNot { it.id in ids }) }
        }
    }

    /**
     * 1.4.0-alpha04 起每一笔删掉的流水都记删除标记（以前只记周期生成与账单导入的）：
     * 手动记的流水删掉后，导入一份旧备份也不会再回来。标记只有 ID 与时间。
     */
    @Suppress("UNUSED_PARAMETER")
    private fun needsTombstone(id: String): Boolean = true

    // ── 自动记账 ──────────────────────────────────────────────────

    /**
     * 收进自动记账识别出的几笔（解锁后从暂存里取出）。默认放进“待确认”；
     * [autoSave] 为 true（用户打开了“自动入账”）时直接记成流水。重复的（同金额、两分钟内）跳过。
     * 返回收进的笔数。
     */
    suspend fun ingest(captured: List<CapturedPayment>, autoSave: Boolean): Int = withContext(Dispatchers.IO) {
        if (captured.isEmpty()) return@withContext 0
        recurringLock.withLock {
            val keys = keys()
            val current = state.value
            val fresh = PendingMath.fresh(captured, pendingState.value, current.transactions)
            if (fresh.isEmpty()) return@withLock 0
            val now = System.currentTimeMillis()
            val entries = fresh.map { PendingEntry(LedgerIds.newId(), it.source, it.type, it.amount, it.merchant, it.postedAt, now) }
            if (autoSave) {
                // 没有可用账户等原因记不进去的，仍然放进待确认
                val pairs = entries.map { e ->
                    e to PendingMath.suggestion(e, current)?.copy(createdAt = now, updatedAt = now)
                        ?.takeIf { LedgerMath.validate(it, current.accounts) == null }
                }
                val saved = pairs.mapNotNull { it.second }
                val leftovers = pairs.filter { it.second == null }.map { it.first }
                if (saved.isNotEmpty()) database.importLedger(null, saved.map { it.toRow(keys) })
                if (leftovers.isNotEmpty()) database.insertPending(leftovers.map { it.toRow(keys) })
                publish { data -> data.copy(transactions = data.transactions + saved) }
                publishPending { list -> list + leftovers }
            } else {
                database.insertPending(entries.map { it.toRow(keys) })
                publishPending { list -> list + entries }
            }
            entries.size
        }
    }

    /** 确认几笔待确认：写成流水（[transactions] 由界面给出，可能被用户改过），并从待确认里删除。 */
    suspend fun confirmPending(pendingIds: Collection<String>, transactions: List<LedgerTransaction>) = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val current = state.value
            val now = System.currentTimeMillis()
            val saved = transactions.map { it.copy(note = it.note.trim().take(500), createdAt = now, updatedAt = now) }
            saved.forEach { tx -> LedgerMath.validate(tx, current.accounts)?.let { throw LedgerInputException(it) } }
            database.confirmPending(null, saved.map { it.toRow(keys) }, pendingIds)
            val ids = pendingIds.toSet()
            publish { data -> data.copy(transactions = data.transactions.filterNot { t -> saved.any { it.id == t.id } } + saved) }
            publishPending { list -> list.filterNot { it.id in ids } }
        }
    }

    /** 忽略（删除）几笔待确认。 */
    suspend fun ignorePending(pendingIds: Collection<String>) = withContext(Dispatchers.IO) {
        keys()
        database.deletePending(pendingIds)
        val ids = pendingIds.toSet()
        publishPending { list -> list.filterNot { it.id in ids } }
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
        // 记账面板改一笔时不知道它属于哪笔欠款 / 哪次导入：沿用原来的归属
        val saved = normalized.copy(
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            importBatch = normalized.importBatch ?: previous?.importBatch,
            debtId = normalized.debtId ?: previous?.debtId,
            // 退款 / 报销的关联同样沿用；改成别的类型时去掉（只有支出能报销、只有收入能是退款）
            refundOf = (normalized.refundOf ?: previous?.refundOf).takeIf { normalized.type == TxType.INCOME },
            reimburse = (if (normalized.reimburse != Reimburse.NONE) normalized.reimburse else previous?.reimburse ?: Reimburse.NONE)
                .takeIf { normalized.type == TxType.EXPENSE } ?: Reimburse.NONE,
            reimburseOf = normalized.reimburseOf.ifEmpty { previous?.reimburseOf.orEmpty() }.takeIf { normalized.type == TxType.INCOME }.orEmpty(),
        )
        database.saveLedgerTransaction(saved.toRow(keys))
        publish { data -> data.copy(transactions = data.transactions.filterNot { it.id == saved.id } + saved) }
        saved
    }

    /**
     * 删除一笔流水。周期记账生成的流水同时记下删除标记：
     * 之后补记不会再生成这一天，导入旧备份也不会把它加回来。
     */
    suspend fun deleteTransaction(id: String) = withContext(Dispatchers.IO) {
        // 1.3 起账单导入的流水也记删除标记：否则导入一份旧备份，删掉的导入流水又会回来
        val tombstone = if (needsTombstone(id)) {
            LedgerTombstone(id, LedgerTombstone.KIND_TX, System.currentTimeMillis())
        } else {
            null
        }
        recurringLock.withLock {
            database.deleteLedgerTransaction(id, tombstone?.toRow())
            publish { data ->
                data.copy(
                    transactions = data.transactions.filterNot { it.id == id },
                    tombstones = if (tombstone == null) data.tombstones else data.tombstones.filterNot { it.id == id } + tombstone,
                )
            }
        }
    }

    // ── 退款与报销（1.4.0-alpha04 起）────────────────────────────────

    /**
     * 给一笔支出记一笔退款（全额或部分）：退回 [accountId] 的一笔收入，分类“退款”，关联原支出。
     * 统计里原支出按退款后的金额算，退款不算收入（[RefundMath.forStats]）。
     */
    suspend fun refund(
        expenseId: String,
        amount: Long,
        accountId: String,
        date: LocalDate,
        note: String = "",
        zone: ZoneId = ZoneId.systemDefault(),
    ): LedgerTransaction = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val data = state.value
            val expense = data.transactions.firstOrNull { it.id == expenseId }
            RefundMath.validateRefund(expense, amount, data.transactions)?.let { throw LedgerInputException(it) }
            expense!!
            val now = System.currentTimeMillis()
            val label = note.trim().ifEmpty { "退款 · " + expense.note.ifBlank { categoryName(data, expense.categoryId) } }
            val tx = LedgerTransaction(
                id = LedgerIds.newId(), type = TxType.INCOME, amount = amount,
                occurredAt = timeOn(date, zone, now).coerceAtLeast(expense.occurredAt), accountId = accountId,
                categoryId = categoryOr(data, "cat-refund", "cat-other-income"),
                note = label.take(500), createdAt = now, updatedAt = now, refundOf = expense.id,
            )
            LedgerMath.validate(tx, data.accounts)?.let { throw LedgerInputException(it) }
            database.writeTransactions(listOf(tx.toRow(keys)))
            publish { d -> d.copy(transactions = d.transactions + tx) }
            tx
        }
    }

    private fun categoryName(data: LedgerData, id: String?): String =
        id?.let { cid -> data.categories.firstOrNull { it.id == cid }?.name } ?: "支出"

    /** 标记 / 取消“待报销”（[state] 只能是 PENDING 或 NONE）。已报销的不受影响。 */
    suspend fun setReimburse(ids: Collection<String>, state: Reimburse) = withContext(Dispatchers.IO) {
        require(state != Reimburse.DONE) { "请用“标记已报销”" }
        recurringLock.withLock {
            val keys = keys()
            val data = this@LedgerRepository.state.value
            if (state == Reimburse.PENDING) RefundMath.validateReimburse(ids, data.transactions)?.let { throw LedgerInputException(it) }
            val wanted = ids.toHashSet()
            val now = System.currentTimeMillis()
            val changed = data.transactions
                .filter { it.id in wanted && it.type == TxType.EXPENSE && it.reimburse != Reimburse.DONE && it.reimburse != state }
                .map { it.copy(reimburse = state, updatedAt = now) }
            if (changed.isEmpty()) return@withLock
            database.writeTransactions(changed.map { it.toRow(keys) })
            val byId = changed.associateBy { it.id }
            publish { d -> d.copy(transactions = d.transactions.map { byId[it.id] ?: it }) }
        }
    }

    /**
     * 标记已报销。[accountId] 不为空时记一笔报销到账的收入（金额是这几笔的合计，关联这几笔支出，不算收入）；
     * 为空时只标记（冲抵：钱没有单独入账，或已经在别处记过）。返回到账的那笔收入（只标记时为 null）。
     */
    suspend fun markReimbursed(
        ids: Collection<String>,
        accountId: String?,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LedgerTransaction? = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val data = state.value
            RefundMath.validateReimburse(ids, data.transactions)?.let { throw LedgerInputException(it) }
            val wanted = ids.toHashSet()
            val now = System.currentTimeMillis()
            val expenses = data.transactions.filter { it.id in wanted && it.reimburse != Reimburse.DONE }
            if (expenses.isEmpty()) throw LedgerInputException("请选择要报销的支出")
            val changed = expenses.map { it.copy(reimburse = Reimburse.DONE, updatedAt = now) }
            val income = accountId?.let { acc ->
                LedgerTransaction(
                    id = LedgerIds.newId(), type = TxType.INCOME, amount = expenses.sumOf { it.amount },
                    occurredAt = timeOn(date, zone, now), accountId = acc,
                    categoryId = categoryOr(data, "cat-other-income"),
                    note = if (expenses.size == 1) "报销 · " + expenses[0].note.ifBlank { categoryName(data, expenses[0].categoryId) } else "报销 · ${expenses.size} 笔",
                    createdAt = now, updatedAt = now, reimburseOf = expenses.map { it.id },
                )
            }
            income?.let { tx -> LedgerMath.validate(tx, data.accounts)?.let { throw LedgerInputException(it) } }
            database.writeTransactions((changed + listOfNotNull(income)).map { it.toRow(keys) })
            val byId = changed.associateBy { it.id }
            publish { d -> d.copy(transactions = d.transactions.map { byId[it.id] ?: it } + listOfNotNull(income)) }
            income
        }
    }

    /**
     * 撤回报销：这几笔回到“待报销”；关联的报销到账收入删掉（记删除标记），
     * 同一笔到账收入关联的其他支出也一起回到待报销，金额不会对不上。返回回到待报销的笔数。
     */
    suspend fun revertReimbursed(ids: Collection<String>): Int = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val data = state.value
            val incomes = RefundMath.reimbursementIncomes(ids, data.transactions)
            val all = (ids + incomes.flatMap { it.reimburseOf }).toHashSet()
            val now = System.currentTimeMillis()
            val changed = data.transactions.filter { it.id in all && it.reimburse == Reimburse.DONE }.map { it.copy(reimburse = Reimburse.PENDING, updatedAt = now) }
            val tombstones = incomes.map { LedgerTombstone(it.id, LedgerTombstone.KIND_TX, now) }
            database.writeTransactions(changed.map { it.toRow(keys) }, incomes.map { it.id }, tombstones.map { it.toRow() })
            val byId = changed.associateBy { it.id }
            val gone = incomes.mapTo(HashSet()) { it.id }
            publish { d ->
                d.copy(
                    transactions = d.transactions.filterNot { it.id in gone }.map { byId[it.id] ?: it },
                    tombstones = d.tombstones.filterNot { it.id in gone } + tombstones,
                )
            }
            changed.size
        }
    }

    // ── 自动分类规则与记账模板（1.4.0-alpha04 起）────────────────────

    suspend fun saveRule(rule: LedgerRule): LedgerRule = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val data = state.value
            val normalized = rule.copy(
                keywords = rule.keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20),
                tags = rule.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12),
                categoryId = rule.categoryId?.takeIf { id -> data.categories.any { it.id == id } },
                accountId = rule.accountId?.takeIf { id -> data.accounts.any { it.id == id } },
            )
            normalized.problem()?.let { throw LedgerInputException(it) }
            val previous = data.rules.firstOrNull { it.id == rule.id }
            if (previous == null && data.rules.size >= RuleMath.MAX_RULES) throw LedgerInputException("规则太多了")
            val saved = normalized.copy(
                position = previous?.position ?: ((data.rules.maxOfOrNull { it.position } ?: -1) + 1),
                updatedAt = System.currentTimeMillis(),
            )
            database.saveLedgerRules(listOf(saved.toRow(keys)))
            publish { d -> d.copy(rules = (d.rules.filterNot { it.id == saved.id } + saved).sortedBy { it.position }) }
            saved
        }
    }

    /** 调整规则顺序（上面的先生效）：[ids] 是新的顺序。 */
    suspend fun reorderRules(ids: List<String>) = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val now = System.currentTimeMillis()
            val byId = state.value.rules.associateBy { it.id }
            val changed = ids.mapIndexedNotNull { index, id -> byId[id]?.takeIf { it.position != index }?.copy(position = index, updatedAt = now) }
            if (changed.isEmpty()) return@withLock
            database.saveLedgerRules(changed.map { it.toRow(keys) })
            val map = changed.associateBy { it.id }
            publish { d -> d.copy(rules = d.rules.map { map[it.id] ?: it }.sortedBy { it.position }) }
        }
    }

    suspend fun deleteRule(id: String) = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            keys()
            val tombstone = LedgerTombstone(id, LedgerTombstone.KIND_RULE, System.currentTimeMillis())
            database.deleteLedgerRule(id, tombstone.toRow())
            publish { d ->
                d.copy(
                    rules = d.rules.filterNot { it.id == id },
                    templates = d.templates.filterNot { it.id == id },
                    tombstones = d.tombstones.filterNot { it.id == id } + tombstone,
                )
            }
        }
    }

    suspend fun saveTemplate(template: LedgerTemplate): LedgerTemplate = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val data = state.value
            val normalized = template.copy(
                name = template.name.trim(),
                note = template.note.trim(),
                tags = template.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12),
                toAccountId = template.toAccountId.takeIf { template.type == TxType.TRANSFER },
                categoryId = template.categoryId.takeIf { template.type != TxType.TRANSFER }?.takeIf { id -> data.categories.any { it.id == id } },
                accountId = template.accountId?.takeIf { id -> data.accounts.any { it.id == id } },
            )
            normalized.problem()?.let { throw LedgerInputException(it) }
            val previous = data.templates.firstOrNull { it.id == template.id }
            if (previous == null && data.templates.size >= RuleMath.MAX_TEMPLATES) throw LedgerInputException("模板最多 ${RuleMath.MAX_TEMPLATES} 个")
            val saved = normalized.copy(
                position = previous?.position ?: ((data.templates.maxOfOrNull { it.position } ?: -1) + 1),
                updatedAt = System.currentTimeMillis(),
            )
            database.saveLedgerRules(listOf(saved.toRow(keys)))
            publish { d -> d.copy(templates = (d.templates.filterNot { it.id == saved.id } + saved).sortedBy { it.position }) }
            saved
        }
    }

    // ── 账户 ──────────────────────────────────────────────────────

    suspend fun saveAccount(account: LedgerAccount): LedgerAccount = withContext(Dispatchers.IO) {
        val keys = keys()
        val name = account.name.trim()
        if (name.isEmpty()) throw LedgerInputException("请填写账户名称")
        val current = state.value
        val now = System.currentTimeMillis()
        val previous = current.accounts.firstOrNull { it.id == account.id }
        if (kotlin.math.abs(account.openingBalance) > com.xiguli.jinkui.data.ledger.Money.MAX_CENTS) {
            throw LedgerInputException("期初余额超出范围")
        }
        account.credit?.problem()?.let { throw LedgerInputException(it) }
        val saved = account.copy(
            name = name.take(40),
            // 只有银行卡 / 信用卡账户能关联卡片；改成别的种类时去掉关联，免得留下看不见的引用
            cardId = account.cardId.takeIf { account.kind == AccountKind.BANK || account.kind == AccountKind.CREDIT },
            // 账单设置只对信用账户有意义
            credit = account.credit.takeIf { account.kind == AccountKind.CREDIT },
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
        if (state.value.recurring.any { it.accountId == id || it.toAccountId == id }) {
            throw LedgerInputException("有周期记账在用这个账户，请先修改或删除那条周期记账")
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
        if (category.parentId == category.id) throw LedgerInputException("分类最多两级")
        // 已经有子分类的一级分类不能再挂到别的分类下面，否则会变成三级
        if (category.parentId != null && current.categories.any { it.parentId == category.id }) {
            throw LedgerInputException("这个分类下面还有子分类，不能再移到其他分类下")
        }
        if (parent != null && parent.type != category.type) throw LedgerInputException("子分类要和上级分类同为支出或收入")
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

    // ── 欠款 / 账单（1.4 起）──────────────────────────────────────
    // 每次改动一个事务：欠款记录、它带来的流水与删除标记一起写入。所有金额最终都是普通流水，
    // 余额、统计、导出照旧由流水算出；分期与借贷记录只存约定（期数、手续费、预计还款日、利息）。

    /** 某一天对应的记账时间：今天用现在的时刻，其他日子用中午 12 点（避免时区边界把日期挪一天）。 */
    private fun timeOn(date: LocalDate, zone: ZoneId, now: Long): Long =
        if (date == LedgerMath.dateOf(now, zone)) now else date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun categoryOr(data: LedgerData, vararg ids: String): String? =
        ids.firstOrNull { id -> data.categories.any { it.id == id } }

    /** 保存信用账户的账单设置（[settings] 为 null 时清除）。 */
    suspend fun saveCreditSettings(accountId: String, settings: CreditSettings?): LedgerAccount {
        val account = state.value.accounts.firstOrNull { it.id == accountId } ?: throw LedgerInputException("找不到这个账户")
        if (account.kind != AccountKind.CREDIT) throw LedgerInputException("只有信用账户可以设置账单日")
        return saveAccount(account.copy(credit = settings))
    }

    /**
     * 信用账户还款：从资金账户 [fromId] 转 [amount] 到信用账户，可以全额也可以部分还款。
     * 还款就是一笔转账，账单的“已还”由它算出。
     */
    suspend fun repayCredit(
        creditId: String,
        fromId: String,
        amount: Long,
        date: LocalDate,
        note: String = "",
        zone: ZoneId = ZoneId.systemDefault(),
    ): LedgerTransaction {
        val data = state.value
        val credit = data.accounts.firstOrNull { it.id == creditId && it.kind == AccountKind.CREDIT } ?: throw LedgerInputException("找不到这个信用账户")
        val from = data.accounts.firstOrNull { it.id == fromId } ?: throw LedgerInputException("请选择还款账户")
        if (from.kind == AccountKind.CREDIT) throw LedgerInputException("请用资金账户还款")
        val now = System.currentTimeMillis()
        return saveTransaction(
            LedgerTransaction(
                id = LedgerIds.newId(),
                type = TxType.TRANSFER,
                amount = amount,
                occurredAt = timeOn(date, zone, now),
                accountId = from.id,
                toAccountId = credit.id,
                note = note.ifBlank { "还款 · ${credit.name}" },
            ),
        )
    }

    /**
     * 新建分期。[recordPurchase] 为 true 时把分期总额记成信用账户上的一笔消费（分期前没有记过这笔消费时）；
     * 手续费合计大于 0 时记成一笔“分期手续费”支出。两笔都带着这笔分期的 ID，删分期时一并删掉。
     */
    suspend fun createInstallment(
        plan: LedgerInstallment,
        recordPurchase: Boolean,
        purchaseCategoryId: String? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LedgerInstallment = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val data = state.value
            val account = data.accounts.firstOrNull { it.id == plan.accountId && it.kind == AccountKind.CREDIT }
                ?: throw LedgerInputException("请选择信用账户")
            if (account.credit == null) throw LedgerInputException("请先设置这个账户的账单日与还款日")
            val now = System.currentTimeMillis()
            val saved = plan.copy(title = plan.title.trim().take(40), note = plan.note.trim().take(500), createdAt = now, updatedAt = now)
            saved.problem()?.let { throw LedgerInputException(it) }
            val at = timeOn(saved.startDate, zone, now)
            val txs = buildList {
                if (recordPurchase) {
                    add(
                        LedgerTransaction(
                            LedgerIds.newId(), TxType.EXPENSE, saved.total, at, account.id,
                            categoryId = purchaseCategoryId?.takeIf { id -> data.categories.any { it.id == id } },
                            note = "分期消费 · ${saved.title}", createdAt = now, updatedAt = now, debtId = saved.id,
                        ),
                    )
                }
                if (saved.chargedFees > 0) {
                    add(
                        LedgerTransaction(
                            LedgerIds.newId(), TxType.EXPENSE, saved.chargedFees, at, account.id,
                            categoryId = feeCategory(data),
                            note = "分期手续费 · ${saved.title}（${saved.periods} 期）", createdAt = now, updatedAt = now, debtId = saved.id,
                        ),
                    )
                }
            }
            txs.forEach { tx -> LedgerMath.validate(tx, data.accounts)?.let { throw LedgerInputException(it) } }
            database.writeDebtChange(debts = listOf(saved.toRow(keys)), transactions = txs.map { it.toRow(keys) })
            publish { d -> d.copy(installments = d.installments.filterNot { it.id == saved.id } + saved, transactions = d.transactions + txs) }
            saved
        }
    }

    /** 手续费的分类：账本里有叫“手续费”的就用它（1.3 导入会建），否则“其他支出”。 */
    private fun feeCategory(data: LedgerData): String? =
        data.categories.firstOrNull { it.type == TxType.EXPENSE && !it.archived && it.name == "手续费" }?.id
            ?: categoryOr(data, "cat-other-expense")

    /**
     * 提前结清分期：之后各期的本金并入 [date] 所在的那一期账单，之后各期的手续费不再收取——
     * 手续费那一笔流水改成实际收取的金额（为 0 时删掉，并记删除标记）。
     */
    suspend fun payoffInstallment(id: String, date: LocalDate): LedgerInstallment = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val data = state.value
            val plan = data.installments.firstOrNull { it.id == id } ?: throw LedgerInputException("找不到这笔分期")
            if (plan.payoffDate != null) throw LedgerInputException("这笔分期已经提前结清")
            val now = System.currentTimeMillis()
            val saved = DebtMath.payoff(plan, date).copy(updatedAt = now)
            val feeTx = data.transactions.firstOrNull { it.debtId == id && it.note.startsWith("分期手续费") }
            val fees = saved.chargedFees
            val changed = feeTx?.takeIf { fees > 0 && it.amount != fees }?.copy(amount = fees, updatedAt = now)
            val removed = feeTx?.takeIf { fees == 0L }
            val tombstones = listOfNotNull(removed?.let { LedgerTombstone(it.id, LedgerTombstone.KIND_TX, now) })
            database.writeDebtChange(
                debts = listOf(saved.toRow(keys)),
                transactions = listOfNotNull(changed).map { it.toRow(keys) },
                deleteTransactions = listOfNotNull(removed?.id),
                tombstones = tombstones.map { it.toRow() },
            )
            publish { d ->
                d.copy(
                    installments = d.installments.map { if (it.id == id) saved else it },
                    transactions = d.transactions.filterNot { it.id == removed?.id }.map { if (it.id == changed?.id) changed else it },
                    tombstones = d.tombstones + tombstones,
                )
            }
            saved
        }
    }

    /**
     * 删除一笔欠款（分期或借贷）：删掉记录和它名下的全部流水（分期的消费与手续费、借贷的借出 / 还款 / 利息），
     * 都记下删除标记，导入旧备份时不会加回来。“导入的借贷”的记录只删记录（预计还款日等约定），导入的流水保留。
     * 返回删掉的流水笔数。
     */
    suspend fun deleteDebt(id: String): Int = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            keys()
            val data = state.value
            val legacy = data.loans.firstOrNull { it.id == id }?.legacy == true
            val doomed = if (legacy) emptyList() else data.transactions.filter { it.debtId == id }
            val now = System.currentTimeMillis()
            val tombstones = listOf(LedgerTombstone(id, LedgerTombstone.KIND_DEBT, now)) +
                doomed.map { LedgerTombstone(it.id, LedgerTombstone.KIND_TX, now) }
            database.writeDebtChange(
                deleteDebts = listOf(id),
                deleteTransactions = doomed.map { it.id },
                tombstones = tombstones.map { it.toRow() },
            )
            val gone = doomed.mapTo(HashSet()) { it.id }
            publish { d ->
                d.copy(
                    installments = d.installments.filterNot { it.id == id },
                    loans = d.loans.filterNot { it.id == id },
                    transactions = d.transactions.filterNot { it.id in gone },
                    tombstones = d.tombstones.filterNot { it.id == id || it.id in gone } + tombstones,
                )
            }
            doomed.size
        }
    }

    /**
     * 新建一笔借贷：对方用已有的应收 / 应付账户（[personAccountId]），或者按 [person] 新建一个
     * （“借出 · 小李”“借入 · 老王”，与导入同名的会复用）；同时记一笔对方账户与资金账户 [fundId] 之间的转账。
     */
    suspend fun createLoan(
        direction: LoanDirection,
        person: String,
        personAccountId: String?,
        amount: Long,
        /** 资金账户；null = 不经过账户（只记欠款：不改任何资金账户的余额）。 */
        fundId: String?,
        date: LocalDate,
        dueDate: LocalDate?,
        interest: Long,
        note: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LedgerLoan = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val data = state.value
            val now = System.currentTimeMillis()
            val name = person.trim().take(20)
            val prefix = if (direction == LoanDirection.LEND) "借出 · " else "借入 · "
            val existing = personAccountId?.let { id -> data.accounts.firstOrNull { it.id == id && it.kind == direction.accountKind } }
                ?: data.accounts.firstOrNull { it.kind == direction.accountKind && (it.name == prefix + name || DebtMath.personOf(it) == name) && name.isNotEmpty() }
            if (existing == null && name.isEmpty()) throw LedgerInputException("请填写对方")
            if (interest < 0 || interest > Money.MAX_CENTS) throw LedgerInputException("利息超出范围")
            if (dueDate != null && dueDate < date) throw LedgerInputException("预计还款日不能早于借款日期")
            val (fund, newAdjust) = loanFund(data, fundId, now)
            val created = if (existing == null) {
                LedgerAccount(
                    LedgerIds.newId(), prefix + name, direction.accountKind,
                    position = (data.accounts.maxOfOrNull { it.position } ?: -1) + 1, createdAt = now, updatedAt = now,
                )
            } else null
            // 停用的对方账户重新有了借贷：恢复启用
            val revived = existing?.takeIf { it.archived }?.copy(archived = false, updatedAt = now)
            val account = created ?: revived ?: existing!!
            val loan = LedgerLoan(
                id = LedgerIds.newId(), accountId = account.id, direction = direction, date = date, dueDate = dueDate,
                interest = interest, note = note.trim().take(500), createdAt = now, updatedAt = now,
            )
            val tx = DebtMath.loanPrincipalTx(LedgerIds.newId(), loan, account, fund, amount, timeOn(date, zone, now), now)
            val accounts = data.accounts.filterNot { it.id == account.id } + account + listOfNotNull(newAdjust)
            LedgerMath.validate(tx, accounts)?.let { throw LedgerInputException(it) }
            database.writeDebtChange(
                accounts = listOfNotNull(created ?: revived, newAdjust).map { it.toRow(keys) },
                debts = listOf(loan.toRow(keys)),
                transactions = listOf(tx.toRow(keys)),
            )
            publish { d ->
                d.copy(
                    accounts = (if (created == null && revived == null) d.accounts else d.accounts.filterNot { it.id == account.id } + account)
                        .plus(listOfNotNull(newAdjust)).sortedBy { it.position },
                    loans = d.loans + loan,
                    transactions = d.transactions + tx,
                )
            }
            loan
        }
    }

    /**
     * 借贷的资金一边（1.4.0-alpha04）。[fundId] 为 null 表示“不经过账户（只记欠款）”：本金记成内部“余额调整”账户
     * 与对方账户之间的转账——对方账户（应收 / 应付）照常增减，欠款总额正确；“余额调整”不计入资产、负债、净资产，
     * 也不出现在记账面板里，所以任何资金账户的余额都不变。还没有“余额调整”账户时新建一个（停用状态，与账单导入同一种），
     * 第二个返回值就是要一并写入的新账户。指定了账户时，可以是任何在用的资金账户（含信用卡 / 花呗），不能是借贷对象。
     */
    private fun loanFund(data: LedgerData, fundId: String?, now: Long): Pair<LedgerAccount, LedgerAccount?> =
        try {
            DebtMath.loanFundAccount(data.accounts, fundId, now)
        } catch (e: IllegalArgumentException) {
            throw LedgerInputException(e.message ?: "请选择资金账户")
        }

    /**
     * 改借贷的约定（预计还款日、利息、备注）。“导入的借贷”第一次改时落库成一条记录（ID 不变）。
     */
    suspend fun saveLoan(loan: LedgerLoan): LedgerLoan = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            if (loan.interest < 0 || loan.interest > Money.MAX_CENTS) throw LedgerInputException("利息超出范围")
            if (state.value.accounts.none { it.id == loan.accountId }) throw LedgerInputException("找不到对方账户")
            val now = System.currentTimeMillis()
            val previous = state.value.loans.firstOrNull { it.id == loan.id }
            val saved = loan.copy(note = loan.note.trim().take(500), createdAt = previous?.createdAt ?: now, updatedAt = now)
            database.writeDebtChange(debts = listOf(saved.toRow(keys)))
            publish { d -> d.copy(loans = d.loans.filterNot { it.id == saved.id } + saved, tombstones = d.tombstones.filterNot { it.id == saved.id }) }
            saved
        }
    }

    /**
     * 收款（借出）/ 还款（借入）：本金部分是对方账户与资金账户之间的转账，可以只还一部分；
     * 利息部分是资金账户上的一笔收入（借出）/ 支出（借入）。两笔都带着借贷 ID。
     */
    suspend fun loanPayment(
        loanId: String,
        accountId: String,
        principal: Long,
        interest: Long,
        /** 资金账户；null = 不经过账户（只记欠款：本金只减少欠款，不改资金账户余额；利息照样计入收支）。 */
        fundId: String?,
        date: LocalDate,
        note: String = "",
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<LedgerTransaction> = withContext(Dispatchers.IO) {
        recurringLock.withLock {
            val keys = keys()
            val data = state.value
            val account = data.accounts.firstOrNull { it.id == accountId } ?: throw LedgerInputException("找不到对方账户")
            val direction = LoanDirection.of(account.kind) ?: throw LedgerInputException("找不到对方账户")
            if (principal <= 0 && interest <= 0) throw LedgerInputException("金额要大于 0")
            val now = System.currentTimeMillis()
            val (fund, newAdjust) = loanFund(data, fundId, now)
            val at = timeOn(date, zone, now)
            val lend = direction == LoanDirection.LEND
            // 合成的“导入的借贷”：流水不挂 ID，照样按账户归到它名下
            val link = loanId.takeIf { id -> data.loans.any { it.id == id && !it.legacy } }
            val txs = DebtMath.loanPaymentTxs(
                account, fund, principal, interest, at, now, note, link, loanId,
                if (lend) categoryOr(data, "cat-invest", "cat-other-income") else categoryOr(data, "cat-other-expense"),
            )
            val accounts = data.accounts + listOfNotNull(newAdjust)
            txs.forEach { tx -> LedgerMath.validate(tx, accounts)?.let { throw LedgerInputException(it) } }
            database.writeDebtChange(accounts = listOfNotNull(newAdjust).map { it.toRow(keys) }, transactions = txs.map { it.toRow(keys) })
            publish { d -> d.copy(accounts = (d.accounts + listOfNotNull(newAdjust)).sortedBy { it.position }, transactions = d.transactions + txs) }
            txs
        }
    }

    /**
     * 结清（剩余不再收 / 不再还）：把剩余本金记成对方账户上的一笔支出（借出：坏账 / 免除）或收入（借入：对方免还），
     * 对方账户余额归零，这笔借贷变成已结清。
     */
    suspend fun forgiveLoan(loanId: String, accountId: String, remaining: Long, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LedgerTransaction? =
        withContext(Dispatchers.IO) {
            recurringLock.withLock {
                val keys = keys()
                if (remaining <= 0) return@withLock null
                val data = state.value
                val account = data.accounts.firstOrNull { it.id == accountId } ?: throw LedgerInputException("找不到对方账户")
                val lend = LoanDirection.of(account.kind) ?: throw LedgerInputException("找不到对方账户")
                val now = System.currentTimeMillis()
                val tx = LedgerTransaction(
                    LedgerIds.newId(),
                    if (lend == LoanDirection.LEND) TxType.EXPENSE else TxType.INCOME,
                    remaining, timeOn(date, zone, now), account.id,
                    categoryId = if (lend == LoanDirection.LEND) categoryOr(data, "cat-other-expense") else categoryOr(data, "cat-other-income"),
                    note = (if (lend == LoanDirection.LEND) "免除 · " else "对方免还 · ") + DebtMath.personOf(account),
                    createdAt = now, updatedAt = now,
                    debtId = loanId.takeIf { id -> data.loans.any { it.id == id && !it.legacy } },
                )
                LedgerMath.validate(tx, data.accounts)?.let { throw LedgerInputException(it) }
                database.writeDebtChange(transactions = listOf(tx.toRow(keys)))
                publish { d -> d.copy(transactions = d.transactions + tx) }
                tx
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
        // 导入批次只存本机、不进备份：备份里较新的同一笔覆盖本机时，保留本机记下的批次
        val localBatch = state.value.transactions.filter { it.importBatch != null }.associate { it.id to it.importBatch }
        val write = plan.toWrite.let { w -> w.copy(transactions = w.transactions.map { t -> t.copy(importBatch = localBatch[t.id] ?: t.importBatch) }) }
        try {
            if (!write.isEmpty) {
                database.writeLedger(
                    LedgerBatch(
                        accounts = write.accounts.map { it.toRow(keys) },
                        categories = write.categories.map { it.toRow(keys) },
                        transactions = write.transactions.map { it.toRow(keys) },
                        budgets = write.budgets.map { it.toRow(keys) },
                        recurring = write.recurring.map { it.toRow(keys) },
                        tombstones = write.tombstones.map { it.toRow() },
                        debts = write.installments.map { it.toRow(keys) } + write.loans.map { it.toRow(keys) },
                        rules = write.rules.map { it.toRow(keys) } + write.templates.map { it.toRow(keys) },
                    ),
                )
            }
        } finally {
            runCatching { load() }
        }
        LedgerImportSummary(plan.added, plan.updated, plan.skipped, plan.suppressed)
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
        importBatch,
    )

    private fun LedgerBudget.toRow(keys: SessionKeys) = LedgerBudgetRow(
        id, categoryId, updatedAt,
        keys.field.encrypt(LedgerJson.budgetPayload(this), budgetContext(id)),
    )

    private fun PendingEntry.toRow(keys: SessionKeys) = LedgerPendingRow(
        id, createdAt,
        keys.field.encrypt(PendingMath.payload(this), pendingContext(id)),
    )

    private fun LedgerImportBatch.toRow(keys: SessionKeys) = LedgerImportBatchRow(
        id, createdAt, keys.field.encrypt(LedgerJson.importBatchPayload(this), batchContext(id)),
    )

    private fun LedgerTombstone.toRow() = LedgerTombstoneRow(id, kind, deletedAt)

    private fun LedgerInstallment.toRow(keys: SessionKeys) = LedgerDebtRow(
        id, LedgerJson.DEBT_INSTALLMENT, updatedAt, keys.field.encrypt(LedgerJson.debtPayload(this), debtContext(id)),
    )

    private fun LedgerLoan.toRow(keys: SessionKeys) = LedgerDebtRow(
        id, LedgerJson.DEBT_LOAN, updatedAt, keys.field.encrypt(LedgerJson.debtPayload(this), debtContext(id)),
    )

    private fun LedgerRule.toRow(keys: SessionKeys) = LedgerRuleRow(
        id, RuleMath.KIND_RULE, updatedAt, keys.field.encrypt(RuleMath.ruleJson(this).toString(), ruleContext(id)),
    )

    private fun LedgerTemplate.toRow(keys: SessionKeys) = LedgerRuleRow(
        id, RuleMath.KIND_TEMPLATE, updatedAt, keys.field.encrypt(RuleMath.templateJson(this).toString(), ruleContext(id)),
    )

    private fun LedgerRecurring.toRow(keys: SessionKeys) = LedgerRecurringRow(
        id, updatedAt,
        keys.field.encrypt(LedgerJson.recurringPayload(this), recurringContext(id)),
    )

    private companion object {
        // AAD：每段密文绑定到自己的记录，挪到别处无法解密。
        fun accountContext(id: String) = "ledger:account:$id"
        fun categoryContext(id: String) = "ledger:category:$id"
        fun transactionContext(id: String) = "ledger:tx:$id"
        fun budgetContext(id: String) = "ledger:budget:$id"
        fun recurringContext(id: String) = "ledger:recurring:$id"
        fun pendingContext(id: String) = "ledger:pending:$id"
        fun batchContext(id: String) = "ledger:batch:$id"
        fun debtContext(id: String) = "ledger:debt:$id"
        fun ruleContext(id: String) = "ledger:rule:$id"
    }
}
