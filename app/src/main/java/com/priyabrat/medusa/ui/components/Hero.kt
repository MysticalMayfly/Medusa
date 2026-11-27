package com.priyabrat.medusa.ui.components

import kotlinx.coroutines.launch

import androidx.compose.ui.focus.focusProperties

import androidx.compose.foundation.focusGroup

import androidx.compose.foundation.border

import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewResponder

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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
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
 * Trailers play only while focus is inside; then the carousel grows to the full screen height.
 * Left and right on Watch now page it; Left on Interested goes back, Right on Seen it forward.
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
    height: Dp = heroRestHeight(),
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
    // Tells the shell the carousel has focus (it shrinks and fades the top bar).
    val heroFocus = LocalHeroFocus.current
    androidx.compose.runtime.DisposableEffect(heroFocus) { onDispose { heroFocus?.value = false } }
    var chooserOpen by remember { mutableStateOf(false) }
    val i = index.coerceIn(0, picks.lastIndex)
    val pick = picks[i]
    // From the current index state, not the value captured at the last draw: quick presses each count.
    fun step(by: Int) { index = (index.coerceIn(0, picks.lastIndex) + by + picks.size) % picks.size }
    val mainButton = entry ?: remember { FocusRequester() }
    val firstRound = remember { FocusRequester() }
    var heroSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val wholeHero = remember { WholeHero { heroSize } }
    val bringHero = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Load every pick's backdrop ahead, so a new slide's picture is there with its text (it used to arrive late).
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(picks) {
        val loader = coil3.SingletonImageLoader.get(context)
        picks.mapNotNull { it.backdropUrl }.forEach { loader.enqueue(coil3.request.ImageRequest.Builder(context).data(it).build()) }
    }

    // The playlist (tonight.js): a pick without a trailer stays 8 s; one with a trailer plays it, fades out before
    // the end, and the next pick comes AFTER_TRAILER later. A trailer that never starts falls back to the still.
    val covered = LocalTrailersPaused.current
    var onScreen by remember { mutableStateOf(true) }
    val paused = covered || !onScreen
    var trailer by remember(i) { mutableIntStateOf(TRAILER_PLAYING) }
    // Once the trailer is really playing, the still picture and the poster fade away behind it.
    var trailerShown by remember(i) { mutableStateOf(false) }
    val stillAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (trailerShown) 0f else 1f, tween(TrailerTiming.FADE_MS), label = "heroStill",
    )
    // Trailers play only while focus is in the carousel; otherwise it turns through the stills.
    LaunchedEffect(focusInside) {
        if (!focusInside) { trailerShown = false; trailer = TRAILER_PLAYING }
    }
    val trailerId = pick.trailerId?.takeIf { trailer != TRAILER_FAILED && focusInside }
    // A few seconds into a trailer the description fades and the left fade lightens, so most of the video shows.
    // Any button press brings the full look back for a few seconds.
    var nudge by remember { mutableStateOf(0) }
    var quiet by remember(i) { mutableStateOf(false) }
    LaunchedEffect(trailerShown, nudge) {
        quiet = false
        if (trailerShown) { delay(TrailerTiming.QUIET_AFTER_MS); quiet = true }
    }
    val quietness by androidx.compose.animation.core.animateFloatAsState(
        if (quiet && trailerShown) 1f else 0f, tween(TrailerTiming.FADE_MS), label = "heroQuiet",
    )
    LaunchedEffect(i, focusInside, chooserOpen, picks.size, trailer, trailerId, paused) {
        if (picks.size <= 1 || chooserOpen || paused) return@LaunchedEffect
        when {
            // Not focused: the stills turn every few seconds.
            !focusInside -> { delay(Motion.HeroStillMs.toLong()); step(1) }
            // Focused: the next pick comes only after a trailer ends; a still waits for the remote.
            trailerId != null && trailer == TRAILER_ENDED -> { delay(TrailerTiming.AFTER_TRAILER_MS); step(1) }
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

    // With focus inside, the carousel grows to the full screen height and pushes the shelves below further down.
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val grown by androidx.compose.animation.core.animateDpAsState(
        if (focusInside) maxOf(height, screenH) else height, tween(Motion.ColorMs * 2), label = "heroHeight",
    )
    Box(
        modifier
            .fillMaxWidth()
            .layout { m, c ->
                val h = grown.roundToPx()
                val p = m.measure(c.copy(minHeight = h, maxHeight = h))
                layout(p.width, h) { p.place(0, 0) }
            }
            .onFocusChanged {
                heroFocus?.value = it.hasFocus
                // Whenever focus is in the carousel, show all of it: the title stays on screen with the buttons.
                if (it.hasFocus && !focusInside) scope.launch { bringHero.bringIntoView() }
                focusInside = it.hasFocus
            }
            .bringIntoViewRequester(bringHero)
            .onSizeChanged { heroSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
            // A focused button asks the page to scroll it into view; the page would move it towards the middle and
            // push the carousel half off screen. Every such request is turned into "show the whole carousel".
            .bringIntoViewResponder(wholeHero)
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) nudge++; false }

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
        Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = stillAlpha }) {
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
                    onShown = { trailerShown = it },
                )
            }
        }
        // The fades over backdrop and trailer, painted once into their own cached layer: one copy per frame instead of
        // two full-width gradient fills (the TV's GPU is the bottleneck when scrolling).
        // Full look (still picture): solid to 82 % to 20 % black. Quiet look (trailer playing): 50 % black, clear by the middle.
        Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = 1f - quietness }) {
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
        if (quietness > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = quietness }) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to MedusaColors.Bg.copy(alpha = 0.5f), 0.5f to Color.Transparent),
                    ),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.7f to Color.Transparent, 1f to MedusaColors.Bg)))
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = Dimens.SafeH, end = Dimens.SafeH, bottom = Dimens.Gap26),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.widthIn(max = Dimens.HeroInfoMaxW), verticalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
                // Only the text is rebuilt per slide; the buttons below stay, so focus stays on them while paging.
                // Prince, 2026-11-27: the old text fades out, then the new one fades in (no rise).
                androidx.compose.animation.AnimatedContent(
                    targetState = pick,
                    contentKey = { it.id },
                    transitionSpec = { slideFade() },
                    contentAlignment = Alignment.BottomStart,
                    label = "heroText",
                ) { pick ->
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
                        if (pick.eyebrow.isNotEmpty()) Text(pick.eyebrow.uppercase(), style = MedusaType.Eyebrow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // One line at one size, so every slide lines up. Prince, 2026-11-27: up to 22 characters (spaces
                        // count) show in full, so the title may run wider than the text column, up to the poster; longer
                        // titles stop at 22 and end with an ellipsis.
                        Text(
                            if (pick.title.length > TITLE_MAX_CHARS) pick.title.take(TITLE_MAX_CHARS).trimEnd() + "…" else pick.title,
                            style = MedusaType.HeroTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false,
                            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).widthIn(max = TITLE_MAX_W),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap10)) {
                            // Year · runtime with a tight dot: the dot is drawn in the proportional font (the mono font's
                            // dot and spaces are a full character wide each).
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap4)) {
                                listOf(pick.year, pick.runtime).filter { it.isNotBlank() }.forEachIndexed { n, part ->
                                    if (n > 0) Text("·", style = MedusaType.MonoStrong.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Default))
                                    Text(part, style = MedusaType.MonoStrong)
                                }
                            }
                            pick.ratings.forEach { RatingBadge(it) }
                        }
                        // Always two lines tall (minLines), so the title sits at the same height on every slide.
                        // While the trailer plays the synopsis fades and folds away, so the title and the line under it
                        // slide down to sit just above Watch now instead of leaving a gap. Read in layout and draw only.
                        Text(
                            pick.overview, style = MedusaType.Body, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .graphicsLayer { alpha = 1f - quietness; clip = true }
                                .layout { m, c ->
                                    val p = m.measure(c)
                                    layout(p.width, (p.height * (1f - quietness)).toInt()) { p.place(0, 0) }
                                },
                        )
                    }
                }
                    // Watch now, with the three round buttons under it. Left and right on Watch now page the carousel;
                    // left on Interested goes to the previous pick and right on Seen it to the next.
                    fun Modifier.pages(left: Boolean, right: Boolean) = onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown || picks.size <= 1) false
                        else if (left && e.key == Key.DirectionLeft) { step(-1); true }
                        else if (right && e.key == Key.DirectionRight) { step(1); true }
                        else false
                    }
                    WatchButton(
                        pick.watchLabel,
                        pick.watchOptions.map { it.monogram },
                        onClick = {
                            // One place to watch opens it straight away (tonight.js watchHtml); several open the chooser.
                            if (pick.watchOptions.size > 1) chooserOpen = true else openLink(pick.watchOptions.firstOrNull()?.url)
                        },
                        modifier = Modifier
                            .padding(top = Dimens.Gap8)
                            .focusRequester(mainButton)
                            // Down goes to the first round button (Interested), not the one nearest the middle.
                            .focusProperties { down = firstRound }
                            .pages(left = true, right = true),
                    )
                    Row(
                        Modifier.padding(top = Dimens.Gap4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Gap12),
                    ) {
                        GlassRound(
                            if (pick.interested) MedusaIcons.HeartFilled else MedusaIcons.Heart,
                            onClick = { actions.onInterested(pick.id, !pick.interested) },
                            on = pick.interested,
                            onTint = MedusaColors.Accent,
                            modifier = Modifier.focusRequester(firstRound).pages(left = true, right = false),
                        )
                        GlassRound(MedusaIcons.Info, onClick = { actions.onOpen(pick.id) })
                        GlassRound(
                            MedusaIcons.Check,
                            onClick = { actions.onSeen(pick.id, !pick.seen) },
                            on = pick.seen,
                            modifier = Modifier.pages(left = false, right = true),
                        )
                    }
                }
            if (showPoster) {
                androidx.compose.animation.AnimatedContent(
                    targetState = pick,
                    contentKey = { it.id },
                    transitionSpec = { slideFade() },
                    label = "heroPoster",
                ) { pick ->
                    ArtPlaceholder(
                        pick.title,
                        url = pick.posterUrl,
                        modifier = Modifier
                            .graphicsLayer { alpha = stillAlpha }
                            .width(Dimens.HeroPosterW)
                            .aspectRatio(4f / 5f)
                            .clip(RoundedCornerShape(11.dp)),
                    )
                }
            }
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

/** A slide change: the old text and poster fade out, then the new ones fade in, in step with the backdrop's 700 ms. */
private fun slideFade() = androidx.compose.animation.ContentTransform(
    targetContentEnter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 300)),
    initialContentExit = androidx.compose.animation.fadeOut(tween(300)),
    sizeTransform = androidx.compose.animation.SizeTransform(clip = false),
)

private const val TITLE_MAX_CHARS = 22
private val TITLE_MAX_W = 700.dp   // from the left margin to just short of the poster on a 960 dp wide screen

private const val TRAILER_PLAYING = 0
private const val TRAILER_ENDED = 1
private const val TRAILER_FAILED = 2

/** Set by the carousel while it has focus; the shell shrinks and fades the top bar then. */
val LocalHeroFocus = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.MutableState<Boolean>?> { null }

/**
 * The carousel's height at rest: it fills the screen except for a strip at the bottom, where the first shelf's
 * title and the top edge of its pictures show.
 */
@Composable
fun heroRestHeight(): Dp = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - PEEK_DP).dp

private const val PEEK_DP = 78

/** Turns a button's scroll request into one for the whole carousel (see [HeroCarousel]). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private class WholeHero(private val size: () -> androidx.compose.ui.geometry.Size) : androidx.compose.foundation.relocation.BringIntoViewResponder {
    override fun calculateRectForParent(localRect: androidx.compose.ui.geometry.Rect) =
        androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, size())
    override suspend fun bringChildIntoView(localRect: () -> androidx.compose.ui.geometry.Rect?) = Unit
}
