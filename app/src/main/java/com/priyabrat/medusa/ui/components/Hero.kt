package com.priyabrat.medusa.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.priyabrat.medusa.R
import com.priyabrat.medusa.ui.model.HeroPick
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Motion
import kotlinx.coroutines.delay

/** What the carousel's buttons do. */
class HeroActions(
    val onOpen: (String) -> Unit,
    val onInterested: (String, Boolean) -> Unit,
    val onSeen: (String, Boolean) -> Unit,
)

/** Placeholder backdrop tints, so a change of pick is visible before real artwork exists. */
private val heroTints = listOf(Color(0xFF1B1A26), Color(0xFF231719), Color(0xFF132027), Color(0xFF1D2216))

/**
 * The carousel ("Tonight's pick", tonight.css .tn-hero): full-bleed backdrop with a slow 18 s zoom,
 * title, meta, ratings, synopsis and the buttons Watch now, Interested, More info, Seen it.
 * A still stays 8 s, then the next pick fades in; it waits while focus is inside the carousel.
 * Left on Watch now goes to the previous pick, Right on Seen it to the next one.
 *
 * Trailers (stage 1 milestone 6) will play behind the pick here; [HeroPick.trailerId] is the hook.
 *
 * [zoom]: the slow backdrop zoom. Off while the backdrop is a flat placeholder: there it cannot be
 * seen, yet it made the TV redraw most of the screen 60 times a second for as long as the carousel
 * was open. Stage 2 turns it on for picks with real artwork.
 */
@Composable
fun HeroCarousel(
    picks: List<HeroPick>,
    actions: HeroActions,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.HeroHeight,
    showPoster: Boolean = true,
    entry: FocusRequester? = null,
    zoom: Boolean = false,
) {
    if (picks.isEmpty()) {
        Spacer(modifier.height(height))
        return
    }
    val openLink = rememberOpenLink()
    var index by remember(picks.size) { mutableIntStateOf(0) }
    var focusInside by remember { mutableStateOf(false) }
    var chooserOpen by remember { mutableStateOf(false) }
    val i = index.coerceIn(0, picks.lastIndex)
    val pick = picks[i]
    fun step(by: Int) { index = (i + by + picks.size) % picks.size }

    // The playlist (tonight.js): a pick without a trailer stays 8 s; one with a trailer plays it, fades out before
    // the end, and the next pick comes AFTER_TRAILER later. A trailer that never starts falls back to the still.
    val covered = LocalTrailersPaused.current
    var onScreen by remember { mutableStateOf(true) }
    val paused = covered || !onScreen
    var trailer by remember(i) { mutableIntStateOf(TRAILER_PLAYING) }
    val trailerId = pick.trailerId?.takeIf { trailer != TRAILER_FAILED }
    LaunchedEffect(i, focusInside, chooserOpen, picks.size, trailer, trailerId, paused) {
        if (picks.size <= 1 || focusInside || chooserOpen || paused) return@LaunchedEffect
        when {
            trailerId == null -> { delay(Motion.HeroStillMs.toLong()); step(1) }
            trailer == TRAILER_ENDED -> { delay(TrailerTiming.AFTER_TRAILER_MS); step(1) }
        }
    }

    val scale: State<Float> = if (zoom) {
        rememberInfiniteTransition(label = "heroZoom").animateFloat(
            initialValue = 1.04f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(Motion.HeroZoomMs), RepeatMode.Reverse),
            label = "heroScale",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .onFocusChanged { focusInside = it.hasFocus }
            // Away: once the page scrolls down to the rows, the trailer pauses (the Mac waited until 45 % was out of
            // view; the TV pauses sooner, because a playing video behind scrolling rows made every frame slow on it).
            .onGloballyPositioned { c ->
                val shown = c.boundsInWindow().height
                onScreen = c.size.height == 0 || shown >= c.size.height * 0.9f
            },
    ) {
        // Speed: the backdrop and its two fades are painted once into one cached layer (Offscreen), so
        // while the page scrolls the TV's GPU copies one picture instead of filling the whole area
        // three times every frame. The layer is repainted only while the art changes (crossfade, zoom).
        Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            Crossfade(targetState = i, animationSpec = tween(700), label = "heroArt") { n ->
                ArtPlaceholder(
                    null,
                    Modifier.fillMaxSize().graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                    tint = heroTints[n % heroTints.size],
                    url = picks.getOrNull(n)?.backdropUrl,
                )
            }
        }
        // The trailer sits outside that cached layer: video frames must not repaint the backdrop under them.
        if (trailerId != null) {
            key(pick.id) {
                TrailerLayer(
                    videoId = trailerId,
                    paused = paused,
                    dipped = chooserOpen,
                    onEnded = { trailer = TRAILER_ENDED },
                    onFailed = { trailer = TRAILER_FAILED },
                )
            }
        }
        // The fades over backdrop and trailer, painted once into their own cached layer: one copy per frame instead of
        // two full-width gradient fills (the TV's GPU is the bottleneck when scrolling).
        Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            // .tn-art::after: fade to the page colour on the left and at the bottom
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to MedusaColors.Bg,
                        0.38f to MedusaColors.Bg.copy(alpha = 0.82f),
                        0.75f to MedusaColors.Bg.copy(alpha = 0.2f),
                        1f to Color.Transparent,
                    ),
                ),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to MedusaColors.Bg)))
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = Dimens.SafeH, end = Dimens.SafeH, bottom = Dimens.Gap26),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            key(pick.id) {
                Column(Modifier.widthIn(max = Dimens.HeroInfoMaxW).rise(), verticalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
                    Text(pick.eyebrow.uppercase(), style = MedusaType.Eyebrow)
                    Text(pick.title, style = MedusaType.HeroTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap10)) {
                        Text("${pick.year}  ·  ${pick.runtime}", style = MedusaType.MonoStrong)
                        pick.ratings.forEach { RatingBadge(it) }
                    }
                    Text(pick.overview, style = MedusaType.Body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(
                        Modifier.padding(top = Dimens.Gap8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap12),
                    ) {
                        WatchButton(
                            pick.watchLabel,
                            pick.watchOptions.map { it.monogram },
                            onClick = {
                                // One place to watch opens it straight away (tonight.js watchHtml); several open the chooser.
                                if (pick.watchOptions.size > 1) chooserOpen = true else openLink(pick.watchOptions.firstOrNull()?.url)
                            },
                            modifier = Modifier
                                .then(if (entry != null) Modifier.focusRequester(entry) else Modifier)

                        )
                        GlassRound(
                            if (pick.interested) MedusaIcons.HeartFilled else MedusaIcons.Heart,
                            onClick = { actions.onInterested(pick.id, !pick.interested) },
                            on = pick.interested,
                            onTint = MedusaColors.Accent,
                        )
                        GlassRound(MedusaIcons.Info, onClick = { actions.onOpen(pick.id) })
                        GlassRound(
                            MedusaIcons.Check,
                            onClick = { actions.onSeen(pick.id, !pick.seen) },
                            on = pick.seen,

                        )
                    }
                    if (picks.size > 1) {
                        Row(Modifier.padding(top = Dimens.Gap10), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            picks.indices.forEach { n ->
                                Box(
                                    Modifier
                                        .size(width = if (n == i) 32.dp else 17.dp, height = 3.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (n == i) MedusaColors.Fg else MedusaColors.Fg.copy(alpha = 0.28f)),
                                )
                            }
                        }
                    }
                }
            }
            if (showPoster) {
                key(pick.id) {
                    ArtPlaceholder(
                        pick.title,
                        url = pick.posterUrl,
                        modifier = Modifier
                            .rise()
                            .width(Dimens.HeroPosterW)
                            .aspectRatio(4f / 5f)
                            .clip(RoundedCornerShape(11.dp)),
                    )
                }
            }
        }
        // The Mac's ‹ › arrows (.tn-nav): previous and next pick, reached with left and right from the buttons.
        if (picks.size > 1) {
            IconCircle(MedusaIcons.ChevronLeft, { step(-1) }, Modifier.align(Alignment.CenterStart).padding(start = 8.dp), size = 32.dp)
            IconCircle(MedusaIcons.ChevronRight, { step(1) }, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), size = 32.dp)
        }
    }

    if (chooserOpen) {
        OptionsDialog(
            title = stringResource(R.string.watch_now),
            rows = pick.watchOptions.map { OptionRow(it.id, it.service, it.kind, it.monogram, 0xFF26262EL) },
            onPick = { row -> chooserOpen = false; openLink(pick.watchOptions.firstOrNull { it.id == row.id }?.url) },
            onDismiss = { chooserOpen = false },
        )
    }
}

private const val TRAILER_PLAYING = 0
private const val TRAILER_ENDED = 1
private const val TRAILER_FAILED = 2
