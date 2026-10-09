package com.xiguli.jinkui.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardGuardTest {
    @Test
    fun clearsOwnClip() {
        assertTrue(ClipboardGuard.shouldClear(true))
    }

    @Test
    fun clearsWhenClipboardCannotBeInspected() {
        // 后台读不到剪贴板时宁可多清，不能让密码留在剪贴板里。
        assertTrue(ClipboardGuard.shouldClear(null))
    }

    @Test
    fun keepsContentCopiedLaterByAnotherApp() {
        assertFalse(ClipboardGuard.shouldClear(false))
    }
}
