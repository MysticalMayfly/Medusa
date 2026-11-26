package com.priyabrat.medusa.ui.home

import com.priyabrat.medusa.ui.components.rememberFocusable

import com.priyabrat.medusa.ui.components.tvClick

import com.priyabrat.medusa.ui.components.focusRing

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.priyabrat.medusa.ui.components.ContinueCard
import com.priyabrat.medusa.ui.components.HeroActions
import com.priyabrat.medusa.ui.components.HeroCarousel
import com.priyabrat.medusa.ui.components.Monogram
import com.priyabrat.medusa.ui.components.ProgressLine
import com.priyabrat.medusa.ui.components.SectionGap
import com.priyabrat.medusa.ui.components.ShelfHeader
import com.priyabrat.medusa.ui.components.ShelfRow
import com.priyabrat.medusa.ui.components.rise
import com.priyabrat.medusa.ui.model.HomeUiState
import com.priyabrat.medusa.ui.model.PlatformProgress
import com.priyabrat.medusa.ui.model.ProgressSummary
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Motion
import com.priyabrat.medusa.ui.theme.Shapes

class HomeActions(
    val hero: HeroActions,
    val onOpen: (String) -> Unit,
    val onTickEpisode: (String) -> Unit,
    val onSeeAll: (String) -> Unit,
    /** A service tile in the progress section: opens that service's titles. */
    val onPlatform: (String) -> Unit = {},
)

/**
 * Home (tonight.js): the carousel under the header, Continue watching, the shelves,
 * then the watch-progress section (ring plus a floating tile and a bar per platform).
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    header: @Composable () -> Unit,
    heroEntry: FocusRequester? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(bottom = Dimens.SafeBottom),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        item("hero") {
            Box {
                HeroCarousel(state.picks, actions.hero, showPoster = false, entry = heroEntry)
                header()
            }
        }
        if (state.continueWatching.isNotEmpty()) {
            item("continue") {
                Column(Modifier.rise()) {
                    ShelfHeader(state.continueTitle, Modifier.padding(horizontal = Dimens.SafeH), count = state.continueWatching.size)
                    LazyRow(
                        contentPadding = PaddingValues(start = Dimens.SafeH, end = Dimens.SafeH, top = 12.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap14),
                    ) {
                        items(state.continueWatching, key = { it.id }) { ContinueCard(it, actions.onOpen, actions.onTickEpisode) }
                    }
                }
            }
        }
        // contentType lets a shelf scrolled off screen be reused for the next one instead of rebuilt.
        items(state.shelves, key = { it.id }, contentType = { "shelf" }) { shelf ->
            ShelfRow(
                shelf,
                actions.onOpen,
                Modifier.rise(),
                onSeeAll = if (shelf.seeAllLabel != null) ({ actions.onSeeAll(shelf.id) }) else null,
            )
        }
        state.progress?.let { p -> item("progress") { ProgressSection(p, actions.onPlatform, Modifier.rise()) } }
    }
}

/** .prog-row: "18 watched of 1635 titles", the ring, and the platform box. */
@Composable
private fun ProgressSection(p: ProgressSummary, onPlatform: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Dimens.SafeH),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the hero box
        Row(
            Modifier
                .weight(1.6f)
                .clip(Shapes.Card)
                .background(MedusaColors.Surface)
                .border(1.dp, MedusaColors.Line, Shapes.Card)
                .padding(Dimens.Gap22),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
                Text(p.eyebrow.uppercase(), style = MedusaType.Eyebrow)
                Text(
                    buildAnnotatedString {
                        append("${p.watchedCount} watched\n")
                        withStyle(SpanStyle(color = MedusaColors.Muted, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)) { append(p.totalLabel) }
                    },
                    style = MedusaType.Stat,
                )
                Text(p.breakdown, style = MedusaType.Body)
            }
            Ring(p.percent)
        }
        // the platform box
        Column(
            Modifier
                .weight(1f)
                .clip(Shapes.Card)
                .background(MedusaColors.Surface)
                .border(1.dp, MedusaColors.Line, Shapes.Card)
                .padding(Dimens.Gap16),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap14),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                p.platforms.take(4).forEach { PlatformTile(it, onPlatform) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                p.platforms.drop(4).forEach { PlatformTile(it, onPlatform) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
                p.platforms.forEach { PlatformBar(it) }
            }
        }
    }
}

/** The completion ring (.ring): a track, the filled arc and a dot at its tip. */
@Composable
private fun Ring(percent: Int) {
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(104.dp)) {
            val w = 9.dp.toPx()
            val inset = w / 2
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(MedusaColors.RingTrack, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            val sweep = 360f * percent.coerceIn(0, 100) / 100f
            drawArc(MedusaColors.Fg, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            val a = Math.toRadians((sweep - 90f).toDouble())
            val r = arcSize.width / 2
            drawCircle(Color.White, w * 0.75f, Offset(center.x + (r * kotlin.math.cos(a)).toFloat(), center.y + (r * kotlin.math.sin(a)).toFloat()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", style = MedusaType.CardTitle.copy(fontSize = 24.sp))
            Text("complete", style = MedusaType.Small)
        }
    }
}

/**
 * .tile: a service's tile. On the TV it is still and upright (the Mac's floating, tilted tiles were hard to read and
 * kept the GPU busy), it can take focus so the remote can reach the bottom of Home, and OK opens that service.
 * The logo sits on a dark chip: on the service's own colour, Apple's white logo and others disappeared.
 */
@Composable
private fun PlatformTile(p: PlatformProgress, onPlatform: (String) -> Unit) {
    val f = rememberFocusable()
    Column(
        Modifier
            .size(58.dp)
            .focusRing(f.focused.value, radius = 15.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(MedusaColors.Raised)
            .border(1.dp, Color(p.argb).copy(alpha = 0.6f), RoundedCornerShape(15.dp))
            .tvClick(f) { onPlatform(p.id) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Monogram(p.monogram, null, 22.dp)
        Text("${p.watched} / ${p.total}", style = MedusaType.Mono.copy(fontSize = 9.sp), modifier = Modifier.padding(top = 4.dp))
    }
}

/** .pbar: name, bar, percent. */
@Composable
private fun PlatformBar(p: PlatformProgress) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
        Text(p.name, style = MedusaType.Small, modifier = Modifier.width(74.dp), maxLines = 1)
        ProgressLine(p.fraction, Color(p.argb), Modifier.weight(1f), height = 5.dp)
        Text(p.percentLabel, style = MedusaType.Mono, modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
