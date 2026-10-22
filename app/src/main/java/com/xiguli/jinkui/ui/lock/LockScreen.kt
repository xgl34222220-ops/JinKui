package com.xiguli.jinkui.ui.lock

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.core.security.BiometricHelper
import com.xiguli.jinkui.core.security.UnlockResult
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.core.security.findFragmentActivity
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BrandEmblem
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkNotice
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.NoticeTone
import com.xiguli.jinkui.ui.components.pressScale
import com.xiguli.jinkui.ui.components.rejectHaptic
import com.xiguli.jinkui.ui.components.rememberJkPress
import com.xiguli.jinkui.ui.components.rememberReduceMotion
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import com.xiguli.jinkui.ui.theme.jkSurface
import javax.crypto.Cipher
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(viewModel.errorTick) {
        if (viewModel.errorTick > 0) {
            // 输错：拒绝类触感（跟随系统触摸反馈设置）+ 圆点 / 输入框左右摇两下；关闭了动画时只变色不摇
            view.rejectHaptic()
            if (!reduceMotion) {
                animate(0f, 1f, animationSpec = tween(durationMillis = 420)) { value, _ ->
                    shake = sin(value * 4f * PI.toFloat()) * (1f - value) * 28f
                }
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
        onClear = { secret = "" },
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

/**
 * 已输入位数的圆点，放在一枚玻璃胶囊里。PIN 长度不固定，所以按最短位数留出空位，超出后继续增加；
 * 新点亮的一位轻轻弹一下（关闭了动画时直接点亮）。输错时全部变成危险色。
 */
@Composable
private fun PinDots(count: Int, error: Boolean, reduceMotion: Boolean) {
    val colors = Jk.colors
    val slots = maxOf(VaultManager.MIN_PIN_LENGTH, count)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .jkSurface(Jk.surfaces.chip, Radius.pill)
            .padding(horizontal = Space.x2_5, vertical = 14.dp)
            .semantics {
                contentDescription = if (error) "已输入 $count 位，PIN 不正确" else "已输入 $count 位"
            },
    ) {
        for (index in 0 until slots) {
            val filled = index < count
            val target = if (filled) 1f else 0.78f
            val scale by animateFloatAsState(
                target,
                if (reduceMotion) snap() else spring(dampingRatio = 0.45f, stiffness = 900f),
                label = "dot",
            )
            val fill = when {
                error && filled -> colors.danger
                filled -> colors.primary
                else -> Color.Transparent
            }
            val ring = when {
                error -> colors.danger
                filled -> colors.primary
                else -> colors.textTertiary.copy(alpha = 0.6f)
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
                    .border(1.5.dp, ring, CircleShape),
            )
        }
    }
}

/** 一枚数字键的直径与键距：3 × 72 + 2 × 24 = 264dp，与下面的“解锁”按钮同宽。 */
private val KeySize = 72.dp
private val KeyGap = 24.dp
private val PadWidth = KeySize * 3 + KeyGap * 2

/**
 * 自绘数字键盘：PIN 不经过系统输入法，输入法也就无从记录它。
 * [side] 为左下角的附加键（生物识别）；右下角是删除键，长按清空。
 */
@Composable
private fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    side: ImageVector?,
    onSide: () -> Unit,
) {
    val colors = Jk.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
        for (rowKeys in listOf("123", "456", "789")) {
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                for (digit in rowKeys) {
                    PadKey(enabled = enabled, onClick = { onDigit(digit) }) {
                        Text(digit.toString(), style = JkType.display.copy(fontFeatureSettings = "tnum"), color = if (enabled) colors.textPrimary else colors.textTertiary)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            if (side != null) {
                PadKey(enabled = true, glass = false, description = "使用指纹或面容解锁", onClick = onSide) {
                    Icon(side, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
                }
            } else {
                Spacer(Modifier.size(KeySize))
            }
            PadKey(enabled = enabled, onClick = { onDigit('0') }) {
                Text("0", style = JkType.display.copy(fontFeatureSettings = "tnum"), color = if (enabled) colors.textPrimary else colors.textTertiary)
            }
            PadKey(enabled = true, glass = false, description = "删除一位", longLabel = "清空", onLongClick = onClear, onClick = onBackspace) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/**
 * 一颗玻璃圆键：卡片同款玻璃（外阴影、半透明填充、内光泽、左上高光），按下时换成更亮的选中玻璃并轻微缩小。
 * [glass] 为 false 的功能键平时只露出图标，按下时才浮出一层玻璃。
 */
@Composable
private fun PadKey(
    enabled: Boolean,
    onClick: () -> Unit,
    glass: Boolean = true,
    description: String? = null,
    longLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val press = rememberJkPress(0.92f)
    val pressed by press.interaction.collectIsPressedAsState()
    val surfaces = Jk.surfaces
    val surface = when {
        pressed && enabled -> surfaces.chipSelected
        glass -> surfaces.chip
        else -> null
    }
    Box(
        modifier = Modifier
            .size(KeySize)
            .pressScale(press)
            .then(if (surface != null) Modifier.jkSurface(surface, CircleShape) else Modifier.clip(CircleShape))
            .combinedClickable(
                interactionSource = press.interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = longLabel,
                onLongClick = onLongClick?.let { action ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        action()
                    }
                },
            ) {
                // 键盘触感，跟随系统“触摸反馈”设置
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
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

/**
 * 锁屏主体（无状态）。1.4.0-alpha03 按液态玻璃重做：
 *  - 顶部是 B 款品牌徽记（[BrandEmblem]：桌面图标 + 薄荷光晕与小叶）、宋体字标与一句提示；
 *  - 状态条（冷却倒计时 / 输错提示）是玻璃提示条，没有内容时也占着同样的高度，界面不上下跳；
 *  - PIN：玻璃胶囊里的圆点 + 72dp 玻璃圆键的数字键盘（左下生物识别、右下删除，长按清空）+ 同宽的“解锁”；
 *  - 主密码：一张玻璃卡片里放输入框、“解锁”与生物识别；
 *  - 底部：切换 PIN / 主密码、忘记主密码，以及“连续输错将清空”的提醒。
 */
@Composable
fun LockContent(
    state: LockUiState,
    shake: Float = 0f,
    onDigit: (Char) -> Unit = {},
    onBackspace: () -> Unit = {},
    onClear: () -> Unit = {},
    onSecretChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
    onBiometric: () -> Unit = {},
    onToggleMode: () -> Unit = {},
    onForgot: () -> Unit = {},
) {
    val colors = Jk.colors
    val waitSeconds = state.waitSeconds
    val usePin = state.usePin
    val reduceMotion = rememberReduceMotion()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.x4))
        BrandEmblem(markSize = 76.dp)
        Spacer(Modifier.height(Space.x05))
        Text("金匮", style = JkType.brand, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.x05))
        Text(
            text = when {
                waitSeconds > 0 -> "请等待 $waitSeconds 秒后再试"
                state.busy -> "正在解锁…"
                usePin -> "输入 PIN 解锁"
                else -> "输入主密码解锁"
            },
            style = JkType.body,
            color = colors.textSecondary,
        )

        // 状态条：冷却倒计时 / 错误提示。没有内容时也占着同样的高度，界面不上下跳。
        val error = state.error
        Spacer(Modifier.height(Space.x2))
        Box(Modifier.heightIn(min = 44.dp).widthIn(max = 360.dp), contentAlignment = Alignment.Center) {
            when {
                waitSeconds > 0 -> JkNotice(
                    text = "输错次数较多，请等待 $waitSeconds 秒",
                    icon = Icons.Outlined.Timer,
                    tone = NoticeTone.Info,
                    shape = Radius.pill,
                )
                error != null -> JkNotice(
                    text = error,
                    icon = Icons.Outlined.ErrorOutline,
                    tone = NoticeTone.Danger,
                    shape = Radius.pill,
                )
            }
        }
        Spacer(Modifier.height(Space.x2))

        if (usePin) {
            Box(Modifier.graphicsLayer { translationX = shake }) {
                PinDots(count = state.secretLength, error = state.error != null, reduceMotion = reduceMotion)
            }
            Spacer(Modifier.height(Space.x3))
            PinPad(
                enabled = waitSeconds == 0L && !state.busy,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onClear = onClear,
                side = if (state.biometricReady) Icons.Outlined.Fingerprint else null,
                onSide = onBiometric,
            )
            Spacer(Modifier.height(Space.x3))
            JkButton(
                text = "解锁",
                onClick = onSubmit,
                enabled = state.secretLength >= VaultManager.MIN_PIN_LENGTH && waitSeconds == 0L,
                loading = state.busy,
                modifier = Modifier.width(PadWidth),
            )
        } else {
            JkCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .graphicsLayer { translationX = shake },
                padding = PaddingValues(Space.x2_5),
            ) {
                JkTextField(
                    value = state.secret,
                    onValueChange = onSecretChange,
                    label = "主密码",
                    placeholder = "输入主密码",
                    secret = true,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onImeAction = onSubmit,
                    leading = Icons.Outlined.Key,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.x2))
                JkButton(
                    text = "解锁",
                    onClick = onSubmit,
                    enabled = state.secret.isNotEmpty() && waitSeconds == 0L,
                    loading = state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.biometricReady) {
                    Spacer(Modifier.height(Space.x1))
                    JkButton(
                        text = "使用指纹或面容",
                        icon = Icons.Outlined.Fingerprint,
                        style = ButtonStyle.Secondary,
                        onClick = onBiometric,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalAlignment = Alignment.CenterVertically) {
            if (state.pinEnabled) {
                JkButton(
                    text = if (usePin) "改用主密码" else "改用 PIN",
                    style = ButtonStyle.Ghost,
                    compact = true,
                    onClick = onToggleMode,
                )
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(colors.textTertiary.copy(alpha = 0.5f)),
                )
            }
            JkButton("忘记主密码？", onForgot, style = ButtonStyle.Ghost, compact = true)
        }

        if (state.wipeOnFailure) {
            Spacer(Modifier.height(Space.x1_5))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Space.x05))
                Text(
                    text = "连续输错 ${VaultManager.MAX_ATTEMPTS} 次将清空全部数据",
                    style = JkType.caption,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(Space.x4))
    }
}
