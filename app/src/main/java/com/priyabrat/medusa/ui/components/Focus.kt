package com.priyabrat.medusa.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.Motion

/*
 * Hover becomes focus: the Mac app's hover and :focus-visible styles are shown when the
 * remote's focus lands on an element. OK (D-pad centre) clicks it.
 */

/** A focusable, clickable element and whether it has focus right now. */
class Focusable(val source: MutableInteractionSource, val focused: State<Boolean>)

@Composable
fun rememberFocusable(): Focusable {
    val source = remember { MutableInteractionSource() }
    return Focusable(source, source.collectIsFocusedAsState())
}

/** Makes the element focusable and clickable with OK, without a ripple (the look comes from [focusRing] etc.). */
fun Modifier.tvClick(f: Focusable, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(interactionSource = f.source, indication = null, enabled = enabled, onClick = onClick)

/**
 * The Mac app's focus outline: a ring drawn outside the element ([Dimens.RingOffset] away).
 * [radius] null means a pill (fully round ends).
 */
fun Modifier.focusRing(
    focused: Boolean,
    radius: Dp? = null,
    width: Dp = Dimens.RingWidth,
    offset: Dp = Dimens.RingOffset,
    color: Color = MedusaColors.Fg,
): Modifier = drawWithContent {
    drawContent()
    if (focused) {
        val w = width.toPx()
        val o = offset.toPx() + w / 2
        val r = (radius?.toPx() ?: (size.height / 2)) + o
        drawRoundRect(
            color = color,
            topLeft = Offset(-o, -o),
            size = Size(size.width + 2 * o, size.height + 2 * o),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(w),
        )
    }
}

/**
 * Entrance: rise 8 px and fade in over 300 ms (theme.css .ent). Runs once when the element first appears.
 *
 * Speed: lazy lists drop rows that scroll off screen and rebuild them when they come back. The saved
 * flag stops a returning row from fading in again, which would add drawing exactly while the TV scrolls.
 * The fade is applied to each draw call (ModulateAlpha) instead of through an offscreen copy of the
 * whole row, which is costly on the TV's GPU.
 */
@Composable
fun Modifier.rise(delayMs: Int = 0): Modifier {
    var played by rememberSaveable { mutableStateOf(false) }
    val p = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            p.animateTo(1f, tween(Motion.RiseMs, delayMillis = delayMs, easing = Motion.Ease))
            played = true
        }
    }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * Motion.RiseOffset.toPx()
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}
