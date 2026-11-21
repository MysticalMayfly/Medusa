package com.priyabrat.medusa.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.priyabrat.medusa.data.FilterKeys
import com.priyabrat.medusa.data.MedusaRepository
import com.priyabrat.medusa.ui.browse.BrowseActions
import com.priyabrat.medusa.ui.browse.BrowseScreen
import com.priyabrat.medusa.ui.components.HeroActions
import com.priyabrat.medusa.ui.components.LocalToast
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
import com.priyabrat.medusa.ui.model.SearchUiState
import com.priyabrat.medusa.ui.model.SettingsUiState
import com.priyabrat.medusa.ui.model.Tab
import com.priyabrat.medusa.ui.model.TitleDetail
import com.priyabrat.medusa.ui.model.UniverseUiState
import com.priyabrat.medusa.ui.model.UniversesUiState
import com.priyabrat.medusa.ui.search.SearchScreen
import com.priyabrat.medusa.ui.settings.SettingsScreen
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.universes.UniverseActions
import com.priyabrat.medusa.ui.universes.UniverseScreen
import com.priyabrat.medusa.ui.universes.UniversesScreen
import kotlinx.coroutines.delay

/** Where the app is. Tabs are top level; a universe, Search and Settings sit one level below. */
sealed interface Screen {
    data class Top(val tab: Tab) : Screen
    data class Universe(val id: String) : Screen
    data object Search : Screen
    data object Settings : Screen
}

/**
 * The app shell: header, screens, the details panel and the toast.
 * Back closes the panel, then goes up a level, then to Home; on Home it leaves the app.
 * Screens get state from [repo] and send actions back to it; they never fetch anything themselves.
 */
@Composable
fun MedusaApp(repo: MedusaRepository, settings: SettingsUiState, onUpdateButton: () -> Unit) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Top(Tab.Home)) }
    val screen = stack.last()
    var panelId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val toast = remember { ToastState() }
    val headerFocus = remember { HeaderFocus() }
    val settingsButton = remember { FocusRequester() }
    val searchField = remember { FocusRequester() }

    fun go(to: Screen) {
        if (to is Screen.Top) { stack.clear(); stack.add(to) } else if (stack.last() != to) stack.add(to)
    }

    BackHandler(enabled = stack.size > 1 || screen != Screen.Top(Tab.Home)) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else go(Screen.Top(Tab.Home))
    }

    val currentTab: Tab? = when (val s = screen) {
        is Screen.Top -> s.tab
        is Screen.Universe -> Tab.Universes
        else -> null
    }
    val header: @Composable () -> Unit = {
        AppHeader(
            current = currentTab,
            onTab = { go(Screen.Top(it)) },
            onSearch = { go(Screen.Search) },
            onSettings = { go(Screen.Settings) },
            updateWaiting = settings.updateWaiting,
            focus = headerFocus,
        )
    }

    val open: (String) -> Unit = { panelId = it }
    val hero = HeroActions(onOpen = open, onInterested = repo::setInterested, onSeen = repo::setWatched)

    CompositionLocalProvider(LocalToast provides toast::show) {
        Box(Modifier.fillMaxSize().background(MedusaColors.Bg)) {
            when (val s = screen) {
                is Screen.Top -> when (s.tab) {
                    Tab.Home -> {
                        val state by remember { repo.home() }.collectAsState(HomeUiState())
                        HomeScreen(
                            state,
                            HomeActions(hero, open, repo::markEpisodeWatched, onSeeAll = { go(Screen.Top(Tab.Horror)) }),
                            header,
                        )
                    }
                    Tab.Movies, Tab.Shows, Tab.More -> {
                        val kind = when (s.tab) { Tab.Movies -> BrowseKind.Movies; Tab.Shows -> BrowseKind.Shows; else -> BrowseKind.More }
                        val key = FilterKeys.browse(kind)
                        val state by remember(kind) { repo.browse(kind) }.collectAsState(BrowseUiState(kind))
                        BrowseScreen(
                            state,
                            BrowseActions(
                                hero = hero,
                                onOpen = open,
                                onPlatform = { repo.selectFilter(key, FilterKeys.GROUP_PLATFORM, it) },
                                onSegment = { repo.selectFilter(key, FilterKeys.GROUP_SEGMENT, it) },
                                onSelect = { sel, opt -> repo.selectFilter(key, FilterKeys.select(sel), opt) },
                                onToggleUnwatched = { repo.toggleFilter(key, FilterKeys.FLAG_UNWATCHED) },
                            ),
                            header,
                        )
                    }
                    Tab.Horror -> {
                        val state by remember { repo.horror() }.collectAsState(HorrorUiState())
                        val h = FilterKeys.HORROR
                        HorrorScreen(
                            state,
                            HorrorActions(
                                hero = hero,
                                onOpen = open,
                                onTab = { repo.selectFilter(h, FilterKeys.GROUP_TAB, it) },
                                onSeenSegment = { repo.selectFilter(h, FilterKeys.GROUP_SEGMENT, it) },
                                onWhere = { repo.selectFilter(h, FilterKeys.GROUP_WHERE, it) },
                                onSelect = { sel, opt -> repo.selectFilter(h, FilterKeys.select(sel), opt) },
                                onSeeAll = { repo.selectFilter(h, FilterKeys.GROUP_TAB, "all") },
                            ),
                            header,
                        )
                    }
                    Tab.Universes -> {
                        val state by remember { repo.universes() }.collectAsState(UniversesUiState())
                        UniversesScreen(state, onOpenUniverse = { go(Screen.Universe(it)) }, header)
                    }
                }
                is Screen.Universe -> {
                    val state by remember(s.id) { repo.universe(s.id) }.collectAsState(UniverseUiState())
                    val key = FilterKeys.universe(s.id)
                    UniverseScreen(
                        state,
                        UniverseActions(
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onOpen = open,
                            onWatched = repo::setWatched,
                            onSegment = { repo.selectFilter(key, FilterKeys.GROUP_SEGMENT, it) },
                            onToggleHideWatched = { repo.toggleFilter(key, FilterKeys.FLAG_HIDE_WATCHED) },
                        ),
                        header,
                    )
                }
                Screen.Search -> {
                    val state by remember(query) { repo.search(query) }.collectAsState(SearchUiState(query))
                    SearchScreen(state, onQuery = { query = it }, onOpen = open, header = header, fieldFocus = searchField)
                }
                Screen.Settings -> SettingsScreen(settings, onUpdateButton, header, settingsButton)
            }
            toast.Host()
        }

        panelId?.let { id ->
            val detail by remember(id) { repo.detail(id) }.collectAsState(initial = null as TitleDetail?)
            DetailPanel(
                detail,
                DetailActions(onWatched = repo::setWatched, onInterested = repo::setInterested),
                onClose = { panelId = null },
            )
        }
    }

    // Put focus somewhere sensible whenever the screen changes (the old screen's focused element is gone).
    LaunchedEffect(screen) {
        // Home starts on its tab so the carousel keeps turning; it waits while focus is inside it.
        when (screen) {
            is Screen.Top -> headerFocus.tabs.getValue(screen.tab).requestWhenReady()
            is Screen.Universe -> headerFocus.tabs.getValue(Tab.Universes).requestWhenReady()
            Screen.Search -> searchField.requestWhenReady()
            Screen.Settings -> settingsButton.requestWhenReady()
        }
    }}

/** Focus a control as soon as it is on screen (screens fill in a moment after they open). */
private suspend fun FocusRequester.requestWhenReady() {
    repeat(40) {
        val r = runCatching { requestFocus() }
        if (r.isSuccess && (r.getOrNull() as Any?) != false) return
        delay(50)
    }
}
