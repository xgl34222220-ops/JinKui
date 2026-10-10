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
import com.xiguli.jinkui.data.repo.AppSettings
import com.xiguli.jinkui.data.repo.FontMode
import com.xiguli.jinkui.data.repo.FontStore
import com.xiguli.jinkui.core.auto.CapturedPayment
import com.xiguli.jinkui.core.auto.PendingSpool
import com.xiguli.jinkui.core.auto.AutoStatus
import com.xiguli.jinkui.core.auto.ListenerRebinder
import com.xiguli.jinkui.core.auto.UnrecognizedLog
import com.xiguli.jinkui.core.auto.UnrecognizedNotice
import com.xiguli.jinkui.ui.theme.FontConfig
import com.xiguli.jinkui.ui.theme.JkFonts
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    private val keystore = KeystoreManager()
    val vault = VaultManager(meta, keystore, database, attachments, settings)
    val repository = VaultRepository(database, attachments, vault, settings)
    val ledger = LedgerRepository(database, vault)
    val backup = BackupRepository(app, vault, repository, ledger)
    val clipboard = ClipboardGuard(app, settings, meta, scope)
    val lock = LockManager(vault, settings, scope)
    val fonts = FontStore(app)

    /** 自动记账的加密暂存（锁定期间到达的通知先放这里）。 */
    val intake = PendingSpool(app, keystore)

    /** 自动记账的运行状态（连接、最近通知时间、今日计数；不含通知内容）。 */
    val autoStatus = AutoStatus(app)

    /** 没认出来的受支持应用通知（数字已换成 #，加密存放，最多 20 条）。 */
    val unrecognized = UnrecognizedLog(app, keystore)

    private val appContext = app.applicationContext
    private var rebindTried = false

    /**
     * 打开金匮时检查通知监听：开关开着、已授权、但监听没连上时请求系统重新绑定；
     * 几秒后仍没连上，再切换一次组件状态强制重绑（每次进程只做一次，避免反复折腾）。
     */
    fun ensureListener() {
        val on = settings.current.autoBookkeeping
        if (!ListenerRebinder.ensure(appContext, on, autoStatus.current.live)) return
        if (rebindTried) return
        rebindTried = true
        scope.launch {
            kotlinx.coroutines.delay(4_000)
            ListenerRebinder.ensure(appContext, settings.current.autoBookkeeping, autoStatus.current.live, force = true)
        }
    }

    /** “未识别通知 → 这是一笔记账”：用户补上金额和方向后，当作识别出的一笔放进待确认。 */
    fun recordUnrecognized(notice: UnrecognizedNotice, type: com.xiguli.jinkui.data.ledger.TxType, amount: Long) {
        capturePayment(CapturedPayment(notice.source, type, amount, "", notice.postedAt))
        runCatching { unrecognized.remove(notice.id) }
    }

    /** 从桌面快捷方式“记一笔”进来：解锁后打开账本并弹出记账面板。每次请求加一。 */
    val quickRecordRequests = mutableStateOf(0)

    /** 已经处理过的快捷记账请求（账本页打开记账面板后更新）。 */
    var quickRecordHandled = 0

    /** 按当前设置换字体（不在组合里写，换完整个界面立即重组）。 */
    fun applyFonts(value: AppSettings = settings.current) {
        JkFonts.config = when (value.fontMode) {
            FontMode.SYSTEM -> FontConfig.System
            FontMode.ROUNDED -> FontConfig.Rounded
            FontMode.CUSTOM -> fonts.load()?.let { FontConfig.custom(it) } ?: FontConfig.System
        }
    }

    /** 通知监听识别出一笔：加密暂存；已经解锁时马上转进“待确认”。 */
    fun capturePayment(payment: CapturedPayment) {
        runCatching { intake.append(payment) }
        if (vault.isUnlocked) scope.launch { drainIntake() }
    }

    /** 把暂存的几笔转进加密数据库（解锁时、解锁状态下收到通知时）。失败时暂存保留，下次再试。 */
    suspend fun drainIntake() {
        if (vault.keys == null) return
        runCatching {
            intake.drain { items -> ledger.ingest(items, settings.current.autoSave) }
        }
    }

    /** 刚创建完保险库时，引导用户设置快捷解锁。 */
    val showQuickUnlockIntro = mutableStateOf(false)

    /** 输错自毁刚刚发生，供首屏提示。 */
    val justWiped = mutableStateOf(false)

    init {
        vault.onUnlocked = {
            repository.load()
            ledger.load()
            drainIntake()
            runCatching { unrecognized.refresh() }
        }
        // 清空全部数据时，自动记账的暂存也一并删除（它在保险库之外）
        vault.onWiped = {
            intake.deleteAll()
            unrecognized.clear()
        }
        applyFonts()
        scope.launch {
            settings.flow
                .map { it.fontMode to it.customFontStamp }
                .distinctUntilChanged()
                .collect { applyFonts() }
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
