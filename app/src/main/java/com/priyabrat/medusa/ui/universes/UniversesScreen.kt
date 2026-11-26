package com.priyabrat.medusa.ui.universes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.priyabrat.medusa.ui.browse.PageGrid
import com.priyabrat.medusa.ui.browse.fullWidth
import com.priyabrat.medusa.ui.components.ArtPlaceholder
import com.priyabrat.medusa.ui.components.ProgressLine
import com.priyabrat.medusa.ui.components.focusRing
import com.priyabrat.medusa.ui.components.rememberFocusable
import com.priyabrat.medusa.ui.components.rise
import com.priyabrat.medusa.ui.components.tvClick
import com.priyabrat.medusa.ui.model.UniverseTile
import com.priyabrat.medusa.ui.model.UniversesUiState
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Motion
import com.priyabrat.medusa.ui.theme.Shapes

/** Universes wall (universes.js): franchise tiles, grouped ("Keep going", "Start something new"). */
@Composable
fun UniversesScreen(state: UniversesUiState, onOpenUniverse: (String) -> Unit, header: @Composable () -> Unit) {
    PageGrid {
        fullWidth("top") { header() }
        fullWidth("head") {
            Column(Modifier.padding(horizontal = Dimens.SafeH, vertical = Dimens.Gap8).rise(), verticalArrangement = Arrangement.spacedBy(Dimens.Gap4)) {
                Text(state.eyebrow.uppercase(), style = MedusaType.Eyebrow)
                Text(state.headline, style = MedusaType.PageTitle)
                Text(state.intro, style = MedusaType.Body)
            }
        }
        state.groups.forEachIndexed { g, group ->
            fullWidth("group-$g") {
                Row(
                    Modifier.padding(start = Dimens.SafeH, end = Dimens.SafeH, top = Dimens.Gap12),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Gap10),
                ) {
                    Text(group.title, style = MedusaType.ShelfTitle)
                    Text(group.note, style = MedusaType.Small, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            val rows = group.tiles.chunked(Dimens.UniverseColumns)
            rows.forEachIndexed { r, row ->
                fullWidth("group-$g-row-$r") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Dimens.SafeH),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap12),
                    ) {
                        row.forEach { UniverseTileCard(it, onOpenUniverse, Modifier.weight(1f)) }
                        repeat(Dimens.UniverseColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** .uv-tile: a fan of three posters on the franchise's tint, name, progress bar, seen count, up next. */
@Composable
private fun UniverseTileCard(tile: UniverseTile, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val f = rememberFocusable()
    val focused = f.focused.value
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.LiftMs, easing = Motion.Ease), label = "tileLift")
    val tint = Color(tile.tintArgb)
    Column(
        modifier
            .graphicsLayer { translationY = -3 * density * lift }
            .focusRing(focused, radius = 11.dp)
            .clip(Shapes.Card)
            .background(MedusaColors.Surface)
            .border(1.dp, if (focused) MedusaColors.LineHover else MedusaColors.Line, Shapes.Card)
            .tvClick(f) { onOpen(tile.id) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.35f), MedusaColors.Surface))),
        ) {
            listOf(-14f, 0f, 14f).forEachIndexed { n, angle ->
                ArtPlaceholder(
                    null,
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (10 + n * 34).dp, y = 6.dp)
                        .width(44.dp)
                        .aspectRatio(2f / 3f)
                        .graphicsLayer { rotationZ = angle * 0.3f }
                        .clip(RoundedCornerShape(5.dp))
                        .border(1.dp, MedusaColors.Line2, RoundedCornerShape(5.dp)),
                    tint = tint.copy(alpha = 0.5f),
                    url = tile.posterUrls.getOrNull(n),
                )
            }
        }
        Column(Modifier.padding(Dimens.Gap12), verticalArrangement = Arrangement.spacedBy(Dimens.Gap4)) {
            Text(tile.name, style = MedusaType.CardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ProgressLine(tile.progress, MedusaColors.Accent, Modifier.fillMaxWidth().padding(vertical = 2.dp), height = 3.dp)
            Text(tile.seenLabel, style = MedusaType.Small, maxLines = 1)
            Text(tile.upNext, style = MedusaType.Small, color = MedusaColors.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
