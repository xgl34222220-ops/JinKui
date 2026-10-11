package com.xiguli.jinkui.data.repo

import com.xiguli.jinkui.core.crypto.WrongSecretException
import com.xiguli.jinkui.core.security.SessionKeys
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.data.backup.BackupCard
import com.xiguli.jinkui.data.db.AttachmentRow
import com.xiguli.jinkui.data.db.AttachmentStore
import com.xiguli.jinkui.data.db.CardRow
import com.xiguli.jinkui.data.db.FieldRow
import com.xiguli.jinkui.data.db.HistoryRow
import com.xiguli.jinkui.data.db.VaultDatabase
import com.xiguli.jinkui.data.model.Attachment
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.HistoryEntry
import com.xiguli.jinkui.data.model.PendingAttachment
import com.xiguli.jinkui.data.model.TrashMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ImportSummary(
    val added: Int,
    val updated: Int,
    val skipped: Int,
    /** 账本里新增 / 更新的记录数（账户、分类、流水、预算合计）。 */
    val ledgerAdded: Int = 0,
    val ledgerUpdated: Int = 0,
    /** 因为删除标记而没有加回来的账本记录数（删掉的周期生成流水 / 周期规则）。 */
    val ledgerSuppressed: Int = 0,
)

/**
 * 卡片仓库。解锁后把全部卡片解密到内存供搜索与展示，锁定时整体丢弃。
 * 数据库里的字段值、备注与附件文件都是字段级密文，明文只在这里出现。
 */
class VaultRepository(
    private val database: VaultDatabase,
    private val files: AttachmentStore,
    private val vault: VaultManager,
    private val settings: SettingsRepository,
) {
    private val state = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = state.asStateFlow()

    /** 回收站里的卡片（1.4.0-alpha04 起）：解锁后同样解密到内存，锁定时丢弃；最近删的在前。 */
    private val trashState = MutableStateFlow<List<Card>>(emptyList())
    val trash: StateFlow<List<Card>> = trashState.asStateFlow()

    private fun keys(): SessionKeys = vault.keys ?: throw IllegalStateException("金匮已锁定")

    suspend fun load(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val keys = keys()
        // 回收站里满 30 天的卡片：先彻底删除（连同附件文件），再载入
        val rows = database.cards()
        val expired = rows.filter { TrashMath.isExpired(it.deletedAt, now) }.map { it.id }.toSet()
        if (expired.isNotEmpty()) {
            val doomedFiles = database.attachments().filter { it.cardId in expired }.map { it.id }
            database.deleteCards(expired)
            doomedFiles.forEach(files::delete)
        }
        val fields = database.fields().groupBy { it.cardId }
        val attachments = database.attachments().groupBy { it.cardId }
        val loaded = rows.filter { it.id !in expired }.map { row ->
            Card(
                id = row.id,
                type = CardType.of(row.type),
                title = row.title,
                tags = decodeTags(row.tags),
                color = row.color,
                fields = fields[row.id].orEmpty().map { field ->
                    CardField(
                        id = field.id,
                        label = field.label,
                        value = decryptOrPlaceholder(keys, field.valueEnc, fieldContext(row.id, field.id)),
                        sensitive = field.sensitive,
                        kind = FieldKind.of(field.kind),
                    )
                },
                note = decryptOrPlaceholder(keys, row.noteEnc, noteContext(row.id)),
                attachments = attachments[row.id].orEmpty().map {
                    Attachment(it.id, it.name, it.mime, it.size, it.createdAt)
                },
                pinned = row.pinned,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                lastUsedAt = row.lastUsedAt,
                deletedAt = row.deletedAt,
            )
        }
        publish { loaded.filter { it.deletedAt == null } }
        publishTrash { TrashMath.sorted(loaded) }
    }

    private fun publishTrash(transform: (List<Card>) -> List<Card>) {
        if (vault.keys == null) return
        trashState.value = transform(trashState.value)
        if (vault.keys == null) trashState.value = emptyList()
    }

    /**
     * 更新内存中的卡片列表。后台操作完成时金匮可能刚好被锁定：
     * 这时不能再把明文放回内存，直接丢弃。
     */
    private fun publish(transform: (List<Card>) -> List<Card>) {
        if (vault.keys == null) return
        state.value = transform(state.value)
        if (vault.keys == null) state.value = emptyList()
    }

    /** 锁定时调用：丢弃内存中的全部明文。 */
    fun clear() {
        state.value = emptyList()
        trashState.value = emptyList()
    }

    fun find(id: String): Card? = state.value.firstOrNull { it.id == id }

    /**
     * 新建或更新卡片。敏感字段的值发生变化时，旧值写入历史密码（可在设置中关闭）。
     */
    suspend fun save(
        card: Card,
        added: List<PendingAttachment> = emptyList(),
        removedAttachmentIds: List<String> = emptyList(),
    ): Card = withContext(Dispatchers.IO) {
        val keys = keys()
        val now = System.currentTimeMillis()
        val previous = find(card.id)

        val newAttachments = added.map { pending ->
            files.write(pending.id, keys.attachment.encryptBytes(pending.bytes, attachmentContext(pending.id)))
            Attachment(pending.id, pending.name, pending.mime, pending.bytes.size.toLong(), now)
        }
        val saved = card.copy(
            title = card.title.trim(),
            tags = card.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            attachments = card.attachments.filterNot { it.id in removedAttachmentIds } + newAttachments,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            lastUsedAt = previous?.lastUsedAt ?: now,
        )

        val history = if (previous != null && settings.current.keepHistory) {
            val before = previous.fields.associateBy { it.id }
            saved.fields.mapNotNull { field ->
                val old = before[field.id] ?: return@mapNotNull null
                if (!old.sensitive || old.value.isBlank() || old.value == field.value) return@mapNotNull null
                val id = CardTemplates.newId()
                HistoryRow(id, saved.id, old.label, keys.field.encrypt(old.value, historyContext(id)), now)
            }
        } else {
            emptyList()
        }

        try {
            database.saveCard(
                card = saved.toRow(keys),
                fields = saved.fields.mapIndexed { index, field ->
                    FieldRow(
                        id = field.id,
                        cardId = saved.id,
                        position = index,
                        label = field.label.trim(),
                        valueEnc = keys.field.encrypt(field.value, fieldContext(saved.id, field.id)),
                        sensitive = field.sensitive,
                        kind = field.kind.key,
                    )
                },
                addedAttachments = newAttachments.map { AttachmentRow(it.id, saved.id, it.name, it.mime, it.size, it.createdAt) },
                removedAttachmentIds = removedAttachmentIds,
                newHistory = history,
            )
        } catch (e: Exception) {
            // 数据库没写进去：刚落盘的新附件没有任何记录引用它，删掉，免得成为孤儿文件。
            newAttachments.forEach { files.delete(it.id) }
            throw e
        }
        removedAttachmentIds.forEach(files::delete)
        publish { cards -> cards.filterNot { it.id == saved.id } + saved }
        saved
    }

    /** 删除 = 放进回收站（1.4.0-alpha04 起）：内容原样留在加密数据库里，30 天内可以还原。 */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val card = find(id) ?: return@withContext
        val now = System.currentTimeMillis()
        database.setCardDeleted(id, now)
        publish { cards -> cards.filterNot { it.id == id } }
        publishTrash { trash -> TrashMath.sorted(trash.filterNot { it.id == id } + card.copy(deletedAt = now)) }
    }

    /** 从回收站还原。 */
    suspend fun restoreFromTrash(id: String) = withContext(Dispatchers.IO) {
        val card = trashState.value.firstOrNull { it.id == id } ?: return@withContext
        database.setCardDeleted(id, null)
        publishTrash { trash -> trash.filterNot { it.id == id } }
        publish { cards -> cards.filterNot { it.id == id } + card.copy(deletedAt = null) }
    }

    /** 彻底删除回收站里的卡片（[ids] 为空时清空整个回收站）：字段、附件文件与历史密码一并删除，无法恢复。 */
    suspend fun purge(ids: Collection<String> = emptyList()) = withContext(Dispatchers.IO) {
        val doomed = trashState.value.filter { ids.isEmpty() || it.id in ids }
        if (doomed.isEmpty()) return@withContext
        database.deleteCards(doomed.map { it.id })
        doomed.forEach { card -> card.attachments.forEach { files.delete(it.id) } }
        val gone = doomed.mapTo(HashSet()) { it.id }
        publishTrash { trash -> trash.filterNot { it.id in gone } }
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        database.setPinned(id, pinned)
        publish { cards -> cards.map { if (it.id == id) it.copy(pinned = pinned) else it } }
    }

    /** 记录一次使用（查看或复制），用于“最近使用”。 */
    suspend fun markUsed(id: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.touch(id, now)
        publish { cards -> cards.map { if (it.id == id) it.copy(lastUsedAt = now) else it } }
    }

    suspend fun history(cardId: String): List<HistoryEntry> = withContext(Dispatchers.IO) {
        val keys = keys()
        database.history(cardId).map {
            HistoryEntry(it.id, it.label, decryptOrPlaceholder(keys, it.valueEnc, historyContext(it.id)), it.changedAt)
        }
    }

    suspend fun clearHistory(cardId: String) = withContext(Dispatchers.IO) {
        database.clearHistory(cardId)
    }

    suspend fun readAttachment(id: String): ByteArray = withContext(Dispatchers.IO) {
        keys().attachment.decryptBytes(files.read(id), attachmentContext(id))
    }

    // ── 备份 ──────────────────────────────────────────────────────

    suspend fun snapshot(): List<BackupCard> = withContext(Dispatchers.IO) {
        val keys = keys()
        val history = database.history().groupBy { it.cardId }
        state.value.map { card ->
            BackupCard(
                card = card,
                attachmentData = card.attachments.associate { attachment ->
                    attachment.id to keys.attachment.decryptBytes(files.read(attachment.id), attachmentContext(attachment.id))
                },
                history = history[card.id].orEmpty().map {
                    HistoryEntry(it.id, it.label, decryptOrPlaceholder(keys, it.valueEnc, historyContext(it.id)), it.changedAt)
                },
            )
        }
    }

    /**
     * 合并导入：本机没有的卡片直接加入；两边都有时保留更新时间较新的一份。
     * 不会删除本机已有的任何卡片。
     */
    suspend fun restore(items: List<BackupCard>): ImportSummary = withContext(Dispatchers.IO) {
        val keys = keys()
        var added = 0
        var updated = 0
        var skipped = 0
        // 同一份备份里若出现重复 ID，以更新时间较新的那份为准。
        val latest = items.groupBy { it.card.id }.values.map { group -> group.maxBy { it.card.updatedAt } }
        try {
            for (item in latest) {
                val incoming = item.card
                // 回收站里的同一张卡片也算“本机已有”：备份里较新的覆盖它的内容，但它仍留在回收站里
                val trashed = trashState.value.firstOrNull { it.id == incoming.id }
                val existing = find(incoming.id) ?: trashed
                if (existing != null && existing.updatedAt >= incoming.updatedAt) {
                    skipped++
                    continue
                }
                val attachments = incoming.attachments.filter { item.attachmentData.containsKey(it.id) }
                val card = incoming.copy(attachments = attachments, deletedAt = trashed?.deletedAt)
                val plan = AttachmentPlan.of(
                    existing = existing?.attachments.orEmpty().map { it.id },
                    incoming = attachments.map { it.id },
                )
                // 先把字段、备注、历史全部加密好，再动磁盘与数据库。
                val row = card.toRow(keys)
                val fieldRows = card.fields.mapIndexed { index, field ->
                    FieldRow(
                        id = field.id,
                        cardId = card.id,
                        position = index,
                        label = field.label,
                        valueEnc = keys.field.encrypt(field.value, fieldContext(card.id, field.id)),
                        sensitive = field.sensitive,
                        kind = field.kind.key,
                    )
                }
                val historyRows = item.history.map {
                    HistoryRow(it.id, card.id, it.label, keys.field.encrypt(it.value, historyContext(it.id)), it.changedAt)
                }
                try {
                    for (attachment in attachments) {
                        val data = item.attachmentData.getValue(attachment.id)
                        files.write(attachment.id, keys.attachment.encryptBytes(data, attachmentContext(attachment.id)))
                    }
                    // 删除旧内容与写入新内容在同一个事务里：中途失败时本机原来那张卡片原样保留。
                    database.replaceCard(
                        card = row,
                        fields = fieldRows,
                        attachments = attachments.map { AttachmentRow(it.id, card.id, it.name, it.mime, it.size, it.createdAt) },
                        history = historyRows,
                    )
                } catch (e: Exception) {
                    plan.deleteOnFailure.forEach(files::delete)
                    throw e
                }
                plan.deleteAfterCommit.forEach(files::delete)
                if (existing == null) added++ else updated++
            }
        } finally {
            // 即使中途出错，也让内存与数据库保持一致（已锁定时 load 会直接失败，忽略即可）。
            runCatching { load() }
        }
        ImportSummary(added, updated, skipped)
    }

    private fun Card.toRow(keys: SessionKeys) = CardRow(
        id = id,
        type = type.key,
        title = title,
        tags = encodeTags(tags),
        color = color,
        noteEnc = keys.field.encrypt(note, noteContext(id)),
        pinned = pinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
        deletedAt = deletedAt,
    )

    /** 只有密文本身校验失败才显示占位；锁定等其他异常照常抛出，不能把占位文字当成数据。 */
    private fun decryptOrPlaceholder(keys: SessionKeys, token: String, context: String): String =
        try {
            keys.field.decrypt(token, context)
        } catch (e: WrongSecretException) {
            UNREADABLE
        }

    private companion object {
        const val UNREADABLE = "（数据损坏，无法解密）"
        const val TAG_SEPARATOR = "\u001F"

        fun fieldContext(cardId: String, fieldId: String) = "field:$cardId:$fieldId"
        fun noteContext(cardId: String) = "note:$cardId"
        fun historyContext(historyId: String) = "history:$historyId"
        fun attachmentContext(attachmentId: String) = "attachment:$attachmentId"

        fun encodeTags(tags: List<String>) = tags.joinToString(TAG_SEPARATOR)
        fun decodeTags(raw: String) = raw.split(TAG_SEPARATOR).filter { it.isNotEmpty() }
    }
}
