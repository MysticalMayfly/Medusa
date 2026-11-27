package com.priyabrat.medusa.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import com.priyabrat.medusa.data.FilterKeys
import com.priyabrat.medusa.data.MedusaRepository
import com.priyabrat.medusa.ui.browse.BrowseActions
import com.priyabrat.medusa.ui.browse.BrowseScreen
import com.priyabrat.medusa.ui.components.HeroActions
import com.priyabrat.medusa.ui.components.LocalToast
import com.priyabrat.medusa.ui.components.LocalTrailersPaused
import com.priyabrat.medusa.ui.components.ToastState
import com.priyabrat.medusa.ui.detail.DetailActions
import com.priyabrat.medusa.ui.detail.DetailPanel
import com.priyabrat.medusa.ui.home.HomeActions
import com.priyabrat.medusa.ui.home.HomeScreen
import com.priyabrat.medusa.ui.horror.HorrorActions
import com.priyabrat.medusa.ui.horror.HorrorScreen
import com.priyabrat.medusa.ui.model.BrowseKind
import com.priyabrat.medusa.ui.model.BrowseUiState
import com.priyabrat.medusa.ui.model.HomeUiState
import com.priyabrat.medusa.ui.model.HorrorUiState
import com.priyabrat.medusa.ui.model.ProfilePageState
import com.priyabrat.medusa.ui.model.ProfilesUiState
import com.priyabrat.medusa.ui.profiles.ProfileActions
import com.priyabrat.medusa.ui.profiles.ProfileScreen
import com.priyabrat.medusa.ui.profiles.WhoIsWatchingScreen
import com.priyabrat.medusa.ui.model.SearchUiState
import com.priyabrat.medusa.ui.model.SettingsUiState
import com.priyabrat.medusa.ui.model.Tab
import com.priyabrat.medusa.ui.model.TitleDetail
import com.priyabrat.medusa.ui.model.UniverseUiState
import com.priyabrat.medusa.ui.model.UniversesUiState
import com.priyabrat.medusa.ui.search.SearchScreen
import com.priyabrat.medusa.ui.settings.SettingsScreen
import com.priyabrat.medusa.ui.universes.UniverseActions
import com.priyabrat.medusa.ui.universes.UniverseScreen
import com.priyabrat.medusa.ui.universes.UniversesScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the app is. Tabs are top level; a universe, Search and Settings sit one level below. */
sealed interface Screen {
    data class Top(val tab: Tab) : Screen
    data class Universe(val id: String) : Screen
    data object Search : Screen
    data object Settings : Screen
    data object Profiles : Screen
    data class Profile(val id: String) : Screen
}

/**
 * The app shell: header, screens, the details panel and the toast.
 * Back closes the panel, then goes up a level, then to Home; on Home it leaves the app.
 * Screens get state from [repo] and send actions back to it; they never fetch anything themselves.
 */
@Composable
fun MedusaApp(
    repo: MedusaRepository,
    settings: SettingsUiState,
    onUpdateButton: () -> Unit,
    onImportButton: () -> Unit,
    onQuit: () -> Unit,
    onGoogleSignIn: ((String?, () -> Unit) -> Unit)? = null,
    onGoogleSignOut: (String, () -> Unit) -> Unit = { _, done -> done() },
    onGuest: (() -> Unit) -> Unit = { done -> done() },
) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Top(Tab.Home)) }
    val screen = stack.last()
    // The page actually built. While navigation moves (quick left and right on the tabs), only the placeholder
    // shows; the page is built once the remote settles, then fades in over the placeholder (PageVeil).
    var built by remember { mutableStateOf(screen) }
    val page: Screen? = built.takeIf { it == screen }
    val veil = remember { androidx.compose.animation.core.Animatable(0f) }
    val anchor = remember { HeaderAnchor() }
    // Whether focus is anywhere in the header. Only read by the focus effect below, so it never recomposes.
    val inHeader = remember { mutableStateOf(false) }
    // While the carousel has focus the top bar shrinks a little and fades back. Animated outside composition and
    // read only while drawing, so it never rebuilds the page.
    val heroFocus = remember { mutableStateOf(false) }
    val barDip = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { heroFocus.value }.collect {
            launch { barDip.animateTo(if (it) 1f else 0f, androidx.compose.animation.core.tween(FADE_MS)) }
        }
    }
    var panelId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val toast = remember { ToastState() }
    val headerFocus = remember { HeaderFocus() }
    val settingsButton = remember { FocusRequester() }
    val searchField = remember { FocusRequester() }
    // The carousel's main button: where Down from the top menu goes on pages that have a carousel.
    val heroEntry = remember { FocusRequester() }
    val profiles by remember(repo) { repo.profiles() }.collectAsState(ProfilesUiState())

    fun go(to: Screen) {
        if (to is Screen.Top) { stack.clear(); stack.add(to) } else if (stack.last() != to) stack.add(to)
    }

    // Back: closes a level, then returns to Home. On Home it first goes back to the top (the Home tab), and only a
    // second Back within a few seconds leaves; leaving sends Medusa to the background instead of closing it, so
    // coming back finds it where it was (no intro, no Who's watching, same page).
    val activity = androidx.activity.compose.LocalActivity.current
    val homeList = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var exitArmedAt by remember { mutableStateOf(0L) }
    val exitHint = androidx.compose.ui.res.stringResource(com.priyabrat.medusa.R.string.back_again_to_exit)
    BackHandler(enabled = true) {
        when {
            screen == Screen.Profiles && profiles.ready && profiles.current == null -> activity?.moveTaskToBack(true)
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            screen != Screen.Top(Tab.Home) -> go(Screen.Top(Tab.Home))
            System.currentTimeMillis() - exitArmedAt < EXIT_WINDOW_MS -> { exitArmedAt = 0L; activity?.moveTaskToBack(true) }
            else -> {
                exitArmedAt = System.currentTimeMillis()
                // Back to the top of Home first, with focus on the Home tab.
                scope.launch {
                    homeList.scrollToItem(0)
                    headerFocus.tabs.requestWhenReady()
                }
                toast.show(exitHint)
            }
        }
    }

    val currentTab: Tab? = when (val s = screen) {
        is Screen.Top -> s.tab
        is Screen.Universe -> Tab.Universes
        else -> null
    }
    // Pages keep an empty slot where the header sits; the header itself is drawn once, above the pages.
    val header: @Composable () -> Unit = remember(anchor) { { HeaderSlot(anchor) } }
    val hasCarousel = page is Screen.Top && page.tab != Tab.More && page.tab != Tab.Universes

    // Actions are built once (remember), not on every recomposition of the shell. A new object each
    // time made Compose rebuild the whole open screen whenever the shell changed, e.g. when the
    // details panel opened or the update status moved.
    val open: (String) -> Unit = remember { { panelId = it } }
    val hero = remember(repo) { HeroActions(onOpen = open, onInterested = repo::setInterested, onSeen = repo::setWatched) }

    CompositionLocalProvider(
        LocalToast provides toast::show,
        LocalTrailersPaused provides (panelId != null),
        com.priyabrat.medusa.ui.components.LocalHeroFocus provides heroFocus,
    ) {
        // No background here: the window background (themes.xml window_bg, the same colour) already
        // paints it. Painting it twice cost the TV's GPU a full-screen fill on every frame.
        Box(Modifier.fillMaxSize()) {
            when (val s = page) {
                // Placeholder: holds the header's place until the page is built.
                null -> if (screen != Screen.Profiles) header()
                is Screen.Top -> when (s.tab) {
                    Tab.Home -> {
                        val state by remember { repo.home() }.collectAsState(HomeUiState())
                        val actions = remember(repo) {
                            HomeActions(hero, open, repo::markEpisodeWatched, onSeeAll = {
                                repo.selectFilter(FilterKeys.HORROR, FilterKeys.GROUP_SEE_ALL, it)
                                go(Screen.Top(Tab.Horror))
                            }, onPlatform = { slug ->
                                // Premiere platforms are for shows (networks), so a tile opens TV shows on that service.
                                repo.selectFilter(FilterKeys.browse(BrowseKind.Shows), FilterKeys.GROUP_PLATFORM, slug)
                                go(Screen.Top(Tab.Shows))
                            })
                        }
                        HomeScreen(state, actions, header, heroEntry = heroEntry, listState = homeList)
                    }
                    Tab.Movies, Tab.Shows, Tab.More -> {
                        val kind = when (s.tab) { Tab.Movies -> BrowseKind.Movies; Tab.Shows -> BrowseKind.Shows; else -> BrowseKind.More }
                        val key = FilterKeys.browse(kind)
                        val state by remember(kind) { repo.browse(kind) }.collectAsState(BrowseUiState(kind))
                        val actions = remember(repo, kind) {
                            BrowseActions(
                                hero = hero,
                                onOpen = open,
                                onPlatform = { repo.selectFilter(key, FilterKeys.GROUP_PLATFORM, it) },
                                onSegment = { repo.selectFilter(key, FilterKeys.GROUP_SEGMENT, it) },
                                onSelect = { sel, opt -> repo.selectFilter(key, FilterKeys.select(sel), opt) },
                                onToggleUnwatched = { repo.toggleFilter(key, FilterKeys.FLAG_UNWATCHED) },
                            )
                        }
                        BrowseScreen(state, actions, header, heroEntry.takeIf { kind != BrowseKind.More })
                    }
                    Tab.Horror -> {
                        val state by remember { repo.horror() }.collectAsState(HorrorUiState())
                        val h = FilterKeys.HORROR
                        val actions = remember(repo) {
                            HorrorActions(
                                hero = hero,
                                onOpen = open,
                                onTab = { repo.selectFilter(h, FilterKeys.GROUP_TAB, it) },
                                onSeenSegment = { repo.selectFilter(h, FilterKeys.GROUP_SEGMENT, it) },
                                onWhere = { repo.selectFilter(h, FilterKeys.GROUP_WHERE, it) },
                                onSelect = { sel, opt -> repo.selectFilter(h, FilterKeys.select(sel), opt) },
                                onSeeAll = { repo.selectFilter(h, FilterKeys.GROUP_SEE_ALL, it) },
                            )
                        }
                        HorrorScreen(state, actions, header, heroEntry)
                    }
                    Tab.Universes -> {
                        val state by remember { repo.universes() }.collectAsState(UniversesUiState())
                        UniversesScreen(state, onOpenUniverse = { go(Screen.Universe(it)) }, header)
                    }
                }
                is Screen.Universe -> {
                    val state by remember(s.id) { repo.universe(s.id) }.collectAsState(UniverseUiState())
                    val key = FilterKeys.universe(s.id)
                    val actions = remember(repo, s.id) {
                        UniverseActions(
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onOpen = open,
                            onWatched = repo::setWatched,
                            onSegment = { repo.selectFilter(key, FilterKeys.GROUP_SEGMENT, it) },
                            onToggleHideWatched = { repo.toggleFilter(key, FilterKeys.FLAG_HIDE_WATCHED) },
                        )
                    }
                    UniverseScreen(state, actions, header)
                }
                Screen.Search -> {
                    val state by remember(query) { repo.search(query) }.collectAsState(SearchUiState(query))
                    // The field shows what was typed, not the last search result's query: results arrive a moment later,
                    // and writing the older query back into the field threw away every letter after the first.
                    SearchScreen(state.copy(query = query), onQuery = { query = it }, onOpen = open, header = header, fieldFocus = searchField)
                }
                Screen.Settings -> SettingsScreen(settings, onUpdateButton, onImportButton, { Session.askedWho = false; onQuit() }, header, settingsButton)
                Screen.Profiles -> WhoIsWatchingScreen(
                    profiles,
                    onPick = { repo.switchProfile(it); go(Screen.Top(Tab.Home)) },
                    onAdd = { onGoogleSignIn?.invoke(null) { go(Screen.Top(Tab.Home)) } },
                    onGuest = { Session.askedWho = true; onGuest { go(Screen.Top(Tab.Home)) } },
                )
                is Screen.Profile -> {
                    val state by remember(s.id) { repo.profilePage(s.id) }.collectAsState(ProfilePageState(s.id))
                    val actions = remember(repo) {
                        ProfileActions(
                            onOpen = open,
                            onSwitch = { repo.switchProfile(it) },
                            onAllProfiles = { go(Screen.Profiles) },
                            onGoogle = onGoogleSignIn?.let { signIn -> { id -> signIn(id) { go(Screen.Top(Tab.Home)) } } },
                            onGoogleSignOut = { id -> onGoogleSignOut(id) { go(Screen.Profiles) } },
                        )
                    }
                    ProfileScreen(state, actions, header)
                }
            }
            PageVeil(
                pending = page == null,
                alpha = veil.asState(),
                heroHeight = (screen as? Screen.Top)?.tab?.let {
                    when (it) {
                        Tab.More, Tab.Universes -> null
                        else -> com.priyabrat.medusa.ui.components.heroRestHeight()
                    }
                },
            )
            if (anchor.visible) {
                AppHeader(
                    current = currentTab,
                    onTab = { go(Screen.Top(it)) },
                    onSearch = { go(Screen.Search) },
                    onSettings = { go(Screen.Settings) },
                    updateWaiting = settings.updateWaiting,
                    focus = headerFocus,
                    upTarget = when (screen) {
                        Screen.Search -> headerFocus.search
                        Screen.Settings -> headerFocus.settings
                        is Screen.Profile -> headerFocus.profile
                        else -> headerFocus.tabs
                    },
                    profile = profiles.current,
                    down = heroEntry.takeIf { hasCarousel },
                    onProfile = { profiles.current?.let { go(Screen.Profile(it.id)) } },
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(0, anchor.offsetY.toInt()) }
                        .onFocusChanged { inHeader.value = it.hasFocus }
                        .graphicsLayer {
                            val d = barDip.value
                            scaleX = 1f - BAR_SHRINK * d
                            scaleY = scaleX
                            alpha = 1f - BAR_FADE * d
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        },
                )
            }
            toast.Host()
        }

        panelId?.let { id ->
            val detail by remember(id) { repo.detail(id) }.collectAsState(initial = null as TitleDetail?)
            DetailPanel(
                detail,
                remember(repo) { DetailActions(onWatched = repo::setWatched, onInterested = repo::setInterested) },
                onClose = { panelId = null },
            )
        }
    }

    // "Who's watching?" once per launch, when there is more than one profile.
    LaunchedEffect(profiles.ready, profiles.profiles.size) {
        if (profiles.ready && profiles.profiles.isEmpty()) go(Screen.Profiles)
        else if (profiles.ready && !Session.askedWho && profiles.profiles.size > 1) { Session.askedWho = true; go(Screen.Profiles) }
    }

    // Navigation: the placeholder covers at once; the page is built when the remote has settled on it,
    // then the placeholder fades out over it in one go.
    LaunchedEffect(screen) {
        if (built == screen) return@LaunchedEffect
        veil.snapTo(1f)
        if (screen is Screen.Top) delay(SETTLE_MS)
        built = screen
        repeat(2) { withFrameNanos { } }
        veil.animateTo(0f, androidx.compose.animation.core.tween(FADE_MS))
    }

    // Put focus somewhere sensible whenever the screen changes (the old screen's focused element is gone).
    // The tab bar is one focus target that is never rebuilt, so on tab pages this only matters when focus
    // was elsewhere; it can never pull focus back to an older tab.
    LaunchedEffect(screen) {
        // Home starts on its tab so the carousel keeps turning; it waits while focus is inside it.
        when (screen) {
            // Focus already in the header (moving along the tabs, or on to search) stays where the remote put it.
            is Screen.Top, is Screen.Universe -> if (!inHeader.value) headerFocus.tabs.requestWhenReady()
            Screen.Search -> searchField.requestWhenReady()
            Screen.Settings -> settingsButton.requestWhenReady()
            Screen.Profiles -> Unit                       // the picker focuses its first profile itself
            is Screen.Profile -> Unit                     // the page focuses its first button itself
        }
    }}

private const val EXIT_WINDOW_MS = 2_500L
/** How long the remote must rest on a tab before its page is built. Quicker presses only move the pill. */
private const val SETTLE_MS = 180L
private const val FADE_MS = 200
private const val BAR_SHRINK = 0.1f
private const val BAR_FADE = 0.55f

/** Things asked once per app process, not once per screen (coming back to Medusa doesn't ask again). */
private object Session { var askedWho = false }

/** Focus a control as soon as it is on screen (screens fill in a moment after they open). */
private suspend fun FocusRequester.requestWhenReady() {
    repeat(40) {
        // Wait a frame first: until then the requester can still point at the old screen's control,
        // which is about to be removed, and focus would fall to some other control on the new screen.
        withFrameNanos { }
        // The no-argument requestFocus() drops the result; this overload says whether focus moved.
        if (runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return
        delay(50)
    }
}
