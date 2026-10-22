package com.xiguli.jinkui.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.TrashMath
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BotanicalCard
import com.xiguli.jinkui.ui.components.BotanicalLayout
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.launch

// ── 卡包回收站（1.4.0-alpha04）────────────────────────────────────
// 删掉的卡片先放这里 30 天：仍在加密数据库里，可以还原或彻底删除；满 30 天后下次解锁自动彻底删除。
// 列表只显示标题、类型与“几天后删除”，不显示任何字段内容。

@Immutable
data class TrashRowUi(val card: Card, val title: String, val subtitle: String)

fun buildTrashUi(trash: List<Card>, now: Long): List<TrashRowUi> = TrashMath.sorted(trash).map { card ->
    val left = TrashMath.daysLeft(card, now)
    TrashRowUi(
        card,
        card.title.ifBlank { "未命名" },
        "${card.type.label} · " + if (left <= 0) "下次解锁时彻底删除" else "$left 天后彻底删除",
    )
}

class TrashActions(
    val onBack: () -> Unit = {},
    val onRestore: (Card) -> Unit = {},
    val onPurge: (Card) -> Unit = {},
    val onPurgeAll: () -> Unit = {},
)

@Composable
fun CardTrashScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val trash by container.repository.trash.collectAsStateWithLifecycle()
    val rows = remember(trash) { buildTrashUi(trash, System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    fun run(done: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { container.messages.show(done) }
                .onFailure { container.messages.show("操作失败：${it.message.orEmpty()}") }
        }
    }
    CardTrashContent(
        rows,
        TrashActions(
            onBack = onBack,
            onRestore = { card -> run("已还原「${card.title.ifBlank { "未命名" }}」") { container.repository.restoreFromTrash(card.id) } },
            onPurge = { card -> run("已彻底删除") { container.repository.purge(listOf(card.id)) } },
            onPurgeAll = { run("已清空回收站") { container.repository.purge() } },
        ),
    )
}

/** 回收站（无状态）：说明卡 → 列表（还原 / 彻底删除）→ 清空回收站。彻底删除都要再确认一次。 */
@Composable
fun CardTrashContent(rows: List<TrashRowUi>, actions: TrashActions) {
    val colors = Jk.colors
    var confirm by remember { mutableStateOf<Card?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar("回收站", onBack = actions.onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2)
                .padding(bottom = Space.x4),
        ) {
            BotanicalCard(Modifier.fillMaxWidth(), layout = BotanicalLayout.SPRIG) {
                Column(Modifier.padding(Space.x2_5)) {
                    Text(
                        if (rows.isEmpty()) "回收站是空的" else "${rows.size} 张卡片",
                        style = JkType.title.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(Space.x05))
                    Text(
                        "删除的卡片在这里保留 ${TrashMath.RETENTION_DAYS} 天，仍然加密保存，可以随时还原；满 ${TrashMath.RETENTION_DAYS} 天后自动彻底删除。回收站里的卡片不出现在卡包、搜索、体检和备份里。",
                        style = JkType.small,
                        color = colors.textSecondary,
                    )
                }
            }
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(Space.x2))
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                    rows.forEachIndexed { i, row ->
                        if (i > 0) JkDivider(inset = Space.x2)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .padding(start = Space.x2, end = Space.x05, top = Space.x1, bottom = Space.x1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title, style = JkType.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(row.subtitle, style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            JkButton("还原", { actions.onRestore(row.card) }, style = ButtonStyle.Secondary, compact = true, icon = Icons.Outlined.RestoreFromTrash)
                            Spacer(Modifier.width(Space.x05))
                            JkIconButton(Icons.Outlined.DeleteForever, "彻底删除${row.title}", { confirm = row.card }, tint = colors.danger)
                        }
                    }
                }
                Spacer(Modifier.height(Space.x2))
                JkButton("清空回收站", { confirmAll = true }, style = ButtonStyle.Danger, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    confirm?.let { card ->
        JkDialog(
            title = "彻底删除「${card.title.ifBlank { "未命名" }}」？",
            onDismiss = { confirm = null },
            confirmText = "彻底删除",
            danger = true,
            onConfirm = {
                confirm = null
                actions.onPurge(card)
            },
        ) {
            Text("卡片、附件照片和历史密码会一起删除，无法恢复。", style = JkType.body, color = colors.textSecondary)
        }
    }
    if (confirmAll) {
        JkDialog(
            title = "清空回收站？",
            onDismiss = { confirmAll = false },
            confirmText = "全部彻底删除",
            danger = true,
            onConfirm = {
                confirmAll = false
                actions.onPurgeAll()
            },
        ) {
            Text("这 ${rows.size} 张卡片连同附件照片和历史密码会一起删除，无法恢复。", style = JkType.body, color = colors.textSecondary)
        }
    }
}
