package com.xiguli.jinkui.data.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 迁移测试：在 JVM 上用 SQLite（与 SQLCipher 同一套 SQL 方言）模拟 0.5.3 的数据库，
 * 执行 1 → 2 迁移，确认旧数据一条不少、新表可用，且升级后的结构与新装完全一致。
 */
class VaultSchemaMigrationTest {
    private lateinit var db: Connection

    /** 0.5.3 的建表语句原样冻结在这里，不随 [VaultSchema] 改动。 */
    private val schemaV1 = listOf(
        "CREATE TABLE cards(id TEXT PRIMARY KEY NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, tags TEXT NOT NULL, color INTEGER NOT NULL, note_enc TEXT NOT NULL, pinned INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, last_used_at INTEGER NOT NULL)",
        "CREATE TABLE fields(id TEXT PRIMARY KEY NOT NULL, card_id TEXT NOT NULL, position INTEGER NOT NULL, label TEXT NOT NULL, value_enc TEXT NOT NULL, sensitive INTEGER NOT NULL, kind TEXT NOT NULL)",
        "CREATE INDEX fields_card ON fields(card_id)",
        "CREATE TABLE attachments(id TEXT PRIMARY KEY NOT NULL, card_id TEXT NOT NULL, name TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL, created_at INTEGER NOT NULL)",
        "CREATE INDEX attachments_card ON attachments(card_id)",
        "CREATE TABLE history(id TEXT PRIMARY KEY NOT NULL, card_id TEXT NOT NULL, label TEXT NOT NULL, value_enc TEXT NOT NULL, changed_at INTEGER NOT NULL)",
        "CREATE INDEX history_card ON history(card_id)",
    )

    @Before
    fun open() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After
    fun close() {
        db.close()
    }

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    private fun count(sql: String): Int = db.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getInt(1) } }

    private fun seedV1() {
        schemaV1.forEach(::exec)
        exec("INSERT INTO cards VALUES('c-1','bank','招商银行','银行',1,'ENC-NOTE',1,10,20,30)")
        exec("INSERT INTO cards VALUES('c-2','login','邮箱','',-1,'ENC-NOTE2',0,11,21,31)")
        exec("INSERT INTO fields VALUES('f-1','c-1',0,'卡号','ENC-CARDNO',1,'number')")
        exec("INSERT INTO fields VALUES('f-2','c-2',0,'密码','ENC-PW',1,'password')")
        exec("INSERT INTO attachments VALUES('p-1','c-1','正面.jpg','image/jpeg',4096,12)")
        exec("INSERT INTO history VALUES('h-1','c-2','密码','ENC-OLD',13)")
        exec("PRAGMA user_version = 1")
    }

    /** 模拟 SupportSQLiteOpenHelper：在一个事务里执行迁移并更新 user_version。 */
    private fun migrate(from: Int, to: Int) {
        db.autoCommit = false
        try {
            VaultSchema.upgrade(from, to).forEach(::exec)
            exec("PRAGMA user_version = $to")
            db.commit()
        } catch (e: Exception) {
            db.rollback()
            throw e
        } finally {
            db.autoCommit = true
        }
    }

    private fun schema(connection: Connection): List<String> =
        connection.createStatement().use { s ->
            s.executeQuery("SELECT type, name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name").use { rs ->
                buildList {
                    while (rs.next()) add(rs.getString(1) + ":" + rs.getString(2) + ":" + rs.getString(3).replace(Regex("\\s+"), " ").replace("( ", "(").replace(" )", ")"))
                }
            }
        }

    @Test
    fun upgradeFromV1KeepsEveryRow() {
        seedV1()
        migrate(1, 2)
        assertEquals(2, count("PRAGMA user_version"))
        assertEquals(2, count("SELECT COUNT(*) FROM cards"))
        assertEquals(2, count("SELECT COUNT(*) FROM fields"))
        assertEquals(1, count("SELECT COUNT(*) FROM attachments"))
        assertEquals(1, count("SELECT COUNT(*) FROM history"))
        db.createStatement().use { s ->
            s.executeQuery("SELECT title, note_enc, pinned, last_used_at FROM cards WHERE id = 'c-1'").use { rs ->
                assertTrue(rs.next())
                assertEquals("招商银行", rs.getString(1))
                assertEquals("ENC-NOTE", rs.getString(2))
                assertEquals(1, rs.getInt(3))
                assertEquals(30L, rs.getLong(4))
            }
            s.executeQuery("SELECT value_enc FROM fields WHERE id = 'f-1'").use { rs ->
                assertTrue(rs.next())
                assertEquals("ENC-CARDNO", rs.getString(1))
            }
        }
        // 新表为空且可写
        for (table in listOf("ledger_accounts", "ledger_categories", "ledger_transactions", "ledger_budgets")) {
            assertEquals(0, count("SELECT COUNT(*) FROM $table"))
        }
        exec("INSERT INTO ledger_accounts VALUES('acc-cash',0,0,1,1,'ENC')")
        exec("INSERT INTO ledger_categories VALUES('cat-food',NULL,'expense',0,0,1,'ENC')")
        exec("INSERT INTO ledger_transactions VALUES('t-1','expense',100,'acc-cash',NULL,'cat-food',1,1,'ENC')")
        exec("INSERT INTO ledger_budgets VALUES('budget-total',NULL,1,'ENC')")
        assertEquals(1, count("SELECT COUNT(*) FROM ledger_transactions WHERE occurred_at = 100"))
    }

    @Test
    fun upgradeFromV2ToV3AddsRecurringAndKeepsLedger() {
        seedV1()
        migrate(1, 2)
        exec("INSERT INTO ledger_accounts VALUES('acc-cash',0,0,1,1,'ENC')")
        exec("INSERT INTO ledger_transactions VALUES('t-1','expense',100,'acc-cash',NULL,NULL,1,1,'ENC')")
        migrate(2, 3)
        assertEquals(3, count("PRAGMA user_version"))
        assertEquals(1, count("SELECT COUNT(*) FROM ledger_transactions"))
        assertEquals(2, count("SELECT COUNT(*) FROM cards"))
        assertEquals(0, count("SELECT COUNT(*) FROM ledger_recurring"))
        exec("INSERT INTO ledger_recurring VALUES('r-1',1,'ENC')")
        // 周期记账用 INSERT OR IGNORE 写流水：同一 ID 第二次写入不覆盖、不重复
        exec("INSERT OR IGNORE INTO ledger_transactions VALUES('t-1','expense',999,'acc-cash',NULL,NULL,2,2,'ENC2')")
        assertEquals(1, count("SELECT COUNT(*) FROM ledger_transactions WHERE occurred_at = 100"))
    }

    @Test
    fun upgradeFromV3ToV4AddsTombstonesAndKeepsEverything() {
        seedV1()
        migrate(1, 3)
        exec("INSERT INTO ledger_accounts VALUES('acc-cash',0,0,1,1,'ENC')")
        exec("INSERT INTO ledger_transactions VALUES('r20261001-r-1','expense',100,'acc-cash',NULL,NULL,1,1,'ENC')")
        exec("INSERT INTO ledger_recurring VALUES('r-1',1,'ENC')")
        migrate(3, 4)
        assertEquals(4, count("PRAGMA user_version"))
        assertEquals(2, count("SELECT COUNT(*) FROM cards"))
        assertEquals(1, count("SELECT COUNT(*) FROM ledger_transactions"))
        assertEquals(1, count("SELECT COUNT(*) FROM ledger_recurring"))
        assertEquals(0, count("SELECT COUNT(*) FROM ledger_tombstones"))
        exec("INSERT OR REPLACE INTO ledger_tombstones VALUES('r20261002-r-1','tx',5)")
        exec("INSERT OR REPLACE INTO ledger_tombstones VALUES('r20261002-r-1','tx',6)")
        assertEquals(1, count("SELECT COUNT(*) FROM ledger_tombstones WHERE deleted_at = 6"))
    }

    @Test
    fun upgradedSchemaMatchesFreshInstall() {
        seedV1()
        migrate(1, VaultSchema.VERSION)
        val fresh = DriverManager.getConnection("jdbc:sqlite::memory:")
        fresh.use { connection ->
            VaultSchema.create().forEach { sql -> connection.createStatement().use { it.execute(sql) } }
            assertEquals(schema(connection), schema(db))
        }
    }

    @Test
    fun failedMigrationRollsBackAndKeepsData() {
        seedV1()
        // 已经有一张同名表时迁移会失败：必须整体回滚，旧数据与版本号原样保留
        exec("CREATE TABLE ledger_budgets(x TEXT)")
        assertThrows(Exception::class.java) { migrate(1, 2) }
        assertEquals(1, count("PRAGMA user_version"))
        assertEquals(2, count("SELECT COUNT(*) FROM cards"))
        assertEquals(0, count("SELECT COUNT(*) FROM sqlite_master WHERE name = 'ledger_accounts'"))
    }

    @Test
    fun rejectsUnknownVersions() {
        assertThrows(IllegalArgumentException::class.java) { VaultSchema.upgrade(2, 1) }
        assertThrows(IllegalArgumentException::class.java) { VaultSchema.upgrade(1, VaultSchema.VERSION + 1) }
        assertEquals(emptyList<String>(), VaultSchema.upgrade(VaultSchema.VERSION, VaultSchema.VERSION))
    }
}
