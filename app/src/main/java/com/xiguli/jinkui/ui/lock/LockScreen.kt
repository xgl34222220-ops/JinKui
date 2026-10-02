package com.xiguli.jinkui.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.core.security.BiometricHelper
import com.xiguli.jinkui.core.security.UnlockResult
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.core.security.findFragmentActivity
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BrandMark
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import javax.crypto.Cipher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LockViewModel(private val container: AppContainer) : ViewModel() {
    private val vault = container.vault

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var lockoutUntil by mutableLongStateOf(vault.lockoutUntil)
        private set

    fun submit(secret: String, usePin: Boolean, onFinished: (success: Boolean) -> Unit) {
        if (busy || secret.isEmpty()) return
        busy = true
        error = null
        viewModelScope.launch {
            val result = if (usePin) vault.unlockWithPin(secret) else vault.unlockWithPassword(secret)
            handle(result, if (usePin) "PIN" else "主密码")
            busy = false
            onFinished(result is UnlockResult.Success)
        }
    }

    fun unlockWithBiometric(cipher: Cipher) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            handle(vault.unlockWithBiometric(cipher), "生物识别")
            busy = false
        }
    }

    private fun handle(result: UnlockResult, what: String) {
        when (result) {
            UnlockResult.Success -> error = null
            is UnlockResult.Wrong -> {
                lockoutUntil = vault.lockoutUntil
                val remaining = result.remaining
                error = if (remaining != null) {
                    "$what 不正确。再输错 $remaining 次，将清空全部数据"
                } else {
                    "$what 不正确"
                }
            }
            is UnlockResult.LockedOut -> lockoutUntil = result.untilMillis
            UnlockResult.Wiped -> container.justWiped.value = true
            is UnlockResult.Error -> error = result.message
        }
    }
}

@Composable
fun LockScreen() {
    val container = LocalContainer.current
    val context = LocalContext.current
    val viewModel = jkViewModel { LockViewModel(container) }
    val colors = Jk.colors
    val vault = container.vault

    val pinEnabled = remember { vault.pinEnabled }
    val biometricReady = remember { vault.biometricEnabled && BiometricHelper.isAvailable(context) }
    var usePin by remember { mutableStateOf(pinEnabled) }
    var secret by remember { mutableStateOf("") }

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }
    val waitSeconds = ((viewModel.lockoutUntil - now + 999) / 1000).coerceAtLeast(0)
    val canSubmit = waitSeconds == 0L && !viewModel.busy

    val startBiometric: () -> Unit = {
        val activity = context.findFragmentActivity()
        val cipher = vault.biometricUnlockCipher()
        if (activity != null && cipher != null) {
            BiometricHelper.authenticate(
                activity = activity,
                title = "解锁金匮",
                subtitle = "验证指纹或面容",
                negativeText = if (pinEnabled) "使用 PIN 或主密码" else "使用主密码",
                cipher = cipher,
                onSuccess = { viewModel.unlockWithBiometric(it) },
                onError = { message -> if (message != null) viewModel.error = message },
            )
        } else if (cipher == null) {
            viewModel.error = "系统中的指纹或面容有变更，生物识别解锁已关闭。请用主密码解锁后重新开启。"
        }
    }

    LaunchedEffect(Unit) {
        if (biometricReady) startBiometric()
    }

    val submit: () -> Unit = {
        if (canSubmit) {
            viewModel.submit(secret, usePin) { success ->
                if (!success) secret = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.x6 + Space.x3))
        BrandMark(size = 72.dp)
        Spacer(Modifier.height(Space.x2_5))
        Text("金匮", style = JkType.brand, color = colors.textPrimary)
        Spacer(Modifier.height(Space.x05))
        Text(
            text = when {
                waitSeconds > 0 -> "请等待 $waitSeconds 秒后再试"
                viewModel.busy -> "正在解锁…"
                usePin -> "输入 PIN 解锁"
                else -> "输入主密码解锁"
            },
            style = JkType.small,
            color = colors.textSecondary,
        )

        val error = viewModel.error
        Spacer(Modifier.height(Space.x2))
        Text(
            text = error ?: " ",
            style = JkType.small,
            color = colors.danger,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(Space.x2))

        if (usePin) {
            PinDots(count = secret.length)
            Spacer(Modifier.height(Space.x4))
            PinPad(
                enabled = canSubmit,
                onDigit = { digit ->
                    if (secret.length < VaultManager.MAX_PIN_LENGTH) secret += digit
                    viewModel.error = null
                    container.lock.touch()
                },
                onBackspace = { secret = secret.dropLast(1) },
                side = if (biometricReady) Icons.Outlined.Fingerprint else null,
                onSide = startBiometric,
            )
            Spacer(Modifier.height(Space.x3))
            JkButton(
                text = "解锁",
                onClick = submit,
                enabled = secret.length >= VaultManager.MIN_PIN_LENGTH && waitSeconds == 0L,
                loading = viewModel.busy,
                modifier = Modifier.width(232.dp),
            )
        } else {
            JkTextField(
                value = secret,
                onValueChange = { value ->
                    secret = value
                    viewModel.error = null
                },
                placeholder = "主密码",
                secret = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = submit,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.x2))
            JkButton(
                text = "解锁",
                onClick = submit,
                enabled = secret.isNotEmpty() && waitSeconds == 0L,
                loading = viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            if (biometricReady && !usePin) {
                JkButton(
                    text = "生物识别",
                    icon = Icons.Outlined.Fingerprint,
                    style = ButtonStyle.Secondary,
                    compact = true,
                    onClick = startBiometric,
                )
            }
            if (pinEnabled) {
                JkButton(
                    text = if (usePin) "改用主密码" else "改用 PIN",
                    style = ButtonStyle.Ghost,
                    compact = true,
                    onClick = {
                        usePin = !usePin
                        secret = ""
                        viewModel.error = null
                    },
                )
            }
        }

        if (container.settings.current.wipeOnFailure) {
            Spacer(Modifier.height(Space.x3))
            Text(
                text = "连续输错 ${VaultManager.MAX_ATTEMPTS} 次将清空全部数据",
                style = JkType.caption,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(Space.x4))
    }
}

/** 已输入位数的圆点。PIN 长度不固定，所以按最短位数留出空位，超出后继续增加。 */
@Composable
private fun PinDots(count: Int) {
    val colors = Jk.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x1_5), verticalAlignment = Alignment.CenterVertically) {
        for (index in 0 until maxOf(VaultManager.MIN_PIN_LENGTH, count)) {
            val filled = index < count
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (filled) colors.textPrimary else colors.bg)
                    .border(1.dp, if (filled) colors.textPrimary else colors.textTertiary, CircleShape),
            )
        }
    }
}

/**
 * 自绘数字键盘：PIN 不经过系统输入法，输入法也就无从记录它。
 * [side] 为左下角的附加键（生物识别）。
 */
@Composable
private fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    side: ImageVector?,
    onSide: () -> Unit,
) {
    val colors = Jk.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
        for (rowKeys in listOf("123", "456", "789")) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2_5)) {
                for (digit in rowKeys) {
                    PadKey(enabled = enabled, onClick = { onDigit(digit) }) {
                        Text(digit.toString(), style = JkType.display, color = colors.textPrimary)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2_5)) {
            if (side != null) {
                PadKey(enabled = true, outlined = false, onClick = onSide) {
                    Icon(side, contentDescription = "生物识别", tint = colors.textSecondary, modifier = Modifier.size(26.dp))
                }
            } else {
                Spacer(Modifier.size(64.dp))
            }
            PadKey(enabled = enabled, onClick = { onDigit('0') }) {
                Text("0", style = JkType.display, color = colors.textPrimary)
            }
            PadKey(enabled = true, outlined = false, onClick = onBackspace) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "删除一位",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun PadKey(
    enabled: Boolean,
    onClick: () -> Unit,
    outlined: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = Jk.colors
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .then(
                if (outlined) {
                    Modifier
                        .background(colors.surface)
                        .border(1.dp, colors.border, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
