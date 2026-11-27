package com.priyabrat.medusa.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.priyabrat.medusa.R
import com.priyabrat.medusa.ui.components.IconCircle
import com.priyabrat.medusa.ui.components.MedusaIcons
import com.priyabrat.medusa.ui.components.focusRing
import com.priyabrat.medusa.ui.components.rememberFocusable
import com.priyabrat.medusa.ui.components.tvClick
import com.priyabrat.medusa.ui.model.ProfileTile
import com.priyabrat.medusa.ui.model.Tab
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Shapes

/** Tab labels in the Mac order: Home · Movies · TV shows · Horror · More · Universes. */
@Composable
fun tabLabel(tab: Tab): String = stringResource(
    when (tab) {
        Tab.Home -> R.string.tab_home
        Tab.Movies -> R.string.tab_movies
        Tab.Shows -> R.string.tab_shows
        Tab.Horror -> R.string.tab_horror
        Tab.More -> R.string.tab_more
        Tab.Universes -> R.string.tab_universes
    },
)

/** The header's height: the space a page leaves for it at its top. */
val HeaderHeight = Dimens.SafeTop + Dimens.Logo

/** The header's focus targets. The tab bar is one focus target, not one per tab. */
class HeaderFocus(
    val tabs: FocusRequester = FocusRequester(),
    val search: FocusRequester = FocusRequester(),
    val settings: FocusRequester = FocusRequester(),
    val profile: FocusRequester = FocusRequester(),
)

/**
 * The header: Medusa logo, the tab bar, search, the profile in use and the gear (with a dot when an update waits).
 *
 * Up from a page lands on [upTarget] (the tab bar on tab pages, the matching control elsewhere),
 * never on whichever tab happens to sit nearest.
 */
@Composable
fun AppHeader(
    current: Tab?,
    onTab: (Tab) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    updateWaiting: Boolean,
    focus: HeaderFocus,
    upTarget: FocusRequester,
    modifier: Modifier = Modifier,
    profile: ProfileTile? = null,
    onProfile: () -> Unit = {},
    /** Down from the tab bar goes here (the carousel's main button) instead of the nearest thing below. */
    down: FocusRequester? = null,
    /** 0 = full size, 1 = shrunk and faded (the carousel has focus). Read only while drawing. */
    dip: () -> Float = { 0f },
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(start = Dimens.SafeH, end = Dimens.SafeH, top = Dimens.SafeTop)
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Up) {
                        // The avatar is missing while no profile is in use: fall back to the tabs.
                        if (!runCatching { upTarget.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) focus.tabs.requestFocus()
                    }
                }
            }
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap10),
    ) {
        // Left end: the Mac's rounded-square Medusa mark and the tabs. It shrinks toward its top-left corner.
        Row(
            Modifier.graphicsLayer {
                val d = dip()
                scaleX = 1f - BAR_SHRINK * d
                scaleY = scaleX
                alpha = 1f - BAR_FADE * d
                transformOrigin = TransformOrigin(0f, 0f)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap10),
        ) {
            // The mark is the bar's leftmost focus stop (left from the first tab). OK opens Home. Focused, a soft
            // white glow spreads behind it (Prince, 2026-11-27).
            val mark = rememberFocusable()
            Image(
                painter = painterResource(R.drawable.medusa_mark),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(Dimens.Logo)
                    .drawBehind {
                        if (mark.focused.value) drawCircle(
                            Brush.radialGradient(
                                // The mark covers the inner half; the glow shows from its edge outwards.
                                0.45f to MedusaColors.Fg.copy(alpha = 0.42f),
                                0.7f to MedusaColors.Fg.copy(alpha = 0.14f),
                                1f to Color.Transparent,
                                center = center,
                                radius = size.minDimension * MARK_GLOW,
                            ),
                            radius = size.minDimension * MARK_GLOW,
                        )
                    }
                    .then(if (down != null) Modifier.focusProperties { this.down = down } else Modifier)
                    .tvClick(mark) { onTab(Tab.Home) },
            )
            TabBar(current, onTab, focus.tabs, down)
        }
        Box(Modifier.weight(1f))
        // Right end: search, profile and gear. It shrinks toward its top-right corner.
        Row(
            Modifier.graphicsLayer {
                val d = dip()
                scaleX = 1f - BAR_SHRINK * d
                scaleY = scaleX
                alpha = 1f - BAR_FADE * d
                transformOrigin = TransformOrigin(1f, 0f)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap10),
        ) {
        IconCircle(MedusaIcons.Search, onSearch, Modifier.focusRequester(focus.search))
        if (profile != null) {
            // The profile in use: opens its page (switch, edit, its own lists).
            val pf = rememberFocusable()
            Box(
                Modifier.focusRequester(focus.profile).focusRing(pf.focused.value, radius = Dimens.HeaderControl / 2, offset = 2.dp).tvClick(pf, onClick = onProfile),
            ) { com.priyabrat.medusa.ui.profiles.Avatar(profile.avatar, profile.name, Dimens.HeaderControl, photoUrl = profile.photoUrl) }
        }
        IconCircle(MedusaIcons.Gear, onSettings, Modifier.focusRequester(focus.settings), dot = updateWaiting)
        }
    }
}

/** While the carousel has focus each end of the bar shrinks this much toward its own screen edge, and fades this much. */
private const val BAR_SHRINK = 0.1f
private const val BAR_FADE = 0.55f

/** The focused mark's glow reaches this many times the mark's size from its centre. */
private const val MARK_GLOW = 1f

/**
 * The tabs as a single focus target. Left and right move the pill one tab and open it at once, so every press
 * counts and nothing waits on focus moving between tabs. Right on the last tab leaves for search. OK opens the
 * highlighted tab (useful when a page below the tabs, like Settings, is open).
 *
 * The pill sits on the open tab, inverted (light on dark), only while the bar has focus. Without focus there is no
 * pill or outline; the open tab's name is just brighter than the others.
 * It jumps; nothing animates.
 */
@Composable
private fun TabBar(current: Tab?, onTab: (Tab) -> Unit, requester: FocusRequester, down: FocusRequester?) {
    val tabs = Tab.entries
    // Where the pill is. Follows the open tab; on pages without one (Search, Settings) it stays where it was.
    var cursor by remember { mutableIntStateOf((current ?: Tab.Home).ordinal) }
    if (current != null && cursor != current.ordinal) cursor = current.ordinal
    var focused by remember { mutableStateOf(false) }

    fun move(to: Int) {
        cursor = to
        onTab(tabs[to])
    }

    Row(
        Modifier
            .clip(Shapes.Pill)
            .background(MedusaColors.Surface.copy(alpha = 0.72f))
            .border(1.dp, MedusaColors.Line, Shapes.Pill)
            .padding(3.dp)
            .focusRequester(requester)
            .then(if (down != null) Modifier.focusProperties { this.down = down } else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    // Left on the first tab leaves for the Medusa mark.
                    Key.DirectionLeft -> if (cursor > 0) { move(cursor - 1); true } else false
                    Key.DirectionRight -> if (cursor < tabs.lastIndex) { move(cursor + 1); true } else false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onTab(tabs[cursor]); true }
                    else -> false
                }
            }
            .focusable(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        tabs.forEachIndexed { i, tab ->
            val on = i == cursor
            Box(
                Modifier
                    .height(Dimens.NavItemH)
                    .drawBehind {
                        if (on && focused) drawRoundRect(MedusaColors.Fg, cornerRadius = CornerRadius(size.height / 2))
                    }
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tabLabel(tab),
                    style = MedusaType.Nav,
                    color = when {
                        on && focused -> MedusaColors.Inv
                        on && current != null -> MedusaColors.Fg
                        else -> MedusaColors.Muted
                    },
                )
            }
        }
    }
}
