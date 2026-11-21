package com.priyabrat.medusa.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.priyabrat.medusa.R
import com.priyabrat.medusa.ui.components.ArtPlaceholder
import com.priyabrat.medusa.ui.components.IconCircle
import com.priyabrat.medusa.ui.components.LinkPill
import com.priyabrat.medusa.ui.components.LocalToast
import com.priyabrat.medusa.ui.components.MedusaIcons
import com.priyabrat.medusa.ui.components.Pill
import com.priyabrat.medusa.ui.components.RatingBadge
import com.priyabrat.medusa.ui.model.TitleDetail
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Motion
import com.priyabrat.medusa.ui.universes.ServiceChip

class DetailActions(
    val onWatched: (String, Boolean) -> Unit,
    val onInterested: (String, Boolean) -> Unit,
)

/**
 * The details panel (app.js .panel): slides in from the right over a dimmed page.
 * Artwork, title, Watched / Interested / Trailer, ratings, tagline and synopsis, facts,
 * where it streams in India with rent prices, and links. No "Edit details" on the TV.
 * Back closes it and focus returns to the poster that opened it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailPanel(detail: TitleDetail?, actions: DetailActions, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val toast = LocalToast.current
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        val first = remember { FocusRequester() }
        Box(Modifier.fillMaxSize().background(MedusaColors.Scrim), contentAlignment = Alignment.CenterEnd) {
            AnimatedVisibility(
                visibleState = shown,
                enter = slideInHorizontally(tween(Motion.PanelMs, easing = Motion.Ease)) { it } + fadeIn(tween(Motion.PanelMs)),
            ) {
                Column(
                    Modifier
                        .width(Dimens.PanelW)
                        .fillMaxHeight()
                        .background(MedusaColors.Panel)
                        .border(1.dp, MedusaColors.Line2)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // .hero-img with the close button
                    Box(Modifier.fillMaxWidth().height(Dimens.PanelArtH)) {
                        ArtPlaceholder(null, Modifier.fillMaxSize(), tint = Color(0xFF1C2230))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.2f to Color.Transparent, 0.98f to MedusaColors.Panel)))
                        IconCircle(MedusaIcons.Close, onClose, Modifier.align(Alignment.TopEnd).padding(12.dp), size = 26.dp)
                    }
                    if (detail != null) Column(
                        Modifier.padding(horizontal = Dimens.Gap22).offset(y = (-56).dp),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Gap12),
                    ) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap12)) {
                            ArtPlaceholder(
                                null,
                                Modifier.width(Dimens.PanelPosterW).aspectRatio(2f / 3f).clip(RoundedCornerShape(9.dp)).border(1.dp, MedusaColors.Line, RoundedCornerShape(9.dp)),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(detail.kindLine, style = MedusaType.Small)
                                Text(detail.title, style = MedusaType.PanelTitle)
                            }
                        }
                        // .quick buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
                            Pill(
                                stringResource(R.string.watched),
                                { actions.onWatched(detail.id, !detail.watched) },
                                Modifier.focusRequester(first),
                                selected = detail.watched,
                                icon = MedusaIcons.Check,
                            )
                            Pill(
                                stringResource(R.string.interested),
                                { actions.onInterested(detail.id, !detail.interested) },
                                selected = detail.interested,
                                icon = if (detail.interested) MedusaIcons.HeartFilled else MedusaIcons.Heart,
                            )
                            if (detail.hasTrailer) {
                                val later = stringResource(R.string.not_wired_trailer)
                                Pill(stringResource(R.string.trailer), { toast(later) }, icon = MedusaIcons.Play)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap8)) { detail.ratings.forEach { RatingBadge(it) } }
                        detail.tagline?.let { Text(it, style = MedusaType.Body, fontStyle = FontStyle.Italic) }
                        Text(detail.overview, style = MedusaType.BodyStrong)
                        // .kv facts
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            detail.facts.forEach { fact ->
                                Row {
                                    Text(fact.label, style = MedusaType.Small, modifier = Modifier.width(110.dp))
                                    Text(fact.value, style = MedusaType.Small, color = MedusaColors.Fg)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.streaming_in_india), style = MedusaType.Small, modifier = Modifier.width(110.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    detail.streaming.forEach { ServiceChip(it) }
                                }
                            }
                            detail.rentPrices.forEach { price ->
                                Row {
                                    Text(price.label, style = MedusaType.Small, modifier = Modifier.width(110.dp))
                                    Text(price.value, style = MedusaType.Small, color = MedusaColors.Fg)
                                }
                            }
                        }
                        // .links
                        val linkLater = stringResource(R.string.not_wired_link)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            detail.links.forEach { LinkPill("$it ↗", { toast(linkLater) }) }
                        }
                    }
                }
            }
        }
        LaunchedEffect(detail != null) { if (detail != null) runCatching { first.requestFocus() } }
    }
}
