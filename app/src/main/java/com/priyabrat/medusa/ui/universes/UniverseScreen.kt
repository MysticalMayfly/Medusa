package com.priyabrat.medusa.ui.universes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.priyabrat.medusa.R
import com.priyabrat.medusa.ui.browse.PageGrid
import com.priyabrat.medusa.ui.browse.Segments
import com.priyabrat.medusa.ui.browse.fullWidth
import com.priyabrat.medusa.ui.components.ArtPlaceholder
import com.priyabrat.medusa.ui.components.Icon
import com.priyabrat.medusa.ui.components.LinkPill
import com.priyabrat.medusa.ui.components.MedusaIcons
import com.priyabrat.medusa.ui.components.Pill
import com.priyabrat.medusa.ui.components.PillStyle
import com.priyabrat.medusa.ui.components.ProgressLine
import com.priyabrat.medusa.ui.components.focusRing
import com.priyabrat.medusa.ui.components.rememberFocusable
import com.priyabrat.medusa.ui.components.rise
import com.priyabrat.medusa.ui.components.tvClick
import com.priyabrat.medusa.ui.model.ServiceTag
import com.priyabrat.medusa.ui.model.UniverseEntry
import com.priyabrat.medusa.ui.model.UniverseUiState
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Shapes

class UniverseActions(
    val onBack: () -> Unit,
    val onOpen: (String) -> Unit,
    val onWatched: (String, Boolean) -> Unit,
    val onSegment: (String) -> Unit,
    val onToggleHideWatched: () -> Unit,
)

/**
 * One universe's page (universes.js): backdrop with name, progress and the Up next card,
 * then the story-order timeline with a tick per film.
 */
@Composable
fun UniverseScreen(state: UniverseUiState, actions: UniverseActions, header: @Composable () -> Unit) {
    val tint = Color(state.tintArgb)
    PageGrid {
        fullWidth("top") { header() }
        fullWidth("hero") {
            Box(
                Modifier
                    .padding(horizontal = Dimens.SafeH, vertical = Dimens.Gap8)
                    .fillMaxWidth()
                    .heightIn(min = 220.dp)
                    .clip(Shapes.Hero)
                    .border(1.dp, MedusaColors.Line, Shapes.Hero)
                    .rise(),
            ) {
                ArtPlaceholder(null, Modifier.matchParentSize(), tint = Color(0xFF1A1A24), url = state.backdropUrl)
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to MedusaColors.Bg, 0.45f to MedusaColors.Bg.copy(alpha = 0.7f), 1f to Color.Transparent)))
                Column(Modifier.padding(Dimens.Gap22), verticalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
                    LinkPill("← " + stringResource(R.string.all_universes), actions.onBack)
                    Text(state.name, style = MedusaType.UniverseTitle)
                    ProgressLine(state.progress, tint, Modifier.width(180.dp), height = 4.dp)
                    Text(state.stat, style = MedusaType.Body)
                    state.upNext?.let { next ->
                        Column(
                            Modifier
                                .widthIn(max = 260.dp)
                                .clip(Shapes.Card)
                                .background(MedusaColors.Bg.copy(alpha = 0.72f))
                                .border(1.dp, MedusaColors.Line, Shapes.Card)
                                .padding(Dimens.Gap12),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(stringResource(R.string.up_next).uppercase(), style = MedusaType.Eyebrow, color = MedusaColors.Accent)
                            Text(next.title, style = MedusaType.CardTitle)
                            Text(next.meta, style = MedusaType.Mono)
                            Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
                                Pill(stringResource(R.string.mark_watched), { actions.onWatched(next.id, true) }, style = PillStyle.Primary, height = 24.dp)
                                Pill(stringResource(R.string.details), { actions.onOpen(next.id) }, height = 24.dp)
                            }
                        }
                    }
                }
            }
        }
        fullWidth("controls") {
            Row(Modifier.padding(horizontal = Dimens.SafeH), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap10), verticalAlignment = Alignment.CenterVertically) {
                Segments(state.segments, actions.onSegment)
                Pill(stringResource(R.string.hide_watched), actions.onToggleHideWatched, selected = state.hideWatched)
            }
        }
        state.entries.forEach { e -> fullWidth("e-" + e.id) { EntryRow(e, actions) } }
    }
}

/** .uv-row: tick, thumbnail, title and meta, where it streams. The Up next row is highlighted. */
@Composable
private fun EntryRow(e: UniverseEntry, actions: UniverseActions) {
    Row(
        Modifier
            .padding(horizontal = Dimens.SafeH)
            .fillMaxWidth()
            .clip(Shapes.Card)
            .background(if (e.upNext) MedusaColors.Accent.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = Dimens.Gap12, vertical = Dimens.Gap8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap12),
    ) {
        Tick(e.seen) { actions.onWatched(e.id, !e.seen) }
        val f = rememberFocusable()
        Row(
            Modifier
                .weight(1f)
                .focusRing(f.focused.value, radius = 8.dp)
                .clip(Shapes.Input)
                .tvClick(f) { actions.onOpen(e.id) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap12),
        ) {
            ArtPlaceholder(null, Modifier.width(30.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp)), url = e.posterUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (e.upNext) {
                        Text(
                            stringResource(R.string.up_next).uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            modifier = Modifier.clip(Shapes.Pill).background(MedusaColors.Accent).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    Text(e.title, style = MedusaType.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(e.meta, style = MedusaType.Mono)
            }
            Spacer(Modifier.width(Dimens.Gap8))
            e.services.forEach { ServiceChip(it) }
        }
    }
}

/** .uv-check: round tick button. */
@Composable
private fun Tick(on: Boolean, onClick: () -> Unit) {
    val f = rememberFocusable()
    Box(
        Modifier
            .focusRing(f.focused.value)
            .size(22.dp)
            .clip(CircleShape)
            .background(if (on) MedusaColors.Fg else Color.Transparent)
            .border(1.5.dp, if (on) MedusaColors.Fg else MedusaColors.LineHover, CircleShape)
            .tvClick(f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(MedusaIcons.Check, MedusaColors.Inv, 12.dp)
    }
}

/** .svc / .uv-svc: a streaming service tag, filled in the service's colour. */
@Composable
fun ServiceChip(tag: ServiceTag) {
    val c = Color(tag.argb)
    Text(
        tag.name,
        style = MedusaType.Small,
        color = Color(0xFF111111),
        maxLines = 1,
        modifier = Modifier
            .clip(Shapes.Pill)
            .background(c)
            .border(1.dp, c, Shapes.Pill)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
