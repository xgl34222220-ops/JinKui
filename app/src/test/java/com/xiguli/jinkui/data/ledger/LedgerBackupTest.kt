package com.xiguli.jinkui.data.ledger

import com.xiguli.jinkui.core.crypto.BackupCodec
import com.xiguli.jinkui.core.crypto.BackupFormatException
import com.xiguli.jinkui.core.crypto.FieldCipher
import com.xiguli.jinkui.core.crypto.Kdf
import com.xiguli.jinkui.core.crypto.WrongSecretException
import com.xiguli.jinkui.data.backup.BackupCard
import com.xiguli.jinkui.data.backup.BackupJson
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerBackupTest {
    private val ledger = LedgerData(
        accounts = listOf(
            LedgerAccount("acc-cash", "现金", AccountKind.CASH, 12_345, null, false, 0, 1, 2),
            LedgerAccount("a-1", "招行储蓄卡", AccountKind.BANK, 0, "c-1", false, 1, 3, 4),
            LedgerAccount("a-2", "信用卡", AccountKind.CREDIT, -88_800, null, true, 2, 5, 6),
        ),
        categories = LedgerDefaults.categories() + LedgerCategory("cat-mine", "猫粮", TxType.EXPENSE, "cat-family", "pets", 9, false, 8),
        transactions = listOf(
            LedgerTransaction("t-1", TxType.EXPENSE, 2_350, 1_760_000_000_000, "acc-cash", null, "cat-food-2", "牛肉面 \"加蛋\"\n换行", listOf("工作日", "午饭"), 10, 11),
            LedgerTransaction("t-2", TxType.INCOME, 1_500_000, 1_760_100_000_000, "a-1", null, "cat-salary", "", emptyList(), 12, 13),
            LedgerTransaction("t-3", TxType.TRANSFER, 88_800, 1_760_200_000_000, "a-1", "a-2", null, "还款", emptyList(), 14, 15),
        ),
        budgets = listOf(
            LedgerBudget(LedgerBudget.TOTAL_ID, null, 500_000, 16),
            LedgerBudget(LedgerBudget.idFor("cat-food"), "cat-food", 150_000, 17),
        ),
    )
    private val card = BackupCard(Card(id = "c-1", type = CardType.BANK, title = "招商银行"), emptyMap(), emptyList())

    @Test
    fun ledgerSurvivesBackupRoundTrip() {
        val content = BackupJson.decodeAll(BackupJson.encode(listOf(card), 99L, ledger))
        assertEquals(ledger, content.ledger)
        assertEquals("招商银行", content.cards.single().card.title)
        // 只取卡片的旧接口照常可用
        assertEquals(1, BackupJson.decode(BackupJson.encode(listOf(card), 99L, ledger)).size)
    }

    @Test
    fun ledgerIsInsideTheEncryptedFile() {
        val file = BackupCodec.encode(BackupJson.encode(listOf(card), 99L, ledger), "主密码".toCharArray(), Kdf.MIN_ITERATIONS)
        val raw = String(file, Charsets.ISO_8859_1)
        assertFalse(raw.contains("牛肉面"))
        assertFalse(raw.contains("1500000"))
        assertThrows(WrongSecretException::class.java) { BackupCodec.decode(file, "错的".toCharArray()) }
        val restored = BackupJson.decodeAll(BackupCodec.decode(file, "主密码".toCharArray()))
        assertEquals(ledger.transactions, restored.ledger.transactions)
    }

    @Test
    fun newBackupsDeclareSchemaTwo() {
        val root = JSONObject(String(BackupJson.encode(listOf(card), 1L, ledger), Charsets.UTF_8))
        assertEquals(2, root.getInt("schema"))
        assertTrue(root.has("ledger"))
    }

    @Test
    fun oldSchemaOneBackupsStillImport() {
        // 0.5.x 导出的备份：schema 1，没有 ledger 一节
        val old = """{"app":"jinkui","schema":1,"exportedAt":1,"cards":[{"id":"c-9","type":"login","title":"旧卡片","fields":[]}]}"""
        val content = BackupJson.decodeAll(old.toByteArray())
        assertEquals("旧卡片", content.cards.single().card.title)
        assertTrue(content.ledger.isEmpty)
        assertEquals(LedgerData.EMPTY, content.ledger)
    }

    @Test
    fun rejectsTamperedLedgerSections() {
        fun withLedger(ledgerJson: String) =
            """{"app":"jinkui","schema":2,"cards":[],"ledger":$ledgerJson}""".toByteArray()
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decodeAll(withLedger("""{"transactions":[{"id":"../x","type":"expense","amount":1,"occurredAt":1,"accountId":"a"}]}"""))
        }
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decodeAll(withLedger("""{"transactions":[{"id":"t","type":"expense","amount":-5,"occurredAt":1,"accountId":"a"}]}"""))
        }
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decodeAll(withLedger("""{"transactions":[{"id":"t","type":"expense","occurredAt":1,"accountId":"a"}]}"""))
        }
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decodeAll(withLedger("""{"categories":[{"id":"c","name":"x","type":"transfer"}]}"""))
        }
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decodeAll(withLedger("""{"budgets":[{"id":"b","amount":99999999999999}]}"""))
        }
        // 缺省的小节视为空
        assertEquals(LedgerData.EMPTY, BackupJson.decodeAll(withLedger("{}")).ledger)
    }

    @Test
    fun dbPayloadsRoundTripThroughFieldCipher() {
        val cipher = FieldCipher(ByteArray(32) { it.toByte() })
        val tx = ledger.transactions.first()
        val sealed = cipher.encrypt(LedgerJson.transactionPayload(tx), "ledger:tx:${tx.id}")
        assertFalse(sealed.contains("2350"))
        val payload = LedgerJson.readTransactionPayload(cipher.decrypt(sealed, "ledger:tx:${tx.id}"))
        assertEquals(tx.amount, payload.amount)
        assertEquals(tx.note, payload.note)
        assertEquals(tx.tags, payload.tags)
        // 密文绑定到自己的记录：挪到另一笔流水上解不开
        assertThrows(WrongSecretException::class.java) { cipher.decrypt(sealed, "ledger:tx:t-2") }

        val account = ledger.accounts[1]
        val a = LedgerJson.readAccountPayload(LedgerJson.accountPayload(account))
        assertEquals(account.name, a.name)
        assertEquals(account.kind, a.kind)
        assertEquals(account.openingBalance, a.opening)
        assertEquals(account.cardId, a.cardId)
        assertEquals(null, LedgerJson.readAccountPayload(LedgerJson.accountPayload(ledger.accounts[0])).cardId)

        val category = ledger.categories.last()
        val c = LedgerJson.readCategoryPayload(LedgerJson.categoryPayload(category))
        assertEquals(category.name, c.name)
        assertEquals(category.icon, c.icon)
        assertEquals(150_000L, LedgerJson.readBudgetAmount(LedgerJson.budgetPayload(ledger.budgets[1])))
    }
}
