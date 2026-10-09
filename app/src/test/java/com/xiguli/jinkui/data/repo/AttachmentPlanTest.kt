package com.xiguli.jinkui.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentPlanTest {
    @Test
    fun newCardOnlyCleansUpItsOwnFilesOnFailure() {
        val plan = AttachmentPlan.of(existing = emptyList(), incoming = listOf("a", "b"))
        assertTrue(plan.deleteAfterCommit.isEmpty())
        assertEquals(setOf("a", "b"), plan.deleteOnFailure)
    }

    @Test
    fun replacingKeepsSharedFilesInBothOutcomes() {
        val plan = AttachmentPlan.of(existing = listOf("a", "old"), incoming = listOf("a", "new"))
        // 提交成功：只删不再被引用的旧文件。
        assertEquals(setOf("old"), plan.deleteAfterCommit)
        // 提交失败：只删这次新增的文件，本机原有的附件（包括同 ID 的 a）绝不删除。
        assertEquals(setOf("new"), plan.deleteOnFailure)
    }

    @Test
    fun droppingAllAttachmentsDeletesOldFilesOnlyAfterCommit() {
        val plan = AttachmentPlan.of(existing = listOf("x", "y"), incoming = emptyList())
        assertEquals(setOf("x", "y"), plan.deleteAfterCommit)
        assertTrue(plan.deleteOnFailure.isEmpty())
    }
}
