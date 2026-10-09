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

    private object Schema : SupportSQLiteOpenHelper.Callback(DB_VERSION) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
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
            )
            db.execSQL(
                """CREATE TABLE fields(
                    id TEXT PRIMARY KEY NOT NULL,
                    card_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    label TEXT NOT NULL,
                    value_enc TEXT NOT NULL,
                    sensitive INTEGER NOT NULL,
                    kind TEXT NOT NULL
                )""",
            )
            db.execSQL("CREATE INDEX fields_card ON fields(card_id)")
            db.execSQL(
                """CREATE TABLE attachments(
                    id TEXT PRIMARY KEY NOT NULL,
                    card_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    mime TEXT NOT NULL,
                    size INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )""",
            )
            db.execSQL("CREATE INDEX attachments_card ON attachments(card_id)")
            db.execSQL(
                """CREATE TABLE history(
                    id TEXT PRIMARY KEY NOT NULL,
                    card_id TEXT NOT NULL,
                    label TEXT NOT NULL,
                    value_enc TEXT NOT NULL,
                    changed_at INTEGER NOT NULL
                )""",
            )
            db.execSQL("CREATE INDEX history_card ON history(card_id)")
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // 第一版没有迁移。以后的结构变更在这里按版本逐级处理，不得丢弃用户数据。
        }
    }

    private companion object {
        const val DB_NAME = "vault.db"
        const val DB_VERSION = 1
    }
}
