package com.priyabrat.medusa.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.priyabrat.medusa.R

/** Bricolage Grotesque for the wordmark and headings, Geist for body text (as on the Mac). */
val Bricolage = FontFamily(
    Font(R.font.bricolage_700, FontWeight.Bold),
    Font(R.font.bricolage_800, FontWeight.ExtraBold),
)

val Geist = FontFamily(
    Font(R.font.geist_400, FontWeight.Normal),
    Font(R.font.geist_500, FontWeight.Medium),
    Font(R.font.geist_600, FontWeight.SemiBold),
    Font(R.font.geist_700, FontWeight.Bold),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_400, FontWeight.Normal),
    Font(R.font.geist_mono_500, FontWeight.Medium),
)

/** Type scale (Mac px x 2/3, rounded up for TV reading distance). */
object MedusaType {
    private val display = TextStyle(fontFamily = Bricolage, letterSpacing = (-0.02).em, color = MedusaColors.Fg)
    private val body = TextStyle(fontFamily = Geist, color = MedusaColors.Fg)
    private val mono = TextStyle(fontFamily = GeistMono, color = MedusaColors.Muted)

    val HeroTitle = display.copy(fontSize = 48.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold)
    val PageTitle = display.copy(fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold)
    val UniverseTitle = display.copy(fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
    val PanelTitle = display.copy(fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold)
    val ShelfTitle = display.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val CardTitle = display.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val Stat = display.copy(fontSize = 34.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
    val Brand = display.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)

    val Eyebrow = body.copy(fontSize = 11.sp, letterSpacing = 0.14.em, color = MedusaColors.Muted)
    val Body = body.copy(fontSize = 13.sp, lineHeight = 19.sp, color = MedusaColors.Muted)
    val BodyStrong = body.copy(fontSize = 13.sp, lineHeight = 19.sp)
    val Nav = body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val Control = body.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val ControlStrong = body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val PosterTitle = body.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
    val PosterSub = body.copy(fontSize = 11.sp, color = MedusaColors.Muted)
    val Small = body.copy(fontSize = 11.sp, color = MedusaColors.Muted)
    val Mono = mono.copy(fontSize = 11.sp)
    val MonoStrong = mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MedusaColors.Fg)
}
