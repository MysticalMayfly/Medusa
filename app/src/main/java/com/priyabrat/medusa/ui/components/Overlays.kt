package com.priyabrat.medusa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.priyabrat.medusa.ui.model.FilterOption
import com.priyabrat.medusa.ui.model.FilterSelect
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Motion
import kotlinx.coroutines.delay

/* ------------------------------------------------------------------ toast (.toast) */

/** Shows a short message at the bottom of the screen. Provided by the app shell. */
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }

class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    private var serial by mutableStateOf(0)

    fun show(text: String) {
        message = text
        serial++
    }

    @Composable
    fun Host(modifier: Modifier = Modifier) {
        val current = message
        LaunchedEffect(serial) {
            if (current != null) {
                delay(Motion.ToastMs.toLong())
                message = null
            }
        }
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = current != null,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MedusaColors.Fg)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(current ?: "", style = MedusaType.ControlStrong, color = MedusaColors.Inv)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ option list (selects, Watch now chooser) */

/** One row of an option list. */
data class OptionRow(val id: String, val label: String, val detail: String? = null, val monogram: String? = null, val argb: Long? = null, val selected: Boolean = false)

/**
 * A remote-friendly list to pick from, shown over the screen (.tn-wmenu glass list).
 * Back closes it. Focus starts on the chosen row.
 */
@Composable
fun OptionsDialog(title: String, rows: List<OptionRow>, onPick: (OptionRow) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        val start = rows.indexOfFirst { it.selected }.coerceAtLeast(0)
        Column(
            Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF0101014))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MedusaType.Eyebrow, modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(rows) { i, row ->
                    val f = rememberFocusable()
                    val focused = f.focused.value
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (i == start) Modifier.focusRequester(first) else Modifier)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (focused) Color.White.copy(alpha = 0.95f) else Color.Transparent)
                            .tvClick(f) { onPick(row) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        if (row.monogram != null) Monogram(row.monogram, row.argb, 18.dp)
                        Text(
                            row.label,
                            style = MedusaType.ControlStrong,
                            color = if (focused) Color(0xFF0A0A0D) else Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        if (row.detail != null) {
                            Text(row.detail, style = MedusaType.Small, color = if (focused) Color(0x8C000000) else Color.White.copy(alpha = 0.6f))
                        }
                        if (row.selected) Icon(MedusaIcons.Check, if (focused) Color(0xFF0A0A0D) else Color.White, 12.dp)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

/** .sel: a dropdown pill. OK opens the option list. */
@Composable
fun SelectPill(select: FilterSelect, onPick: (FilterOption) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Pill(
        text = select.label,
        onClick = { open = true },
        modifier = modifier,
        trailing = MedusaIcons.ChevronDown,
        textStyle = if (select.active) MedusaType.ControlStrong else MedusaType.Control,
    )
    if (open) {
        OptionsDialog(
            title = select.options.firstOrNull()?.label ?: "",
            rows = select.options.map { OptionRow(it.id, it.label, it.count?.toString(), selected = it.selected) },
            onPick = { row -> open = false; select.options.firstOrNull { it.id == row.id }?.let(onPick) },
            onDismiss = { open = false },
        )
    }
}
