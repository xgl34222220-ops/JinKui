package com.xiguli.jinkui.ui

import android.os.Looper
import java.time.Duration
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf

/**
 * 界面测试结束（含 Compose 规则拆除）之后把主线程消息队列跑空。
 *
 * 为什么需要：Compose 的主线程调度器 `AndroidUiDispatcher.Main` 是进程级单例，排队任务时先把
 * “已安排派发”置为 true，再往主线程 Handler / Choreographer 投一条消息，等那条消息执行时才清零。
 * Robolectric 在两个测试之间会直接清空主线程消息队列；如果上一个测试结束时恰好有这样一条消息没跑，
 * 标志位就永远停在 true，之后再也不派发——全局快照的写入通知（GlobalSnapshotManager）随之卡住，
 * `Snapshot.current.hasPendingChanges()` 一直为真，下一个用到动画 / 副作用写状态的页面在
 * `waitForIdle()` 里等满 60 秒报 AppNotIdleException。哪个测试先“漏”一条消息取决于执行顺序与耗时，
 * 所以表现为单独跑都过、一起跑随机挂一串。
 *
 * 用法：`@get:Rule(order = 0) val drain = MainLooperDrainRule()`，Compose 规则用 order = 1，
 * 这样排空发生在 Compose 拆除之后、Robolectric 重置之前。
 */
class MainLooperDrainRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } finally {
                drainMainLooper()
            }
        }
    }
}

/** 跑完主线程上已到期的消息，并推进几帧让 Choreographer 回调也执行掉。 */
fun drainMainLooper() {
    val looper = shadowOf(Looper.getMainLooper())
    repeat(4) {
        looper.idle()
        looper.idleFor(Duration.ofMillis(20))
    }
    looper.idle()
}
