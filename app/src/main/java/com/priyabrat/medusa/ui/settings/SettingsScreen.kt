package com.priyabrat.medusa.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.priyabrat.medusa.R
import com.priyabrat.medusa.ui.components.Pill
import com.priyabrat.medusa.ui.components.PillStyle
import com.priyabrat.medusa.ui.components.ProgressLine
import com.priyabrat.medusa.ui.components.rise
import com.priyabrat.medusa.ui.model.SettingsUiState
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Shapes

/**
 * Settings: one simple page with only Check for updates (moved here from the old home screen).
 * The button's wiring to the Updater stays in MainActivity (docs/RULES.md rule 11).
 */
@Composable
fun SettingsScreen(state: SettingsUiState, onUpdateButton: () -> Unit, onImportButton: () -> Unit, header: @Composable () -> Unit, buttonFocus: FocusRequester) {
    Column(Modifier.fillMaxSize()) {
        header()
        Column(
            Modifier.padding(horizontal = Dimens.SafeH, vertical = Dimens.Gap22).rise(),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap22),
        ) {
            Text(stringResource(R.string.settings), style = MedusaType.PageTitle)
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap12)) {
                Text(stringResource(R.string.updates), style = MedusaType.ShelfTitle)
                Column(
                    Modifier
                        .width(360.dp)
                        .clip(Shapes.Card)
                        .background(MedusaColors.Surface)
                        .border(1.dp, MedusaColors.Line, Shapes.Card)
                        .padding(Dimens.Gap16),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Gap8),
                ) {
                    Text(state.version, style = MedusaType.BodyStrong)
                    Text(state.status, style = MedusaType.Body)
                    state.progressPercent?.let {
                        ProgressLine(it / 100f, MedusaColors.Accent, Modifier.fillMaxWidth().padding(top = 4.dp), height = 4.dp)
                    }
                    Pill(
                        state.buttonLabel,
                        onClick = onUpdateButton,
                        modifier = Modifier.padding(top = Dimens.Gap8),
                        style = PillStyle.Primary,
                        enabled = state.buttonEnabled,
                        height = 32.dp,
                        textStyle = MedusaType.ControlStrong,
                    )
                }
            }
            // Library: the one-time copy of the Mac app's collection (scripts/export_library.py --serve on the Mac).
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap12)) {
                Text(stringResource(R.string.library), style = MedusaType.ShelfTitle)
                Column(
                    Modifier
                        .width(360.dp)
                        .clip(Shapes.Card)
                        .background(MedusaColors.Surface)
                        .border(1.dp, MedusaColors.Line, Shapes.Card)
                        .padding(Dimens.Gap16),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Gap8),
                ) {
                    Text(state.libraryStatus, style = MedusaType.Body)
                    Pill(
                        state.importLabel,
                        onClick = onImportButton,
                        // Settings opens here, not on the update button: a stray OK there installed an update.
                        modifier = Modifier.padding(top = Dimens.Gap8).focusRequester(buttonFocus),
                        enabled = state.importEnabled,
                        height = 32.dp,
                        textStyle = MedusaType.ControlStrong,
                    )
                }
            }
        }
    }
}
