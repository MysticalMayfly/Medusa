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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
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
fun TrailerLayer(videoId: String, paused: Boolean, dipped: Boolean, onEnded: () -> Unit, onFailed: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ended by rememberUpdatedState(onEnded)
    val failed by rememberUpdatedState(onFailed)
    val isPaused by rememberUpdatedState(paused)

    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var second by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var playingSince by remember { mutableFloatStateOf(-1f) }   // playback second when this run of playing began
    var errors by remember { mutableIntStateOf(0) }
    val alpha = remember { Animatable(0f) }

    val view = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val options = IFramePlayerOptions.Builder(context)
                .controls(0).rel(0).ivLoadPolicy(3).ccLoadPolicy(0).fullscreen(0).autoplay(0).mute(1)
                .build()
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) { player = youTubePlayer }
                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) { this@apply.post { setSecond(second) } }
                override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) { this@apply.post { setDuration(duration) } }
                override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) { this@apply.post { setState(state) } }
                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) { android.util.Log.d("MedusaTrailer", "error $error"); this@apply.post { errors += 1 } }
                // Setters reach the Compose state from the player's thread via post().
                private fun setSecond(s: Float) { second = s }
                private fun setDuration(d: Float) { duration = d }
                private fun setState(st: PlayerConstants.PlayerState) {
                    if (st == PlayerConstants.PlayerState.PLAYING) { if (playingSince < 0f) playingSince = second } else playingSince = -1f
                }
            }, false, options)
        }
    }
    DisposableEffect(view) {
        lifecycle.addObserver(view)
        onDispose { lifecycle.removeObserver(view); view.release() }
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
    var fading by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(second, playingSince, paused) {
        val p = player ?: return@LaunchedEffect
        if (paused || fading) return@LaunchedEffect
        val played = if (playingSince >= 0f) second - playingSince else 0f
        if (!revealed && played >= TrailerTiming.SIGN_S) {
            revealed = true
            p.unMute()
            launch { fadeVolume(p, 0, if (dipped) 30 else 100) }
            alpha.animateTo(1f, tween(TrailerTiming.FADE_MS, easing = LinearEasing))
        }
        if (revealed && duration > 0f && second >= duration - TrailerTiming.END_LEAD_S) {
            fading = true
            launch { fadeVolume(p, if (dipped) 30 else 100, 0) }
            alpha.animateTo(0f, tween(TrailerTiming.FADE_MS, easing = LinearEasing))
            p.pause()
            if (!isPaused) ended()
        }
    }
    // A shown trailer resumed after a pause has to wait out YouTube's sign again.
    LaunchedEffect(paused) { if (paused) revealed = false }
    LaunchedEffect(dipped) { if (revealed && !fading) player?.setVolume(if (dipped) 30 else 100) }

    // Cover the carousel like a background image: 16:9, cropped to fill.
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val w = maxOf(maxWidth, maxHeight * (16f / 9f))
        val h = w * (9f / 16f)
        AndroidView(
            factory = { view },
            modifier = Modifier.requiredSize(w, h).graphicsLayer { this.alpha = alpha.value },
        )
    }
}

private suspend fun fadeVolume(p: YouTubePlayer, from: Int, to: Int) {
    val steps = (TrailerTiming.FADE_MS / 100).coerceAtLeast(1)
    for (i in 1..steps) {
        p.setVolume(from + (to - from) * i / steps)
        delay(100)
    }
    if (to == 0) p.mute()
}
