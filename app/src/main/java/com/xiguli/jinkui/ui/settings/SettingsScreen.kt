package com.xiguli.jinkui.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.core.crypto.BackupFormatException
import com.xiguli.jinkui.core.crypto.WrongSecretException
import com.xiguli.jinkui.core.security.BiometricHelper
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.core.security.findFragmentActivity
import com.xiguli.jinkui.data.repo.AppSettings
import com.xiguli.jinkui.data.repo.SettingsRepository
import com.xiguli.jinkui.data.repo.ThemeMode
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.card.formatTime
import com.xiguli.jinkui.ui.components.BrandMark
import com.xiguli.jinkui.ui.components.JkBadge
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSectionLabel
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkSwitch
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.setup.StrengthMeter
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberCoroutineScope
import com.xiguli.jinkui.core.qr.OtpMigration
import com.xiguli.jinkui.core.qr.OtpQr
import com.xiguli.jinkui.core.qr.OtpQrResult
import com.xiguli.jinkui.data.repo.QrImageReader
import com.xiguli.jinkui.ui.card.OtpBatchSheet
import com.xiguli.jinkui.ui.theme.JkSize

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val vault = container.vault

    var busy by mutableStateOf(false)
        private set
    var dialogError by mutableStateOf<String?>(null)
    var pinEnabled by mutableStateOf(vault.pinEnabled)
        private set
    var biometricEnabled by mutableStateOf(vault.biometricEnabled)
        private set
    /** 导出 / 导入完成后的结果卡片（标题 + 几行明细）；null 时不显示。 */
    var result by mutableStateOf<OperationResult?>(null)

    fun refreshUnlockMethods() {
        pinEnabled = vault.pinEnabled
        biometricEnabled = vault.biometricEnabled
    }

    fun removePin() {
        vault.removePin()
        refreshUnlockMethods()
        container.messages.show("已移除 PIN")
    }

    fun disableBiometric() {
        vault.disableBiometric()
        refreshUnlockMethods()
    }

    fun changePassword(old: String, new: String, onDone: () -> Unit) = launchBusy {
        val changed = vault.changePassword(old, new)
        if (changed) {
            container.messages.show("主密码已修改")
            onDone()
        } else {
            dialogError = "当前主密码不正确"
        }
    }

    fun export(target: Uri, password: String, onDone: () -> Unit) = launchBusy {
        try {
            val exported = container.backup.export(target, password)
            container.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
            onDone()
            result = OperationResult(
                title = "备份已导出",
                lines = listOf(
                    "卡片" to "${exported.cards} 张",
                    "账目" to "${exported.transactions} 笔",
                ),
                note = "请把备份文件存到安全的地方。恢复时需要现在的主密码。",
            )
        } catch (e: WrongSecretException) {
            dialogError = "主密码不正确"
        }
    }

    fun import(source: Uri, password: String, onDone: () -> Unit) = launchBusy {
        try {
            val summary = container.backup.import(source, password)
            onDone()
            result = OperationResult(
                title = "导入完成",
                lines = buildList {
                    add("新增卡片" to "${summary.added} 张")
                    add("更新卡片" to "${summary.updated} 张")
                    add("跳过（本机较新或相同）" to "${summary.skipped} 张")
                    add("账本新增 / 更新" to "${summary.ledgerAdded} / ${summary.ledgerUpdated} 条")
                    if (summary.ledgerSuppressed > 0) add("已删除、未加回" to "${summary.ledgerSuppressed} 条")
                },
                note = "导入只合并，不会删除本机已有的卡片或账目。",
            )
        } catch (e: WrongSecretException) {
            dialogError = e.message
        } catch (e: BackupFormatException) {
            dialogError = e.message
        }
    }

    fun wipe(password: String) = launchBusy {
        if (vault.verifyPassword(password)) {
            vault.wipeAll()
        } else {
            dialogError = "主密码不正确"
        }
    }

    /** 统一的忙碌状态与兜底错误处理。 */
    private fun launchBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        dialogError = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                dialogError = e.message ?: "操作失败"
            } finally {
                busy = false
            }
        }
    }
}

/** 一次操作的结果：标题、若干“名称—数值”行与一句说明。 */
class OperationResult(val title: String, val lines: List<Pair<String, String>>, val note: String? = null)

private sealed interface SettingsDialog {
    data object AutoLock : SettingsDialog
    data object Clipboard : SettingsDialog
    data object PinSetup : SettingsDialog
    data object PinManage : SettingsDialog
    data object ChangePassword : SettingsDialog
    data class Export(val target: Uri) : SettingsDialog
    data class Import(val source: Uri) : SettingsDialog
    data object Wipe : SettingsDialog
    data object RemovePin : SettingsDialog
    data class OtpBatch(val batch: com.xiguli.jinkui.core.qr.MigrationBatch) : SettingsDialog
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val viewModel = jkViewModel { SettingsViewModel(container) }
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val colors = Jk.colors
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    val close: () -> Unit = {
        dialog = null
        viewModel.dialogError = null
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri != null) dialog = SettingsDialog.Export(uri)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) dialog = SettingsDialog.Import(uri)
    }
    // 身份验证器的导出二维码：从相册 / 文件选一张截图，离线识别（不需要相机或存储权限）
    val scope = rememberCoroutineScope()
    var scanningOtp by remember { mutableStateOf(false) }
    val otpPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scanningOtp = true
        scope.launch {
            val read = runCatching { QrImageReader.read(context, uri) }
            scanningOtp = false
            read.exceptionOrNull()?.let {
                container.messages.show(it.message ?: "无法读取这张图片")
                return@launch
            }
            when (val parsed = OtpQr.interpret(read.getOrNull())) {
                is OtpQrResult.Batch -> dialog = SettingsDialog.OtpBatch(parsed.batch)
                is OtpQrResult.Ok -> dialog = SettingsDialog.OtpBatch(OtpMigration.single(parsed.uri))
                is OtpQrResult.Rejected -> container.messages.show(parsed.message)
            }
        }
    }

    SettingsContent(
        settings = settings,
        biometricAvailable = biometricAvailable,
        biometricEnabled = viewModel.biometricEnabled,
        pinEnabled = viewModel.pinEnabled,
        scanningOtp = scanningOtp,
        version = versionName(context),
        actions = SettingsActions(
            onBack = onBack,
            onUpdate = { change -> container.settings.update(change) },
            onAutoLock = { dialog = SettingsDialog.AutoLock },
            onBiometric = { enable ->
                if (!enable) {
                    viewModel.disableBiometric()
                } else {
                    val activity = context.findFragmentActivity()
                    if (activity != null) {
                        enrollBiometric(activity, container.vault) { _, message ->
                            viewModel.refreshUnlockMethods()
                            if (message != null) container.messages.show(message)
                        }
                    }
                }
            },
            onPin = { dialog = if (viewModel.pinEnabled) SettingsDialog.PinManage else SettingsDialog.PinSetup },
            onChangePassword = { dialog = SettingsDialog.ChangePassword },
            onClipboard = { dialog = SettingsDialog.Clipboard },
            onExport = {
                container.lock.holdForExternalActivity()
                exportPicker.launch(container.backup.suggestedFileName())
            },
            onImport = {
                container.lock.holdForExternalActivity()
                importPicker.launch(arrayOf("*/*"))
            },
            onImportOtp = {
                if (!scanningOtp) {
                    container.lock.holdForExternalActivity()
                    otpPicker.launch("image/*")
                }
            },
            onLockNow = { container.vault.lock() },
            onWipe = { dialog = SettingsDialog.Wipe },
        ),
    )

    viewModel.result?.let { done ->
        ResultDialog(done, onDismiss = { viewModel.result = null })
    }

    when (val current = dialog) {
        null -> Unit
        SettingsDialog.AutoLock -> OptionSheet(
            title = "自动锁定",
            hint = "切到后台后按这个时间锁定；前台无操作也按它锁定（最短 30 秒）。",
            options = SettingsRepository.AUTO_LOCK_OPTIONS,
            selected = settings.autoLockSeconds,
            onDismiss = close,
            onSelect = { seconds -> container.settings.update { it.copy(autoLockSeconds = seconds) } },
        )
        SettingsDialog.Clipboard -> OptionSheet(
            title = "剪贴板自动清空",
            hint = "复制账号、密码等内容后，到时间自动从剪贴板移除。",
            options = SettingsRepository.CLIPBOARD_OPTIONS,
            selected = settings.clipboardSeconds,
            onDismiss = close,
            onSelect = { seconds -> container.settings.update { it.copy(clipboardSeconds = seconds) } },
        )
        SettingsDialog.PinSetup -> PinSetupDialog(
            onDismiss = close,
            onSaved = {
                viewModel.refreshUnlockMethods()
                container.messages.show("PIN 已设置")
                close()
            },
        )
        SettingsDialog.PinManage -> JkSheet(onDismiss = close, title = "PIN 解锁") {
            JkRow(
                title = "更换 PIN",
                icon = Icons.Outlined.Dialpad,
                onClick = { dialog = SettingsDialog.PinSetup },
                modifier = Modifier.clip(Radius.control),
            )
            JkRow(
                title = "移除 PIN",
                icon = Icons.Outlined.DeleteForever,
                danger = true,
                onClick = { dialog = SettingsDialog.RemovePin },
                modifier = Modifier.clip(Radius.control),
            )
        }
        SettingsDialog.RemovePin -> JkDialog(
            title = "移除 PIN？",
            onDismiss = close,
            confirmText = "移除",
            danger = true,
            onConfirm = {
                viewModel.removePin()
                close()
            },
        ) {
            Text("移除后只能用主密码或生物识别解锁。之后可以随时重新设置。", style = JkType.body, color = colors.textSecondary)
        }
        is SettingsDialog.OtpBatch -> OtpBatchSheet(current.batch, onDismiss = close)
        SettingsDialog.ChangePassword -> ChangePasswordDialog(viewModel, close)
        is SettingsDialog.Export -> PasswordDialog(
            title = "导出加密备份",
            message = "输入主密码确认。备份文件将用这个主密码加密，恢复时需要它。",
            confirmText = "导出",
            viewModel = viewModel,
            onDismiss = {
                // 取消时把系统已创建的空文件删掉，免得留下一个打不开的备份。
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, current.target) }
                close()
            },
            onConfirm = { password -> viewModel.export(current.target, password, close) },
        )
        is SettingsDialog.Import -> PasswordDialog(
            title = "导入加密备份",
            message = "输入创建这份备份时使用的主密码。文件会先校验完整性，再合并到本机。",
            confirmText = "导入",
            viewModel = viewModel,
            onDismiss = close,
            onConfirm = { password -> viewModel.import(current.source, password, close) },
        )
        SettingsDialog.Wipe -> PasswordDialog(
            title = "清空全部数据？",
            message = "本机所有卡片、附件照片、账本记录与密钥将被永久删除，无法恢复。输入主密码确认。",
            confirmText = "永久清空",
            danger = true,
            viewModel = viewModel,
            onDismiss = close,
            onConfirm = { password -> viewModel.wipe(password) },
        )
    }
}

/** 设置页的所有操作；默认空实现，方便截图测试只传关心的回调。 */
class SettingsActions(
    val onBack: () -> Unit = {},
    /** 直接改一项非敏感偏好（主题、开关等）。 */
    val onUpdate: ((AppSettings) -> AppSettings) -> Unit = {},
    val onAutoLock: () -> Unit = {},
    val onBiometric: (Boolean) -> Unit = {},
    val onPin: () -> Unit = {},
    val onChangePassword: () -> Unit = {},
    val onClipboard: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onImportOtp: () -> Unit = {},
    val onLockNow: () -> Unit = {},
    val onWipe: () -> Unit = {},
)

/**
 * 设置页内容（无状态）：只读传入的设置与解锁方式状态，所有操作经 [actions] 交给外层。
 * 不碰数据库、密钥与文件选择器，界面测试可以直接渲染。
 */
@Composable
fun SettingsContent(
    settings: AppSettings,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    pinEnabled: Boolean,
    scanningOtp: Boolean,
    version: String,
    actions: SettingsActions,
) {
    val colors = Jk.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        JkTopBar(title = "设置", onBack = actions.onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2_5)
                .padding(top = Space.x05, bottom = Space.x5),
        ) {
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.x2_5)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 48.dp)
                    Spacer(Modifier.width(Space.x2))
                    Column {
                        Text("金匮", style = JkType.brand.copy(fontSize = JkType.title.fontSize), color = colors.textPrimary)
                        Text("数据只保存在这台设备上", style = JkType.small, color = colors.textTertiary)
                    }
                }
                Spacer(Modifier.height(Space.x2))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    verticalArrangement = Arrangement.spacedBy(Space.x1),
                ) {
                    JkBadge("全程离线", Icons.Outlined.CloudOff)
                    JkBadge("双层加密", Icons.Outlined.Shield)
                    JkBadge("${SettingsRepository.autoLockLabel(settings.autoLockSeconds)}自动锁定", Icons.Outlined.Timer)
                    if (settings.wipeOnFailure) JkBadge("输错自毁已开启", Icons.Outlined.WarningAmber)
                }
            }

            JkSectionLabel("解锁与锁定")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "自动锁定",
                    subtitle = "切到后台或长时间无操作后锁定",
                    icon = Icons.Outlined.Timer,
                    value = SettingsRepository.autoLockLabel(settings.autoLockSeconds),
                    onClick = actions.onAutoLock,
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "生物识别解锁",
                    subtitle = if (biometricAvailable) "用指纹或面容打开金匮" else "设备未录入指纹或面容",
                    icon = Icons.Outlined.Fingerprint,
                    trailing = {
                        JkSwitch(
                            checked = biometricEnabled,
                            enabled = biometricAvailable || biometricEnabled,
                            onCheckedChange = actions.onBiometric,
                        )
                    },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "PIN 解锁",
                    subtitle = "生物识别不可用时的备用方式",
                    icon = Icons.Outlined.Dialpad,
                    value = if (pinEnabled) "已设置" else "未设置",
                    onClick = actions.onPin,
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "修改主密码",
                    icon = Icons.Outlined.Password,
                    onClick = actions.onChangePassword,
                )
            }

            JkSectionLabel("数据保护")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "输错自毁",
                    subtitle = "连续输错主密码或 PIN 达 ${VaultManager.MAX_ATTEMPTS} 次，将永久清空本机全部数据，无法恢复。默认开启；开启时请务必保留一份加密备份。",
                    icon = Icons.Outlined.WarningAmber,
                    trailing = {
                        JkSwitch(
                            checked = settings.wipeOnFailure,
                            onCheckedChange = { on -> actions.onUpdate { it.copy(wipeOnFailure = on) } },
                        )
                    },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "剪贴板自动清空",
                    subtitle = "复制的内容到时间后从剪贴板移除",
                    icon = Icons.Outlined.ContentPaste,
                    value = SettingsRepository.clipboardLabel(settings.clipboardSeconds),
                    onClick = actions.onClipboard,
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "记录历史密码",
                    subtitle = "敏感字段被修改时保留旧值",
                    icon = Icons.Outlined.History,
                    trailing = {
                        JkSwitch(
                            checked = settings.keepHistory,
                            onCheckedChange = { on -> actions.onUpdate { it.copy(keepHistory = on) } },
                        )
                    },
                )
            }

            JkSectionLabel("外观")
            JkSegmented(
                options = ThemeMode.entries.map { it to it.label },
                selected = settings.theme,
                onSelect = { mode -> actions.onUpdate { it.copy(theme = mode) } },
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Spacer(Modifier.height(Space.x1))
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                    JkRow(
                        title = "跟随壁纸取色",
                        subtitle = "品牌色改用系统的 Material You 配色；关闭时为金匮靛青",
                        icon = Icons.Outlined.Palette,
                        trailing = {
                            JkSwitch(
                                checked = settings.dynamicColor,
                                onCheckedChange = { on -> actions.onUpdate { it.copy(dynamicColor = on) } },
                            )
                        },
                    )
                }
            }

            JkSectionLabel("加密备份")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "导出加密备份",
                    subtitle = if (settings.lastBackupAt > 0L) {
                        "上次备份：${formatTime(settings.lastBackupAt)}"
                    } else {
                        "还没有备份过。生成的 .jkbak 文件需要主密码才能解开"
                    },
                    icon = Icons.Outlined.FileUpload,
                    onClick = actions.onExport,
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "导入加密备份",
                    subtitle = "校验完整性后合并到本机，不会删除已有卡片",
                    icon = Icons.Outlined.FileDownload,
                    onClick = actions.onImport,
                )
            }
            Text(
                "金匮不提供明文导出。备份文件用主密码加密（AES-256-GCM），被改动过的文件无法导入。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x1),
            )

            JkSectionLabel("两步验证码")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = if (scanningOtp) "正在识别二维码…" else "从身份验证器导入",
                    subtitle = "选一张 Google 身份验证器“导出账号”的二维码截图，预览后选择要添加的账号",
                    icon = Icons.Outlined.QrCode2,
                    onClick = actions.onImportOtp,
                )
            }

            JkSectionLabel("其他")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "立即锁定",
                    icon = Icons.Outlined.Lock,
                    onClick = actions.onLockNow,
                    trailing = {},
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "清空全部数据",
                    subtitle = "删除本机所有卡片、附件、账本与密钥",
                    icon = Icons.Outlined.DeleteForever,
                    danger = true,
                    onClick = actions.onWipe,
                )
            }
            Text(
                "金匮 $version　全程离线：未申请任何联网权限",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x2),
            )
        }
    }

}

/** 导出 / 导入的结果：一个对勾、标题与逐行明细，数字用等宽字形对齐。 */
@Composable
private fun ResultDialog(result: OperationResult, onDismiss: () -> Unit) {
    val colors = Jk.colors
    JkDialog(title = result.title, onDismiss = onDismiss, confirmText = "好", onConfirm = onDismiss, dismissText = null) {
        Box(
            modifier = Modifier
                .size(JkSize.chip)
                .clip(CircleShape)
                .background(colors.incomeSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.income, modifier = Modifier.size(JkSize.icon))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.x05)) {
            for ((label, value) in result.lines) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = JkType.small, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Text(value, style = JkType.amountSmall, color = colors.textPrimary)
                }
            }
        }
        if (result.note != null) Text(result.note, style = JkType.caption, color = colors.textTertiary)
    }
}

private fun versionName(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

@Composable
private fun OptionSheet(
    title: String,
    hint: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    JkSheet(onDismiss = onDismiss, title = title) {
        Text(hint, style = JkType.small, color = Jk.colors.textTertiary, modifier = Modifier.padding(bottom = Space.x1))
        for ((value, label) in options) {
            JkRow(
                title = label,
                value = if (value == selected) "当前" else null,
                onClick = {
                    onSelect(value)
                    onDismiss()
                },
                trailing = {},
                modifier = Modifier.clip(Radius.control),
            )
        }
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    message: String,
    confirmText: String,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    danger: Boolean = false,
) {
    var password by remember { mutableStateOf("") }
    JkDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = if (viewModel.busy) "请稍候…" else confirmText,
        danger = danger,
        confirmEnabled = password.isNotEmpty(),
        loading = viewModel.busy,
        onConfirm = { onConfirm(password) },
    ) {
        Text(message, style = JkType.small, color = Jk.colors.textSecondary)
        JkTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.dialogError = null
            },
            placeholder = "主密码",
            secret = true,
            imeAction = ImeAction.Done,
            onImeAction = { if (password.isNotEmpty() && !viewModel.busy) onConfirm(password) },
            error = viewModel.dialogError,
        )
        if (viewModel.busy) {
            Text("正在用主密码派生密钥并校验，通常需要几秒…", style = JkType.caption, color = Jk.colors.textTertiary)
        }
    }
}

@Composable
private fun ChangePasswordDialog(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = old.isNotEmpty() && new.length >= VaultManager.MIN_PASSWORD_LENGTH && new == confirm
    JkDialog(
        title = "修改主密码",
        onDismiss = onDismiss,
        confirmText = if (viewModel.busy) "请稍候…" else "修改",
        confirmEnabled = valid,
        loading = viewModel.busy,
        onConfirm = { viewModel.changePassword(old, new, onDismiss) },
    ) {
        Text(
            "修改后，旧主密码立即失效。此前导出的备份文件仍然要用当时的主密码解开。",
            style = JkType.small,
            color = Jk.colors.textSecondary,
        )
        JkTextField(
            value = old,
            onValueChange = {
                old = it
                viewModel.dialogError = null
            },
            label = "当前主密码",
            secret = true,
            imeAction = ImeAction.Next,
            error = viewModel.dialogError,
        )
        JkTextField(
            value = new,
            onValueChange = { new = it },
            label = "新主密码（至少 ${VaultManager.MIN_PASSWORD_LENGTH} 位）",
            secret = true,
            imeAction = ImeAction.Next,
        )
        StrengthMeter(new)
        JkTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "再输入一次",
            secret = true,
            imeAction = ImeAction.Done,
            error = if (confirm.isNotEmpty() && confirm != new) "两次输入不一致" else null,
        )
    }
}
