package com.priyabrat.medusa.ui.components

import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Carousel trailers, ported from the Mac app's engine (public/tonight.js, brief B5), with YouTube's own embedded
 * player (android-youtube-player). The player starts hidden and muted; it is shown only after SIGN_S of real
 * playback, because YouTube draws its pause sign for 3.95 s after every start or resume (measured on the Mac).
 * Picture and sound fade together; the trailer fades out END_LEAD before its end so YouTube's replay screen never
 * shows. Then the carousel waits AFTER_TRAILER and moves on. A trailer that doesn't start falls back to the still.
 *
 * Dropped from the Mac: postMessage plumbing (the library does it) and the instant stop for macOS timers.
 * Stage 2 hook: broken-trailer reporting ("Trailer won't play" in the Mac's Review) is not on the TV; onFailed is
 * where it would go.
 */
object TrailerTiming {
    const val START_AT_MS = 900L        // a trailer pick starts its hidden player this long after it appears
    const val SIGN_S = 4.1f             // seconds of real playback before it is shown (measure again on the TV)
    const val FADE_MS = 1200            // picture and sound fade together
    const val QUIET_AFTER_MS = 3000L    // then the hero text and fade step back so the video shows
    const val END_LEAD_S = 6f           // start fading this many seconds before the end
    const val AFTER_TRAILER_MS = 3000L  // then the next pick, this much later
    const val NO_START_MS = 10_000L     // no playback by then: show the still instead
}

/** True while something covers the carousel (the details panel, a dialog): trailers pause. */
val LocalTrailersPaused = compositionLocalOf { false }

/**
 * Plays [videoId] behind the carousel pick. [paused] pauses it and hides it at once; resuming waits for SIGN_S again.
 * [dipped] lowers the sound to 30 % (the Mac's Watch-list behaviour). [onEnded] fires once when it has faded out at
 * the end, [onFailed] once when it never started.
 */
@Composable
fun TrailerLayer(
    videoId: String,
    paused: Boolean,
    dipped: Boolean,
    onEnded: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    onShown: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val ended by rememberUpdatedState(onEnded)
    val failed by rememberUpdatedState(onFailed)
    val isPaused by rememberUpdatedState(paused)

    // One player for the whole app (SharedTrailerPlayer): this layer only borrows it, so focusing the carousel or
    // paging to the next pick no longer builds a new WebView (that stalled the TV for 130 to 170 ms).
    val view = remember { SharedTrailerPlayer.warmUp(context) }
    val player = SharedTrailerPlayer.player
    var second by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var playingSince by remember { mutableFloatStateOf(-1f) }   // playback second when this run of playing began
    var errors by remember { mutableIntStateOf(0) }
    val alpha = remember { Animatable(0f) }

    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose {}
        val listener = object : AbstractYouTubePlayerListener() {
            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) { view.post { setSecond(second) } }
            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) { view.post { setDuration(duration) } }
            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) { view.post { setState(state) } }
            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) { android.util.Log.d("MedusaTrailer", "error $error"); view.post { errors += 1 } }
            // Setters reach the Compose state from the player's thread via post().
            private fun setSecond(s: Float) { second = s }
            private fun setDuration(d: Float) { duration = d }
            private fun setState(st: PlayerConstants.PlayerState) {
                if (st == PlayerConstants.PlayerState.PLAYING) { hideCaptions(view); if (playingSince < 0f) playingSince = second } else playingSince = -1f
            }
        }
        p.addListener(listener)
        // Leaving: stop listening and silence the shared player; the next layer loads its own video into it.
        onDispose { p.removeListener(listener); p.setVolume(0); p.mute(); p.pause() }
    }

    // Load the trailer hidden and muted, a moment after the pick appears.
    LaunchedEffect(player, videoId) {
        val p = player ?: return@LaunchedEffect
        alpha.snapTo(0f); second = 0f; duration = 0f; playingSince = -1f
        p.mute(); p.setVolume(0)
        delay(TrailerTiming.START_AT_MS)
        p.loadVideo(videoId, 0f)
        delay(TrailerTiming.NO_START_MS)
        if (duration <= 0f || second <= 0.5f) failed()
    }
    LaunchedEffect(errors) { if (errors > 0) failed() }

    // Pause and resume: hidden and silent at once; shown again only after SIGN_S of playback.
    LaunchedEffect(player, paused) {
        val p = player ?: return@LaunchedEffect
        if (paused) { alpha.snapTo(0f); p.setVolume(0); p.mute(); p.pause() } else if (second > 0f) p.play()
    }

    // Reveal once playing for SIGN_S; fade out END_LEAD before the end; then tell the carousel.
    var revealed by remember(videoId) { mutableStateOf(false) }
    val shown by rememberUpdatedState(onShown)
    LaunchedEffect(revealed) { shown(revealed) }
    var fading by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(second, playingSince, paused, revealed, fading) {
        if (player == null) return@LaunchedEffect
        if (paused || fading) return@LaunchedEffect
        val played = if (playingSince >= 0f) second - playingSince else 0f
        if (!revealed && played >= TrailerTiming.SIGN_S) revealed = true
        if (revealed && duration > 0f && second >= duration - TrailerTiming.END_LEAD_S) fading = true
    }
    // Playback timestamps update repeatedly. Keep fades in a separate effect so those updates cannot cancel them.
    LaunchedEffect(player, videoId, revealed, fading, paused) {
        val p = player ?: return@LaunchedEffect
        if (paused) return@LaunchedEffect
        if (fading) {
            launch { fadeVolume(p, if (dipped) 30 else 100, 0) }
            alpha.animateTo(0f, tween(TrailerTiming.FADE_MS, easing = LinearEasing))
            p.pause()
            if (!isPaused) ended()
        } else if (revealed) {
            p.unMute()
            launch { fadeVolume(p, 0, if (dipped) 30 else 100) }
            alpha.animateTo(1f, tween(TrailerTiming.FADE_MS, easing = LinearEasing))
        }
    }
    // A shown trailer resumed after a pause has to wait out YouTube's sign again.
    LaunchedEffect(paused) { if (paused) revealed = false }
    LaunchedEffect(dipped) { if (revealed && !fading) player?.setVolume(if (dipped) 30 else 100) }

    // Cover the carousel like a background image: 16:9, cropped to fill.
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val w = maxOf(maxWidth, maxHeight * (16f / 9f))
        val h = w * (9f / 16f)
        // Full size: the video surface ignores Compose scaling, so a half-size player scaled up only filled part of
        // the carousel and left the backdrop showing beside it.
        AndroidView(
            // The shared view may still sit in the previous pick's layer for a moment: take it from there.
            factory = { (view.parent as? ViewGroup)?.removeView(view); view },
            modifier = Modifier
                .requiredSize(w, h)
                .focusProperties { canFocus = false }
                .graphicsLayer { this.alpha = alpha.value },
        )
    }
}

/**
 * The app's single trailer player. [warmUp] builds it and loads YouTube's player page; the loading screen calls it
 * 5 s in, so the player is ready before Home shows. It lives until the activity is destroyed.
 */
object SharedTrailerPlayer {
    private var view: YouTubePlayerView? = null
    var player by mutableStateOf<YouTubePlayer?>(null)
        private set

    fun warmUp(context: android.content.Context): YouTubePlayerView {
        view?.let { return it }
        val activity = context.findActivity()
        val v = YouTubePlayerView(activity).apply {
            enableAutomaticInitialization = false
            // The player is a picture behind the pick, never a control: its WebView must not take the remote's focus
            // (it did, and the D-pad then went nowhere after switching tabs).
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val options = IFramePlayerOptions.Builder(activity)
                .controls(0).rel(0).ivLoadPolicy(3).ccLoadPolicy(0).fullscreen(0).autoplay(0).mute(1)
                .build()
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) { this@apply.post { if (view === this@apply) player = youTubePlayer } }
                // YouTube turns captions on by itself for a muted video (every trailer starts muted), whatever
                // cc_load_policy says. Unload the captions module whenever it loads and whenever playback starts.
                override fun onApiChange(youTubePlayer: YouTubePlayer) { this@apply.post { hideCaptions(this@apply) } }
            }, false, options)
        }
        view = v
        activity.lifecycle.addObserver(v)
        activity.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            // The view releases itself on destroy (it observes the lifecycle too); forget it here.
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                if (view === v) { view = null; player = null }
            }
        })
        return v
    }
}

private tailrec fun android.content.Context.findActivity(): androidx.activity.ComponentActivity = when (this) {
    is androidx.activity.ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> error("No activity")
}

/** Turns YouTube's captions off in the player's page (the library has no call for it). */
private fun hideCaptions(root: android.view.View) {
    val web = findWebView(root) ?: return
    web.evaluateJavascript(
        "try{player.setOption('captions','track',{});player.unloadModule('captions');player.unloadModule('cc');}catch(e){}",
        null,
    )
}

private fun findWebView(v: android.view.View): android.webkit.WebView? = when (v) {
    is android.webkit.WebView -> v
    is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findWebView(v.getChildAt(it)) }
    else -> null
}

private suspend fun fadeVolume(p: YouTubePlayer, from: Int, to: Int) {
    val steps = (TrailerTiming.FADE_MS / 100).coerceAtLeast(1)
    for (i in 1..steps) {
        p.setVolume(from + (to - from) * i / steps)
        delay(100)
    }
    if (to == 0) p.mute()
}
