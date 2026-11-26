package com.priyabrat.medusa.data

import com.priyabrat.medusa.data.FilterKeys.FLAG_HIDE_WATCHED
import com.priyabrat.medusa.data.FilterKeys.FLAG_UNWATCHED
import com.priyabrat.medusa.data.FilterKeys.GROUP_PLATFORM
import com.priyabrat.medusa.data.FilterKeys.GROUP_SEGMENT
import com.priyabrat.medusa.data.FilterKeys.GROUP_SORT
import com.priyabrat.medusa.data.FilterKeys.GROUP_TAB
import com.priyabrat.medusa.data.FilterKeys.GROUP_WHERE
import com.priyabrat.medusa.data.FilterKeys.HORROR
import com.priyabrat.medusa.ui.model.BrowseKind
import com.priyabrat.medusa.ui.model.BrowseUiState
import com.priyabrat.medusa.ui.model.ContinueItem
import com.priyabrat.medusa.ui.model.Fact
import com.priyabrat.medusa.ui.model.Link
import com.priyabrat.medusa.ui.model.FilterOption
import com.priyabrat.medusa.ui.model.FilterSelect
import com.priyabrat.medusa.ui.model.HeroPick
import com.priyabrat.medusa.ui.model.HomeUiState
import com.priyabrat.medusa.ui.model.HorrorUiState
import com.priyabrat.medusa.ui.model.HorrorWall
import com.priyabrat.medusa.ui.model.ProfilePageState
import com.priyabrat.medusa.ui.model.ProfilesUiState
import com.priyabrat.medusa.ui.model.PlatformProgress
import com.priyabrat.medusa.ui.model.ProgressSummary
import com.priyabrat.medusa.ui.model.Rating
import com.priyabrat.medusa.ui.model.RatingKind
import com.priyabrat.medusa.ui.model.SearchUiState
import com.priyabrat.medusa.ui.model.ServiceTag
import com.priyabrat.medusa.ui.model.Shelf
import com.priyabrat.medusa.ui.model.TitleCard
import com.priyabrat.medusa.ui.model.TitleDetail
import com.priyabrat.medusa.ui.model.UniverseEntry
import com.priyabrat.medusa.ui.model.UniverseGroup
import com.priyabrat.medusa.ui.model.UniverseTile
import com.priyabrat.medusa.ui.model.UniverseUiState
import com.priyabrat.medusa.ui.model.UniversesUiState
import com.priyabrat.medusa.ui.model.UpNext
import com.priyabrat.medusa.ui.model.WatchOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Stage 1 placeholder data: a template only. Nothing here comes from the Mac app or any database.
 * Every title, number and name is a stand-in that shows the layout. Stage 2 replaces this class.
 */
class FakeMedusaRepository : MedusaRepository {

    private data class Choices(
        val interested: Set<String> = setOf("t01", "t02", "t03", "t04", "t05", "t06"),
        val watched: Set<String> = setOf("t11", "t12"),
        val selected: Map<String, String> = emptyMap(),   // "screen|group" -> option id
        val flags: Set<String> = emptySet(),             // "screen|flag"
        val episodes: Map<String, Int> = emptyMap(),     // continue-watching id -> episodes ticked
    )

    private val state = MutableStateFlow(Choices())

    // ---------------------------------------------------------------- placeholders

    private val platforms = listOf(
        Platform("p1", "Platform A", "PA", 0xFFA2AAADL),
        Platform("p2", "Platform B", "PB", 0xFFE50914L),
        Platform("p3", "Platform C", "PC", 0xFF00A8E1L),
        Platform("p4", "Platform D", "PD", 0xFF9B5CFFL),
        Platform("p5", "Platform E", "PE", 0xFFF47521L),
        Platform("p6", "Platform F", "PF", 0xFF3D7BFFL),
        Platform("p7", "Platform G", "PG", 0xFF1CE783L),
    )

    private data class Platform(val id: String, val name: String, val monogram: String, val argb: Long)

    private fun id(n: Int) = "t%02d".format(n)

    private fun card(n: Int, c: Choices, sub: String = "Year · Score", dot: Long? = null, badge: String? = null) = TitleCard(
        id = id(n),
        title = "Title %02d".format(n),
        subtitle = sub,
        interested = id(n) in c.interested,
        watched = id(n) in c.watched,
        badge = badge,
        dotArgb = dot,
    )

    private fun cards(from: Int, count: Int, c: Choices, sub: String = "Year · Score", dot: Long? = null) =
        (from until from + count).map { card(it, c, sub, dot) }

    private val watchOptions = listOf(
        WatchOption("w1", "Platform A", "PA", "Your plan"),
        WatchOption("w2", "Platform B", "PB", "Rent ₹00"),
        WatchOption("w3", "Platform C", "PC", "Buy ₹000"),
    )

    private fun pick(n: Int, eyebrow: String, c: Choices) = HeroPick(
        id = id(n),
        eyebrow = eyebrow,
        title = "Title %02d".format(n),
        year = "Year",
        runtime = "000 min",
        ratings = listOf(Rating(RatingKind.Imdb, "0.0"), Rating(RatingKind.RtFresh, "00%")),
        overview = "The synopsis appears here: two or three lines about the story, cut off after the third line so the buttons always sit in the same place.",
        watchLabel = "Watch on Platform A",
        watchOptions = watchOptions,
        interested = id(n) in c.interested,
        seen = id(n) in c.watched,
    )

    private fun key(screen: String, group: String) = "$screen|$group"
    private fun Choices.sel(screen: String, group: String, default: String) = selected[key(screen, group)] ?: default
    private fun Choices.flag(screen: String, flag: String) = key(screen, flag) in flags

    private fun options(ids: List<Pair<String, String>>, chosen: String, counts: List<Int?> = emptyList()) =
        ids.mapIndexed { i, (oid, label) -> FilterOption(oid, label, counts.getOrNull(i), selected = oid == chosen) }

    private fun select(screen: String, sid: String, labels: List<String>, c: Choices): FilterSelect {
        val chosen = c.sel(screen, FilterKeys.select(sid), "0")
        val opts = labels.mapIndexed { i, l -> FilterOption(i.toString(), l, selected = i.toString() == chosen) }
        return FilterSelect(sid, opts.first { it.selected }.label, active = chosen != "0", options = opts)
    }

    // ---------------------------------------------------------------- Home

    override fun home(): Flow<HomeUiState> {
        val shelfOrder = listOf(0, 1, 2, 3, 4, 5).shuffled()     // a new order each time Home opens
        return state.map { c ->
            val shelves = listOf(
                Shelf("interested", "You’re interested in", items = cards(1, 12, c)),
                Shelf("airing", "Airing now and coming up", items = cards(13, 5, c, "S0 airing · in 0d")),
                Shelf("best", "Best reviewed on your plans", items = cards(20, 14, c)),
                Shelf("newhorror", "New horror you can watch now", seeAllLabel = "See all", items = cards(40, 12, c)),
                Shelf("start", "Shows to start", items = cards(60, 12, c)),
                Shelf("added", "Just added to your collection", items = cards(80, 12, c)),
            )
            HomeUiState(
                picks = listOf(pick(1, "Tonight’s pick · Genre", c), pick(2, "Tonight’s pick · Genre", c), pick(3, "Tonight’s pick · Genre", c)),
                continueTitle = "Continue watching",
                continueWatching = listOf(1, 2).map { n ->
                    val done = c.episodes["c$n"] ?: 0
                    ContinueItem(
                        id = "c$n",
                        title = "Show %02d".format(n),
                        progress = (0.2f + 0.1f * done).coerceAtMost(1f),
                        progressLabel = "S1 E${2 + done} done · next S1 E${3 + done}",
                        nextLabel = "Watched S1 E${3 + done}",
                    )
                },
                shelves = shelfOrder.map { shelves[it] },
                progress = ProgressSummary(
                    eyebrow = "Your watch progress",
                    watchedCount = 0,
                    totalLabel = "of 0000 titles",
                    breakdown = "0 of 000 shows · 0 of 0000 movies · 0 shows in progress.",
                    percent = 0,
                    platforms = platforms.map { p ->
                        PlatformProgress(p.id, p.name, p.monogram, watched = 0, total = 0, percentLabel = "0%", fraction = 0f, argb = p.argb)
                    },
                ),
            )
        }
    }

    // ---------------------------------------------------------------- Movies, TV shows, More

    override fun browse(kind: BrowseKind): Flow<BrowseUiState> = state.map { c ->
        val screen = FilterKeys.browse(kind)
        val platform = c.sel(screen, GROUP_PLATFORM, "all")
        val tabs = listOf(FilterOption("all", "All", 0, selected = platform == "all", monogram = "▦")) +
            platforms.take(if (kind == BrowseKind.More) 4 else 6).map {
                FilterOption(it.id, it.name, 0, selected = platform == it.id, dotArgb = it.argb, monogram = it.monogram)
            }
        val segment = c.sel(screen, GROUP_SEGMENT, "movies")
        val unwatched = c.flag(screen, FLAG_UNWATCHED)
        BrowseUiState(
            kind = kind,
            pick = if (kind == BrowseKind.More) null else pick(if (kind == BrowseKind.Movies) 4 else 5, if (kind == BrowseKind.Movies) "Movie pick · Genre" else "Show pick · Genre", c),
            title = when (kind) { BrowseKind.Movies -> "Movies"; BrowseKind.Shows -> "TV shows"; BrowseKind.More -> "More" },
            summary = if (kind == BrowseKind.More) "Not streaming, free, for rent or for sale right now · 0 of 000 watched" else "0 of 000 watched",
            platformTabs = tabs,
            typeSegments = if (kind == BrowseKind.More) options(listOf("all" to "Everything", "shows" to "Shows", "movies" to "Movies"), segment) else emptyList(),
            selects = listOf(
                select(screen, "franchise", listOf("Any franchise", "Franchise 1", "Franchise 2", "Franchise 3"), c),
                select(screen, "genre", listOf("Any genre", "Genre 1", "Genre 2"), c),
                select(screen, "studio", listOf("Any studio", "Studio 1"), c),
            ),
            resultCount = "000 titles",
            sort = select(screen, GROUP_SORT, listOf("By platform", "Recently added", "Title A–Z", "Newest first", "Top rated"), c),
            unwatchedOnly = unwatched,
            items = cards(100, 32, c).filter { !unwatched || !it.watched },
        )
    }

    // ---------------------------------------------------------------- Horror ("Standard" look)

    override fun horror(): Flow<HorrorUiState> = state.map { c ->
        val tab = c.sel(HORROR, GROUP_TAB, "browse")
        val tabs = options(listOf("browse" to "Browse", "y1" to "Year 1", "y2" to "Year 2", "y3" to "Year 3", "all" to "All horror"), tab)
        val services = platforms.take(4)
        HorrorUiState(
            pick = pick(6, "Horror pick", c),
            tabs = tabs,
            shelves = if (tab != "browse") emptyList() else listOf(
                Shelf("h-int", "You’re interested in", items = cards(1, 10, c, "00% · Platform A", services[0].argb)),
            ) + services.map { s ->
                Shelf("h-${s.id}", "On ${s.name}", seeAllLabel = "See all 000", dotArgb = s.argb, items = cards(200, 10, c, "Year · 00%"))
            } + listOf(
                Shelf("h-next", "Next in your franchises", items = cards(220, 8, c)),
                Shelf("h-y1", "Year 1 horror", items = cards(230, 10, c)),
                Shelf("h-rent", "Rent or buy only", items = cards(240, 10, c, "Rent only · 00%")),
            ),
            wall = if (tab == "browse") null else HorrorWall(
                title = tabs.first { it.selected }.label.let { if (tab == "all") it else "$it horror" },
                summary = "0 of 000 seen · 000 ready to watch on your plans or free",
                seenSegments = options(listOf("notseen" to "Not seen", "seen" to "Seen", "all" to "Everything"), c.sel(HORROR, GROUP_SEGMENT, "notseen")),
                sort = select(HORROR, GROUP_SORT, listOf("Best reviewed", "Your list order", "Newest first", "Title A–Z"), c),
                whereChips = listOf(
                    FilterOption("anywhere", "Anywhere"),
                    FilterOption("plans", "On my plans"),
                    FilterOption("free", "Free with ads"),
                ).plus(services.map { FilterOption(it.id, it.name, dotArgb = it.argb) })
                    .plus(FilterOption("rent", "Rent only"))
                    .let { list -> val w = c.sel(HORROR, GROUP_WHERE, "anywhere"); list.map { it.copy(selected = it.id == w) } },
                items = cards(300, 24, c, "Platform A · 00%", services[0].argb),
            ),
        )
    }

    // ---------------------------------------------------------------- Universes

    override fun universes(): Flow<UniversesUiState> = state.map {
        fun tile(n: Int, seen: Int, total: Int) = UniverseTile(
            id = "u$n",
            name = "Universe $n",
            seenLabel = "$seen of $total seen",
            progress = if (total == 0) 0f else seen.toFloat() / total,
            upNext = "Up next: Title %02d".format(n),
            tintArgb = platforms[n % platforms.size].argb,
        )
        UniversesUiState(
            eyebrow = "Universes",
            headline = "00 universes, 00 of 000 seen",
            intro = "Pick one to see every film in order, with what’s next and where it streams.",
            groups = listOf(
                UniverseGroup("Keep going", 4, "You’ve started these. The next film is waiting.", (1..4).map { tile(it, 1, 3) }),
                UniverseGroup("Start something new", 6, "Not a single one seen yet.", (5..10).map { tile(it, 0, 4) }),
            ),
        )
    }

    override fun universe(id: String): Flow<UniverseUiState> = state.map { c ->
        val screen = FilterKeys.universe(id)
        val hide = c.flag(screen, FLAG_HIDE_WATCHED)
        val entries = (1..6).map { n ->
            UniverseEntry(
                id = "$id-e$n",
                title = "Title %02d".format(n),
                meta = "Year · 0h 00m · 00%",
                seen = n <= 2 || "$id-e$n" in c.watched,
                upNext = n == 3,
                services = if (n == 1) listOf(ServiceTag("Platform A", mine = true, argb = platforms[0].argb)) else emptyList(),
            )
        }
        UniverseUiState(
            id = id,
            name = "Universe ${id.removePrefix("u")}",
            stat = "0 of 0 seen · about 0h 00m of films to go",
            progress = 2f / 6f,
            tintArgb = 0xFFFF5D73L,
            upNext = UpNext("$id-e3", "Title 03", "Year · 0h 00m"),
            segments = options(listOf("all" to "Everything", "movies" to "Movies", "shows" to "Shows"), c.sel(screen, GROUP_SEGMENT, "all")),
            hideWatched = hide,
            entries = entries.filter { !hide || !it.seen },
        )
    }

    // ---------------------------------------------------------------- details panel and search

    override fun detail(id: String): Flow<TitleDetail> = state.map { c ->
        TitleDetail(
            id = id,
            kindLine = "Type · Year · 000 min",
            title = "Title",
            tagline = "The tagline goes here.",
            overview = "The synopsis appears here. A few sentences about the story, as long as the source gives, wrapped to the panel's width.",
            ratings = listOf(Rating(RatingKind.Imdb, "0.0"), Rating(RatingKind.RtFresh, "00%")),
            facts = listOf(
                Fact("Rating", "★ 0.0"),
                Fact("Genres", "Genre, Genre"),
                Fact("Tomatometer", "00%"),
                Fact("Status", "Status"),
            ),
            streaming = listOf(ServiceTag("Platform A · your plan", mine = true, argb = platforms[0].argb), ServiceTag("Platform B", mine = false, argb = platforms[1].argb)),
            rentPrices = listOf(Fact("Platform C", "Rent ₹00"), Fact("Platform D", "Buy ₹000")),
            links = listOf(Link("IMDb", "https://www.imdb.com/"), Link("Where to watch (IN)", "https://www.justwatch.com/in")),
            watched = id in c.watched,
            interested = id in c.interested,
            hasTrailer = true,
        )
    }

    override fun search(query: String): Flow<SearchUiState> = state.map { c ->
        val q = query.trim()
        if (q.isEmpty()) SearchUiState(query = query, resultLabel = "Type to search your collection")
        else SearchUiState(query = query, resultLabel = "00 results for “$q”", results = cards(400, 16, c))
    }

    // ---------------------------------------------------------------- actions

    override fun setInterested(id: String, on: Boolean) =
        state.update { it.copy(interested = if (on) it.interested + id else it.interested - id) }

    override fun setWatched(id: String, on: Boolean) =
        state.update { it.copy(watched = if (on) it.watched + id else it.watched - id) }

    override fun markEpisodeWatched(id: String) =
        state.update { it.copy(episodes = it.episodes + (id to ((it.episodes[id] ?: 0) + 1))) }

    override fun profiles(): Flow<ProfilesUiState> = kotlinx.coroutines.flow.flowOf(ProfilesUiState())
    override fun profilePage(id: String): Flow<ProfilePageState> = kotlinx.coroutines.flow.flowOf(ProfilePageState(id))
    override fun switchProfile(id: String) {}
    override fun saveProfile(id: String?, name: String, avatar: Int): String = id ?: "p1"
    override fun deleteProfile(id: String) {}

    override fun selectFilter(screen: String, group: String, optionId: String) =
        state.update { it.copy(selected = it.selected + (key(screen, group) to optionId)) }

    override fun toggleFilter(screen: String, flag: String) = state.update {
        val k = key(screen, flag)
        it.copy(flags = if (k in it.flags) it.flags - k else it.flags + k)
    }
}
