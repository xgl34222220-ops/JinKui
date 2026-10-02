package com.xiguli.jinkui.ui.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.model.HealthIssue
import com.xiguli.jinkui.data.model.HealthKind
import com.xiguli.jinkui.data.model.HealthReport
import com.xiguli.jinkui.data.model.PasswordHealth
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.CardTile
import com.xiguli.jinkui.ui.components.EmptyIllustration
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSectionLabel
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HealthViewModel(container: AppContainer) : ViewModel() {
    val report: StateFlow<HealthReport> = container.repository.cards
        .map { PasswordHealth.analyze(it, LocalDate.now()) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PasswordHealth.analyze(container.repository.cards.value, LocalDate.now()),
        )
}

/** 安全体检：弱密码、重复密码、到期提醒与备份状态。全部在本机完成。 */
@Composable
fun HealthScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { HealthViewModel(container) }
    val report by viewModel.report.collectAsStateWithLifecycle()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val colors = Jk.colors

    val weak = report.of(HealthKind.WEAK)
    val reused = report.of(HealthKind.REUSED)
    val expired = report.of(HealthKind.EXPIRED)
    val expiring = report.of(HealthKind.EXPIRING)
    val noBackup = settings.lastBackupAt == 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        JkTopBar(title = "安全体检", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2_5)
                .padding(top = Space.x05, bottom = Space.x5),
        ) {
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.x2_5)) {
                if (report.isClean) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyIllustration()
                        Spacer(Modifier.height(Space.x2))
                        Text("一切妥当", style = JkType.title, color = colors.textPrimary)
                        Spacer(Modifier.height(Space.x05))
                        Text(
                            text = if (report.checkedPasswords == 0) {
                                "还没有可检查的密码。存入登录密码后，这里会提醒弱密码和重复使用的密码。"
                            } else {
                                "检查了 ${report.checkedPasswords} 个密码，没有发现弱密码、重复密码或即将到期的卡片。"
                            },
                            style = JkType.small,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Text("发现 ${report.issues.size} 处可以改进", style = JkType.heading, color = colors.textPrimary)
                    Spacer(Modifier.height(Space.x2))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                        Stat(weak.size, "弱密码", colors.danger, Modifier.weight(1f))
                        Stat(reused.size, "重复使用", colors.accent, Modifier.weight(1f))
                        Stat(expired.size + expiring.size, "到期提醒", colors.accent, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Space.x1_5))
                    Text(
                        "体检只在这台手机上进行，不会把任何密码发往别处。",
                        style = JkType.caption,
                        color = colors.textTertiary,
                    )
                }
            }

            if (noBackup) {
                Spacer(Modifier.height(Space.x1_5))
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                    JkRow(
                        title = "还没有加密备份",
                        subtitle = if (settings.wipeOnFailure) {
                            "输错自毁已开启：手机丢失或连续输错后，没有备份就无法恢复"
                        } else {
                            "手机丢失或损坏后，没有备份就无法恢复"
                        },
                        icon = Icons.Outlined.FileUpload,
                        onClick = onSettings,
                    )
                }
            }

            IssueSection("弱密码", "太短或太简单，建议在编辑页用密码生成器换一个", weak, colors.danger, onOpen)
            IssueSection("重复使用的密码", "一处泄露，其他几处也跟着不安全", reused, colors.textTertiary, onOpen)
            IssueSection("已过期", "证件或卡片的有效期已过", expired, colors.danger, onOpen)
            IssueSection("即将到期", "90 天内到期，记得提前办理", expiring, colors.accent, onOpen)
        }
    }
}

@Composable
private fun Stat(count: Int, label: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    Column(modifier) {
        Text(
            text = count.toString(),
            style = JkType.display,
            color = if (count == 0) colors.textTertiary else tint,
        )
        Text(label, style = JkType.caption, color = colors.textSecondary)
    }
}

@Composable
private fun IssueSection(
    title: String,
    hint: String,
    issues: List<HealthIssue>,
    detailColor: Color,
    onOpen: (String) -> Unit,
) {
    if (issues.isEmpty()) return
    val colors = Jk.colors
    JkSectionLabel("$title　${issues.size}")
    JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
        issues.forEachIndexed { index, issue ->
            if (index > 0) JkDivider(inset = Space.x2 + 36.dp + Space.x1_5)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(issue.card.id) }
                    .padding(horizontal = Space.x2, vertical = Space.x1_5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardTile(issue.card.type, issue.card.color, size = 36.dp)
                Spacer(Modifier.width(Space.x1_5))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = issue.card.title.ifBlank { "未命名" },
                        style = JkType.bodyStrong,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(issue.detail, style = JkType.small, color = detailColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
    Text(hint, style = JkType.caption, color = colors.textTertiary, modifier = Modifier.padding(start = Space.x05, top = Space.x1))
}
