package com.xiguli.jinkui.ui.nav

import android.view.HapticFeedbackConstants
import androidx.compose.animation.togetherWith
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
import com.xiguli.jinkui.ui.theme.jkSurface
import com.xiguli.jinkui.ui.theme.JkBackdrop
import com.xiguli.jinkui.ui.theme.jkBackdrop
import com.xiguli.jinkui.ui.theme.jkBackdropSource
import com.xiguli.jinkui.ui.theme.rememberJkBackdrop
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
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
 * 首页外壳（1.3 液态玻璃）：分区内容铺满整屏，底部浮着一条磨砂玻璃胶囊栏——账本 · 日历 · 卡包 · 统计 · 我的。
 * 内容从栏后面滚过：Android 12+ 上栏里画的是栏后面内容的实时模糊（见 [rememberJkBackdrop]），
 * 低版本退回更不透明的磨砂填充。两个分区之间 160ms 淡入淡出，离开的分区随即销毁。
 */
@Composable
fun HomeTabs(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    content: @Composable (HomeTab) -> Unit,
) {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val inset = TabBarSpec.height + TabBarSpec.bottomMargin + nav + 12.dp
    val backdrop = rememberJkBackdrop()
    Box(Modifier.fillMaxSize()) {
        // 页面底与分区内容一起录进 backdrop，底栏里模糊的是“栏后面真正的样子”
        Box(
            Modifier
                .fillMaxSize()
                .jkBackdropSource(backdrop)
                .jkBackdrop(),
        ) {
            CompositionLocalProvider(LocalTabBarInset provides inset) {
                // 分区切换：弹簧驱动的淡入 + 朝点击方向轻移 24dp（离开的分区淡出后即销毁）
                androidx.compose.animation.AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        val move = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.86f, stiffness = 520f)
                        val fade = androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 900f)
                        (androidx.compose.animation.fadeIn(fade) + androidx.compose.animation.slideInHorizontally(move) { dir * it / 14 }) togetherWith
                            (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.slideOutHorizontally(move) { -dir * it / 20 })
                    },
                    label = "homeTab",
                    modifier = Modifier.fillMaxSize(),
                ) { tab ->
                    Box(Modifier.fillMaxSize()) { content(tab) }
                }
            }
        }
        HomeTabBar(
            selected = selected,
            onSelect = onSelect,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = nav + TabBarSpec.bottomMargin),
        )
    }
}

/**
 * 悬浮玻璃胶囊底栏：五个均分的线条图标 + 文字。选中项坐在一颗凸起的亮玻璃泡里（薄荷色图标与文字），
 * 切换时玻璃泡用弹簧滑过去，滑动途中轻微拉长再收回（像液体）。保留触感反馈与 48dp 以上的触控区。
 */
@Composable
fun HomeTabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit, modifier: Modifier = Modifier, backdrop: JkBackdrop? = null) {
    val surfaces = Jk.surfaces
    val position by animateFloatAsState(
        targetValue = selected.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "tabBubble",
    )
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = TabBarSpec.sideMargin)
            .fillMaxWidth()
            .height(TabBarSpec.height)
            .jkSurface(surfaces.bar, Radius.pill, backdrop),
    ) {
        val count = HomeTab.entries.size
        val inner = maxWidth - 12.dp
        val slot = inner / count
        // 滑动中途（position 不是整数）把玻璃泡拉长一点，越接近两格中间越长
        val frac = position - kotlin.math.floor(position)
        val stretch = 1f + 0.22f * (1f - kotlin.math.abs(frac - 0.5f) * 2f).let { if (frac == 0f) 0f else it }
        val bubbleWidth = slot - 6.dp
        Box(
            Modifier
                .padding(start = 6.dp + 3.dp, top = 5.dp, bottom = 5.dp)
                .fillMaxHeight()
                .width(bubbleWidth)
                .graphicsLayer {
                    translationX = (slot * position).toPx()
                    scaleX = stretch
                    scaleY = 1f - (stretch - 1f) * 0.25f
                }
                .clearAndSetSemantics { }
                .jkSurface(surfaces.bubble, Radius.pill),
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tab in HomeTab.entries) {
                TabItem(tab, tab == selected, onSelect, Modifier.weight(1f))
            }
        }
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
            fill = colors.accentSoft.copy(alpha = fillAlpha * 0.6f),
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
