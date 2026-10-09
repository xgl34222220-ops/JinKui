package com.xiguli.jinkui.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.core.qr.MigrationBatch
import com.xiguli.jinkui.core.qr.MigrationEntry
import com.xiguli.jinkui.data.model.OtpImport
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCheck
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * 身份验证器批量导出码的预览：列出每个账号（只显示发行方与账号，不显示密钥或验证码），
 * 默认勾选可用且金匮里还没有的；“添加”为每个勾选的账号新建一张登录密码卡片。
 */
@Composable
fun OtpBatchSheet(batch: MigrationBatch, onDismiss: () -> Unit, onAdded: (Int) -> Unit = {}) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val existing = remember(batch) { OtpImport.existingSecrets(container.repository.cards.value) }
    val duplicate = remember(batch) { batch.entries.map { OtpImport.isDuplicate(it, existing) } }
    val selected = remember(batch) {
        mutableStateListOf<Boolean>().apply { batch.entries.forEachIndexed { i, e -> add(e.usable && !duplicate[i]) } }
    }
    var saving by remember { mutableStateOf(false) }
    val count = selected.count { it }

    JkSheet(onDismiss = { if (!saving) onDismiss() }, title = "导入两步验证码") {
        OtpBatchContent(
            batch = batch,
            duplicate = duplicate,
            selected = selected,
            onToggle = { i -> if (batch.entries[i].usable) selected[i] = !selected[i] },
        )
        Spacer(Modifier.height(Space.x2))
        JkButton(
            text = if (count == 0) "请选择要添加的账号" else "添加 $count 个账号",
            enabled = count > 0,
            loading = saving,
            onClick = {
                saving = true
                scope.launch {
                    var added = 0
                    try {
                        batch.entries.forEachIndexed { i, entry ->
                            if (!selected[i]) return@forEachIndexed
                            val card = OtpImport.cardFor(entry) ?: return@forEachIndexed
                            container.repository.save(card)
                            added++
                        }
                        container.messages.show("已添加 $added 张登录密码卡片")
                        onAdded(added)
                        onDismiss()
                    } catch (e: Exception) {
                        container.messages.show("添加中断：已添加 $added 个，${e.message ?: "保存失败"}")
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        JkButton("取消", onClick = onDismiss, style = ButtonStyle.Ghost, enabled = !saving, modifier = Modifier.fillMaxWidth())
    }
}

/** 预览列表本体（无状态，界面测试直接渲染它）。 */
@Composable
fun OtpBatchContent(
    batch: MigrationBatch,
    duplicate: List<Boolean>,
    selected: List<Boolean>,
    onToggle: (Int) -> Unit,
) {
    val colors = Jk.colors
    val summary = buildString {
        append("识别到 ${batch.entries.size} 个账号")
        if (batch.usableCount < batch.entries.size) append("，其中 ${batch.entries.size - batch.usableCount} 个无法导入")
        if (batch.batchSize > 1) append("。这是第 ${batch.batchIndex + 1} / ${batch.batchSize} 张导出码，其余几张请分别导入")
        append("。")
    }
    Text(summary, style = JkType.small, color = colors.textSecondary)
    Text(
        "密钥只在本机解析并加密保存；这里不显示密钥与验证码。",
        style = JkType.caption,
        color = colors.textTertiary,
        modifier = Modifier.padding(top = Space.x05, bottom = Space.x1_5),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .clip(Radius.field)
            .background(colors.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        batch.entries.forEachIndexed { i, entry ->
            if (i > 0) JkDivider(inset = Space.x2 + JkSize.chip + Space.x1_5)
            OtpBatchRow(entry, duplicate.getOrElse(i) { false }, selected.getOrElse(i) { false }) { onToggle(i) }
        }
    }
}

@Composable
private fun OtpBatchRow(entry: MigrationEntry, duplicate: Boolean, checked: Boolean, onToggle: () -> Unit) {
    val colors = Jk.colors
    val detail = when {
        entry.problem != null -> entry.problem
        duplicate -> "金匮里已有同一个密钥" + if (entry.account.isNotBlank()) "　${entry.account}" else ""
        else -> listOfNotNull(
            entry.account.takeIf { it.isNotBlank() && entry.issuer.isNotBlank() },
            entry.config?.let { "${it.digits} 位 · ${it.algorithm.key}" },
        ).joinToString("　")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = entry.usable, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = Space.x2, vertical = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(JkSize.chip)
                .clip(Radius.tile)
                .background(if (entry.usable) colors.accentSoft else colors.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = null, tint = if (entry.usable) colors.accent else colors.textTertiary, modifier = Modifier.size(JkSize.icon))
        }
        Spacer(Modifier.width(Space.x1_5))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = JkType.bodyStrong,
                color = if (entry.usable) colors.textPrimary else colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = JkType.caption,
                    color = if (entry.problem != null) colors.danger else colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Space.x1))
        if (entry.usable) JkCheck(checked)
    }
}
