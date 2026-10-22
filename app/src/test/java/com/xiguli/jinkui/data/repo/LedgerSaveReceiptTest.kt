package com.xiguli.jinkui.data.repo

import com.xiguli.jinkui.data.ledger.*
import org.junit.Assert.*
import org.junit.Test

class LedgerSaveReceiptTest {
    private val account = LedgerAccount("demo-account", "虚构账户", AccountKind.CASH)
    private val saved = LedgerTransaction("demo-entry", TxType.EXPENSE, 3250, 1000, account.id, updatedAt=2000)
    private val data = LedgerData(accounts=listOf(account), transactions=listOf(saved))
    @Test fun undoCreateRemovesEntryAndWritesTombstone() {
        val restored = LedgerSaveReceipt(saved, null).restore(data, 3000)
        assertTrue(restored.transactions.isEmpty())
        assertEquals(saved.id, restored.tombstones.single().id)
    }
    @Test fun undoEditRestoresContentWithNewerMergeClock() {
        val before = saved.copy(amount=1000, note="原备注", updatedAt=1000)
        val restored = LedgerSaveReceipt(saved, before).restore(data, 3000).transactions.single()
        assertEquals(before.copy(updatedAt=3000), restored)
    }
    @Test fun undoPreservesOtherTransactionsAndMetadata() {
        val other = saved.copy(id="another", amount=500)
        val current = data.copy(transactions=listOf(saved,other), unreadable=3)
        val restored = LedgerSaveReceipt(saved,null).restore(current,3000)
        assertEquals(listOf(other), restored.transactions)
        assertEquals(3, restored.unreadable)
        assertEquals(current.accounts, restored.accounts)
    }
    @Test(expected=LedgerInputException::class) fun laterEditCannotBeOverwritten() {
        LedgerSaveReceipt(saved,null).restore(data.copy(transactions=listOf(saved.copy(note="后续修改"))),3000)
    }
    @Test(expected=LedgerInputException::class) fun deletedEntryCannotBeResurrectedBySaveUndo() {
        LedgerSaveReceipt(saved,saved.copy(amount=500)).restore(data.copy(transactions=emptyList()),3000)
    }
    @Test(expected=LedgerInputException::class) fun cannotOrphanRefund() {
        LedgerSaveReceipt(saved,null).restore(data.copy(transactions=listOf(saved,saved.copy(id="refund",type=TxType.INCOME,refundOf=saved.id))),3000)
    }
    @Test(expected=LedgerInputException::class) fun cannotOrphanReimbursement() {
        LedgerSaveReceipt(saved,null).restore(data.copy(transactions=listOf(saved,saved.copy(id="reimburse",type=TxType.INCOME,reimburseOf=listOf(saved.id)))),3000)
    }
    @Test(expected=LedgerInputException::class) fun cannotUndoEditedAmountAfterRefundWasLinked() {
        LedgerSaveReceipt(saved,saved.copy(amount=500)).restore(data.copy(transactions=listOf(saved,saved.copy(id="refund-after-edit",type=TxType.INCOME,amount=1000,refundOf=saved.id))),3000)
    }
    @Test(expected=LedgerInputException::class) fun cannotRestoreDeletedPreviousAccount() {
        LedgerSaveReceipt(saved,saved.copy(accountId="deleted-account")).restore(data,3000)
    }
}
