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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
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
fun ArtPlaceholder(label: String?, modifier: Modifier = Modifier, tint: Color = MedusaColors.Raised, shape: Shape = RectangleShape, url: String? = null) {
    Box(
        modifier.background(Brush.linearGradient(listOf(tint, MedusaColors.Surface)), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            // The real poster or backdrop from TMDB's image server, over the placeholder while it loads.
            coil3.compose.AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (shape == RectangleShape) Modifier else Modifier.graphicsLayer { this.shape = shape; clip = true }),
            )
        } else if (label != null) {
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
                // Lift, scale and the shadow that grows with the lift, all in one layer. Set here
                // (layer phase), the animation only redraws; as .shadow((14 * lift).dp) it rebuilt the
                // whole card on every frame of every focus move.
                .graphicsLayer {
                    translationY = -Motion.PosterLift * density * lift
                    val s = 1f + (Motion.PosterScale - 1f) * lift
                    scaleX = s; scaleY = s
                    shadowElevation = 14.dp.toPx() * lift
                    shape = Shapes.Poster
                    clip = false
                }
                .focusRing(focused, radius = 8.dp, width = 2.5.dp),
        ) {
            // Speed: the rounded art is drawn with its shape instead of clipped (a clip is an extra layer
            // per poster), and the 40 % fade layer is only added to watched titles.
            ArtPlaceholder(
                card.title,
                url = card.posterUrl,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (card.watched) {
                            Modifier.graphicsLayer { alpha = 0.4f; compositingStrategy = CompositingStrategy.ModulateAlpha }
                        } else {
                            Modifier
                        },
                    )
                    .border(1.dp, MedusaColors.Line, Shapes.Poster),
                shape = Shapes.Poster,
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
                        .background(Color(0xB8000000), Shapes.Pill)
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
                    Modifier.size(Dimens.HeartBtn).background(MedusaColors.CardScrim, CircleShape),
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
            if (card.dotArgb != null) Box(Modifier.size(5.dp).background(Color(card.dotArgb), CircleShape))
            Text(card.subtitle, style = MedusaType.PosterSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Shelf heading (.tn-rhead): title, optional small count, optional "See all". */
@Composable
fun ShelfHeader(title: String, modifier: Modifier = Modifier, count: Int? = null, dotArgb: Long? = null, seeAll: String? = null, onSeeAll: (() -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dotArgb != null) Box(Modifier.size(7.dp).background(Color(dotArgb), CircleShape))
        Text(title, style = MedusaType.ShelfTitle)
        if (count != null) Text(count.toString(), style = MedusaType.Mono, color = MedusaColors.Faint)
        if (seeAll != null && onSeeAll != null) LinkPill(seeAll, onSeeAll)
    }
}

/**
 * A sideways-scrolling row of posters (.tn-row). No scroll bar; focus comes back to the last poster you left.
 *
 * Speed: when the page is about to scroll a shelf into view, Compose builds it ahead of time in the
 * idle gaps between frames. By default that covers only the first 2 posters, so the rest of the row
 * was still built in the very frame the row appeared, which dropped frames on the TV. Prefetching a
 * whole screen width of posters moves that work out of the scroll.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ShelfRow(shelf: Shelf, onOpen: (String) -> Unit, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    val rowState = rememberLazyListState(prefetchStrategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = PostersPerScreen) })
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
            state = rowState,
            contentPadding = PaddingValues(start = Dimens.SafeH, end = Dimens.SafeH, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap14),
        ) {
            items(shelf.items, key = { it.id }, contentType = { "poster" }) { PosterCard(it, onOpen) }
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
                url = item.backdropUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .graphicsLayer { translationY = -2 * density * lift }
                    .focusRing(focused, radius = 9.dp, width = 2.5.dp)
                    .border(1.dp, MedusaColors.Line, ContinueShape),
                shape = ContinueShape,
            )
            ProgressLine(item.progress, MedusaColors.Accent, Modifier.fillMaxWidth().padding(top = 3.dp), height = 3.dp, track = MedusaColors.Line)
            Text(item.title, style = MedusaType.PosterTitle, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
            Text(item.progressLabel, style = MedusaType.PosterSub, maxLines = 1)
        }
        Pill("✓ " + item.nextLabel, onClick = { onTick(item.id) }, height = 22.dp, textStyle = MedusaType.Small, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Posters that fit across the screen at once (960 dp wide, [Dimens.PosterW] plus the gap): 8 and a part. */
private const val PostersPerScreen = 10

private val ContinueShape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)

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
