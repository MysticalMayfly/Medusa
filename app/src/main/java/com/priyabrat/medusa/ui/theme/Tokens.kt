package com.priyabrat.medusa.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Design tokens, ported from the Mac app's CSS (public/app.css dark theme + public/theme.css).
 *
 * Sizing: the Mac desktop layout (1440 px wide) is scaled onto the TV canvas (960 x 540 dp,
 * 1080p at xhdpi), so 1 Mac px = 2/3 dp. Text is rounded up where needed so it stays
 * readable from the sofa. Screens use these values only; no raw numbers in screen code.
 */

/** Colours: the Mac app's dark tokens (app.css :root[data-theme="dark"]). */
object MedusaColors {
    val Bg = Color(0xFF07070B)
    val Surface = Color(0xFF0E0E14)
    val Card = Color(0xFF101017)
    val Raised = Color(0xFF15151E)
    val Line = Color(0xFF22222D)
    val Line2 = Color(0xFF2A2A36)
    val LineHover = Color(0xFF3A3A48)
    val Track = Color(0xFF1C1C26)
    val RingTrack = Color(0xFF1E1E28)
    val Fg = Color(0xFFF2F1EE)
    val FgStrong = Color(0xFFFFFFFF)
    val Muted = Color(0xFFA09EAC)
    val Faint = Color(0xFF6E6C7A)
    val Inv = Color(0xFF07070B)
    val Panel = Color(0xFF0F0F16)
    val Scrim = Color(0xA8040408)          // rgba(4,4,8,.66)
    val Shadow = Color(0x80000000)
    val Accent = Color(0xFFFF5D73)         // --live, used as --accent
    val Soon = Color(0xFF7CC4FF)
    val Warning = Color(0xFFFAB219)
    val Critical = Color(0xFFD03B3B)
    val Imdb = Color(0xFFF5C518)
    val GlassFill = Color(0x6114141A)      // rgba(20,20,26,.38)
    val GlassLine = Color(0x47FFFFFF)      // rgba(255,255,255,.28)
    val CardScrim = Color(0x9E000000)      // rgba(0,0,0,.62)
}

/** Spacing and sizes in dp (Mac px x 2/3). */
object Dimens {
    // TV safe area: about 5% of 960 x 540
    val SafeH = 48.dp
    val SafeTop = 20.dp
    val SafeBottom = 28.dp

    // header (Mac: 46 px logo, 42 px controls)
    val Logo = 31.dp
    val HeaderControl = 30.dp
    val NavItemH = 24.dp

    // gaps
    val Gap4 = 3.dp
    val Gap8 = 5.dp
    val Gap10 = 7.dp
    val Gap12 = 8.dp
    val Gap14 = 9.dp
    val Gap16 = 11.dp
    val Gap22 = 15.dp
    val Gap26 = 17.dp
    val Gap28 = 19.dp
    val Gap36 = 24.dp

    // home carousel (Mac: full-bleed 16:7, poster 170-250 px at 4:5)
    val HeroHeight = 340.dp
    val HeroPosterW = 128.dp
    val HeroInfoMaxW = 430.dp
    val HeroBtn = 35.dp
    val PageHeroHeight = 300.dp

    // posters (Mac: 148 px shelf posters, 150 px min wall column, 2:3)
    val PosterW = 104.dp
    val ContinueW = 174.dp           // 260 px, 16:9
    val WallColumns = 8
    val WallGapH = 11.dp
    val WallGapV = 17.dp
    val HeartBtn = 23.dp

    // details panel (Mac: 560 px wide side panel)
    val PanelW = 374.dp
    val PanelArtH = 140.dp
    val PanelPosterW = 70.dp

    // universes (Mac: tiles minmax 230 px)
    val UniverseColumns = 5

    // focus (Mac :focus-visible outline 2-3 px, offset 2-3 px)
    val RingWidth = 2.dp
    val RingOffset = 3.dp
}

/** Corner radii. Mac: inputs 12, cards 16, hero 24-28, posters 12, controls are pills. */
object Shapes {
    val Input = RoundedCornerShape(8.dp)
    val Card = RoundedCornerShape(11.dp)
    val Hero = RoundedCornerShape(16.dp)
    val Poster = RoundedCornerShape(8.dp)
    val Pill = RoundedCornerShape(percent = 50)
    val Small = RoundedCornerShape(5.dp)
}

/** Motion (theme.css). */
object Motion {
    val Ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    const val RiseMs = 300            // entrance: rise 8 px, fade in
    val RiseOffset = 5.dp
    const val PanelMs = 320           // details panel slide
    const val LiftMs = 250            // poster lift on focus
    const val HeartMs = 200           // heart fade
    const val ColorMs = 150           // controls only change colour
    const val HeroZoomMs = 18_000     // carousel backdrop slow zoom
    const val HeroSwapMs = 450
    const val HeroStillMs = 8_000     // a still stays 8 s before the next pick
    const val BobMs = 6_000           // platform tiles float
    const val ToastMs = 2_600
    const val PosterLift = 3          // dp, Mac 4 px
    const val PosterScale = 1.03f
}
