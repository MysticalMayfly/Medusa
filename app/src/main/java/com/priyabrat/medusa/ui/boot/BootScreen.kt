package com.priyabrat.medusa.ui.boot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.priyabrat.medusa.R
import com.priyabrat.medusa.ui.theme.Bricolage
import com.priyabrat.medusa.ui.theme.Geist
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/*
 * The Medusa loading screen, ported from the Mac app (public/boot.css, boot.js, index.html).
 * Timeline in ms from launch, as in boot.css: aura 150, portrait 100, sheen 1450 (again 5500), eyes 1000 to 1950,
 * wordmark 1100, line 1800, status note 2000; it stays 10 s (index.html bootWait), then lifts away in 700 ms.
 * Every value is read from one clock inside draw lambdas, so the animation redraws without recomposing.
 * Nothing loops after it ends: the screen leaves composition. Lines live in res/values/loading_lines.xml.
 */

private object Boot {
    const val HOLD_MS = 10_000          // index.html: window.bootWait = 10000
    const val OUT_MS = 700              // .boot.out
    const val SHOW_APP_MS = 9_000       // compose the app under the intro shortly before it lifts away
    const val LINE_MS = 1_700           // boot.js: one line every 1.7 s
    const val SWAP_MS = 260             // boot.js crossfade
    val bgInner = Color(0xFF0D1410)
    val bgMid = Color(0xFF07090A)
    val bgOuter = Color(0xFF030304)
    val word = Color(0xFFEFE6CF)
    val gold = Color(0xFFD9B25A)
    val note = Color(0x73F2F1EE)        // rgba(242,241,238,.45)
}

private val EaseOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val EaseImg = CubicBezierEasing(0.16f, 0.84f, 0.24f, 1f)
private val EaseSheen = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val EaseEye = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.2f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** Progress 0..1 of a segment that starts at [start] ms and lasts [dur] ms. */
private fun seg(t: Float, start: Int, dur: Int, easing: Easing = LinearEasing): Float =
    easing.transform(((t - start) / dur).coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/** Eye glows: position in the portrait (fraction), size (fraction of its width), start delay (ms). */
private class Eye(val x: Float, val y: Float, val size: Float, val delay: Int)

private val EYES = listOf(
    Eye(0.435f, 0.477f, 0.07f, 1000), Eye(0.606f, 0.477f, 0.07f, 1080),
    Eye(0.138f, 0.18f, 0.042f, 1500), Eye(0.893f, 0.217f, 0.042f, 1650),
    Eye(0.108f, 0.431f, 0.042f, 1800), Eye(0.887f, 0.557f, 0.042f, 1950),
)

/**
 * Plays once. [showApp] turns true a moment before the end so the app is ready underneath; [onDone] when it is gone.
 */
@Composable
fun BootScreen(showApp: () -> Unit, onDone: () -> Unit) {
    val clock = remember { Animatable(0f) }
    val latestShowApp by rememberUpdatedState(showApp)
    val latestDone by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        val end = (Boot.HOLD_MS + Boot.OUT_MS).toFloat()
        val appAt = Boot.SHOW_APP_MS.toFloat()
        var appShown = false
        clock.animateTo(end, tween(durationMillis = end.toInt(), easing = LinearEasing)) {
            if (!appShown && value >= appAt) { appShown = true; latestShowApp() }
        }
        if (!appShown) latestShowApp()
        latestDone()
    }
    val t: () -> Float = { clock.value }

    // Status lines: the first always the same, the rest shuffled each launch.
    val context = LocalContext.current
    val lines = remember {
        // java.util shuffle, not Kotlin's shuffled(): with packageScope optimisation R8 moves Kotlin's
        // MutableCollectionsJVM facade out of its package and the release build crashes on start.
        val rest = java.util.ArrayList(context.resources.getStringArray(R.array.boot_lines).asList())
        java.util.Collections.shuffle(rest)
        listOf(context.getString(R.string.boot_first_line)) + rest
    }
    var lineIndex by remember { mutableIntStateOf(0) }
    var lineSwapAt by remember { mutableIntStateOf(-10_000) }
    var lineEnteredAt by remember { mutableIntStateOf(-10_000) }
    LaunchedEffect(Unit) {
        // Let the initial line finish its entrance before its first full reading interval.
        delay(2_600L)
        while (isActive && lineIndex < lines.lastIndex) {
            delay(Boot.LINE_MS.toLong() - Boot.SWAP_MS)
            lineSwapAt = clock.value.toInt()
            delay(Boot.SWAP_MS.toLong())
            lineIndex += 1
            lineEnteredAt = clock.value.toInt()
            lineSwapAt = -10_000
        }
    }

    val out: () -> Float = { seg(t(), Boot.HOLD_MS, Boot.OUT_MS, EaseSheen) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - out() }
            .background(Boot.bgOuter)
            .drawBehind {
                // Background: radial-gradient(120% 90% at 50% 42%, #0d1410, #07090a 45%, #030304)
                drawRect(
                    Brush.radialGradient(
                        0f to Boot.bgInner, 0.45f to Boot.bgMid, 1f to Boot.bgOuter,
                        center = Offset(size.width / 2, size.height * 0.42f),
                        radius = size.width * 0.6f,
                    ),
                )
                // Green and gold aura behind the portrait (::before)
                val a = seg(t(), 150, 2400, EaseOut)
                val r = lerp(0.6f, 1f, a) * minOf(size.height * 1.2f, size.width * 1.4f) / 2
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0x4D608C40), 0.55f to Color(0x14D4AA48), 0.72f to Color.Transparent,
                        center = Offset(size.width / 2, size.height * 0.44f), radius = r,
                    ),
                    radius = r, center = Offset(size.width / 2, size.height * 0.44f), alpha = a,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // .boot-art: height min(58vh, 74vw), aspect 714 / 760
        val artH = minOf(maxHeight * 0.58f, maxWidth * 0.74f)
        val artW = artH * (714f / 760f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Portrait(t, out, Modifier.size(artW, artH))
            Wordmark(t)
            RunLine(t)
            Note(lines[lineIndex], t, { lineSwapAt }, { lineEnteredAt })
        }
    }
}

@Composable
private fun Portrait(t: () -> Float, out: () -> Float, modifier: Modifier) {
    Box(
        modifier.graphicsLayer {
            // .boot.out .boot-art: rises 3vh, scales to 1.08, blurs and fades
            val o = out()
            translationY = -0.03f * 540.dp.toPx() * o
            scaleX = lerp(1f, 1.08f, o); scaleY = scaleX
            alpha = 1f - o
            renderEffect = if (o > 0f) BlurEffect(8.dp.toPx() * o, 8.dp.toPx() * o, TileMode.Decal) else null
        },
    ) {
        Image(
            painter = painterResource(R.drawable.medusa_portrait),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // boot-in: opacity 0 -> 1, scale .92 -> 1, blur 14 px -> 0; then a slow breathe toward 1.025
                    val p = seg(t(), 100, 1600, EaseImg)
                    val breathe = seg(t(), 1800, 7000, EaseInOut)
                    alpha = p
                    scaleX = lerp(0.92f, 1f, p) * lerp(1f, 1.025f, breathe); scaleY = scaleX
                    rotationZ = lerp(-0.4f, 0.4f, breathe)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.6f)
                    val blur = 9.dp.toPx() * (1f - p)
                    renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // brightness .4 -> 1 while it comes in
                    val p = seg(t(), 100, 1600, EaseImg)
                    if (p < 1f) drawRect(Color.Black.copy(alpha = 0.6f * (1f - p)), blendMode = BlendMode.SrcAtop)
                    // The gold sheen, masked by the portrait itself (SrcAtop only paints where she is)
                    val s1 = seg(t(), 1450, 1500, EaseSheen)
                    val s2 = seg(t(), 5500, 1800, EaseSheen)
                    val s = if (t() >= 5500) s2 else s1
                    if (s > 0f && s < 1f) {
                        // background-position 140% -> -40% of a 260% wide band
                        val bandW = size.width * 2.6f
                        val left = lerp(-2.24f, 0.64f, s) * size.width
                        drawRect(
                            Brush.linearGradient(
                                0.38f to Color.Transparent, 0.42f to Color(0x00FFE296), 0.5f to Color(0x8CFFE296),
                                0.58f to Color(0x00FFE296), 0.62f to Color.Transparent,
                                start = Offset(left, 0f), end = Offset(left + bandW, bandW * 0.27f),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
        )
        // Eye glows, hers first and then the snakes' (.boot-eye)
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    for (e in EYES) {
                        val p = seg(t(), e.delay, 900, LinearEasing)
                        if (p <= 0f) continue
                        val (alpha, scale) = if (p < 0.55f) {
                            val k = EaseEye.transform(p / 0.55f); k to lerp(0.4f, 1.35f, k)
                        } else {
                            val k = EaseEye.transform((p - 0.55f) / 0.45f); lerp(1f, 0.8f, k) to lerp(1.35f, 1f, k)
                        }
                        // then a slow glow toward .35 (boot-eye-glow, once)
                        val glow = seg(t(), e.delay + 1900, 2600, EaseInOut)
                        val a = alpha * lerp(1f, 0.55f, glow)
                        val r = e.size * size.width / 2 * scale
                        val c = Offset(e.x * size.width, e.y * size.height)
                        drawCircle(
                            Brush.radialGradient(
                                0f to Color(0xFFFFEC96), 0.35f to Color(0xBFFFC428), 0.6f to Color(0x40FFAA00), 0.75f to Color.Transparent,
                                center = c, radius = r,
                            ),
                            radius = r, center = c, alpha = a, blendMode = BlendMode.Screen,
                        )
                    }
                },
        )
    }
}

@Composable
private fun Wordmark(t: () -> Float) {
    // boot-word: fades in, letter spacing .9em -> .42em, blur 6 px -> 0 (only this Text recomposes, for 1.6 s)
    val spacing by remember { derivedStateOf { lerp(0.9f, 0.42f, seg(t(), 1100, 1600, EaseOut)) } }
    Text(
        text = "MEDUSA",
        style = TextStyle(
            fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
            letterSpacing = spacing.em, color = Boot.word, textAlign = TextAlign.Center,
            shadow = androidx.compose.ui.graphics.Shadow(Color(0x59D4AA48), blurRadius = 16f),
        ),
        modifier = Modifier.graphicsLayer {
            val p = seg(t(), 1100, 1600, EaseOut)
            alpha = p
            // margin-right: -spacing, so the letters stay centred
            translationX = spacing * 20.sp.toPx() / 2
            val blur = 4.dp.toPx() * (1f - p)
            renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
        },
    )
}

@Composable
private fun RunLine(t: () -> Float) {
    // .boot-line: a thin track with a gold runner that crosses it every 1.4 s
    Box(
        Modifier
            .width(146.dp)
            .height(2.dp)
            .graphicsLayer { alpha = seg(t(), 1800, 600) }
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0x14FFFFFF))
            .drawBehind {
                val cycle = (t() % 1400f) / 1400f
                val k = EaseInOut.transform(cycle)
                val w = size.width * 0.4f
                val x = lerp(-1.1f, 2.6f, k) * w
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Boot.gold, Color.Transparent), startX = x, endX = x + w),
                    topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(w, size.height),
                )
            },
    )
}

@Composable
private fun Note(line: String, t: () -> Float, swapAt: () -> Int, enteredAt: () -> Int) {
    Text(
        text = line.uppercase(),
        style = TextStyle(fontFamily = Geist, fontSize = 11.sp, letterSpacing = 0.14.em, color = Boot.note, textAlign = TextAlign.Center),
        modifier = Modifier.graphicsLayer {
            // fades in at 2 s; each change fades out and drops 4 px, then the new line appears
            val enter = seg(t(), 2000, 600)
            val since = t() - swapAt()
            val swap = if (since in 0f..260f) since / 260f else 0f
            val newLine = ((t() - enteredAt()) / Boot.SWAP_MS).coerceIn(0f, 1f)
            alpha = enter * (1f - swap) * newLine
            translationY = 4.dp.toPx() * (swap + 1f - newLine)
        },
    )
}
