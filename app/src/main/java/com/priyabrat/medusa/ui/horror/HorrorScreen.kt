package com.priyabrat.medusa.ui.horror

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.priyabrat.medusa.ui.browse.FilterBar
import com.priyabrat.medusa.ui.browse.OptionRow
import com.priyabrat.medusa.ui.browse.PageGrid
import com.priyabrat.medusa.ui.browse.PageTitle
import com.priyabrat.medusa.ui.browse.Segments
import com.priyabrat.medusa.ui.browse.fullWidth
import com.priyabrat.medusa.ui.browse.posterWall
import com.priyabrat.medusa.ui.components.HeroActions
import com.priyabrat.medusa.ui.components.HeroCarousel
import com.priyabrat.medusa.ui.components.SelectPill
import com.priyabrat.medusa.ui.components.ShelfRow
import com.priyabrat.medusa.ui.components.rise
import com.priyabrat.medusa.ui.model.HorrorUiState
import com.priyabrat.medusa.ui.theme.Dimens

class HorrorActions(
    val hero: HeroActions,
    val onOpen: (String) -> Unit,
    val onTab: (String) -> Unit,
    val onSeenSegment: (String) -> Unit,
    val onWhere: (String) -> Unit,
    /** (select id, option id) */
    val onSelect: (String, String) -> Unit,
    val onSeeAll: (String) -> Unit,
)

/**
 * Horror, "Standard" look only (horror-stream.js): the horror carousel, the tabs
 * (Browse, the years, All horror), then either the Browse shelves or a wall with its filters.
 * The Mac's four "Classic looks" are not on the TV.
 */
@Composable
fun HorrorScreen(state: HorrorUiState, actions: HorrorActions, header: @Composable () -> Unit, heroEntry: androidx.compose.ui.focus.FocusRequester? = null) {
    PageGrid {
        fullWidth("top") {
            Box {
                HeroCarousel(state.picks.ifEmpty { listOfNotNull(state.pick) }, actions.hero, height = Dimens.PageHeroHeight, entry = heroEntry)
                header()
            }
        }
        fullWidth("tabs") { OptionRow(state.tabs, actions.onTab, Modifier.padding(horizontal = Dimens.SafeH)) }

        val wall = state.wall
        if (wall == null) {
            state.shelves.forEach { shelf ->
                fullWidth("shelf-" + shelf.id) {
                    ShelfRow(
                        shelf,
                        actions.onOpen,
                        Modifier.rise(),
                        onSeeAll = if (shelf.seeAllLabel != null) ({ actions.onSeeAll(shelf.id) }) else null,
                    )
                }
            }
        } else {
            fullWidth("wall-title") { PageTitle(wall.title, wall.summary, Modifier.padding(horizontal = Dimens.SafeH).rise()) }
            fullWidth("wall-filters") {
                Column(Modifier.padding(horizontal = Dimens.SafeH).rise(), verticalArrangement = Arrangement.spacedBy(Dimens.Gap12)) {
                    FilterBar {
                        Segments(wall.seenSegments, actions.onSeenSegment)
                        Spacer(Modifier.weight(1f))
                        SelectPill(wall.sort, { actions.onSelect(wall.sort.id, it.id) })
                    }
                    OptionRow(wall.whereChips, actions.onWhere)
                }
            }
            posterWall(wall.items, actions.onOpen)
        }
    }
}
