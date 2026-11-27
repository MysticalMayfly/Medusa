package com.priyabrat.medusa.ui.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors

/**
 * Where the header sits. The header itself lives once in the shell, above the pages, so it never leaves the
 * tree and never loses focus when the page changes. Each page keeps a [HeaderSlot] where it used to draw the
 * header; the header follows the slot as the page scrolls and hides when the slot scrolls out of the list.
 */
class HeaderAnchor {
    internal val y = mutableFloatStateOf(0f)
    internal val slots = mutableIntStateOf(0)
    val visible: Boolean get() = slots.intValue > 0
    val offsetY: Float get() = y.floatValue
}

/** The empty space a page keeps for the header. */
@Composable
fun HeaderSlot(anchor: HeaderAnchor) {
    DisposableEffect(anchor) {
        anchor.slots.intValue++
        onDispose { anchor.slots.intValue-- }
    }
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .onGloballyPositioned { anchor.y.floatValue = it.positionInRoot().y },
    )
}

/**
 * The placeholder over a page while it opens: black with the page's rough shape (a carousel block when the page
 * has one, then a title and a row of posters). It covers the page fully while navigation is moving, then fades out
 * once, so the page appears all at once. Drawn in one pass, no layout of its own; it reads its alpha only while
 * drawing, so the fade never recomposes anything.
 */
@Composable
fun PageVeil(pending: Boolean, alpha: State<Float>, heroHeight: Dp?) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val a = if (pending) 1f else alpha.value
                if (a <= 0f) return@drawBehind
                drawRect(MedusaColors.Bg, alpha = a)
                val safeH = Dimens.SafeH.toPx()
                var top: Float
                if (heroHeight != null) {
                    drawRect(MedusaColors.Surface, size = Size(size.width, heroHeight.toPx()), alpha = a)
                    top = heroHeight.toPx() + Dimens.Gap22.toPx()
                } else {
                    top = HeaderHeight.toPx() + Dimens.Gap36.toPx()
                }
                val r = CornerRadius(Dimens.Gap8.toPx())
                drawRoundRect(MedusaColors.Raised, Offset(safeH, top), Size(Dimens.Gap36.toPx() * 7, Dimens.Gap16.toPx()), r, alpha = a)
                top += Dimens.Gap16.toPx() + Dimens.Gap22.toPx()
                val w = Dimens.HeroPosterW.toPx() * 0.75f
                val gap = Dimens.Gap14.toPx()
                var x = safeH
                while (x + w <= size.width - safeH) {
                    drawRoundRect(MedusaColors.Card, Offset(x, top), Size(w, w * 1.5f), r, alpha = a)
                    x += w + gap
                }
            },
    )
}
