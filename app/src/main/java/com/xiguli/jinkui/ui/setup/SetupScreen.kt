package com.xiguli.jinkui.ui.setup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.core.crypto.PasswordGenerator
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BrandEmblem
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkCheck
import com.xiguli.jinkui.ui.components.JkNotice
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.NoticeTone
import com.xiguli.jinkui.ui.components.rememberReduceMotion
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.launch

class SetupViewModel(private val container: AppContainer) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun create(password: String) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                container.justWiped.value = false
                container.showQuickUnlockIntro.value = true
                container.vault.create(password)
            } catch (e: Exception) {
                container.showQuickUnlockIntro.value = false
                error = "创建失败：${e.message ?: "设备密钥库不可用"}"
            } finally {
                busy = false
            }
        }
    }
}

/** 口令强度分级：0 弱 / 1 中 / 2 强。 */
fun strengthLevel(password: String): Int {
    val bits = PasswordGenerator.estimateBits(password)
    return when {
        bits < 45 -> 0
        bits < 70 -> 1
        else -> 2
    }
}

/** 口令强度条：三段，按强度点亮并渐变到对应颜色；读屏读作“密码强度：中等”。 */
@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val level = strengthLevel(password)
    val reduceMotion = rememberReduceMotion()
    val tint = when (level) {
        0 -> colors.danger
        1 -> colors.accent
        else -> colors.success
    }
    val label = if (password.isEmpty()) "强度" else listOf("偏弱", "中等", "很强")[level]
    Row(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = if (password.isEmpty()) "密码强度：未输入" else "密码强度：$label"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0..2) {
            val on = password.isNotEmpty() && index <= level
            val color by animateColorAsState(
                if (on) tint else colors.border,
                if (reduceMotion) snap() else tween(220),
                label = "strength",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
            if (index < 2) Spacer(Modifier.width(Space.x05))
        }
        Spacer(Modifier.width(Space.x1_5))
        Text(
            text = label,
            style = JkType.caption,
            color = if (password.isEmpty()) colors.textTertiary else tint,
        )
    }
}

/** 首次设置底部的一条安全说明：浅薄荷圆底里的图标 + 一行字。 */
@Composable
private fun SafetyLine(icon: ImageVector, text: String) {
    val colors = Jk.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(Space.x1_5))
        Text(text, style = JkType.small, color = colors.textSecondary)
    }
}

/**
 * 首次设置（1.4.0-alpha03 按液态玻璃重做）：居中的 B 款品牌徽记与字标 → 玻璃卡片里设置主密码
 * （强度条、两次一致提示、“我已了解”勾选、创建）→ 一张玻璃卡片列出三条安全说明。
 */
@Composable
fun SetupScreen() {
    val container = LocalContainer.current
    val viewModel = jkViewModel { SetupViewModel(container) }
    val colors = Jk.colors

    // 口令只放在 remember 里，不进入可保存状态，也就不会被写进系统的实例状态。
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }

    val tooShort = password.isNotEmpty() && password.length < VaultManager.MIN_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != password
    val matched = confirm.isNotEmpty() && confirm == password && !tooShort
    val ready = password.length >= VaultManager.MIN_PASSWORD_LENGTH && confirm == password && acknowledged

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x3, vertical = Space.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandEmblem(markSize = 72.dp)
        Text("金匮", style = JkType.brand, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.x05))
        Text("只存在这台手机里的安全卡包", style = JkType.body, color = colors.textSecondary, textAlign = TextAlign.Center)

        if (container.justWiped.value) {
            Spacer(Modifier.height(Space.x2_5))
            JkNotice(
                text = "连续输错次数已达上限，本机数据已全部清空。如果你有加密备份，创建新的保险库后可以在设置里导入。",
                icon = Icons.Outlined.WarningAmber,
                tone = NoticeTone.Danger,
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            )
        }

        Spacer(Modifier.height(Space.x3))
        JkCard(Modifier.fillMaxWidth().widthIn(max = 480.dp), padding = PaddingValues(Space.x2_5)) {
            Text("设置主密码", style = JkType.heading, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Space.x05))
            Text(
                "主密码是打开金匮的唯一钥匙。它不会上传到任何地方，也没有人能帮你找回。",
                style = JkType.small,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(Space.x2_5))
            JkTextField(
                value = password,
                onValueChange = { password = it },
                label = "主密码",
                placeholder = "至少 ${VaultManager.MIN_PASSWORD_LENGTH} 位，建议用一句只有你知道的话",
                secret = true,
                imeAction = ImeAction.Next,
                leading = Icons.Outlined.Key,
                error = if (tooShort) "至少需要 ${VaultManager.MIN_PASSWORD_LENGTH} 位" else null,
            )
            StrengthMeter(password, Modifier.padding(top = Space.x1_5, start = Space.x05, end = Space.x05))
            Spacer(Modifier.height(Space.x2))
            JkTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = "再输入一次",
                placeholder = "再输入一次主密码",
                secret = true,
                imeAction = ImeAction.Done,
                leading = Icons.Outlined.Key,
                error = if (mismatch) "两次输入不一致" else null,
            )
            if (matched) {
                Row(Modifier.padding(start = Space.x05, top = Space.x05), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Space.x05))
                    Text("两次输入一致", style = JkType.caption, color = colors.success)
                }
            }
            Spacer(Modifier.height(Space.x1_5))
            // 与多选同一枚勾选框；整行可点，读屏读作复选框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(Radius.control)
                    .toggleable(value = acknowledged, role = Role.Checkbox, onValueChange = { acknowledged = it })
                    .padding(horizontal = Space.x05, vertical = Space.x1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JkCheck(acknowledged)
                Spacer(Modifier.width(Space.x1_5))
                Text("我已了解：忘记主密码，数据将无法恢复", style = JkType.small, color = colors.textSecondary)
            }
            val error = viewModel.error
            if (error != null) {
                Spacer(Modifier.height(Space.x1))
                JkNotice(text = error, icon = Icons.Outlined.ErrorOutline, tone = NoticeTone.Danger, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Space.x2))
            JkButton(
                text = if (viewModel.busy) "正在生成密钥…" else "创建保险库",
                onClick = { viewModel.create(password) },
                enabled = ready,
                loading = viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Space.x2))
        JkCard(Modifier.fillMaxWidth().widthIn(max = 480.dp), padding = PaddingValues(horizontal = Space.x2_5, vertical = Space.x2)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                SafetyLine(Icons.Outlined.CloudOff, "全程离线，不申请联网权限")
                SafetyLine(Icons.Outlined.Shield, "SQLCipher 整库加密，敏感字段再加一层 AES-256-GCM")
                SafetyLine(Icons.Outlined.Key, "设备主密钥存放在系统 Keystore，无法导出")
            }
        }
    }
}
