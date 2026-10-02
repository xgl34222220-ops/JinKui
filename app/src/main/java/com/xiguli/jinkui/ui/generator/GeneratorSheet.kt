package com.xiguli.jinkui.ui.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.core.crypto.GeneratorOptions
import com.xiguli.jinkui.core.crypto.PasswordGenerator
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlin.math.roundToInt

/**
 * 密码生成器。[onUse] 不为空时显示“使用此密码”，把结果填回正在编辑的字段。
 */
@Composable
fun GeneratorSheet(onDismiss: () -> Unit, onUse: ((String) -> Unit)? = null) {
    val container = LocalContainer.current
    val colors = Jk.colors
    var options by remember { mutableStateOf(GeneratorOptions()) }
    var round by remember { mutableIntStateOf(0) }
    val password = remember(options, round) {
        if (options.hasAnySet) PasswordGenerator.generate(options) else ""
    }
    val bits = PasswordGenerator.entropyBits(options).roundToInt()

    JkSheet(onDismiss = onDismiss, title = "密码生成器") {
        Text(
            text = password.ifEmpty { "至少选择一种字符" },
            style = JkType.mono.copy(fontSize = JkType.heading.fontSize),
            color = if (password.isEmpty()) colors.textTertiary else colors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radius.control)
                .background(colors.surfaceAlt)
                .border(1.dp, colors.border, Radius.control)
                .padding(Space.x2),
        )
        Spacer(Modifier.height(Space.x1))
        Text(
            text = "约 $bits 位熵，${if (bits >= 100) "很强" else if (bits >= 70) "够用" else "偏弱"}",
            style = JkType.caption,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(Space.x2_5))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("长度", style = JkType.small, color = colors.textSecondary)
            Slider(
                value = options.length.toFloat(),
                onValueChange = { options = options.copy(length = it.roundToInt()) },
                valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.border,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Space.x1_5),
            )
            Text("${options.length}", style = JkType.bodyStrong, color = colors.textPrimary)
        }

        Spacer(Modifier.height(Space.x1))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            JkChip("A–Z", options.upper, { options = options.copy(upper = !options.upper) })
            JkChip("a–z", options.lower, { options = options.copy(lower = !options.lower) })
            JkChip("0–9", options.digits, { options = options.copy(digits = !options.digits) })
            JkChip("!@#", options.symbols, { options = options.copy(symbols = !options.symbols) })
        }
        Spacer(Modifier.height(Space.x1))
        JkChip(
            text = "避开易混字符（0/O、1/l/I）",
            selected = options.avoidAmbiguous,
            onClick = { options = options.copy(avoidAmbiguous = !options.avoidAmbiguous) },
        )

        Spacer(Modifier.height(Space.x3))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            JkButton(
                text = "换一个",
                icon = Icons.Outlined.Refresh,
                style = ButtonStyle.Secondary,
                onClick = { round++ },
                modifier = Modifier.weight(1f),
            )
            JkButton(
                text = "复制",
                icon = Icons.Outlined.ContentCopy,
                style = if (onUse == null) ButtonStyle.Primary else ButtonStyle.Secondary,
                enabled = password.isNotEmpty(),
                onClick = {
                    val seconds = container.clipboard.copy("密码", password)
                    container.messages.show("已复制，$seconds 秒后清空剪贴板")
                    if (onUse == null) onDismiss()
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (onUse != null) {
            Spacer(Modifier.height(Space.x1))
            JkButton(
                text = "使用此密码",
                enabled = password.isNotEmpty(),
                onClick = {
                    onUse(password)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
