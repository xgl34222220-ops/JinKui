package com.xiguli.jinkui.data.repo

import com.xiguli.jinkui.data.model.Attachment
import com.xiguli.jinkui.data.model.CardTemplates

/**
 * 单张卡片的附件采用 copy-on-write：导入内容先用新的本地 ID 写入，
 * 数据库提交后才删旧文件。不能覆盖同 ID 的旧文件后指望数据库回滚恢复它。
 *
 * [write] 必须用目标附件的 ID 加密 AAD 和写文件；[commit] 必须在一个数据库事务中
 * 替换附件索引。进程中断可能留下未引用的新文件，但不会提前覆盖旧附件。
 * 这不是整份备份（卡片加账本）的跨表/跨文件事务。
 */
internal object AttachmentRestore {
    fun run(
        existingIds: Collection<String>,
        incoming: List<Attachment>,
        newId: () -> String = CardTemplates::newId,
        reservedIds: Collection<String> = existingIds,
        write: (source: Attachment, target: Attachment) -> Unit,
        commit: (List<Attachment>) -> Unit,
        delete: (String) -> Unit,
    ) {
        val reserved = (reservedIds + existingIds + incoming.map { it.id }).toMutableSet()
        // 在任何写入前生成并检查全部 ID，避免异常的生成器覆盖已有或同批文件。
        val copies = incoming.map { source ->
            val id = newId()
            require(reserved.add(id)) { "导入附件 ID 冲突" }
            source to source.copy(id = id)
        }
        val staged = mutableListOf<String>()
        try {
            for ((source, target) in copies) {
                // 写入可能已经创建文件后才失败，因此先登记清理范围。
                staged += target.id
                write(source, target)
            }
            commit(copies.map { it.second })
        } catch (e: Exception) {
            staged.forEach { runCatching { delete(it) } }
            throw e
        }
        // 只有数据库提交完成后，旧附件才不再被本卡片引用。
        existingIds.forEach(delete)
    }
}
