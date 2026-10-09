package com.xiguli.jinkui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import com.xiguli.jinkui.core.security.ClipboardGuard
import com.xiguli.jinkui.core.security.KeystoreManager
import com.xiguli.jinkui.core.security.LockManager
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.core.security.VaultMetaStore
import com.xiguli.jinkui.data.db.AttachmentStore
import com.xiguli.jinkui.data.db.VaultDatabase
import com.xiguli.jinkui.data.repo.BackupRepository
import com.xiguli.jinkui.data.repo.LedgerRepository
import com.xiguli.jinkui.data.repo.SettingsRepository
import com.xiguli.jinkui.data.repo.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class JinKuiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** 界面底部的一行轻提示。 */
class MessageBus {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = flow.asSharedFlow()

    fun show(message: String) {
        flow.tryEmit(message)
    }
}

/** 手动依赖注入：整个应用只有这一份对象图。 */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(app)
    val messages = MessageBus()

    private val meta = VaultMetaStore(app)
    private val database = VaultDatabase(app)
    private val attachments = AttachmentStore(app)

    val vault = VaultManager(meta, KeystoreManager(), database, attachments, settings)
    val repository = VaultRepository(database, attachments, vault, settings)
    val ledger = LedgerRepository(database, vault)
    val backup = BackupRepository(app, vault, repository, ledger)
    val clipboard = ClipboardGuard(app, settings, meta, scope)
    val lock = LockManager(vault, settings, scope)

    /** 刚创建完保险库时，引导用户设置快捷解锁。 */
    val showQuickUnlockIntro = mutableStateOf(false)

    /** 输错自毁刚刚发生，供首屏提示。 */
    val justWiped = mutableStateOf(false)

    init {
        vault.onUnlocked = {
            repository.load()
            ledger.load()
        }
        // 锁定只丢弃内存明文；剪贴板按自己的倒计时清空，
        // 否则“复制后切到别的应用粘贴”会因为自动锁定而粘贴不到。
        vault.onLocked = {
            repository.clear()
            ledger.clear()
        }
        // 上次运行若留下未清空的剪贴内容，启动时补清。
        clipboard.clearNow()
    }
}
