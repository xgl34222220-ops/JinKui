package com.xiguli.jinkui.data.repo

/**
 * 替换一张卡片的附件时，哪些密文文件该在什么时候删。
 *
 * 数据库与附件文件不在同一个事务里，所以顺序很重要：
 *  1. 先写入新附件文件（同 ID 的文件被原子替换）；
 *  2. 再提交数据库；
 *  3. 提交成功后才删除不再被引用的旧文件 [deleteAfterCommit]；
 *     提交失败则删除这次新写入、原来并不存在的文件 [deleteOnFailure]，本机旧数据保持原样。
 */
class AttachmentPlan(
    val deleteAfterCommit: Set<String>,
    val deleteOnFailure: Set<String>,
) {
    companion object {
        fun of(existing: Collection<String>, incoming: Collection<String>): AttachmentPlan {
            val before = existing.toSet()
            val after = incoming.toSet()
            return AttachmentPlan(
                deleteAfterCommit = before - after,
                deleteOnFailure = after - before,
            )
        }
    }
}
