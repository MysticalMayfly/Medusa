package com.priyabrat.medusa.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.priyabrat.medusa.ui.model.Rating
import com.priyabrat.medusa.ui.model.RatingKind
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.GeistMono
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Motion
import com.priyabrat.medusa.ui.theme.Shapes

/** How a pill looks. Controls are pills and only change colour on focus (theme.css). */
enum class PillStyle {
    /** .btn / .tab / .chip: surface fill, thin line; selected = solid light fill. */
    Default,
    /** .btn.pri: solid light fill. */
    Primary,
    /** .seg inside a .segs group: no line; selected = solid light fill. */
    Segment,
}

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Default,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    dotArgb: Long? = null,
    monogram: String? = null,
    count: Int? = null,
    trailing: ImageVector? = null,
    height: Dp = 28.dp,
    textStyle: androidx.compose.ui.text.TextStyle = MedusaType.Control,
    f: Focusable = rememberFocusable(),
) {
    val focused = f.focused.value
    val filled = style == PillStyle.Primary || selected
    val bgTarget = when {
        filled -> MedusaColors.Fg
        focused -> MedusaColors.Line2
        style == PillStyle.Segment -> Color.Transparent
        else -> MedusaColors.Surface
    }
    // Read only while drawing (drawBehind), so the 150 ms colour change redraws the pill without rebuilding it.
    val bg = animateColorAsState(bgTarget, tween(Motion.ColorMs), label = "pillBg")
    val fg = when {
        filled -> MedusaColors.Inv
        focused || style == PillStyle.Default && selected -> MedusaColors.Fg
        else -> MedusaColors.Muted
    }
    val line = if (style == PillStyle.Segment || filled) Color.Transparent else if (focused) MedusaColors.LineHover else MedusaColors.Line
    Row(
        modifier = modifier
            .focusRing(focused)
            .defaultMinSize(minHeight = height)
            .clip(Shapes.Pill)
            .drawBehind { drawRect(bg.value) }
            .border(1.dp, line, Shapes.Pill)
            // A disabled pill stays focusable (OK does nothing), so focus does not jump away when
            // a button greys out under it, e.g. Check for updates while it checks.
            .tvClick(f) { if (enabled) onClick() }
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, fg, 12.dp)
        if (dotArgb != null && monogram == null) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(dotArgb)))
        if (monogram != null) Monogram(monogram, dotArgb, 16.dp)
        Text(text, style = textStyle, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) CountBadge(count, onFill = filled)
        if (trailing != null) Icon(trailing, fg.copy(alpha = 0.7f), 10.dp)
    }
}

/** .cnt: small rounded count next to a tab label. */
@Composable
fun CountBadge(count: Int, onFill: Boolean = false) {
    Box(
        Modifier
            .clip(Shapes.Pill)
            .background(if (onFill) MedusaColors.Inv.copy(alpha = 0.14f) else MedusaColors.Track)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(count.toString(), style = MedusaType.Mono, color = if (onFill) MedusaColors.Inv else MedusaColors.Muted, fontSize = 10.sp)
    }
}

/** Stand-in for a platform logo: its initials on its colour. Stage 2 can swap in the real logo. */
@Composable
fun Monogram(text: String, argb: Long?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(Shapes.Small)
            .background(argb?.let { Color(it) } ?: MedusaColors.Line2),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** .iconbtn: round button with a thin line (header search, settings, close). */
@Composable
fun IconCircle(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.HeaderControl,
    dot: Boolean = false,
    f: Focusable = rememberFocusable(),
) {
    val focused = f.focused.value
    val bg = animateColorAsState(if (focused) MedusaColors.Fg else MedusaColors.Surface, tween(Motion.ColorMs), label = "iconBg")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .drawBehind { drawRect(bg.value) }
                .border(1.dp, if (focused) MedusaColors.Fg else MedusaColors.Line, CircleShape)
                .tvClick(f, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, if (focused) MedusaColors.Inv else MedusaColors.Fg, size * 0.45f)
        }
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MedusaColors.Warning),
            )
        }
    }
}

/** .tn-round: the carousel's round glass buttons (Interested, More info, Seen it). */
@Composable
fun GlassRound(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    onTint: Color = MedusaColors.Fg,
    f: Focusable = rememberFocusable(),
) {
    val focused = f.focused.value
    val bg = animateColorAsState(
        when {
            on && onTint == MedusaColors.Accent -> MedusaColors.Accent.copy(alpha = 0.28f)
            focused -> Color.White.copy(alpha = 0.16f)
            else -> MedusaColors.GlassFill
        },
        tween(Motion.ColorMs), label = "glassBg",
    )
    Box(
        modifier
            .focusRing(focused)
            .size(Dimens.HeroBtn)
            .clip(CircleShape)
            .drawBehind { drawRect(bg.value) }
            .border(1.dp, if (on && onTint == MedusaColors.Accent) MedusaColors.Accent.copy(alpha = 0.7f) else if (focused) Color.White.copy(alpha = 0.55f) else MedusaColors.GlassLine, CircleShape)
            .tvClick(f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, if (on) onTint else Color.White, 15.dp)
    }
}

/** .btn-watch: the white glass "Watch on …" pill with a chevron that opens the chooser. */
@Composable
fun WatchButton(
    label: String,
    monograms: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    f: Focusable = rememberFocusable(),
) {
    val focused = f.focused.value
    val lift by animateFloatAsState(if (focused) -1f else 0f, tween(180), label = "watchLift")
    Row(
        modifier
            .graphicsLayer { translationY = lift * density }
            .focusRing(focused, color = Color.White)
            .height(Dimens.HeroBtn)
            .clip(Shapes.Pill)
            .background(Brush.verticalGradient(listOf(Color(0xF7FFFFFF), Color(0xEBE8E8EC))))
            .tvClick(f, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(MedusaIcons.Play, Color(0xFF0A0A0D), 13.dp)
        Text(label, style = MedusaType.ControlStrong, color = Color(0xFF0A0A0D), maxLines = 1)
        if (monograms.isNotEmpty()) {
            Box(Modifier.padding(start = 2.dp).size(1.dp, 16.dp).background(Color(0x24000000)))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                monograms.take(3).forEach { Monogram(it, 0xFF26262EL, 15.dp) }
            }
        }
        Icon(MedusaIcons.ChevronDown, Color(0x8C0A0A0D), 11.dp)
    }
}

/** .rt-badge: IMDb score or Tomatometer, in mono type. */
@Composable
fun RatingBadge(rating: Rating) {
    Row(
        Modifier
            .clip(Shapes.Small)
            .background(MedusaColors.Bg.copy(alpha = 0.55f))
            .border(1.dp, MedusaColors.Line, Shapes.Small)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (rating.kind) {
            RatingKind.Imdb -> Box(
                Modifier.clip(RoundedSmall).background(MedusaColors.Imdb).padding(horizontal = 3.dp, vertical = 1.dp),
            ) { Text("IMDb", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) }
            RatingKind.RtFresh -> Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFA320A)))
            RatingKind.RtRotten -> Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF0AC855)))
            RatingKind.Star -> Text("★", color = MedusaColors.Warning, fontSize = 10.sp)
        }
        Text(rating.value, color = MedusaColors.Fg, fontFamily = GeistMono, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private val RoundedSmall = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)

/** A text link that can take focus (.tn-link, .links a). */
@Composable
fun LinkPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, f: Focusable = rememberFocusable()) {
    val focused = f.focused.value
    Box(
        modifier
            .focusRing(focused)
            .clip(Shapes.Pill)
            .border(1.dp, if (focused) MedusaColors.Fg else MedusaColors.Line2, Shapes.Pill)
            .tvClick(f, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, style = MedusaType.Small, color = if (focused) MedusaColors.Fg else MedusaColors.Muted)
    }
}

/** Thin progress bar (.prog-track, .pbar .track, .uv-hbar). */
@Composable
fun ProgressLine(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 4.dp, track: Color = MedusaColors.Track) {
    Box(modifier.height(height).background(track, Shapes.Pill)) {
        Box(
            Modifier
                .height(height)
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(color, Shapes.Pill),
        )
    }
}
