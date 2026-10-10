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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("未提供 AppContainer") }

@Composable
fun JinKuiRoot(container: AppContainer) {
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val state by container.vault.state.collectAsStateWithLifecycle()
    val touch: () -> Unit = remember(container) { { container.lock.touch() } }

    JinKuiTheme(settings.theme, dynamicColor = settings.dynamicColor) {
        CompositionLocalProvider(
            LocalContainer provides container,
            LocalUserActivity provides touch,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Jk.colors.bg),
            ) {
                // 界面只由保险库状态决定：一旦锁定，下面整棵含明文的界面树立即离开组合。
                when (state) {
                    VaultState.Uninitialized -> SetupScreen()
                    VaultState.Locked -> LockScreen()
                    VaultState.Unlocked -> {
                        if (container.showQuickUnlockIntro.value) {
                            QuickUnlockIntro(onDone = { container.showQuickUnlockIntro.value = false })
                        } else {
                            MainNavigation()
                        }
                    }
                }
                MessageHost(container.messages)
            }
        }
    }
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
            ScreenEnter {
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
                                onAuto = { navigator.push(Screen.AutoBookkeeping) },
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
                    onAutoBookkeeping = { navigator.push(Screen.AutoBookkeeping) },
                )
                Screen.FontSettings -> FontSettingsScreen(onBack = { navigator.pop() })
                Screen.BillImport -> BillImportScreen(onBack = { navigator.pop() })
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
                Screen.LedgerAccounts -> LedgerAccountsScreen(onBack = { navigator.pop() })
                Screen.LedgerAssets -> LedgerAssetsScreen(
                    onBack = { navigator.pop() },
                    onAccounts = { navigator.push(Screen.LedgerAccounts) },
                )
                Screen.LedgerBudget -> LedgerBudgetScreen(onBack = { navigator.pop() })
                Screen.LedgerCategories -> LedgerCategoriesScreen(onBack = { navigator.pop() })
                Screen.LedgerRecurring -> LedgerRecurringScreen(onBack = { navigator.pop() })
            }
            }
        }
    }
}

/** 进入一页时的轻微淡入上移。只做进入动画：离开的页面连同其中的明文必须立刻销毁。 */
@Composable
private fun ScreenEnter(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "screenEnter",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 24f
            },
    ) {
        content()
    }
}

@Composable
private fun BoxScope.MessageHost(bus: MessageBus) {
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(bus) {
        bus.messages.collectLatest { message ->
            text = message
            visible = true
            delay(2600)
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
        Text(
            text = text,
            style = JkType.small,
            color = Jk.colors.bg,
            modifier = Modifier
                // 消息条在悬浮底栏之上
                .padding(bottom = 84.dp)
                .clip(Radius.control)
                .background(Jk.colors.textPrimary)
                .padding(horizontal = Space.x2, vertical = Space.x1_5),
        )
    }
}
