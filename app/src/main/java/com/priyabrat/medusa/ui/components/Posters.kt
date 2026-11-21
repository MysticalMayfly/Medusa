package com.priyabrat.medusa.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.priyabrat.medusa.ui.model.ContinueItem
import com.priyabrat.medusa.ui.model.Shelf
import com.priyabrat.medusa.ui.model.TitleCard
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Motion
import com.priyabrat.medusa.ui.theme.Shapes

/**
 * Placeholder artwork (.pc-img.ph): a soft gradient with the title in the middle.
 * Stage 2 replaces this with the real poster or backdrop image.
 */
@Composable
fun ArtPlaceholder(label: String?, modifier: Modifier = Modifier, tint: Color = MedusaColors.Raised) {
    Box(
        modifier.background(Brush.linearGradient(listOf(tint, MedusaColors.Surface))),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                label,
                style = MedusaType.CardTitle,
                color = MedusaColors.Muted,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(9.dp),
            )
        }
    }
}

/**
 * A poster (.pc on walls, .tn-item on shelves). Focus lifts it 4 px, scales it to 1.03 and draws a 3 px ring.
 * The Interested heart shows on focus, and stays when the title is marked Interested.
 */
@Composable
fun PosterCard(card: TitleCard, onOpen: (String) -> Unit, modifier: Modifier = Modifier, width: Dp? = Dimens.PosterW) {
    val f = rememberFocusable()
    val focused = f.focused.value
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.LiftMs, easing = Motion.Ease), label = "lift")
    Column(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .tvClick(f) { onOpen(card.id) },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer {
                    translationY = -Motion.PosterLift * density * lift
                    val s = 1f + (Motion.PosterScale - 1f) * lift
                    scaleX = s; scaleY = s
                }
                .focusRing(focused, radius = 8.dp, width = 2.5.dp)
                // The shadow grows with the lift. Set here (layer phase), the animation only redraws;
                // as .shadow((14 * lift).dp) it rebuilt the whole card on every frame of every focus move.
                .graphicsLayer {
                    shadowElevation = 14.dp.toPx() * lift
                    shape = Shapes.Poster
                    clip = false
                },
        ) {
            ArtPlaceholder(
                card.title,
                Modifier
                    .fillMaxSize()
                    .clip(Shapes.Poster)
                    .border(1.dp, MedusaColors.Line, Shapes.Poster)
                    .graphicsLayer {
                        alpha = if (card.watched) 0.4f else 1f
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            )
            if (card.badge != null) {
                Text(
                    card.badge,
                    style = MedusaType.Small,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(Shapes.Pill)
                        .background(Color(0xB8000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = focused || card.interested,
                enter = fadeIn(tween(Motion.HeartMs)),
                exit = fadeOut(tween(Motion.HeartMs)),
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            ) {
                Box(
                    Modifier.size(Dimens.HeartBtn).clip(CircleShape).background(MedusaColors.CardScrim),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (card.interested) MedusaIcons.HeartFilled else MedusaIcons.Heart,
                        if (card.interested) MedusaColors.Accent else Color.White,
                        11.dp,
                    )
                }
            }
            if (card.progress != null) {
                ProgressLine(
                    card.progress,
                    MedusaColors.Accent,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    height = 3.dp,
                    track = Color.White.copy(alpha = 0.25f),
                )
            }
        }
        Text(
            card.title,
            style = MedusaType.PosterTitle,
            color = if (card.watched) MedusaColors.Muted else MedusaColors.Fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (card.dotArgb != null) Box(Modifier.size(5.dp).clip(CircleShape).background(Color(card.dotArgb)))
            Text(card.subtitle, style = MedusaType.PosterSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Shelf heading (.tn-rhead): title, optional small count, optional "See all". */
@Composable
fun ShelfHeader(title: String, modifier: Modifier = Modifier, count: Int? = null, dotArgb: Long? = null, seeAll: String? = null, onSeeAll: (() -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dotArgb != null) Box(Modifier.size(7.dp).clip(CircleShape).background(Color(dotArgb)))
        Text(title, style = MedusaType.ShelfTitle)
        if (count != null) Text(count.toString(), style = MedusaType.Mono, color = MedusaColors.Faint)
        if (seeAll != null && onSeeAll != null) LinkPill(seeAll, onSeeAll)
    }
}

/** A sideways-scrolling row of posters (.tn-row). No scroll bar; focus comes back to the last poster you left. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShelfRow(shelf: Shelf, onOpen: (String) -> Unit, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        ShelfHeader(
            shelf.title,
            Modifier.padding(horizontal = Dimens.SafeH),
            count = shelf.count,
            dotArgb = shelf.dotArgb,
            seeAll = shelf.seeAllLabel,
            onSeeAll = onSeeAll,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(start = Dimens.SafeH, end = Dimens.SafeH, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap14),
        ) {
            items(shelf.items, key = { it.id }) { PosterCard(it, onOpen) }
        }
    }
}

/** Continue watching (.tn-c): a 16:9 still with a progress bar, then a button to tick the next episode. */
@Composable
fun ContinueCard(item: ContinueItem, onOpen: (String) -> Unit, onTick: (String) -> Unit) {
    val f = rememberFocusable()
    val focused = f.focused.value
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.LiftMs, easing = Motion.Ease), label = "clift")
    Column(Modifier.width(Dimens.ContinueW), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Column(Modifier.tvClick(f) { onOpen(item.id) }) {
            ArtPlaceholder(
                item.title,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .graphicsLayer { translationY = -2 * density * lift }
                    .focusRing(focused, radius = 9.dp, width = 2.5.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                    .border(1.dp, MedusaColors.Line, androidx.compose.foundation.shape.RoundedCornerShape(9.dp)),
            )
            ProgressLine(item.progress, MedusaColors.Accent, Modifier.fillMaxWidth().padding(top = 3.dp), height = 3.dp, track = MedusaColors.Line)
            Text(item.title, style = MedusaType.PosterTitle, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
            Text(item.progressLabel, style = MedusaType.PosterSub, maxLines = 1)
        }
        Pill("✓ " + item.nextLabel, onClick = { onTick(item.id) }, height = 22.dp, textStyle = MedusaType.Small, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Spacer height between page sections. */
val SectionGap: Dp = Dimens.Gap26

/** Empty-state text (.empty). */
@Composable
fun EmptyNote(title: String, note: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MedusaType.CardTitle)
        Text(note, style = MedusaType.Body)
    }
}
