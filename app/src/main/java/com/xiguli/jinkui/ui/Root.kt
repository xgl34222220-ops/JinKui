package com.xiguli.jinkui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.MessageBus
import com.xiguli.jinkui.core.security.VaultState
import com.xiguli.jinkui.ui.card.CardDetailScreen
import com.xiguli.jinkui.ui.card.CardEditScreen
import com.xiguli.jinkui.ui.components.LocalUserActivity
import com.xiguli.jinkui.ui.health.HealthScreen
import com.xiguli.jinkui.ui.home.HomeScreen
import com.xiguli.jinkui.ui.nav.HomeTabs
import com.xiguli.jinkui.ui.ledger.CalendarScreen
import com.xiguli.jinkui.ui.ledger.LedgerAssetsScreen
import com.xiguli.jinkui.ui.me.MeActions
import com.xiguli.jinkui.ui.me.MeScreen
import com.xiguli.jinkui.ui.ledger.LedgerAccountsScreen
import com.xiguli.jinkui.ui.ledger.LedgerBudgetScreen
import com.xiguli.jinkui.ui.ledger.LedgerCategoriesScreen
import com.xiguli.jinkui.ui.ledger.LedgerRecurringScreen
import com.xiguli.jinkui.ui.ledger.LedgerScreen
import com.xiguli.jinkui.ui.ledger.LedgerStatsScreen
import com.xiguli.jinkui.ui.nav.HomeTab
import com.xiguli.jinkui.ui.organize.OrganizeScreen
import com.xiguli.jinkui.ui.lock.LockScreen
import com.xiguli.jinkui.ui.nav.Navigator
import com.xiguli.jinkui.ui.nav.Screen
import com.xiguli.jinkui.ui.settings.QuickUnlockIntro
import com.xiguli.jinkui.ui.settings.SettingsScreen
import com.xiguli.jinkui.ui.settings.FontSettingsScreen
import com.xiguli.jinkui.ui.settings.AutoBookkeepingScreen
import com.xiguli.jinkui.ui.ledger.BillImportScreen
import com.xiguli.jinkui.ui.ledger.PendingInboxScreen
import com.xiguli.jinkui.ui.setup.SetupScreen
import com.xiguli.jinkui.ui.theme.JinKuiTheme
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import com.xiguli.jinkui.ui.theme.jkBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("未提供 AppContainer") }

@Composable
fun JinKuiRoot(container: AppContainer) {
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val state by container.vault.state.collectAsStateWithLifecycle()
    val touch: () -> Unit = remember(container) { { container.lock.touch() } }

    JinKuiTheme(settings.theme, dynamicColor = settings.dynamicColor, reduceTransparency = settings.reduceTransparency) {
        CompositionLocalProvider(
            LocalContainer provides container,
            LocalUserActivity provides touch,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .jkBackdrop(),
            ) {
                // 界面只由保险库状态决定：一旦锁定，下面整棵含明文的界面树立即离开组合。
                when (state) {
                    VaultState.Uninitialized -> SetupScreen()
                    VaultState.Locked -> LockScreen()
                    VaultState.Unlocked -> UnlockEnter {
                        if (container.showQuickUnlockIntro.value) {
                            QuickUnlockIntro(onDone = { container.showQuickUnlockIntro.value = false })
                        } else {
                            MainNavigation()
                        }
                    }
                }
                MessageHost(container.messages)
                LaunchVeil()
            }
        }
    }
}

/**
 * 解锁后的进入动画（1.4.0-alpha03）：主界面从 96% 轻轻放大并淡入，像锁屏“打开”了一样。
 * 只做进入：锁定时整棵界面树立即离开组合，不做任何退出动画；系统关闭了动画时直接显示。
 */
@Composable
private fun UnlockEnter(content: @Composable () -> Unit) {
    val reduceMotion = com.xiguli.jinkui.ui.components.rememberReduceMotion()
    val progress = remember { androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 380f))
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value.coerceIn(0f, 1f)
                alpha = p
                val k = 0.96f + 0.04f * p
                scaleX = k
                scaleY = k
            },
    ) { content() }
}

@Composable
private fun MainNavigation() {
    val navigator = remember { Navigator() }
    DisposableEffect(navigator) {
        onDispose { navigator.dispose() }
    }
    BackHandler(enabled = navigator.canPop) { navigator.pop() }

    // 桌面快捷方式“记一笔”：回到账本（正在编辑卡片时不打断，先提示）
    val container = LocalContainer.current
    val quickRecord by container.quickRecordRequests
    LaunchedEffect(quickRecord) {
        if (quickRecord > container.quickRecordHandled) {
            if (navigator.screens.any { it is Screen.Edit }) {
                container.quickRecordHandled = quickRecord
                container.messages.show("正在编辑卡片，保存或返回后再记一笔")
            } else {
                navigator.popToHome()
                navigator.homeTab = HomeTab.LEDGER
            }
        }
    }

    val entry = navigator.current
    CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
        key(entry.id) {
            ScreenEnter(origin = if (entry.screen is Screen.Detail) remember(entry.id) { com.xiguli.jinkui.ui.nav.TransitionOrigin.take() } else null) {
            when (val screen = entry.screen) {
                Screen.Home -> HomeTabs(
                    selected = navigator.homeTab,
                    onSelect = { navigator.homeTab = it },
                ) { tab ->
                    when (tab) {
                        HomeTab.LEDGER -> LedgerScreen(
                            onAccounts = { navigator.push(Screen.LedgerAccounts) },
                            onStats = { navigator.homeTab = HomeTab.STATS },
                            onBudget = { navigator.push(Screen.LedgerBudget) },
                            onCategories = { navigator.push(Screen.LedgerCategories) },
                            onRecurring = { navigator.push(Screen.LedgerRecurring) },
                            onSettings = { navigator.push(Screen.Settings) },
                            onAssets = { navigator.push(Screen.LedgerAssets) },
                            onImport = { navigator.push(Screen.BillImport) },
                            onPending = { navigator.push(Screen.PendingInbox) },
                            onDebts = { navigator.push(Screen.Debts) },
                        )
                        HomeTab.CALENDAR -> CalendarScreen()
                        HomeTab.CARDS -> HomeScreen(
                            onOpen = { navigator.push(Screen.Detail(it)) },
                            onCreate = { navigator.push(Screen.Edit(null, it)) },
                            onSettings = { navigator.push(Screen.Settings) },
                            onHealth = { navigator.push(Screen.Health) },
                            onOrganize = { navigator.push(Screen.Organize) },
                        )
                        HomeTab.STATS -> LedgerStatsScreen()
                        HomeTab.ME -> MeScreen(
                            MeActions(
                                onRecurring = { navigator.push(Screen.LedgerRecurring) },
                                onBudget = { navigator.push(Screen.LedgerBudget) },
                                onAssets = { navigator.push(Screen.LedgerAssets) },
                                onAccounts = { navigator.push(Screen.LedgerAccounts) },
                                onCategories = { navigator.push(Screen.LedgerCategories) },
                                onOrganize = { navigator.push(Screen.Organize) },
                                onBackup = { navigator.push(Screen.Settings) },
                                onSettings = { navigator.push(Screen.Settings) },
                                onHealth = { navigator.push(Screen.Health) },
                                onImport = { navigator.push(Screen.BillImport) },
                                onImportHistory = { navigator.push(Screen.ImportHistory) },
                                onAuto = { navigator.push(Screen.AutoBookkeeping) },
                                onDebts = { navigator.push(Screen.Debts) },
                            ),
                        )
                    }
                }
                is Screen.Detail -> CardDetailScreen(
                    cardId = screen.cardId,
                    onBack = { navigator.pop() },
                    onEdit = { navigator.push(Screen.Edit(screen.cardId, null)) },
                )
                is Screen.Edit -> CardEditScreen(
                    cardId = screen.cardId,
                    newType = screen.type,
                    onClose = { navigator.pop() },
                )
                Screen.Settings -> SettingsScreen(
                    onBack = { navigator.pop() },
                    onFonts = { navigator.push(Screen.FontSettings) },
                    onBillImport = { navigator.push(Screen.BillImport) },
                    onImportHistory = { navigator.push(Screen.ImportHistory) },
                    onAutoBookkeeping = { navigator.push(Screen.AutoBookkeeping) },
                )
                Screen.FontSettings -> FontSettingsScreen(onBack = { navigator.pop() })
                Screen.BillImport -> BillImportScreen(onBack = { navigator.pop() }, onHistory = { navigator.push(Screen.ImportHistory) })
                Screen.ImportHistory -> com.xiguli.jinkui.ui.ledger.ImportHistoryScreen(
                    onBack = { navigator.pop() },
                    onImport = { navigator.push(Screen.BillImport) },
                )
                Screen.AutoBookkeeping -> AutoBookkeepingScreen(
                    onBack = { navigator.pop() },
                    onInbox = { navigator.push(Screen.PendingInbox) },
                )
                Screen.PendingInbox -> PendingInboxScreen(
                    onBack = { navigator.pop() },
                    onSettings = { navigator.push(Screen.AutoBookkeeping) },
                )
                Screen.Health -> HealthScreen(
                    onBack = { navigator.pop() },
                    onOpen = { navigator.push(Screen.Detail(it)) },
                    onSettings = { navigator.push(Screen.Settings) },
                )
                Screen.Organize -> OrganizeScreen(onBack = { navigator.pop() })
                Screen.LedgerAccounts -> LedgerAccountsScreen(
                    onBack = { navigator.pop() },
                    onDebt = { account ->
                        navigator.push(if (account.kind == com.xiguli.jinkui.data.ledger.AccountKind.CREDIT) Screen.CreditStatement(account.id) else Screen.Loans)
                    },
                )
                Screen.LedgerAssets -> LedgerAssetsScreen(
                    onBack = { navigator.pop() },
                    onAccounts = { navigator.push(Screen.LedgerAccounts) },
                    onDebts = { navigator.push(Screen.Debts) },
                    onCredit = { navigator.push(Screen.CreditStatement(it)) },
                    onLoans = { navigator.push(Screen.Loans) },
                )
                Screen.Debts -> com.xiguli.jinkui.ui.ledger.DebtOverviewScreen(
                    onBack = { navigator.pop() },
                    onCredit = { navigator.push(Screen.CreditStatement(it)) },
                    onLoans = { navigator.push(Screen.Loans) },
                    onAccounts = { navigator.push(Screen.LedgerAccounts) },
                )
                is Screen.CreditStatement -> com.xiguli.jinkui.ui.ledger.CreditStatementScreen(screen.accountId, onBack = { navigator.pop() })
                Screen.Loans -> com.xiguli.jinkui.ui.ledger.LoanListScreen(onBack = { navigator.pop() })
                Screen.LedgerBudget -> LedgerBudgetScreen(onBack = { navigator.pop() })
                Screen.LedgerCategories -> LedgerCategoriesScreen(onBack = { navigator.pop() })
                Screen.LedgerRecurring -> LedgerRecurringScreen(onBack = { navigator.pop() })
            }
            }
        }
    }
}

/** 本进程是否已经播过启动淡出（转屏、换主题时不再播）。 */
private var launchVeilShown = false

/**
 * 启动画面的延续：系统启动画面（Android 12+）或启动窗口底（12 以下）在第一帧后消失，
 * 这里画同样的薄荷渐变 + 图标 + “金匮 / 卡包 · 账本 · 仅存本机”，立刻（不等待、不拖延启动）用 420ms 淡出到玻璃背景。
 * 不拦截触摸；读屏忽略。
 */
@Composable
private fun BoxScope.LaunchVeil() {
    if (launchVeilShown) return
    val alpha = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(0f, tween(durationMillis = 420))
        launchVeilShown = true
    }
    if (alpha.value <= 0f) return
    val colors = Jk.colors
    androidx.compose.foundation.layout.Column(
        Modifier
            .matchParentSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(androidx.compose.ui.res.colorResource(com.xiguli.jinkui.R.color.splash_top), androidx.compose.ui.res.colorResource(com.xiguli.jinkui.R.color.splash_bottom)),
                ),
            )
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        com.xiguli.jinkui.ui.components.BrandMark(size = 120.dp)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Space.x3))
        Text("金匮", style = JkType.brand.copy(fontSize = 40.sp, lineHeight = 48.sp), color = colors.textPrimary)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Space.x1))
        Text("卡包 · 账本 · 仅存本机", style = JkType.body, color = colors.textSecondary)
    }
}

/**
 * 进入一页时的动画。只做进入：离开的页面连同其中的明文必须立刻销毁。
 *  - 有 [origin]（从列表里点开一张卡片）：容器变换——从那一项的矩形用弹簧展开到全屏，圆角收起、内容淡入；
 *  - 否则：轻微的淡入上移（弹簧）。
 */
@Composable
private fun ScreenEnter(origin: androidx.compose.ui.geometry.Rect? = null, content: @Composable () -> Unit) {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = if (origin != null) 380f else 600f))
    }
    val radius = with(androidx.compose.ui.platform.LocalDensity.current) { 22.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                if (origin != null) {
                    clip = p < 1f
                    shape = com.xiguli.jinkui.ui.nav.RevealShape(origin, p, radius)
                    alpha = (0.35f + p * 1.3f).coerceAtMost(1f)
                } else {
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 24f
                }
            },
    ) {
        content()
    }
}

@Composable
private fun BoxScope.MessageHost(bus: MessageBus) {
    var visible by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<com.xiguli.jinkui.BusMessage?>(null) }
    LaunchedEffect(bus) {
        bus.messages.collectLatest { message ->
            current = message
            visible = true
            // 带“撤销”等操作的提示多停留一会儿
            delay(if (message.action != null) 5200 else 2600)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = Space.x3, vertical = Space.x3),
    ) {
        val message = current
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                // 消息条在悬浮底栏之上
                .padding(bottom = 84.dp)
                .clip(Radius.control)
                .background(Jk.colors.textPrimary)
                .padding(start = Space.x2, end = if (message?.action != null) Space.x05 else Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message?.text.orEmpty(),
                style = JkType.small,
                color = Jk.colors.bg,
                modifier = Modifier.padding(vertical = Space.x1_5).weight(1f, fill = false),
            )
            val action = message?.action
            if (action != null) {
                Text(
                    text = message.actionLabel.orEmpty(),
                    style = JkType.small.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    ),
                    color = Jk.colors.bg,
                    modifier = Modifier
                        .padding(start = Space.x1)
                        .heightIn(min = 48.dp)
                        .clip(Radius.pill)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            visible = false
                            action()
                        }
                        .padding(horizontal = Space.x1_5, vertical = Space.x1_5),
                )
            }
        }
    }
}
