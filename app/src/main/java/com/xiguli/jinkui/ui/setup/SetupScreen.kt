package com.xiguli.jinkui.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.core.crypto.PasswordGenerator
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BrandMark
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkTextField
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

@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val level = strengthLevel(password)
    val tint = when (level) {
        0 -> colors.danger
        1 -> colors.accent
        else -> colors.success
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (index in 0..2) {
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (password.isNotEmpty() && index <= level) tint else colors.border),
            )
            if (index < 2) Spacer(Modifier.width(Space.x05))
        }
        Spacer(Modifier.width(Space.x1_5))
        Text(
            text = if (password.isEmpty()) "强度" else listOf("偏弱", "中等", "很强")[level],
            style = JkType.caption,
            color = if (password.isEmpty()) colors.textTertiary else tint,
        )
    }
}

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
    val ready = password.length >= VaultManager.MIN_PASSWORD_LENGTH && confirm == password && acknowledged

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x3, vertical = Space.x4),
    ) {
        BrandMark()
        Spacer(Modifier.height(Space.x3))
        Text("金匮", style = JkType.brand, color = colors.textPrimary)
        Spacer(Modifier.height(Space.x05))
        Text("只存在这台手机里的安全卡包", style = JkType.body, color = colors.textSecondary)

        if (container.justWiped.value) {
            Spacer(Modifier.height(Space.x3))
            Text(
                text = "连续输错次数已达上限，本机数据已全部清空。如果你有加密备份，创建新的保险库后可以在设置里导入。",
                style = JkType.small,
                color = colors.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radius.control)
                    .background(colors.dangerSoft)
                    .padding(Space.x2),
            )
        }

        Spacer(Modifier.height(Space.x4))
        JkCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(Space.x2_5)) {
            Text("设置主密码", style = JkType.heading, color = colors.textPrimary)
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
                error = if (tooShort) "至少需要 ${VaultManager.MIN_PASSWORD_LENGTH} 位" else null,
            )
            StrengthMeter(password, Modifier.padding(top = Space.x1_5, start = Space.x05, end = Space.x05))
            Spacer(Modifier.height(Space.x2))
            JkTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = "再输入一次",
                secret = true,
                imeAction = ImeAction.Done,
                error = if (mismatch) "两次输入不一致" else null,
            )
            Spacer(Modifier.height(Space.x2))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radius.chip)
                    .clickable { acknowledged = !acknowledged }
                    .padding(vertical = Space.x1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (acknowledged) colors.primary else colors.surface)
                        .border(1.dp, if (acknowledged) colors.primary else colors.border, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (acknowledged) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(Space.x1_5))
                Text("我已了解：忘记主密码，数据将无法恢复", style = JkType.small, color = colors.textSecondary)
            }
            if (viewModel.error != null) {
                Spacer(Modifier.height(Space.x1))
                Text(viewModel.error.orEmpty(), style = JkType.small, color = colors.danger)
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

        Spacer(Modifier.height(Space.x3))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            for (line in listOf(
                "全程离线，不申请联网权限",
                "SQLCipher 整库加密，敏感字段再加一层 AES-256-GCM",
                "设备主密钥存放在系统 Keystore，无法导出",
            )) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Space.x1))
                    Text(line, style = JkType.caption, color = colors.textTertiary)
                }
            }
        }
    }
}
