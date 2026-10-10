package com.xiguli.jinkui.ui.nav

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.jinkui.ui.components.MintGlyph
import com.xiguli.jinkui.ui.components.MintIcon
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius

/** 悬浮底栏的尺寸：栏高、左右与下方的留白。 */
object TabBarSpec {
    val height = 66.dp
    val sideMargin = 16.dp
    val bottomMargin = 10.dp
    val icon = 28.dp
}

/**
 * 各分区页面底部要让出的高度：悬浮底栏 + 留白 + 系统手势条。
 * 列表把它加进 contentPadding，最后一行能完整滚到底栏上方；底栏之外的页面（详情等）为 0。
 */
val LocalTabBarInset = staticCompositionLocalOf { 0.dp }

/**
 * 首页外壳（1.1）：分区内容铺满整屏，底部浮着一条白色胶囊栏——账本 · 日历 · 卡包 · 统计 · 我的。
 * 内容从栏后面滚过，栏的上方有一段渐隐到底色的遮罩，滚到底栏附近的文字会柔和地淡出，而不是被硬切。
 * 两个分区之间 160ms 淡入淡出，离开的分区随即销毁。
 */
@Composable
fun HomeTabs(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    content: @Composable (HomeTab) -> Unit,
) {
    val colors = Jk.colors
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val inset = TabBarSpec.height + TabBarSpec.bottomMargin + nav + 12.dp
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTabBarInset provides inset) {
            Crossfade(targetState = selected, animationSpec = tween(160), label = "homeTab", modifier = Modifier.fillMaxSize()) { tab ->
                Box(Modifier.fillMaxSize()) { content(tab) }
            }
        }
        // 底栏上方的渐隐：内容滚到这里慢慢融进底色
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(inset + 8.dp)
                .clearAndSetSemantics { }
                .background(
                    Brush.verticalGradient(
                        0f to colors.bg.copy(alpha = 0f),
                        0.3f to colors.bg.copy(alpha = 0.78f),
                        0.6f to colors.bg.copy(alpha = 0.97f),
                        1f to colors.bg,
                    ),
                ),
        )
        HomeTabBar(
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = nav + TabBarSpec.bottomMargin),
        )
    }
}

/** 悬浮胶囊底栏：白底、大圆角、柔和的宽阴影；五个均分的图标 + 文字，选中项薄荷色、图标里铺浅薄荷并轻轻放大。 */
@Composable
fun HomeTabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    Row(
        modifier = modifier
            .padding(horizontal = TabBarSpec.sideMargin)
            .fillMaxWidth()
            .height(TabBarSpec.height)
            .softShadow(colors.barShadow, if (colors.isDark) 0.05f else 0.012f)
            .clip(Radius.pill)
            .background(colors.bar)
            // 深色下阴影几乎看不见，用一圈极淡的亮边把栏和底色分开
            .then(if (colors.isDark) Modifier.border(1.dp, Color.White.copy(alpha = 0.07f), Radius.pill) else Modifier)
            .padding(horizontal = 6.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in HomeTab.entries) {
            TabItem(tab, tab == selected, onSelect, Modifier.weight(1f))
        }
    }
}

/**
 * 柔和的宽阴影：在胶囊外面画十几圈逐渐外扩、几乎透明的圆角矩形，叠起来就是一圈向外渐淡的阴影，
 * 稍微往下偏一点。不依赖系统的硬件阴影，所以截图、低版本系统和深色下都画得一样。
 */
private fun Modifier.softShadow(color: Color, alphaPerLayer: Float): Modifier = drawBehind {
    val layers = 14
    val step = 1.4.dp.toPx()
    val dy = 4.dp.toPx()
    for (i in layers downTo 1) {
        val spread = i * step
        drawRoundRect(
            color = color.copy(alpha = alphaPerLayer),
            topLeft = Offset(-spread, -spread * 0.6f + dy),
            size = Size(size.width + spread * 2, size.height + spread * 1.6f),
            cornerRadius = CornerRadius(size.height / 2 + spread),
        )
    }
}

fun HomeTab.glyph(): MintGlyph = when (this) {
    HomeTab.LEDGER -> MintGlyph.LEDGER
    HomeTab.CALENDAR -> MintGlyph.CALENDAR
    HomeTab.CARDS -> MintGlyph.CARDS
    HomeTab.STATS -> MintGlyph.STATS
    HomeTab.ME -> MintGlyph.ME
}

@Composable
private fun TabItem(tab: HomeTab, active: Boolean, onSelect: (HomeTab) -> Unit, modifier: Modifier) {
    val colors = Jk.colors
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateColorAsState(if (active) colors.accent else colors.iconMuted, tween(220), label = "tabTint")
    val labelColor by animateColorAsState(if (active) colors.accent else colors.textTertiary, tween(220), label = "tabLabel")
    val fillAlpha by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "tabFill")
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.9f
            active -> 1.08f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "tabScale",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(Radius.pill)
            .selectable(
                selected = active,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
                onClick = {
                    if (!active) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSelect(tab)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MintIcon(
            glyph = tab.glyph(),
            size = TabBarSpec.icon,
            tint = tint,
            accent = if (active) colors.primary else colors.iconMuted.copy(alpha = 0.9f),
            fill = colors.accentSoft.copy(alpha = fillAlpha * (if (colors.isDark) 0.9f else 1f)),
            filled = true,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = JkType.caption.copy(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
            color = labelColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 测试与预览用：给分区内容让出的底部高度。 */
@Composable
fun tabBarInset(): Dp = LocalTabBarInset.current
