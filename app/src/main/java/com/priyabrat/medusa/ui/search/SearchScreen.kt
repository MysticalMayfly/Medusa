package com.priyabrat.medusa.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.priyabrat.medusa.R
import com.priyabrat.medusa.ui.browse.PageGrid
import com.priyabrat.medusa.ui.browse.fullWidth
import com.priyabrat.medusa.ui.browse.posterWall
import com.priyabrat.medusa.ui.components.Icon
import com.priyabrat.medusa.ui.components.MedusaIcons
import com.priyabrat.medusa.ui.components.focusRing
import com.priyabrat.medusa.ui.model.SearchUiState
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Shapes

/** Search (header .search): a pill field, then matching posters. It is for browsing, not upkeep. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    header: @Composable () -> Unit,
    fieldFocus: FocusRequester,
) {
    PageGrid {
        fullWidth("top") { header() }
        fullWidth("field") {
            Column(Modifier.padding(horizontal = Dimens.SafeH, vertical = Dimens.Gap8), verticalArrangement = Arrangement.spacedBy(Dimens.Gap10)) {
                val source = remember { MutableInteractionSource() }
                val focused by source.collectIsFocusedAsState()
                Row(
                    Modifier
                        .width(420.dp)
                        .focusRing(focused, offset = 2.dp)
                        .clip(Shapes.Pill)
                        .background(MedusaColors.Surface)
                        .border(1.dp, if (focused) MedusaColors.Fg else MedusaColors.Line, Shapes.Pill)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(MedusaIcons.Search, MedusaColors.Muted, 14.dp)
                    Box(Modifier.weight(1f)) {
                        if (state.query.isEmpty()) Text(stringResource(R.string.search_hint), style = MedusaType.Body, color = MedusaColors.Faint)
                        BasicTextField(
                            value = state.query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = MedusaType.BodyStrong,
                            cursorBrush = SolidColor(MedusaColors.Fg),
                            interactionSource = source,
                            modifier = Modifier.focusRequester(fieldFocus),
                        )
                    }
                }
                Text(state.resultLabel, style = MedusaType.Small)
            }
        }
        posterWall(state.results, onOpen)
    }
}
