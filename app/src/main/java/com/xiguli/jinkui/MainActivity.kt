package com.xiguli.jinkui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.xiguli.jinkui.ui.JinKuiRoot

class MainActivity : FragmentActivity() {
    private val container: AppContainer get() = (application as JinKuiApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 防截屏、防录屏；最近任务列表里也只显示空白缩略图。必须在显示任何内容之前设置。
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShortcut(intent)
        setContent { JinKuiRoot(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    /** 桌面快捷方式“记一笔”：记下请求，解锁后账本页打开记账面板。 */
    private fun handleShortcut(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_RECORD) {
            container.quickRecordRequests.value += 1
            intent.action = null
        }
    }

    companion object {
        const val ACTION_QUICK_RECORD = "com.xiguli.jinkui.action.QUICK_RECORD"
    }

    override fun onStart() {
        super.onStart()
        container.lock.onForeground()
        // 自动记账：监听被系统停掉时请求重新绑定
        container.ensureListener()
    }

    override fun onStop() {
        super.onStop()
        container.lock.onBackground()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.lock.touch()
    }
}
