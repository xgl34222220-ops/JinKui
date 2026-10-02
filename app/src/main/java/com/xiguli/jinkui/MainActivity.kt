package com.xiguli.jinkui

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
        setContent { JinKuiRoot(container) }
    }

    override fun onStart() {
        super.onStart()
        container.lock.onForeground()
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
