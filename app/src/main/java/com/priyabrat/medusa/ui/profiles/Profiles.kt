package com.priyabrat.medusa.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.priyabrat.medusa.ui.components.Pill
import com.priyabrat.medusa.ui.components.PillStyle
import com.priyabrat.medusa.ui.components.ShelfRow
import com.priyabrat.medusa.ui.components.focusRing
import com.priyabrat.medusa.ui.components.rememberFocusable
import com.priyabrat.medusa.ui.components.rise
import com.priyabrat.medusa.ui.components.tvClick
import com.priyabrat.medusa.ui.model.AVATAR_COUNT
import com.priyabrat.medusa.ui.model.ProfilePageState
import com.priyabrat.medusa.ui.model.ProfileTile
import com.priyabrat.medusa.ui.model.ProfilesUiState
import com.priyabrat.medusa.ui.model.Shelf
import com.priyabrat.medusa.data.library.Profiles
import com.priyabrat.medusa.ui.theme.Dimens
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType
import com.priyabrat.medusa.ui.theme.Shapes

/*
 * Profiles: "Who's watching?", each profile's page, and adding or editing one.
 * Pictures are built-in avatars (a gradient with the profile's initial), picked with the remote.
 */

private val avatarColours = listOf(
    Color(0xFFE5484D) to Color(0xFF7A1F3D), Color(0xFFF76B15) to Color(0xFF7A2E0B), Color(0xFFFFC53D) to Color(0xFF8A5A00),
    Color(0xFF46A758) to Color(0xFF1B4D2A), Color(0xFF12A594) to Color(0xFF0B4A44), Color(0xFF0090FF) to Color(0xFF0B3A7A),
    Color(0xFF6E56CF) to Color(0xFF2E1F6B), Color(0xFFD6409F) to Color(0xFF6B1A4D),
)

@Composable
fun Avatar(avatar: Int, name: String, size: Dp, modifier: Modifier = Modifier, photoUrl: String? = null) {
    val (a, b) = avatarColours[Math.floorMod(avatar, avatarColours.size)]
    Box(
        modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center,
    ) {
        // A Google picture covers the letter once it has loaded.
        if (photoUrl != null) coil3.compose.AsyncImage(
            model = photoUrl, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize(),
        ) else Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** A focusable avatar with its name under it (the picker's tiles). */
@Composable
private fun AvatarTile(name: String, avatar: Int, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, label: String = name, add: Boolean = false, photoUrl: String? = null) {
    val f = rememberFocusable()
    val focused = f.focused.value
    Column(modifier.tvClick(f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.focusRing(focused, radius = size / 2, width = 3.dp)) {
            if (add) {
                Box(Modifier.size(size).clip(CircleShape).border(2.dp, MedusaColors.Line2, CircleShape), contentAlignment = Alignment.Center) {
                    Text("+", color = MedusaColors.Muted, fontSize = (size.value * 0.4f).sp)
                }
            } else {
                Avatar(avatar, name, size, photoUrl = photoUrl)
            }
        }
        Text(label, style = MedusaType.BodyStrong, color = if (focused) MedusaColors.Fg else MedusaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Who's watching?": every profile, plus Add profile. Picking one switches to it. */
@Composable
fun WhoIsWatchingScreen(state: ProfilesUiState, onPick: (String) -> Unit, onAdd: () -> Unit, onGuest: () -> Unit) {
    val first = remember { FocusRequester() }
    val accounts = state.profiles.filterNot { it.id == Profiles.GUEST_ID }
    Column(
        Modifier.fillMaxSize().background(MedusaColors.Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Who’s watching?", style = MedusaType.PageTitle, modifier = Modifier.rise())
        Row(Modifier.padding(top = 36.dp).rise(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            accounts.forEachIndexed { i, p ->
                AvatarTile(p.name, p.avatar, 96.dp, { onPick(p.id) }, if (i == 0) Modifier.focusRequester(first) else Modifier, photoUrl = p.photoUrl)
            }
            if (accounts.size < 6) AvatarTile("", 0, 96.dp, onAdd, if (accounts.isEmpty()) Modifier.focusRequester(first) else Modifier, label = "Add account", add = true)
            AvatarTile("Guest", 0, 96.dp, onGuest, label = "Continue as guest")
        }
    }
    LaunchedEffect(state.profiles.size) { runCatching { first.requestFocus() } }
}

class ProfileActions(
    val onOpen: (String) -> Unit,
    val onSwitch: (String) -> Unit,
    val onAllProfiles: () -> Unit,
    /** Signs this profile in with Google (null when the build has no Google sign-in). */
    val onGoogle: ((String) -> Unit)? = null,
    val onGoogleSignOut: (String) -> Unit = {},
)

/** A profile's own page: big avatar and name, its numbers, then its Interested and Watched rows. */
@Composable
fun ProfileScreen(state: ProfilePageState, actions: ProfileActions, header: @Composable () -> Unit) {
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(state.id, state.name.isNotEmpty()) {
        if (state.name.isNotEmpty()) repeat(20) { if (runCatching { firstButton.requestFocus() }.isSuccess) return@LaunchedEffect; kotlinx.coroutines.delay(50) }
    }
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.Gap22)) {
        item("header") { header() }
        item("top") {
            Row(
                Modifier.padding(horizontal = Dimens.SafeH).rise(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Gap22),
            ) {
                Avatar(state.avatar, state.name, 96.dp, photoUrl = state.photoUrl)
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
                    Text(state.name, style = MedusaType.PageTitle)
                    Text(state.stat, style = MedusaType.Body)
                    state.google?.let { Text(it, style = MedusaType.Small) }
                    Row(Modifier.padding(top = Dimens.Gap8), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap8)) {
                        if (!state.active) Pill("Switch to ${state.name}", { actions.onSwitch(state.id) }, Modifier.focusRequester(firstButton), style = PillStyle.Primary)
                        Pill("All profiles", actions.onAllProfiles, if (state.active) Modifier.focusRequester(firstButton) else Modifier)
                        actions.onGoogle?.let { google -> if (state.google == null && state.id != Profiles.GUEST_ID) Pill("Sign in", { google(state.id) }) }
                        if (state.google != null) Pill("Sign out", { actions.onGoogleSignOut(state.id) })
                        if (state.id == Profiles.GUEST_ID) Pill("Leave guest", { actions.onGoogleSignOut(state.id) })
                    }
                }
            }
        }
        if (state.interested.isNotEmpty()) item("interested") {
            ShelfRow(Shelf("p-int", "Interested", count = state.interested.size, items = state.interested), actions.onOpen)
        }
        if (state.watched.isNotEmpty()) item("watched") {
            ShelfRow(Shelf("p-seen", "Watched", count = state.watched.size, items = state.watched), actions.onOpen)
        }
        if (state.interested.isEmpty() && state.watched.isEmpty()) item("empty") {
            Text("Nothing yet. Mark titles Interested or Watched and they show up here.", style = MedusaType.Body, modifier = Modifier.padding(horizontal = Dimens.SafeH))
        }
    }
}
