package com.xiguli.jinkui.data.repo

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.xiguli.jinkui.core.crypto.FieldCipher
import com.xiguli.jinkui.core.security.SessionKeys
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.data.db.VaultDatabase
import com.xiguli.jinkui.data.db.VaultSchema
import com.xiguli.jinkui.data.ledger.*
import com.xiguli.jinkui.ui.ledger.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId


/** Real repository + real field encryption + real Android SQLite via test adapter.
 * Not SQLCipher/Keystore, not user data, not physical UI clicks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class OutstandingInterestIntegrationTest {
    data class Fixture(val repo: LedgerRepository, val sql: SQLiteDatabase, val vault: VaultManager)
    private fun fixture(): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val sql = SQLiteDatabase.create(null)
        val db = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, m, a ->
            when(m.name) {
                "execSQL" -> { if(a!!.size == 1) sql.execSQL(a[0] as String) else sql.execSQL(a[0] as String, a[1] as Array<out Any?>); null }
                "beginTransaction" -> {sql.beginTransaction(); null}
                "setTransactionSuccessful" -> {sql.setTransactionSuccessful(); null}
                "endTransaction" -> {sql.endTransaction(); null}
                "inTransaction" -> sql.inTransaction()
                "isOpen" -> sql.isOpen
                "query" -> { val args = if(a!!.size > 1) (a[1] as Array<*>).map { it?.toString() }.toTypedArray() else emptyArray(); sql.rawQuery(a[0] as String,args) }
                else -> error("unhandled database method ${m.name}")
            }
        } as SupportSQLiteDatabase
        VaultSchema.create().forEach { sql.execSQL(it) }
        val helper = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteOpenHelper::class.java)) { _, m, _ ->
            when(m.name) { "getWritableDatabase", "getReadableDatabase" -> db; "close" -> {sql.close(); null}; else -> error(m.name) }
        } as SupportSQLiteOpenHelper
        val database = VaultDatabase(context)
        VaultDatabase::class.java.getDeclaredField("helper").apply { isAccessible=true }.set(database, helper)
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible=true }.get(null)
        val vault = unsafeClass.getMethod("allocateInstance",Class::class.java).invoke(unsafe,VaultManager::class.java) as VaultManager
        val key = ByteArray(32) { (it+1).toByte() }
        VaultManager::class.java.getDeclaredField("keys").apply { isAccessible=true }.set(vault, SessionKeys(key,FieldCipher(key),FieldCipher(key)))
        return Fixture(LedgerRepository(database,vault),sql,vault)
    }
    private val today = LocalDate.of(2026,10,21)
    private val zone = ZoneId.of("Asia/Shanghai")
    @Test fun explicitPrincipalAndSeparateInterestKeepsApiContract() = runBlocking {
        val f=fixture()
        try {
            f.repo.saveAccount(LedgerAccount("bank","虚构银行",AccountKind.BANK,openingBalance=500000))
            val loan=f.repo.createLoan(LoanDirection.BORROW,"虚构甲",null,100000,"bank",today.minusDays(10),today.minusDays(1),5000,"isolated",zone)
            val target=LoanEntryMath.targets(f.repo.data.value,LoanAction.REPAY,today,zone).single()
            val request=LoanEntryRequest(LoanAction.REPAY,target.person,target.accountId,target.loanId,100000,"bank",today,null,5000,"")
            assertNull(LoanEntryMath.validate(request,target))
            val split=LoanEntryMath.splitOverpay(request,target)
            assertEquals(request,split)
            val before=LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank")
            val written=f.repo.loanPayment(split.loanId!!,split.accountId!!,split.amount,split.interest,split.fundId,split.date,split.note,zone)
            assertEquals(2,written.size)
            val after=LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank")
            assertEquals(105000L,before-after)
            assertTrue(DebtMath.overview(f.repo.data.value,today,zone).loans.single().settled)
            println("INTEGRATED principal=1000 separateInterest=50 -> stored total=1050; API remains principal+interest")
        } finally {f.vault.keys!!.wipe(); f.sql.close()}
    }
    @Test fun automaticOverpayWithoutExplicitInterestStillSubmits1050() = runBlocking {
        val f=fixture()
        try {
            f.repo.saveAccount(LedgerAccount("bank","虚构银行",AccountKind.BANK,openingBalance=500000))
            val loan=f.repo.createLoan(LoanDirection.BORROW,"虚构甲",null,100000,"bank",today.minusDays(10),today.minusDays(1),5000,"isolated",zone)
            val target=LoanEntryMath.targets(f.repo.data.value,LoanAction.REPAY,today,zone).single()
            val request=LoanEntryRequest(LoanAction.REPAY,target.person,target.accountId,target.loanId,105000,"bank",today,null,0,"")
            assertNull(LoanEntryMath.validate(request,target))
            val split=LoanEntryMath.splitOverpay(request,target)
            assertEquals(100000L,split.amount);assertEquals(5000L,split.interest)
            val before=LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank")
            f.repo.loanPayment(loan.id,loan.accountId,split.amount,split.interest,"bank",today,"",zone)
            val after=LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank")
            assertEquals(105000L,before-after)
        } finally {f.vault.keys!!.wipe();f.sql.close()}
    }
    @Test fun outstandingInterestRetainsTargetsAndCanBePaidThroughRepository() = runBlocking {
        for(direction in LoanDirection.entries) {
            val f=fixture()
            try {
                f.repo.saveAccount(LedgerAccount("bank","虚构银行",AccountKind.BANK,openingBalance=500000))
                val loan=f.repo.createLoan(direction,"虚构乙",null,100000,"bank",today.minusDays(10),today.minusDays(1),5000,"isolated",zone)
                f.repo.loanPayment(loan.id,loan.accountId,100000,0,"bank",today,"",zone)
                val overview=DebtMath.overview(f.repo.data.value,today,zone)
                val view=overview.loans.single()
                assertEquals(0L,view.remaining);assertEquals(5000L,view.interestRemaining)
                assertFalse(view.settled);assertTrue(view.overdue(today))
                assertEquals(5000L, overview.items.single().amount)
                assertEquals(if (direction == LoanDirection.LEND) 5000L else 0L, overview.receivable)
                assertEquals(if (direction == LoanDirection.BORROW) 5000L else 0L, overview.payable)
                assertEquals(if (direction == LoanDirection.BORROW) 5000L else 0L, overview.totalDebt)
                assertEquals(if (direction == LoanDirection.LEND) 5000L else 0L, overview.people.single().receivable)
                assertEquals(if (direction == LoanDirection.BORROW) 5000L else 0L, overview.people.single().payable)
                val person=PersonDebtMath.person(listOf(view),loan.accountId,today)!!
                assertFalse(person.settled);assertEquals(listOf(view),person.open)
                val action=if(direction==LoanDirection.BORROW) LoanAction.REPAY else LoanAction.COLLECT
                val target=LoanEntryMath.targets(f.repo.data.value,action,today,zone).single()
                val request=LoanEntryRequest(action,target.person,target.accountId,target.loanId,0,"bank",today,null,5000,"")
                assertNull(LoanEntryMath.validate(request,target));assertEquals(request,LoanEntryMath.splitOverpay(request,target))
                val before=f.repo.data.value.transactions.size
                val written=f.repo.loanPayment(loan.id,loan.accountId,0,5000,"bank",today,"",zone)
                assertEquals(1,written.size);assertEquals(before+1,f.repo.data.value.transactions.size)
                val after=DebtMath.overview(f.repo.data.value,today,zone).loans.single()
                assertTrue(after.settled);assertEquals(0L,after.interestRemaining)
                assertTrue(LoanEntryMath.targets(f.repo.data.value,action,today,zone).isEmpty())
                println("INTEGRATED $direction principal zero keeps unpaid interest and payment target; actual interest-only payment settles")
            } finally {f.vault.keys!!.wipe();f.sql.close()}
        }
    }
    @Test fun personInterestOnlyPaymentStaysLinkedToOpenLoan() = runBlocking {
        val f=fixture()
        try {
            f.repo.saveAccount(LedgerAccount("bank","虚构银行",AccountKind.BANK,openingBalance=500000))
            val loan=f.repo.createLoan(LoanDirection.BORROW,"虚构乙",null,100000,"bank",today.minusDays(10),today.minusDays(1),5000,"isolated",zone)
            f.repo.loanPayment(loan.id,loan.accountId,100000,0,"bank",today,"",zone)
            val written=f.repo.personPayment(loan.accountId,0,5000,"bank",today,"",zone)
            assertEquals(loan.id,written.single().debtId)
            assertTrue(DebtMath.overview(f.repo.data.value,today,zone).loans.single().settled)
        } finally {f.vault.keys!!.wipe();f.sql.close()}
    }
    @Test fun interestOnlyArrearsDoNotReceiveZeroPrincipalRowsForAnotherLoan() = runBlocking {
        val f=fixture()
        try {
            f.repo.saveAccount(LedgerAccount("bank","虚构银行",AccountKind.BANK,openingBalance=500000))
            val first=f.repo.createLoan(LoanDirection.BORROW,"虚构丙",null,100000,"bank",today.minusDays(10),today.minusDays(1),5000,"isolated",zone)
            f.repo.loanPayment(first.id,first.accountId,100000,0,"bank",today,"",zone)
            val second=f.repo.createLoan(LoanDirection.BORROW,"虚构丙",first.accountId,20000,"bank",today.minusDays(2),today.plusDays(10),0,"isolated",zone)
            val person=PersonDebtMath.person(DebtMath.overview(f.repo.data.value,today,zone).loans,first.accountId,today)!!
            val parts=PersonDebtMath.split(person,10000)!!
            assertEquals(1,parts.size);assertEquals(second.id,parts.single().first.loan.id);assertEquals(10000L,parts.single().second)
            val written=f.repo.personPayment(first.accountId,10000,0,"bank",today,"",zone)
            assertEquals(1,written.size);assertEquals(second.id,written.single().debtId)
            val after=DebtMath.overview(f.repo.data.value,today,zone)
            assertFalse(after.loans.first {it.loan.id==first.id}.settled)
            assertEquals(5000L,after.loans.first {it.loan.id==first.id}.interestRemaining)
            println("INTEGRATED old interest arrears remain open; principal payment targets next loan without zero-value rows")
        } finally {f.vault.keys!!.wipe();f.sql.close()}
    }

    @Test fun partialPaymentsPersistAndGroupedDeleteUndoRestoresBothDirections() = runBlocking {
        for (direction in LoanDirection.entries) {
            val f = fixture()
            try {
                f.repo.saveAccount(LedgerAccount("bank", "虚构银行", AccountKind.BANK, openingBalance=500000))
                val loan=f.repo.createLoan(direction,"虚构分期",null,100000,"bank",today.minusDays(10),today.plusDays(10),5000,"isolated",zone)
                val baselineBalance=LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank")
                val first=40000L to 1000L
                f.repo.loanPayment(loan.id,loan.accountId,first.first,first.second,"bank",today.minusDays(1),"first",zone)
                f.repo.load()
                val partial=DebtMath.overview(f.repo.data.value,today,zone).loans.single()
                assertEquals(60000L,partial.remaining);assertEquals(4000L,partial.interestRemaining);assertFalse(partial.settled)
                val paidBalance=LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank")
                assertEquals(if(direction==LoanDirection.BORROW) -41000L else 41000L,paidBalance-baselineBalance)
                val second=60000L to 4000L
                val written=f.repo.loanPayment(loan.id,loan.accountId,second.first,second.second,"bank",today,"second",zone)
                f.repo.load()
                assertTrue(DebtMath.overview(f.repo.data.value,today,zone).loans.single().settled)
                val finalBalance=LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank")
                assertEquals(if(direction==LoanDirection.BORROW) -105000L else 105000L,finalBalance-baselineBalance)
                val inverse=f.repo.deleteDebtRecord(written.first().id,zone)
                f.repo.load()
                val deleted=DebtMath.overview(f.repo.data.value,today,zone).loans.single()
                assertEquals(60000L,deleted.remaining);assertEquals(4000L,deleted.interestRemaining);assertFalse(deleted.settled)
                assertEquals(paidBalance,LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank"))
                assertTrue(written.none { row -> f.repo.data.value.transactions.any {it.id==row.id} })
                f.repo.undoDebtChange(inverse)
                f.repo.load()
                assertTrue(DebtMath.overview(f.repo.data.value,today,zone).loans.single().settled)
                assertEquals(finalBalance,LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank"))
                assertTrue(written.all {row -> f.repo.data.value.transactions.any {it.id==row.id && it.amount==row.amount && it.debtId==row.debtId}})
                println("$direction SQLite reload: partial400+10 -> owed600+40; payment600+40 settles; grouped delete/undo restores principal, interest and balance")
            } finally {f.vault.keys!!.wipe();f.sql.close()}
        }
    }

    @Test fun deletingAndUndoingInterestOnlyPaymentReopensAndResettlesBothDirections() = runBlocking {
        for(direction in LoanDirection.entries) {
            val f=fixture()
            try {
                f.repo.saveAccount(LedgerAccount("bank","虚构银行",AccountKind.BANK,openingBalance=500000))
                val loan=f.repo.createLoan(direction,"虚构尾息",null,100000,"bank",today.minusDays(10),today.minusDays(1),5000,"isolated",zone)
                f.repo.loanPayment(loan.id,loan.accountId,100000,0,"bank",today.minusDays(1),"",zone)
                val written=f.repo.personPayment(loan.accountId,0,5000,"bank",today,"interest only",zone)
                assertEquals(1,written.size);assertEquals(loan.id,written.single().debtId)
                f.repo.load()
                assertTrue(DebtMath.overview(f.repo.data.value,today,zone).loans.single().settled)
                val inverse=f.repo.deleteDebtRecord(written.single().id,zone)
                f.repo.load()
                val open=DebtMath.overview(f.repo.data.value,today,zone).loans.single()
                assertEquals(0L,open.remaining);assertEquals(5000L,open.interestRemaining);assertFalse(open.settled)
                assertFalse(PersonDebtMath.person(listOf(open),loan.accountId,today)!!.settled)
                f.repo.undoDebtChange(inverse);f.repo.load()
                assertTrue(DebtMath.overview(f.repo.data.value,today,zone).loans.single().settled)
                println("$direction interest-only payment via person route: delete reopens unpaid interest; undo and SQLite reload resettle")
            } finally {f.vault.keys!!.wipe();f.sql.close()}
        }
    }


    @Test fun principalPlusInterestContractRemainsUnchangedForBelowRemainingAmount() = runBlocking {
        val f=fixture()
        try {
            f.repo.saveAccount(LedgerAccount("bank","虚构银行",AccountKind.BANK,openingBalance=500000))
            val loan=f.repo.createLoan(LoanDirection.BORROW,"虚构语义",null,200000,"bank",today.minusDays(10),today.plusDays(10),5000,"isolated",zone)
            val target=LoanEntryMath.targets(f.repo.data.value,LoanAction.REPAY,today,zone).single()
            val request=LoanEntryRequest(LoanAction.REPAY,target.person,target.accountId,target.loanId,105000,"bank",today,null,5000,"")
            assertNull(LoanEntryMath.validate(request,target));assertEquals(request,LoanEntryMath.splitOverpay(request,target))
            val before=LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank")
            f.repo.loanPayment(loan.id,loan.accountId,request.amount,request.interest,"bank",today,"",zone)
            f.repo.load()
            assertEquals(110000L,before-LedgerMath.balances(f.repo.data.value.accounts,f.repo.data.value.transactions).getValue("bank"))
            println("UNCHANGED CONTRACT: remaining2000, principal1050 + separate50 -> actual1100; total-inclusive proposal is NOT implemented")
        } finally {f.vault.keys!!.wipe();f.sql.close()}
    }

}
