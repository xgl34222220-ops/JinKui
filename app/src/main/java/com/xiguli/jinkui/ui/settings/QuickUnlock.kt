package com.xiguli.jinkui.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.fragment.app.FragmentActivity
import com.xiguli.jinkui.core.security.BiometricHelper
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.core.security.findFragmentActivity
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BrandMark
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * 开启生物识别解锁：新建一把必须经生物识别才能使用的 Keystore 密钥，
 * 认证通过后用它封装保险库密钥。[onResult] 的消息为 null 表示用户主动取消。
 */
fun enrollBiometric(
    activity: FragmentActivity,
    vault: VaultManager,
    onResult: (success: Boolean, message: String?) -> Unit,
) {
    val cipher = try {
        vault.newBiometricEnrollCipher()
    } catch (e: Exception) {
        onResult(false, "无法创建生物识别密钥，请先在系统设置里录入指纹或面容")
        return
    }
    BiometricHelper.authenticate(
        activity = activity,
        title = "开启生物识别解锁",
        subtitle = "验证通过后，可以用指纹或面容打开金匮",
        negativeText = "取消",
        cipher = cipher,
        onSuccess = { unlocked ->
            try {
                vault.enableBiometric(unlocked)
                onResult(true, null)
            } catch (e: Exception) {
                vault.disableBiometric()
                onResult(false, "开启失败，请重试")
            }
        },
        onError = { message ->
            vault.disableBiometric()
            onResult(false, message)
        },
    )
}

@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val valid = pin.length in VaultManager.MIN_PIN_LENGTH..VaultManager.MAX_PIN_LENGTH && pin.all { it.isDigit() }
    JkDialog(
        title = "设置 PIN",
        onDismiss = onDismiss,
        confirmText = "保存",
        confirmEnabled = valid && pin == confirm,
        loading = saving,
        onConfirm = {
            saving = true
            scope.launch {
                try {
                    container.vault.setPin(pin)
                    onSaved()
                } catch (e: Exception) {
                    error = "保存失败：${e.message.orEmpty()}"
                } finally {
                    saving = false
                }
            }
        },
    ) {
        Text(
            "PIN 是生物识别不可用时的快捷方式，强度低于主密码。输错 PIN 同样计入自毁次数。",
            style = JkType.small,
            color = Jk.colors.textSecondary,
        )
        JkTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(VaultManager.MAX_PIN_LENGTH) },
            label = "${VaultManager.MIN_PIN_LENGTH}–${VaultManager.MAX_PIN_LENGTH} 位数字",
            secret = true,
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Next,
        )
        JkTextField(
            value = confirm,
            onValueChange = { confirm = it.filter(Char::isDigit).take(VaultManager.MAX_PIN_LENGTH) },
            label = "再输入一次",
            secret = true,
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            error = when {
                confirm.isNotEmpty() && confirm != pin -> "两次输入不一致"
                else -> error
            },
        )
    }
}

/** 创建保险库后的一步引导：可选开启生物识别与 PIN。 */
@Composable
fun QuickUnlockIntro(onDone: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val colors = Jk.colors
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }
    var biometricOn by remember { mutableStateOf(container.vault.biometricEnabled) }
    var pinOn by remember { mutableStateOf(container.vault.pinEnabled) }
    var showPin by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x3, vertical = Space.x4),
    ) {
        BrandMark()
        Spacer(Modifier.height(Space.x3))
        Text("保险库已创建", style = JkType.display, color = colors.textPrimary)
        Spacer(Modifier.height(Space.x05))
        Text(
            "可以再设一种更顺手的解锁方式。主密码始终有效，这一步随时能在设置里调整。",
            style = JkType.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(Space.x4))
        JkCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.x1)) {
            JkRow(
                title = "生物识别解锁",
                subtitle = when {
                    !biometricAvailable -> "设备未录入指纹或面容"
                    biometricOn -> "已开启"
                    else -> "用指纹或面容打开金匮"
                },
                icon = Icons.Outlined.Fingerprint,
                trailing = {
                    if (biometricAvailable && !biometricOn) {
                        JkButton(
                            text = "开启",
                            compact = true,
                            style = ButtonStyle.Secondary,
                            onClick = {
                                val activity = context.findFragmentActivity() ?: return@JkButton
                                enrollBiometric(activity, container.vault) { success, message ->
                                    biometricOn = success
                                    if (message != null) container.messages.show(message)
                                }
                            },
                        )
                    }
                },
            )
            JkDivider(inset = Space.x6 + Space.x05)
            JkRow(
                title = "PIN 解锁",
                subtitle = if (pinOn) "已设置" else "生物识别不可用时的备用方式",
                icon = Icons.Outlined.Dialpad,
                trailing = {
                    if (!pinOn) {
                        JkButton("设置", compact = true, style = ButtonStyle.Secondary, onClick = { showPin = true })
                    }
                },
            )
        }
        Spacer(Modifier.height(Space.x4))
        JkButton(
            text = if (biometricOn || pinOn) "完成" else "暂不设置",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showPin) {
        PinSetupDialog(
            onDismiss = { showPin = false },
            onSaved = {
                showPin = false
                pinOn = true
            },
        )
    }
}
