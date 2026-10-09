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

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val vault = container.vault

    var busy by mutableStateOf(false)
        private set
    var dialogError by mutableStateOf<String?>(null)
    var pinEnabled by mutableStateOf(vault.pinEnabled)
        private set
    var biometricEnabled by mutableStateOf(vault.biometricEnabled)
        private set

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
            container.messages.show("已导出 ${exported.cards} 张卡片、${exported.transactions} 笔账目的加密备份")
            onDone()
        } catch (e: WrongSecretException) {
            dialogError = "主密码不正确"
        }
    }

    fun import(source: Uri, password: String, onDone: () -> Unit) = launchBusy {
        try {
            val summary = container.backup.import(source, password)
            val ledgerNote = if (summary.ledgerAdded + summary.ledgerUpdated > 0) {
                "；账本新增 ${summary.ledgerAdded}，更新 ${summary.ledgerUpdated}"
            } else {
                ""
            }
            container.messages.show("导入完成：新增 ${summary.added}，更新 ${summary.updated}，跳过 ${summary.skipped}$ledgerNote")
            onDone()
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

private sealed interface SettingsDialog {
    data object AutoLock : SettingsDialog
    data object Clipboard : SettingsDialog
    data object PinSetup : SettingsDialog
    data object PinManage : SettingsDialog
    data object ChangePassword : SettingsDialog
    data class Export(val target: Uri) : SettingsDialog
    data class Import(val source: Uri) : SettingsDialog
    data object Wipe : SettingsDialog
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        JkTopBar(title = "设置", onBack = onBack)
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
                    onClick = { dialog = SettingsDialog.AutoLock },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "生物识别解锁",
                    subtitle = if (biometricAvailable) "用指纹或面容打开金匮" else "设备未录入指纹或面容",
                    icon = Icons.Outlined.Fingerprint,
                    trailing = {
                        JkSwitch(
                            checked = viewModel.biometricEnabled,
                            enabled = biometricAvailable || viewModel.biometricEnabled,
                            onCheckedChange = { enable ->
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
                        )
                    },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "PIN 解锁",
                    subtitle = "生物识别不可用时的备用方式",
                    icon = Icons.Outlined.Dialpad,
                    value = if (viewModel.pinEnabled) "已设置" else "未设置",
                    onClick = {
                        dialog = if (viewModel.pinEnabled) SettingsDialog.PinManage else SettingsDialog.PinSetup
                    },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "修改主密码",
                    icon = Icons.Outlined.Password,
                    onClick = { dialog = SettingsDialog.ChangePassword },
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
                            onCheckedChange = { on -> container.settings.update { it.copy(wipeOnFailure = on) } },
                        )
                    },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "剪贴板自动清空",
                    subtitle = "复制的内容到时间后从剪贴板移除",
                    icon = Icons.Outlined.ContentPaste,
                    value = SettingsRepository.clipboardLabel(settings.clipboardSeconds),
                    onClick = { dialog = SettingsDialog.Clipboard },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "记录历史密码",
                    subtitle = "敏感字段被修改时保留旧值",
                    icon = Icons.Outlined.History,
                    trailing = {
                        JkSwitch(
                            checked = settings.keepHistory,
                            onCheckedChange = { on -> container.settings.update { it.copy(keepHistory = on) } },
                        )
                    },
                )
            }

            JkSectionLabel("外观")
            JkSegmented(
                options = ThemeMode.entries.map { it to it.label },
                selected = settings.theme,
                onSelect = { mode -> container.settings.update { it.copy(theme = mode) } },
            )

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
                    onClick = {
                        container.lock.holdForExternalActivity()
                        exportPicker.launch(container.backup.suggestedFileName())
                    },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "导入加密备份",
                    subtitle = "校验完整性后合并到本机，不会删除已有卡片",
                    icon = Icons.Outlined.FileDownload,
                    onClick = {
                        container.lock.holdForExternalActivity()
                        importPicker.launch(arrayOf("*/*"))
                    },
                )
            }
            Text(
                "金匮不提供明文导出。备份文件用主密码加密（AES-256-GCM），被改动过的文件无法导入。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x1),
            )

            JkSectionLabel("其他")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "立即锁定",
                    icon = Icons.Outlined.Lock,
                    onClick = { container.vault.lock() },
                    trailing = {},
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "清空全部数据",
                    subtitle = "删除本机所有卡片、附件、账本与密钥",
                    icon = Icons.Outlined.DeleteForever,
                    danger = true,
                    onClick = { dialog = SettingsDialog.Wipe },
                )
            }
            Text(
                "金匮 ${versionName(context)}　全程离线：未申请任何联网权限",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x2),
            )
        }
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
                onClick = {
                    viewModel.removePin()
                    close()
                },
                modifier = Modifier.clip(Radius.control),
            )
        }
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
