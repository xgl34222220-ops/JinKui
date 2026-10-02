package com.xiguli.jinkui.ui.components

import androidx.compose.foundation.BorderStroke
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
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** 白底、1dp 极淡描边、24dp 圆角的容器；不使用投影。 */
@Composable
fun JkCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Space.x2),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(Radius.card)
            .background(Jk.colors.surface)
            .border(1.dp, Jk.colors.border, Radius.card)
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
    Text(
        text = text,
        style = JkType.small,
        color = Jk.colors.textTertiary,
        modifier = modifier.padding(start = Space.x05, top = Space.x3, bottom = Space.x1),
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
        ButtonStyle.Secondary -> colors.surface
        ButtonStyle.Ghost -> Color.Transparent
    }
    val foreground = when (style) {
        ButtonStyle.Primary -> colors.onPrimary
        ButtonStyle.Danger -> colors.danger
        ButtonStyle.Secondary -> colors.textPrimary
        ButtonStyle.Ghost -> colors.textSecondary
    }
    val active = enabled && !loading
    Row(
        modifier = modifier
            .heightIn(min = if (compact) 36.dp else 48.dp)
            .clip(Radius.control)
            .background(if (active || style != ButtonStyle.Primary) background else colors.textTertiary)
            .then(
                if (style == ButtonStyle.Secondary) Modifier.border(1.dp, colors.border, Radius.control) else Modifier,
            )
            .clickable(enabled = active, onClick = onClick)
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
            color = if (active || style == ButtonStyle.Primary) foreground else colors.textTertiary,
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
    val outline = when {
        error != null -> colors.danger
        focused -> colors.textSecondary
        else -> colors.border
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
                        .border(1.dp, outline, Radius.control)
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

@Composable
fun JkChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(Radius.chip)
            .background(if (selected) colors.primary else colors.surface)
            .border(BorderStroke(1.dp, if (selected) colors.primary else colors.border), Radius.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x1_5),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = JkType.small,
            color = if (selected) colors.onPrimary else colors.textSecondary,
            maxLines = 1,
        )
    }
}

/** 等宽分段选择（主题、长度档位等互斥选项）。 */
@Composable
fun <T> JkSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.control)
            .background(colors.surfaceAlt)
            .padding(Space.x05),
        horizontalArrangement = Arrangement.spacedBy(Space.x05),
    ) {
        for ((value, label) in options) {
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(Radius.chip)
                    .background(if (active) colors.surface else Color.Transparent)
                    .then(if (active) Modifier.border(1.dp, colors.border, Radius.chip) else Modifier)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = if (active) JkType.bodyStrong.copy(fontSize = JkType.small.fontSize) else JkType.small,
                    color = if (active) colors.textPrimary else colors.textSecondary,
                )
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
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        properties = SecureDialog,
        containerColor = Jk.colors.surface,
        titleContentColor = Jk.colors.textPrimary,
        textContentColor = Jk.colors.textSecondary,
        shape = Radius.card,
        tonalElevation = 0.dp,
        title = { Text(title, style = JkType.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.x1_5),
                content = content,
            )
        },
        confirmButton = {
            JkButton(
                text = confirmText,
                onClick = onConfirm,
                style = if (danger) ButtonStyle.Danger else ButtonStyle.Primary,
                enabled = confirmEnabled,
                loading = loading,
                compact = true,
            )
        },
        dismissButton = if (dismissText != null) {
            { JkButton(dismissText, onDismiss, style = ButtonStyle.Ghost, compact = true, enabled = !loading) }
        } else {
            null
        },
    )
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radius.sheet)
                    .background(Jk.colors.surface)
                    .border(1.dp, Jk.colors.border, Radius.sheet)
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
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Jk.colors.border),
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

/** 页面中央的空状态 / 说明。 */
@Composable
fun JkEmpty(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x4, vertical = Space.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = JkType.heading, color = Jk.colors.textPrimary)
        Spacer(Modifier.height(Space.x1))
        Text(message, style = JkType.small, color = Jk.colors.textTertiary)
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
