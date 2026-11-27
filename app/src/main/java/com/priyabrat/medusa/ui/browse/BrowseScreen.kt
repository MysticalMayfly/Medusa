package com.priyabrat.medusa.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.priyabrat.medusa.R
import com.priyabrat.medusa.ui.components.EmptyNote
import com.priyabrat.medusa.ui.components.HeroActions
import com.priyabrat.medusa.ui.components.HeroCarousel
import com.priyabrat.medusa.ui.components.MedusaIcons
import com.priyabrat.medusa.ui.components.Pill
import com.priyabrat.medusa.ui.components.PillStyle
import com.priyabrat.medusa.ui.components.PosterCard
import com.priyabrat.medusa.ui.components.SelectPill
import com.priyabrat.medusa.ui.components.rise
import com.priyabrat.medusa.ui.model.BrowseKind
import com.priyabrat.medusa.ui.model.BrowseUiState
import com.priyabrat.medusa.ui.model.FilterOption
import com.priyabrat.medusa.ui.model.TitleCard
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Shapes

class BrowseActions(
    val hero: HeroActions,
    val onOpen: (String) -> Unit,
    val onPlatform: (String) -> Unit,
    val onSegment: (String) -> Unit,
    /** (select id, option id) */
    val onSelect: (String, String) -> Unit,
    val onToggleUnwatched: () -> Unit,
)

/**
 * Movies, TV shows and More (app.js): a carousel for that type (not on More), the page title
 * and counts, platform tabs, the filters and sort, then the poster wall.
 */
@Composable
fun BrowseScreen(state: BrowseUiState, actions: BrowseActions, header: @Composable () -> Unit, heroEntry: androidx.compose.ui.focus.FocusRequester? = null) {
    PageGrid {
        fullWidth("top") {
            val picks = state.picks.ifEmpty { listOfNotNull(state.pick) }
            // The header stays in the same place in the tree whether or not the carousel has picks yet: the picks
            // arrive a moment after the page opens, and rebuilding the header then threw the remote's focus away.
            // More has no carousel (BrowseKind.More), so it keeps a short gap instead.
            Box {
                if (state.kind != BrowseKind.More) HeroCarousel(picks, actions.hero, entry = heroEntry)
                else Spacer(Modifier.height(Dimens.HeaderControl + Dimens.SafeTop + Dimens.Gap22))
                header()
            }
        }
        fullWidth("title") { PageTitle(state.title, state.summary, Modifier.padding(horizontal = Dimens.SafeH).rise()) }
        fullWidth("filters") {
            Column(Modifier.padding(horizontal = Dimens.SafeH).rise(), verticalArrangement = Arrangement.spacedBy(Dimens.Gap12)) {
                OptionRow(state.platformTabs, actions.onPlatform)
                FilterBar {
                    if (state.typeSegments.isNotEmpty()) Segments(state.typeSegments, actions.onSegment)
                    state.selects.forEach { sel -> SelectPill(sel, { actions.onSelect(sel.id, it.id) }) }
                    Spacer(Modifier.weight(1f))
                    Text(state.resultCount, style = MedusaType.Small)
                    state.sort?.let { sel -> SelectPill(sel, { actions.onSelect(sel.id, it.id) }) }
                    Pill(
                        stringResource(R.string.unwatched_only),
                        onClick = actions.onToggleUnwatched,
                        selected = state.unwatchedOnly,
                        icon = MedusaIcons.Eye,
                    )
                }
            }
        }
        posterWall(state.items, actions.onOpen)
    }
}

/* ---------------------------------------------------------------- shared page pieces */

/** A page that scrolls as one column: full-width blocks on top, then the poster wall in rows. */
@Composable
fun PageGrid(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Dimens.SafeBottom),
        verticalArrangement = Arrangement.spacedBy(Dimens.WallGapV),
        content = content,
    )
}

fun LazyListScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key) { content() }
}

/** The poster wall (.grid of .pc): rows of [Dimens.WallColumns] equal posters inside the safe margins. */
fun LazyListScope.posterWall(items: List<TitleCard>, onOpen: (String) -> Unit) {
    if (items.isEmpty()) {
        item("empty") { EmptyNote("Nothing to show", "Try another filter, or check back after the catalogue refreshes.") }
        return
    }
    val rows = items.chunked(Dimens.WallColumns)
    items(rows.size, key = { "wall-" + rows[it].first().id }, contentType = { "wall-row" }) { r ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.SafeH),
            horizontalArrangement = Arrangement.spacedBy(Dimens.WallGapH),
        ) {
            rows[r].forEach { card -> PosterCard(card, onOpen, Modifier.weight(1f), width = null) }
            repeat(Dimens.WallColumns - rows[r].size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** .page-title: big title with a muted line after it. */
@Composable
fun PageTitle(title: String, summary: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = Dimens.Gap8), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap10)) {
        Text(title, style = MedusaType.PageTitle)
        Text(summary, style = MedusaType.Body, modifier = Modifier.padding(bottom = 4.dp))
    }
}

/** A row of tabs/chips (.tabs). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionRow(options: List<FilterOption>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap8), verticalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
        options.forEach { o ->
            Pill(
                o.label,
                onClick = { onPick(o.id) },
                selected = o.selected,
                icon = if (o.monogram == "▦") MedusaIcons.Grid else null,
                monogram = o.monogram?.takeIf { it != "▦" },
                dotArgb = o.dotArgb,
                count = o.count,
            )
        }
    }
}

/** .segs: joined segment buttons in one rounded frame. */
@Composable
fun Segments(options: List<FilterOption>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(Shapes.Pill)
            .background(MedusaColors.Surface)
            .border(1.dp, MedusaColors.Line, Shapes.Pill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { o -> Pill(o.label, onClick = { onPick(o.id) }, style = PillStyle.Segment, selected = o.selected, height = 24.dp) }
    }
}

/** .frow: filters on the left, count and sort on the right. */
@Composable
fun FilterBar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap8),
        content = content,
    )
}
