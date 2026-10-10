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
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.sin
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import javax.crypto.Cipher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.xiguli.jinkui.ui.components.pressScale
import com.xiguli.jinkui.ui.components.rememberJkPress
import com.xiguli.jinkui.ui.theme.Radius

class LockViewModel(private val container: AppContainer) : ViewModel() {
    private val vault = container.vault

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var lockoutUntil by mutableLongStateOf(vault.lockoutUntil)
        private set

    /** 每输错一次加一，界面据此播放一次“摇头”动画。 */
    var errorTick by mutableIntStateOf(0)
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
                errorTick++
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
    var showForgot by remember { mutableStateOf(false) }
    var shake by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(viewModel.errorTick) {
        if (viewModel.errorTick > 0) {
            animate(0f, 1f, animationSpec = tween(durationMillis = 380)) { value, _ ->
                shake = sin(value * 4f * PI.toFloat()) * (1f - value) * 36f
            }
            shake = 0f
        }
    }

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

    LockContent(
        state = LockUiState(
            waitSeconds = waitSeconds,
            busy = viewModel.busy,
            usePin = usePin,
            secretLength = secret.length,
            secret = secret,
            error = viewModel.error,
            pinEnabled = pinEnabled,
            biometricReady = biometricReady,
            wipeOnFailure = container.settings.current.wipeOnFailure,
        ),
        shake = shake,
        onDigit = { digit ->
            if (secret.length < VaultManager.MAX_PIN_LENGTH) secret += digit
            viewModel.error = null
            container.lock.touch()
        },
        onBackspace = { secret = secret.dropLast(1) },
        onSecretChange = { value ->
            secret = value
            viewModel.error = null
        },
        onSubmit = submit,
        onBiometric = startBiometric,
        onToggleMode = {
            usePin = !usePin
            secret = ""
            viewModel.error = null
        },
        onForgot = { showForgot = true },
    )

    if (showForgot) {
        JkDialog(
            title = "忘记主密码",
            onDismiss = { showForgot = false },
            confirmText = "知道了",
            dismissText = null,
            onConfirm = { showForgot = false },
        ) {
            Text(
                "金匮不保存主密码，也没有任何找回途径。数据只能用主密码解开，这正是它安全的原因。",
                style = JkType.body,
                color = colors.textSecondary,
            )
            Text(
                "如果确实想不起来，唯一的办法是清空本机数据、重新创建保险库，再从以前导出的加密备份恢复。恢复时需要输入那份备份当时的主密码。",
                style = JkType.small,
                color = colors.textTertiary,
            )
        }
    }
}

/** 已输入位数的圆点。PIN 长度不固定，所以按最短位数留出空位，超出后继续增加；新点亮的一位轻轻弹一下。 */
@Composable
private fun PinDots(count: Int, error: Boolean) {
    val colors = Jk.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 20.dp)
            .semantics { contentDescription = "已输入 $count 位" },
    ) {
        for (index in 0 until maxOf(VaultManager.MIN_PIN_LENGTH, count)) {
            val filled = index < count
            val scale by animateFloatAsState(if (filled) 1f else 0.8f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "dot")
            val fill = when {
                error -> colors.danger
                filled -> colors.textPrimary
                else -> colors.bg
            }
            Box(
                Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(fill)
                    .border(1.dp, if (filled || error) fill else colors.textTertiary, CircleShape),
            )
        }
    }
}

/** 锁屏上的一条状态提示：浅底胶囊 + 图标 + 文字。 */
@Composable
private fun LockNotice(icon: ImageVector, text: String, tint: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .clip(Radius.control)
            .background(background)
            .padding(horizontal = Space.x1_5, vertical = Space.x1)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Space.x1))
        Text(text, style = JkType.small.copy(fontFeatureSettings = "tnum"), color = tint, textAlign = TextAlign.Start)
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
                        Text(digit.toString(), style = JkType.display.copy(fontFeatureSettings = "tnum"), color = if (enabled) colors.textPrimary else colors.textTertiary)
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
                Text("0", style = JkType.display.copy(fontFeatureSettings = "tnum"), color = if (enabled) colors.textPrimary else colors.textTertiary)
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
    val view = LocalView.current
    val press = rememberJkPress(0.92f)
    val pressed by press.interaction.collectIsPressedAsState()
    // 数字键：浅灰圆底、不画描边；按下时加深一档
    val fill by animateColorAsState(if (pressed && outlined) colors.border else colors.surfaceAlt, tween(90), label = "padKey")
    Box(
        modifier = Modifier
            .size(64.dp)
            .pressScale(press)
            .clip(CircleShape)
            .then(
                if (outlined) {
                    Modifier
                        .background(fill)
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = press.interaction, indication = LocalIndication.current, enabled = enabled, role = Role.Button) {
                // 键盘触感，跟随系统“触摸反馈”设置
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 锁屏要显示的状态（无状态的 [LockContent] 用；界面测试可以直接构造）。 */
data class LockUiState(
    val waitSeconds: Long = 0L,
    val busy: Boolean = false,
    val usePin: Boolean = false,
    val secretLength: Int = 0,
    /** 主密码输入框的内容；PIN 模式下不用。 */
    val secret: String = "",
    val error: String? = null,
    val pinEnabled: Boolean = false,
    val biometricReady: Boolean = false,
    val wipeOnFailure: Boolean = true,
)

/** 锁屏主体（无状态）：品牌、提示、状态条、PIN 圆点与数字键盘（或主密码输入框）、切换与说明。 */
@Composable
fun LockContent(
    state: LockUiState,
    shake: Float = 0f,
    onDigit: (Char) -> Unit = {},
    onBackspace: () -> Unit = {},
    onSecretChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
    onBiometric: () -> Unit = {},
    onToggleMode: () -> Unit = {},
    onForgot: () -> Unit = {},
) {
    val colors = Jk.colors
    val waitSeconds = state.waitSeconds
    val usePin = state.usePin
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
                state.busy -> "正在解锁…"
                usePin -> "输入 PIN 解锁"
                else -> "输入主密码解锁"
            },
            style = JkType.small,
            color = colors.textSecondary,
        )

        // 状态条：冷却倒计时 / 错误提示。没有内容时也占着同样的高度，界面不上下跳。
        val error = state.error
        Spacer(Modifier.height(Space.x2))
        Box(Modifier.heightIn(min = 40.dp).widthIn(max = 340.dp), contentAlignment = Alignment.Center) {
            when {
                waitSeconds > 0 -> LockNotice(
                    icon = Icons.Outlined.Timer,
                    text = "输错次数较多，请等待 $waitSeconds 秒",
                    tint = colors.accent,
                    background = colors.accentSoft,
                )
                error != null -> LockNotice(
                    icon = Icons.Outlined.ErrorOutline,
                    text = error,
                    tint = colors.danger,
                    background = colors.dangerSoft,
                )
            }
        }
        Spacer(Modifier.height(Space.x2))

        if (usePin) {
            Box(Modifier.graphicsLayer { translationX = shake }) { PinDots(count = state.secretLength, error = state.error != null) }
            Spacer(Modifier.height(Space.x4))
            PinPad(
                enabled = waitSeconds == 0L && !state.busy,
                onDigit = onDigit,
                onBackspace = onBackspace,
                side = if (state.biometricReady) Icons.Outlined.Fingerprint else null,
                onSide = onBiometric,
            )
            Spacer(Modifier.height(Space.x3))
            JkButton(
                text = "解锁",
                onClick = onSubmit,
                enabled = state.secretLength >= VaultManager.MIN_PIN_LENGTH && waitSeconds == 0L,
                loading = state.busy,
                modifier = Modifier.width(232.dp),
            )
        } else {
            JkTextField(
                value = state.secret,
                onValueChange = onSecretChange,
                placeholder = "主密码",
                secret = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shake },
            )
            Spacer(Modifier.height(Space.x2))
            JkButton(
                text = "解锁",
                onClick = onSubmit,
                enabled = state.secret.isNotEmpty() && waitSeconds == 0L,
                loading = state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            if (state.biometricReady && !usePin) {
                JkButton(
                    text = "生物识别",
                    icon = Icons.Outlined.Fingerprint,
                    style = ButtonStyle.Secondary,
                    compact = true,
                    onClick = onBiometric,
                )
            }
            if (state.pinEnabled) {
                JkButton(
                    text = if (usePin) "改用主密码" else "改用 PIN",
                    style = ButtonStyle.Ghost,
                    compact = true,
                    onClick = onToggleMode,
                )
            }
        }

        if (state.wipeOnFailure) {
            Spacer(Modifier.height(Space.x3))
            Text(
                text = "连续输错 ${VaultManager.MAX_ATTEMPTS} 次将清空全部数据",
                style = JkType.caption,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(Space.x1))
        JkButton("忘记主密码？", onForgot, style = ButtonStyle.Ghost, compact = true)
        Spacer(Modifier.height(Space.x4))
    }

}
