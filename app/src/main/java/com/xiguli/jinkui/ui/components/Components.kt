package com.xiguli.jinkui.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.jkSurface
import com.xiguli.jinkui.ui.theme.specularBrush
import com.xiguli.jinkui.ui.theme.windowGlass
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space

/** 输入框内容变化时调用，用来刷新“无操作自动锁定”的计时（软键盘输入不会触发 Activity 的交互回调）。 */
val LocalUserActivity = compositionLocalOf<() -> Unit> { {} }

/** 所有对话框 / 面板窗口都强制带上防截屏标记。 */
val SecureDialog = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)

// ── 页面骨架 ──────────────────────────────────────────────────────

@Composable
fun JkTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            JkIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
        } else {
            Spacer(Modifier.width(Space.x1_5))
        }
        Text(
            text = title,
            style = JkType.heading,
            color = Jk.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.x05),
        )
        actions()
    }
}

@Composable
fun JkIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Jk.colors.textSecondary,
    enabled: Boolean = true,
) {
    // 48dp 触控区（无障碍最小值）；图标仍是 22dp
    Box(
        modifier = modifier
            .size(com.xiguli.jinkui.ui.theme.JkSize.touch)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
    }
}

/**
 * 分组容器：白底（深色为抬高一档的炭灰）、20dp 圆角，不画描边也不用投影——
 * 和页面底色本身就分得开，少一圈线，页面更安静。
 */
@Composable
fun JkCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Space.x2),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .jkSurface(Jk.surfaces.card, Radius.card)
            .padding(padding),
        content = content,
    )
}

@Composable
fun JkDivider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(Jk.colors.border),
    )
}

@Composable
fun JkSectionLabel(text: String, modifier: Modifier = Modifier) {
    // 与详情页、编辑页同一种眉题；读屏当作小节标题
    Text(
        text = text,
        style = JkType.overline,
        color = Jk.colors.textTertiary,
        modifier = modifier
            .padding(start = Space.x05, top = Space.x3, bottom = Space.x1)
            .semantics { heading() },
    )
}

// ── 按钮 ──────────────────────────────────────────────────────────

enum class ButtonStyle { Primary, Secondary, Danger, Ghost }

@Composable
fun JkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val colors = Jk.colors
    val background = when (style) {
        ButtonStyle.Primary -> colors.primary
        ButtonStyle.Danger -> colors.dangerSoft
        ButtonStyle.Secondary -> Jk.surfaces.chipSelected.fill
        ButtonStyle.Ghost -> Color.Transparent
    }
    val glassEdge = Jk.surfaces.chip
    val foreground = when (style) {
        ButtonStyle.Primary -> colors.onPrimary
        ButtonStyle.Danger -> colors.danger
        ButtonStyle.Secondary -> colors.accent
        ButtonStyle.Ghost -> colors.accent
    }
    val active = enabled && !loading
    // 按压反馈：按下时轻微缩小，松手回弹。
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed && active) 0.97f else 1f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f), label = "press")
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = if (compact) 36.dp else 48.dp)
            .clip(Radius.pill)
            // 不可用的主按钮退成浅灰底 + 三级文字，不再是一大块深灰
            .background(if (active || style != ButtonStyle.Primary) background else colors.surfaceAlt)
            .then(
                if (style == ButtonStyle.Ghost) Modifier
                else Modifier.border(1.dp, specularBrush(glassEdge.highlightColor, if (style == ButtonStyle.Primary) glassEdge.highlight * 0.6f else glassEdge.highlight), Radius.pill),
            )
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = active, onClick = onClick)
            .padding(horizontal = if (compact) Space.x1_5 else Space.x2_5),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = foreground,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(Space.x1))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.x1))
        }
        Text(
            text = text,
            style = if (compact) JkType.small else JkType.bodyStrong,
            color = if (active) foreground else colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── 输入 ──────────────────────────────────────────────────────────

@Composable
fun JkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    textStyle: TextStyle = JkType.body,
    error: String? = null,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = Jk.colors
    val touch = LocalUserActivity.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by remember { mutableStateOf(false) }
    // 聚焦时描边加深一级，是这套界面里唯一的“焦点环”。
    val control = Jk.surfaces.control
    val outline: androidx.compose.ui.graphics.Brush = when {
        error != null -> SolidColor(colors.danger)
        focused -> SolidColor(colors.accent)
        else -> specularBrush(control.highlightColor, control.highlight)
    }
    Column(modifier) {
        if (label != null) {
            Text(
                text = label,
                style = JkType.small,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = Space.x05, bottom = Space.x1),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = {
                touch()
                onValueChange(it)
            },
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            interactionSource = interaction,
            textStyle = textStyle.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret && keyboardType == KeyboardType.Text) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(Radius.control)
                        .background(colors.surface)
                        .border(if (focused || error != null) 1.5.dp else 1.dp, outline, Radius.control)
                        .padding(start = Space.x2, end = Space.x05),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leading != null) {
                        Icon(leading, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.x1_5))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = Space.x1_5),
                    ) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = textStyle, color = colors.textTertiary, maxLines = 1)
                        }
                        inner()
                    }
                    if (secret) {
                        JkIconButton(
                            icon = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            description = if (revealed) "隐藏" else "显示",
                            onClick = { revealed = !revealed },
                        )
                    }
                    if (trailing != null) trailing()
                    if (!secret && trailing == null) Spacer(Modifier.width(Space.x1_5))
                }
            },
        )
        if (error != null) {
            Text(
                text = error,
                style = JkType.caption,
                color = colors.danger,
                modifier = Modifier.padding(start = Space.x05, top = Space.x05),
            )
        }
    }
}

/** 没有外框的输入：放在自带描边的容器里使用（例如编辑页的字段块）。 */
@Composable
fun JkBareField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    textStyle: TextStyle = JkType.body,
    textColor: Color = Jk.colors.textPrimary,
    hidden: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = Jk.colors
    val touch = LocalUserActivity.current
    BasicTextField(
        value = value,
        onValueChange = {
            touch()
            onValueChange(it)
        },
        modifier = modifier,
        singleLine = true,
        textStyle = textStyle.copy(color = textColor),
        cursorBrush = SolidColor(colors.accent),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle, color = colors.textTertiary, maxLines = 1)
                }
                inner()
            }
        },
    )
}

// ── 列表行与开关 ──────────────────────────────────────────────────

@Composable
fun JkRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Jk.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.x2, vertical = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) colors.danger else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Space.x2))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = JkType.body, color = if (danger) colors.danger else colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = JkType.small, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (value != null) {
            Text(value, style = JkType.small, color = colors.textSecondary, modifier = Modifier.padding(start = Space.x1))
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.x1))
            trailing()
        } else if (onClick != null && !danger) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier
                    .padding(start = Space.x05)
                    .size(18.dp),
            )
        }
    }
}

@Composable
fun JkSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = Jk.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.primary,
            checkedBorderColor = colors.primary,
            uncheckedThumbColor = colors.textTertiary,
            uncheckedTrackColor = colors.surfaceAlt,
            uncheckedBorderColor = colors.border,
        ),
    )
}

/**
 * 小标签 / 筛选项。视觉上仍是 32dp 高，但布局与触控区补足到 48dp（minimumInteractiveComponentSize），
 * 相邻两枚之间不会抢点；选中态用 semantics 告诉读屏。
 */
@Composable
fun JkChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    // 未选中是浅灰底、无描边；选中换成品牌浅底 + 品牌色字，比整块墨黑更安静
    // 玻璃胶囊：未选中是浅磨砂，选中是更亮、略微凸起的一颗，字换成品牌色
    val content by animateColorAsState(if (selected) colors.accent else colors.textSecondary, tween(160), label = "chipText")
    val press = rememberJkPress(0.97f)
    val view = androidx.compose.ui.platform.LocalView.current
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pressScale(press)
            .height(34.dp)
            .jkSurface(if (selected) Jk.surfaces.chipSelected else Jk.surfaces.chip, Radius.chip)
            .clickable(interactionSource = press.interaction, indication = LocalIndication.current) {
                if (!selected) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(horizontal = Space.x1_5),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (selected) JkType.small.copy(fontWeight = FontWeight.Medium) else JkType.small,
            color = content,
            maxLines = 1,
        )
    }
}

/**
 * 等宽分段选择（主题、长度档位、月 / 年等互斥选项）：玻璃轨道里一颗亮玻璃“药丸”，
 * 切换时用弹簧滑到选中的那一段。
 */
@Composable
fun <T> JkSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val position by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.78f, stiffness = 420f), label = "segment")
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .jkSurface(Jk.surfaces.control, Radius.pill)
            .padding(Space.x05),
    ) {
        val gap = Space.x05
        val count = options.size.coerceAtLeast(1)
        val segment = (maxWidth - gap * (count - 1)) / count
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(segment)
                    .graphicsLayer { translationX = ((segment + gap) * position).toPx() }
                    .jkSurface(Jk.surfaces.chipSelected, Radius.pill),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            for ((value, label) in options) {
                val active = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .clip(Radius.pill)
                        .selectable(selected = active, role = androidx.compose.ui.semantics.Role.Tab, onClick = { onSelect(value) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = if (active) JkType.bodyStrong.copy(fontSize = JkType.small.fontSize) else JkType.small,
                        color = if (active) colors.accent else colors.textSecondary,
                    )
                }
            }
        }
    }
}

// ── 对话框与底部面板 ──────────────────────────────────────────────

@Composable
fun JkDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = "取消",
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    loading: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = { if (!loading) onDismiss() }, properties = SecureDialog) {
        // 居中的玻璃对话框：Android 12+ 由窗口模糊背后的页面，否则用不透明的磨砂填充
        val surface = windowGlass(Jk.surfaces.dialog, Jk.colors.surfaceSolid)
        Column(
            Modifier
                .fillMaxWidth()
                .jkSurface(surface, Radius.dialog)
                .padding(start = Space.x3, end = Space.x3, top = Space.x3, bottom = Space.x2),
        ) {
            Text(title, style = JkType.title, color = Jk.colors.textPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Space.x2))
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides Jk.colors.textSecondary,
                androidx.compose.material3.LocalTextStyle provides JkType.body,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.x1_5),
                    content = content,
                )
            }
            Spacer(Modifier.height(Space.x2_5))
            Row(
                Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(Space.x1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissText != null) {
                    JkButton(dismissText, onDismiss, style = ButtonStyle.Ghost, compact = true, enabled = !loading)
                }
                JkButton(
                    text = confirmText,
                    onClick = onConfirm,
                    style = if (danger) ButtonStyle.Danger else ButtonStyle.Primary,
                    enabled = confirmEnabled,
                    loading = loading,
                    compact = true,
                )
            }
        }
    }
}

/** 底部面板：全屏透明对话框窗口 + 贴底的圆角容器，点空白处关闭。 */
@Composable
fun JkSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 预测性返回（Android 14 起系统返回手势）：跟着手势把面板往下推、略微缩小；松手关闭，取消则弹回。
            var backProgress by remember { mutableStateOf(0f) }
            val latestDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
            androidx.activity.compose.PredictiveBackHandler { events ->
                try {
                    events.collect { backProgress = it.progress }
                    latestDismiss()
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    backProgress = 0f
                    throw e
                }
            }
            val shownBack by animateFloatAsState(backProgress, spring(dampingRatio = 0.9f, stiffness = 800f), label = "sheetBack")
            // 只做进入动画（从底部滑上来）；关闭时立刻移除，面板里的明文不多停留一帧。
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            // 从列表里点开一笔（容器变换）：面板从那一行的位置略微放大着长出来；否则从底部滑上来
            val origin = remember { com.xiguli.jinkui.ui.nav.TransitionOrigin.take() }
            val screenWidth = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
            val enter = if (origin != null) {
                androidx.compose.animation.scaleIn(
                    spring(dampingRatio = 0.86f, stiffness = 420f),
                    initialScale = 0.88f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin((origin.center.x / screenWidth.coerceAtLeast(1f)).coerceIn(0f, 1f), 0f),
                ) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 4 } + fadeIn(tween(160))
            } else {
                slideInVertically(spring(dampingRatio = 0.9f, stiffness = 520f)) { it / 2 } + fadeIn(tween(140))
            }
            AnimatedVisibility(
                visible = shown,
                enter = enter,
            ) {
            // 玻璃面板：Android 12+ 由窗口模糊背后的页面，否则用不透明的磨砂填充
            val sheetSurface = windowGlass(Jk.surfaces.sheet, Jk.colors.surfaceSolid)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = shownBack * size.height * 0.18f
                        val k = 1f - shownBack * 0.06f
                        scaleX = k
                        scaleY = k
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .jkSurface(sheetSurface, Radius.sheet)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Space.x2_5)
                    .padding(top = Space.x1_5, bottom = Space.x2_5),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Jk.colors.textTertiary.copy(alpha = 0.35f)),
                )
                if (title != null) {
                    Text(
                        text = title,
                        style = JkType.title,
                        color = Jk.colors.textPrimary,
                        modifier = Modifier.padding(top = Space.x2, bottom = Space.x1_5),
                    )
                } else {
                    Spacer(Modifier.height(Space.x1_5))
                }
                content()
            }
            }
        }
    }
}

/** 页面中央的空状态 / 说明。 */
@Composable
fun JkEmpty(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = { EmptyIllustration() },
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x4, vertical = Space.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        illustration()
        Spacer(Modifier.height(Space.x2_5))
        Text(title, style = JkType.heading, color = Jk.colors.textPrimary)
        Spacer(Modifier.height(Space.x1))
        Text(message, style = JkType.small, color = Jk.colors.textTertiary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Space.x2_5))
            action()
        }
    }
}

@Composable
fun BoxScope.JkLoading() {
    CircularProgressIndicator(
        modifier = Modifier
            .align(Alignment.Center)
            .size(28.dp),
        color = Jk.colors.textSecondary,
        strokeWidth = 2.dp,
    )
}

/** 一枚小状态标签（例如“离线”“已加密”）。 */
@Composable
fun JkBadge(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    Row(
        modifier = modifier
            .clip(Radius.chip)
            .background(colors.accentSoft)
            .padding(horizontal = Space.x1, vertical = Space.x05),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(Space.x05))
        }
        Text(text, style = JkType.caption, color = colors.accent, maxLines = 1)
    }
}

/** 空状态用的小插画：一叠卡片，最上面一张带锁孔。只用描边与品牌色，不用投影。 */
@Composable
fun EmptyIllustration(modifier: Modifier = Modifier) {
    val line = Jk.colors.border
    val fill = Jk.colors.surface
    val accent = Jk.colors.accent
    val soft = Jk.colors.surfaceAlt
    Canvas(modifier.size(width = 132.dp, height = 96.dp)) {
        val u = 1.dp.toPx()
        val w = size.width
        val stroke = Stroke(width = 1.4f * u)
        // 由远及近三张牌：越靠后越窄、越靠上
        for (layer in 2 downTo 0) {
            val inset = layer * 11f * u
            val top = (2 - layer) * 12f * u
            val cardWidth = w - inset * 2f
            val cardHeight = 68f * u
            val corner = CornerRadius(14f * u, 14f * u)
            drawRoundRect(color = if (layer == 0) fill else soft, topLeft = Offset(inset, top), size = Size(cardWidth, cardHeight), cornerRadius = corner)
            drawRoundRect(color = line, topLeft = Offset(inset, top), size = Size(cardWidth, cardHeight), cornerRadius = corner, style = stroke)
        }
        val top = 24f * u
        // 最上面一张：两行“文字”与一个锁孔
        drawLine(color = line, start = Offset(16f * u, top + 44f * u), end = Offset(62f * u, top + 44f * u), strokeWidth = 3f * u, cap = StrokeCap.Round)
        drawLine(color = line, start = Offset(16f * u, top + 54f * u), end = Offset(44f * u, top + 54f * u), strokeWidth = 3f * u, cap = StrokeCap.Round)
        drawCircle(color = accent, radius = 5f * u, center = Offset(w - 26f * u, top + 20f * u))
        drawLine(color = accent, start = Offset(w - 26f * u, top + 22f * u), end = Offset(w - 26f * u, top + 32f * u), strokeWidth = 3f * u, cap = StrokeCap.Round)
    }
}

/** 多选用的方形勾选框。 */
@Composable
fun JkCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(shape)
            .background(if (checked) colors.primary else colors.surface)
            .border(1.dp, if (checked) colors.primary else colors.textTertiary, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(15.dp))
    }
}

/** 搜索 / 筛选无结果的插画：一张空白卡位，上面压着一只放大镜。只用描边与品牌色。 */
@Composable
fun SearchEmptyIllustration(modifier: Modifier = Modifier) {
    val line = Jk.colors.border
    val fill = Jk.colors.surface
    val soft = Jk.colors.surfaceAlt
    val accent = Jk.colors.accent
    val muted = Jk.colors.textTertiary
    Canvas(modifier.size(width = 132.dp, height = 96.dp)) {
        val u = size.width / 132f // 按实际尺寸等比缩放
        val stroke = Stroke(width = 1.4f * u)
        val corner = CornerRadius(14f * u, 14f * u)
        // 卡位：浅底 + 虚化的两行“文字”
        val cardTop = 12f * u
        val cardSize = Size(100f * u, 64f * u)
        drawRoundRect(color = soft, topLeft = Offset(4f * u, cardTop), size = cardSize, cornerRadius = corner)
        drawRoundRect(color = line, topLeft = Offset(4f * u, cardTop), size = cardSize, cornerRadius = corner, style = stroke)
        drawLine(color = line, start = Offset(18f * u, cardTop + 40f * u), end = Offset(58f * u, cardTop + 40f * u), strokeWidth = 3f * u, cap = StrokeCap.Round)
        drawLine(color = line, start = Offset(18f * u, cardTop + 50f * u), end = Offset(40f * u, cardTop + 50f * u), strokeWidth = 3f * u, cap = StrokeCap.Round)
        // 放大镜：白底镜片、品牌色镜框与手柄，镜片里一道问号似的短弧
        val center = Offset(92f * u, 50f * u)
        val r = 20f * u
        drawCircle(color = fill, radius = r, center = center)
        drawCircle(color = accent, radius = r, center = center, style = Stroke(width = 3f * u))
        drawLine(
            color = accent,
            start = Offset(center.x + r * 0.72f, center.y + r * 0.72f),
            end = Offset(center.x + r * 1.45f, center.y + r * 1.45f),
            strokeWidth = 5f * u,
            cap = StrokeCap.Round,
        )
        drawLine(color = muted, start = Offset(center.x - 8f * u, center.y), end = Offset(center.x + 8f * u, center.y), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
    }
}
