package com.priyabrat.medusa.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Dark only, like the Mac app's dark theme. */
@Composable
fun MedusaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MedusaColors.Fg,
            onPrimary = MedusaColors.Inv,
            background = MedusaColors.Bg,
            onBackground = MedusaColors.Fg,
            surface = MedusaColors.Surface,
            onSurface = MedusaColors.Fg,
        ),
        content = content,
    )
}
