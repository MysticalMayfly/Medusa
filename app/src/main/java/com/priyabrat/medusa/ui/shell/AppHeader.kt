package com.priyabrat.medusa.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
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
import com.priyabrat.medusa.ui.model.Tab
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Motion
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

/** Which header control should take focus when a screen opens. */
class HeaderFocus(
    val tabs: Map<Tab, FocusRequester> = Tab.entries.associateWith { FocusRequester() },
    val search: FocusRequester = FocusRequester(),
    val settings: FocusRequester = FocusRequester(),
)

/**
 * The header (header.top): Medusa logo, the tabs in a rounded bar (.mainnav), search and the gear.
 * The Mac's Review dot, sync pill, theme switch and Add title are not on the TV.
 * The gear shows a dot when an update is waiting.
 */
@Composable
fun AppHeader(
    current: Tab?,
    onTab: (Tab) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    updateWaiting: Boolean,
    focus: HeaderFocus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Dimens.SafeH, end = Dimens.SafeH, top = Dimens.SafeTop),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap10),
    ) {
        Box(
            Modifier
                .size(Dimens.Logo)
                .clip(CircleShape)
                .background(colorResource(R.color.bg_dark)),
            contentAlignment = Alignment.Center,
        ) {
            // The adaptive-icon foreground keeps its art in the middle 2/3, so draw it 1.5x and let the circle crop it.
            Image(
                painter = painterResource(R.mipmap.ic_launcher_fg),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.requiredSize(Dimens.Logo * 1.5f),
            )
        }
        Row(
            Modifier
                .clip(Shapes.Pill)
                .background(MedusaColors.Surface.copy(alpha = 0.72f))
                .border(1.dp, MedusaColors.Line, Shapes.Pill)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Tab.entries.forEach { tab ->
                NavItem(tabLabel(tab), selected = tab == current, onClick = { onTab(tab) }, requester = focus.tabs.getValue(tab))
            }
        }
        Box(Modifier.weight(1f))
        IconCircle(MedusaIcons.Search, onSearch, Modifier.focusRequester(focus.search))
        IconCircle(MedusaIcons.Gear, onSettings, Modifier.focusRequester(focus.settings), dot = updateWaiting)
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit, requester: FocusRequester) {
    val f = rememberFocusable()
    val focused = f.focused.value
    val bg by animateColorAsState(
        when {
            selected -> MedusaColors.Fg
            focused -> MedusaColors.Raised
            else -> Color.Transparent
        },
        tween(Motion.ColorMs), label = "navBg",
    )
    Box(
        Modifier
            .focusRequester(requester)
            .focusRing(focused, offset = 2.dp)
            .height(Dimens.NavItemH)
            .clip(Shapes.Pill)
            .background(bg)
            .tvClick(f, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MedusaType.Nav,
            color = when {
                selected -> MedusaColors.Inv
                focused -> MedusaColors.Fg
                else -> MedusaColors.Muted
            },
        )
    }
}
