package com.priyabrat.medusa.data

import com.priyabrat.medusa.data.FilterKeys.FLAG_HIDE_WATCHED
import com.priyabrat.medusa.data.FilterKeys.FLAG_UNWATCHED
import com.priyabrat.medusa.data.FilterKeys.GROUP_PLATFORM
import com.priyabrat.medusa.data.FilterKeys.GROUP_SEE_ALL
import com.priyabrat.medusa.data.FilterKeys.GROUP_SEGMENT
import com.priyabrat.medusa.data.FilterKeys.GROUP_SORT
import com.priyabrat.medusa.data.FilterKeys.GROUP_TAB
import com.priyabrat.medusa.data.FilterKeys.GROUP_WHERE
import com.priyabrat.medusa.data.FilterKeys.HORROR
import com.priyabrat.medusa.data.library.Changes
import com.priyabrat.medusa.data.library.Library
import com.priyabrat.medusa.data.library.LibraryStore
import com.priyabrat.medusa.data.library.Offer
import com.priyabrat.medusa.data.library.Profile
import com.priyabrat.medusa.data.library.Profiles
import com.priyabrat.medusa.data.library.ProfileEvent
import com.priyabrat.medusa.data.library.ProfileSync
import com.priyabrat.medusa.data.library.OutboxDispatch
import com.priyabrat.medusa.data.library.ServerProfiles
import org.json.JSONObject
import com.priyabrat.medusa.data.library.Service
import com.priyabrat.medusa.data.library.Title
import com.priyabrat.medusa.ui.model.BrowseKind
import com.priyabrat.medusa.ui.model.BrowseUiState
import com.priyabrat.medusa.ui.model.ContinueItem
import com.priyabrat.medusa.ui.model.Fact
import com.priyabrat.medusa.ui.model.FilterOption
import com.priyabrat.medusa.ui.model.FilterSelect
import com.priyabrat.medusa.ui.model.HeroPick
import com.priyabrat.medusa.ui.model.HomeUiState
import com.priyabrat.medusa.ui.model.HorrorUiState
import com.priyabrat.medusa.ui.model.HorrorWall
import com.priyabrat.medusa.ui.model.Link
import com.priyabrat.medusa.ui.model.PlatformProgress
import com.priyabrat.medusa.ui.model.ProfilePageState
import com.priyabrat.medusa.ui.model.ProfileTile
import com.priyabrat.medusa.ui.model.ProfilesUiState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The real repository: your library imported from the Mac (see data/library), with the Mac app's page logic ported
 * from public/app.js, tonight.js, horror-stream.js and universes.js. Every number on every screen comes from here,
 * from one copy of the titles, so the same fact can't disagree between screens (docs/SLOTS.md "Must match").
 * Watched, Interested and episode progress you change on the TV are saved on the TV (changes.json).
 */
class LibraryRepository(private val store: LibraryStore) : MedusaRepository {

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val library = MutableStateFlow(Library.EMPTY)
    private val changes = MutableStateFlow(Changes())
    private val filters = MutableStateFlow(Filters())
    private val profilesFlow = MutableStateFlow(Profiles.DEFAULT)
    private val profilesReady = MutableStateFlow(false)
    private val profileMutex = Mutex()
    private val outboxLock = Any()

    /** Titles with your TV changes applied; rebuilt whenever the library or a change moves. */
    private val snap: Flow<Snap> = combine(library, changes, profilesFlow) { lib, ch, ps -> Snap(lib, ch, ps.current.id == "p1") }

    /** True once a library is on the TV (Settings shows the import button either way). */
    val hasLibrary: StateFlow<Boolean> get() = _hasLibrary
    private val _hasLibrary = MutableStateFlow(false)

    init {
        io.launch {
            val existing = store.loadProfiles()
            val p = existing.withoutRetiredProfile()
            if (p != existing) {
                store.saveProfiles(p)
                store.deleteChanges("p01f2b6c399")
                synchronized(outboxLock) { store.saveOutbox(store.loadOutbox().filterNot { it.profile == "p01f2b6c399" }) }
            }
            profilesFlow.value = p
            changes.value = store.loadChanges(p.current.id)
            profilesReady.value = true
            val lib = store.loadCatalogue()
            library.value = lib
            _hasLibrary.value = lib.titles.isNotEmpty()
        }
    }

    /** A freshly imported library. Your TV changes stay on top of it. */
    fun replaceLibrary(lib: Library) {
        synchronized(rounds) { rounds.clear() }
        library.value = lib
        _hasLibrary.value = lib.titles.isNotEmpty()
    }

    // ================================================================ effective titles

    /** A title as the screens see it: the imported record with your TV changes on top. */
    private class T(val r: Title, ch: Changes, legacyProfile: Boolean) {
        val id = r.id
        val sid = r.id.toString()
        val watched = ch.watched[r.id] ?: (legacyProfile && r.watched)
        val interested = ch.interested[r.id] ?: (legacyProfile && r.interested)
        private val p = ch.progress[r.id]
        val progSeason = if (p != null) p.season else if (legacyProfile) r.progSeason else null
        val progEpisode = if (p != null) p.episode else if (legacyProfile) r.progEpisode else null
        val progAt = if (p != null) p.at else if (legacyProfile) r.progAt else null
        val year: Int? = r.years?.takeWhile { it.isDigit() }?.toIntOrNull()
        val isShow = r.type == "show"
        val isMovie = r.type == "movie"
        val horror = "Horror" in r.genres
    }

    private class Snap(val lib: Library, val ch: Changes, legacyProfile: Boolean) {
        val titles: List<T> = lib.titles.map { T(it, ch, legacyProfile) }
        val byId: Map<Int, T> = titles.associateBy { it.id }
        val today: LocalDate = LocalDate.now()
        val thisYear = today.year
        fun svc(code: String): Service? = lib.serviceBy[code]
    }

    private data class Filters(val selected: Map<String, String> = emptyMap(), val flags: Set<String> = emptySet()) {
        fun sel(screen: String, group: String, default: String) = selected["$screen|$group"] ?: default
        fun flag(screen: String, flag: String) = "$screen|$flag" in flags
    }

    // ================================================================ shared helpers (app.js, tonight.js)

    private fun Snap.released(t: T) = t.year == null || t.year <= thisYear
    /** Indian services for a title: Horror.html's checked list first, else TMDB's. */
    private fun codes(t: T): List<String> = if (t.r.indiaStatus == "streaming") t.r.indiaSubs else t.r.ott
    private fun Snap.free(t: T) = codes(t).filter { svc(it)?.free == true }
    private fun Snap.watchable(t: T) = codes(t).isNotEmpty()
    private fun tonightScore(t: T) = (t.r.score ?: 50.0) / 10.0
    private fun sortScore(t: T) = t.r.score ?: -1.0
    private val byScoreDesc = Comparator<T> { a, b -> tonightScore(b).compareTo(tonightScore(a)) }
    private val byHorrorScore = Comparator<T> { a, b -> sortScore(b).compareTo(sortScore(a)).let { if (it != 0) it else a.r.title.compareTo(b.r.title, ignoreCase = true) } }
    private fun animated(t: T) = "Animation" in t.r.tmdbGenres || "Animation" in t.r.genres || "Anime" in t.r.genres

    /** Titles the carousel and Home shelves draw from (tonight.js candidates()). */
    private fun Snap.candidates() = titles.filter { (!it.watched || it.id in keep) && !it.r.placeholder && released(it) && it.r.type != null && it.r.avail }

    /** Titles marked seen this session stay in the carousel until the app is next opened (Prince's rule). */
    private val keep = HashSet<Int>()

    private fun scoreLabel(t: T): String = when {
        t.r.rtScore != null -> "${t.r.rtScore}%"
        t.r.tmdbRating != null -> "★ %.1f".format(Locale.US, t.r.tmdbRating)
        else -> ""
    }

    private fun ratings(t: T): List<Rating> = buildList {
        t.r.imdbRating?.let { add(Rating(RatingKind.Imdb, "%.1f".format(Locale.US, it))) }
        t.r.rtScore?.let { add(Rating(if (it >= 60) RatingKind.RtFresh else RatingKind.RtRotten, "$it%")) }
    }

    private fun epsDone(t: T): Int = t.r.episodes.take(((t.progSeason ?: 1) - 1).coerceAtLeast(0)).sum() + (t.progEpisode ?: 0)

    /** The next episode to watch (app.js nextEp), or null when the show is done. */
    private fun nextEp(t: T): Pair<Int, Int>? {
        val eps = t.r.episodes
        if (t.progSeason == null) return 1 to 1
        var s = t.progSeason
        var e = (t.progEpisode ?: 0) + 1
        if (eps.isNotEmpty() && s <= eps.size && eps[s - 1] != 0 && e > eps[s - 1]) { s += 1; e = 1 }
        if (eps.isNotEmpty() && s > eps.size) return null
        return s to e
    }

    private class Airing(val date: LocalDate, val days: Long, val live: Boolean, val what: String, val short: String, val source: String)

    private fun day(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))

    /** Airing now or coming up within 60 days (app.js airing): TVmaze first, the hand-set date as fallback. */
    private fun Snap.airing(t: T): Airing? {
        if (!t.isShow) return null
        val nx = t.r.nextEp
        val nxDate = nx?.airdate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (nx != null && nxDate != null) {
            val days = ChronoUnit.DAYS.between(today, nxDate)
            if (days < 0 || days > 60) return null
            val prev = t.r.prevEp?.airdate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val live = prev != null && ChronoUnit.DAYS.between(prev, today) <= 21
            val sEps = t.r.episodes.getOrNull(nx.season - 1)
            val (what, short) = when {
                nx.number == 1 -> "Season ${nx.season} premiere" to "S${nx.season} ${day(nxDate)}"
                sEps != null && nx.number == sEps -> "Season ${nx.season} finale" to "S${nx.season} finale ${day(nxDate)}"
                else -> "Season ${nx.season}, episode ${nx.number}" to (if (live) "S${nx.season} airing" else "S${nx.season}E${nx.number} ${day(nxDate)}")
            }
            return Airing(nxDate, days, live, what, short, "TVmaze")
        }
        val manual = t.r.airingDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val days = ChronoUnit.DAYS.between(today, manual)
        if (days < 0 || days > 60) return null
        val what = t.r.airingNote ?: "New episode"
        val live = !Regex("premiere", RegexOption.IGNORE_CASE).containsMatchIn(t.r.airingNote ?: "")
        return Airing(manual, days, live, what, "${what.replace(Regex("^Season (\\d+)"), "S$1")} ${day(manual)}", "manual")
    }

    private fun argb(hex: String?, fallback: Long = 0xFF8B8A96): Long {
        if (hex == null || !hex.startsWith("#") || hex.length != 7) return fallback
        return hex.substring(1).toLongOrNull(16)?.let { 0xFF000000 or it } ?: fallback
    }

    private fun seasonsLabel(t: T) = if (t.r.seasons > 0) "${t.r.seasons} season${if (t.r.seasons > 1) "s" else ""}" else "Series"
    private fun hm(m: Int) = if (m >= 60) "${m / 60}h${if (m % 60 != 0) " ${m % 60}m" else ""}" else "${m}m"

    // ================================================================ cards

    /** A shelf poster (tonight.js rowHtml): a given line, or year and score. */
    private fun shelfCard(t: T, sub: String? = null, dot: Long? = null) = TitleCard(
        id = t.sid,
        title = t.r.title,
        subtitle = sub ?: listOfNotNull(t.year?.toString(), scoreLabel(t).ifEmpty { null }).joinToString(" · "),
        interested = t.interested,
        watched = t.watched,
        dotArgb = dot,
        posterUrl = t.r.poster,
    )

    /** A wall poster (app.js cardHtml): airing badge, progress bar for a show you're partway through. */
    private fun Snap.wallCard(t: T): TitleCard {
        val going = t.isShow && !t.watched && t.progSeason != null
        val sub = if (going) "S${t.progSeason} E${t.progEpisode ?: 0} done"
        else listOfNotNull(t.year?.toString(), if (t.isShow && t.r.seasons > 0) seasonsLabel(t) else null, scoreLabel(t).ifEmpty { null }).joinToString(" · ")
        val pct = if (going && t.r.totalEpisodes > 0) epsDone(t).toFloat() / t.r.totalEpisodes else null
        return TitleCard(t.sid, t.r.title, sub, t.interested, t.watched, badge = airing(t)?.short, progress = pct?.takeIf { it > 0f }, posterUrl = t.r.poster)
    }

    // ================================================================ carousel (tonight.js)

    private val mix = listOf("movie", "show", "movie", "movie", "show", "movie", "show", "movie")
    private val favSlots = listOf(true, false, false, true, false, false, true, false)
    private val rounds = HashMap<String, List<Int>>()

    private fun inScope(scope: String, t: T) = scope == "all" || (if (scope == "horror") t.horror else t.r.type == scope)
    private fun typeAt(scope: String, n: Int) = if (scope == "movie" || scope == "show") scope else mix[n % mix.size]

    private class Weighted(val t: T, val w: Double)

    private fun Snap.pool(scope: String, type: String?): List<Weighted> {
        val base = candidates().filter { !it.watched && !animated(it) && inScope(scope, it) && (type == null || it.r.type == type) }
        val ready = base.filter { watchable(it) }
        val from = if (ready.isNotEmpty()) ready else base
        return from.map {
            Weighted(it, (if (it.interested) 7.0 else 0.0) + tonightScore(it) / 2 + (if (it.horror) 0.5 else 0.0) +
                (if (it.r.backdrop != null) 1.0 else 0.0) + (if (it.r.trailerKey != null && it.r.trailerOk != 0) 0.5 else 0.0))
        }.sortedByDescending { it.w }
    }

    private fun Snap.choose(scope: String, skip: Collection<Int>, type: String?, fav: Boolean): T? {
        fun ok(x: Weighted) = x.t.id !in skip && (!fav || x.t.interested)
        var all = pool(scope, type).filter(::ok)
        if (all.isEmpty() && type != null && type != scope) all = pool(scope, null).filter(::ok)
        if (all.isEmpty() && fav) return choose(scope, skip, type, false)
        val recent = ch.shown.take(40).toSet()
        val fresh = all.filter { it.t.id !in recent }
        val p = (if (fresh.isNotEmpty()) fresh else all).take(40)
        if (p.isEmpty()) return null
        // Weighted random among the strongest, so it feels like a shuffle but stays good.
        val total = p.sumOf { it.w * it.w }
        var r = Math.random() * total
        for (x in p) { r -= x.w * x.w; if (r <= 0) return x.t }
        return p[0].t
    }

    /** The round of 8 picks for a page, decided once per app session (each page keeps its own round). */
    private fun Snap.round(scope: String): List<T> {
        if (titles.isEmpty()) return emptyList()
        val ids = synchronized(rounds) {
            rounds.getOrPut(scope) {
                val out = ArrayList<Int>()
                for (k in 0 until 8) { val n = choose(scope, out, typeAt(scope, k), favSlots[k % favSlots.size]) ?: break; out.add(n.id) }
                remember(out)
                out
            }
        }
        return ids.mapNotNull { byId[it] }
    }

    private fun remember(ids: List<Int>) {
        if (ids.isEmpty()) return
        changes.update { c -> c.copy(shown = (ids + c.shown.filter { it !in ids }).take(400)) }
        save()
    }

    /** Each Indian service's Android TV app, opened when the catalogue has no deep link for a title. */
    private val SERVICE_APPS = mapOf(
        "NF" to "com.netflix.ninja", "PV" to "com.amazon.amazonvideo.livingroom", "JH" to "in.startv.hotstar",
        "SL" to "com.sonyliv", "AP" to "com.apple.atve.androidtv.appletv", "Z5" to "com.graymatrix.did",
        "LP" to "com.lionsgateplay.videoapp", "MU" to "com.mubi", "PX" to "com.plexapp.android",
    )

    private val kindLabel = listOf("Streaming", "Free", "Streaming", "Rent", "Buy")

    private class Ranked(val o: Offer, val rank: Int)

    /** One choice per service, best first: known streaming services, then free, then other streaming offers, then rent and buy (watchOffers). */
    private fun Snap.offers(t: T): List<Ranked> {
        fun rank(o: Offer): Int {
            val s = o.code?.let { svc(it) }
            return when {
                o.type == "flatrate" && s != null -> 0
                o.type == "free" || o.type == "ads" -> 1
                o.type == "flatrate" -> 2
                o.type == "rent" -> 3
                else -> 4
            }
        }
        val ranked = t.r.watch.map { Ranked(it, rank(it)) }.sortedBy { it.rank }
        val streams = ranked.any { it.rank <= 1 || (it.rank == 2 && it.o.code != null) }
        val seen = HashSet<String>()
        val urls = HashSet<String>()
        val out = ArrayList<Ranked>()
        for (x in ranked) {
            val k = x.o.code ?: x.o.pkg ?: x.o.name
            if (k in seen || x.o.url in urls || (streams && x.rank >= 3)) continue
            if (x.o.code == null && x.rank == 2) continue
            seen.add(k); urls.add(x.o.url)
            out.add(x)
        }
        return out
    }

    private fun Snap.offerLabel(x: Ranked) = x.o.code?.let { svc(it)?.name } ?: x.o.name

    private fun Snap.heroPick(t: T, scope: String, first: Boolean = true): HeroPick {
        // Genres only (franchise and collection names read like genres and confused the line), from TMDB.
        val tags = t.r.tmdbGenres.ifEmpty { t.r.genres }.filter { !(scope == "horror" && it == "Horror") }.take(3)
        // "Tonight's pick" names the first slide only; the others show just their genres.
        val eye = if (!first) "" else when (scope) { "movie" -> "Movie pick"; "show" -> "Show pick"; "horror" -> "Horror pick"; else -> "Tonight’s pick" }
        val offers = offers(t)
        var options = offers.mapIndexed { i, x ->
            WatchOption("o$i", offerLabel(x), x.o.code ?: offerLabel(x).filter { it.isLetterOrDigit() }.take(2).uppercase(), kindLabel[x.rank], x.o.url)
        }
        // No JustWatch link, but the catalogue knows the services it streams on: open those apps instead
        // (the details panel already listed Prime Video while this button said there was no link).
        if (options.isEmpty()) {
            options = codes(t).mapNotNull { c -> SERVICE_APPS[c]?.let { pkg ->
                val s = svc(c)
                WatchOption("s$c", s?.name ?: c, c, if (s?.free == true) "Free" else "Streaming", "app:$pkg")
            } }
        }
        val label = when {
            options.size == 1 && offers.size == 1 -> "Watch on ${offerLabel(offers[0])}${if (offers[0].rank >= 3) " · ${kindLabel[offers[0].rank]}" else ""}"
            options.size == 1 -> "Watch on ${options[0].service}"
            options.isEmpty() -> "Not streaming in India"
            else -> "Watch now"
        }
        return HeroPick(
            id = t.sid,
            eyebrow = listOf(eye, tags.joinToString(" · ")).filter { it.isNotEmpty() }.joinToString(" · "),
            title = t.r.title,
            year = t.year?.toString() ?: "",
            runtime = if (t.isShow) seasonsLabel(t) else t.r.runtime?.let { "$it min" } ?: "",
            ratings = ratings(t),
            overview = t.r.overview ?: "",
            watchLabel = label,
            watchOptions = options,
            interested = t.interested,
            seen = t.watched,
            trailerId = t.r.trailerKey?.takeIf { t.r.trailerOk != 0 },
            backdropUrl = t.r.backdrop,
            posterUrl = t.r.poster,
        )
    }

    // ================================================================ Home

    override fun home(): Flow<HomeUiState> {
        val seed = (Math.random() * Int.MAX_VALUE).toLong()   // shelves shuffle each time Home opens
        return snap.map { s -> s.homeState(seed) }.flowOn(Dispatchers.Default)
    }

    private fun Snap.homeState(seed: Long): HomeUiState {
        if (titles.isEmpty()) return HomeUiState()
        val c = candidates()
        val rows = ArrayList<Shelf>()
        fun cards(list: List<T>) = list.map { shelfCard(it) }
        val fav = c.filter { it.interested }.sortedWith(byScoreDesc)
        val air = titles.filter { it.r.avail }.mapNotNull { t -> airing(t)?.let { t to it } }.sortedBy { it.second.date }
        val newH = c.filter { it.horror && (it.year ?: 0) >= thisYear - 1 && watchable(it) }.sortedWith(byScoreDesc).take(60)
        val shows = c.filter { it.isShow && it.progSeason == null && (watchable(it) || it.r.platform != null) }.sortedWith(byScoreDesc).take(60)
        val added = c.sortedWith(compareByDescending<T> { it.r.createdAt }.thenByDescending { it.id }).take(40)

        // A title shows in one row only (the first that claims it); every row but the dated one is shuffled, 24 at most.
        val used = HashSet<Int>()
        fun claim(list: List<T>) = list.filter { used.add(it.id) }
        var salt = 0
        fun mixed(list: List<T>) = shuffled(list, seed, ++salt).take(24)
        val favC = claim(fav)
        if (favC.isNotEmpty()) rows.add(Shelf("fav", "You’re interested in", items = cards(mixed(favC))))
        if (air.isNotEmpty()) rows.add(Shelf("air", "Airing now and coming up", items = air.map { (t, a) ->
            shelfCard(t, if (a.days == 0L) "${a.short} · today" else "${a.short} · in ${a.days}d")
        }))
        claim(newH).let { if (it.size >= 4) rows.add(Shelf("newh", "New horror you can watch now", seeAllLabel = "See all", items = cards(mixed(it)))) }
        claim(shows).let { if (it.size >= 4) rows.add(Shelf("shows", "Shows to start", items = cards(mixed(it)))) }
        // One row per streaming service, best reviewed first; services with the most titles come first.
        lib.services.map { s -> s to c.filter { s.code in codes(it) }.sortedWith(byScoreDesc).take(60) }
            .sortedByDescending { it.second.size }
            .forEach { (s, list) -> claim(list).let { if (it.size >= 4) rows.add(Shelf("svc:${s.code}", "Streaming on ${s.name}", dotArgb = argb(s.colour), items = cards(mixed(it)))) } }
        claim(added).let { if (it.size >= 4) rows.add(Shelf("added", "Just added to your collection", items = cards(mixed(it)))) }

        val going = titles.filter { it.isShow && !it.watched && it.progSeason != null && it.r.avail }.sortedByDescending { it.progAt ?: "" }
        return HomeUiState(
            picks = round("all").mapIndexed { n, t -> heroPick(t, "all", n == 0) },
            continueTitle = "Continue watching",
            continueWatching = going.map { continueItem(it) },
            shelves = rows,
            progress = progress(),
        )
    }

    private fun continueItem(t: T): ContinueItem {
        val nx = nextEp(t)
        return ContinueItem(
            id = t.sid,
            title = t.r.title,
            progress = if (t.r.totalEpisodes > 0) epsDone(t).toFloat() / t.r.totalEpisodes else 0f,
            progressLabel = "S${t.progSeason} E${t.progEpisode ?: 0} done" + (nx?.let { " · next S${it.first} E${it.second}" } ?: ""),
            nextLabel = nx?.let { "Watched S${it.first} E${it.second}" } ?: "",
            backdropUrl = t.r.backdrop ?: t.r.poster,
        )
    }

    /** "Your watch progress" (app.js progressHtml). */
    private fun Snap.progress(): ProgressSummary {
        val all = titles.filter { !it.r.placeholder }
        val watched = all.count { it.watched }
        val shows = all.filter { it.isShow }
        val movies = all.filter { it.isMovie }
        val going = shows.count { !it.watched && it.progSeason != null }
        val pct = if (all.isEmpty()) 0f else watched.toFloat() / all.size
        return ProgressSummary(
            eyebrow = "Your watch progress",
            watchedCount = watched,
            totalLabel = "of ${all.size} titles",
            breakdown = "${shows.count { it.watched }} of ${shows.size} shows · ${movies.count { it.watched }} of ${movies.size} movies" +
                (if (going > 0) " · $going show${if (going > 1) "s" else ""} in progress" else "") + ".",
            percent = (pct * 100).roundToInt(),
            platforms = lib.platforms.mapNotNull { p ->
                val a = all.filter { it.r.platform == p.slug }
                if (a.isEmpty()) return@mapNotNull null
                val w = a.count { it.watched }
                val f = w.toFloat() / a.size
                PlatformProgress(p.slug, p.name, p.slug, w, a.size, "${(f * 100).roundToInt()}%", f, argb(p.colour))
            },
        )
    }

    // ================================================================ Movies, TV shows, More (app.js filtered, filtersHtml)

    override fun browse(kind: BrowseKind): Flow<BrowseUiState> =
        combine(snap, filters) { s, f -> s.browseState(kind, f) }.flowOn(Dispatchers.Default)

    private fun Snap.browseState(kind: BrowseKind, f: Filters): BrowseUiState {
        val screen = FilterKeys.browse(kind)
        val more = kind == BrowseKind.More
        val type = when (kind) { BrowseKind.Movies -> "movie"; BrowseKind.Shows -> "show"; BrowseKind.More -> f.sel(screen, GROUP_SEGMENT, "all") }
        val plat = f.sel(screen, GROUP_PLATFORM, "all")
        val unw = f.flag(screen, FLAG_UNWATCHED)
        val tagSel = mapOf(
            "franchise" to f.sel(screen, FilterKeys.select("franchise"), ""),
            "genre" to f.sel(screen, FilterKeys.select("genre"), ""),
            "studio" to f.sel(screen, FilterKeys.select("studio"), ""),
        )
        val sort = f.sel(screen, FilterKeys.select(GROUP_SORT), "default")
        val base = titles.filter { (if (more) !it.r.avail else it.r.avail) && (more || it.r.type == type) }
        val list = base.filter { t ->
            (plat == "all" || t.r.platform == plat) && (!more || type == "all" || t.r.type == type) && (!unw || !t.watched) &&
                (tagSel["franchise"]!!.isEmpty() || tagSel["franchise"] in t.r.franchises) &&
                (tagSel["genre"]!!.isEmpty() || tagSel["genre"] in t.r.genres) &&
                (tagSel["studio"]!!.isEmpty() || tagSel["studio"] in t.r.studios)
        }
        val order = lib.platforms.mapIndexed { i, p -> p.slug to i }.toMap()
        val sorted = when (sort) {
            "title" -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.r.title })
            "year" -> list.sortedWith(compareByDescending<T> { it.year ?: 0 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.r.title })
            "added" -> list.sortedWith(compareByDescending<T> { it.r.createdAt }.thenByDescending { it.id })
            "rating" -> list.sortedByDescending { it.r.score ?: -1.0 }
            else -> list.sortedWith(compareBy<T> { order[it.r.platform] ?: 99 }.thenBy { if (it.isShow) 0 else 1 }.thenBy { it.id })
        }
        val mineAll = if (more) base else base
        val going = if (kind == BrowseKind.Shows) mineAll.count { !it.watched && it.progSeason != null } else 0
        val tabs = listOf(FilterOption("all", "All", base.size, selected = plat == "all", monogram = "▦")) +
            lib.platforms.filter { p -> plat == p.slug || base.any { it.r.platform == p.slug } }.map { p ->
                FilterOption(p.slug, p.name, base.count { it.r.platform == p.slug }, selected = plat == p.slug, dotArgb = argb(p.colour), monogram = p.slug)
            }
        fun tagSelect(k: String, any: String): FilterSelect? {
            val opts = lib.tags.filter { it.kind == k && it.count > 0 }
            if (opts.isEmpty()) return null
            val chosen = tagSel[k]!!
            val options = listOf(FilterOption("", any, selected = chosen.isEmpty())) + opts.map { FilterOption(it.name, "${it.name} (${it.count})", selected = chosen == it.name) }
            return FilterSelect(k, if (chosen.isEmpty()) any else chosen, active = chosen.isNotEmpty(), options = options)
        }
        val sorts = listOf("default" to "By platform", "added" to "Recently added", "title" to "Title A–Z", "year" to "Newest first", "rating" to "Top rated")
        return BrowseUiState(
            kind = kind,
            picks = when (kind) {
                BrowseKind.Movies -> round("movie").mapIndexed { n, t -> heroPick(t, "movie", n == 0) }
                BrowseKind.Shows -> round("show").mapIndexed { n, t -> heroPick(t, "show", n == 0) }
                BrowseKind.More -> emptyList()
            },
            title = when (kind) { BrowseKind.Movies -> "Movies"; BrowseKind.Shows -> "TV shows"; BrowseKind.More -> "More" },
            summary = (if (more) "Not streaming, free, for rent or for sale in India right now · " else "") +
                "${base.count { it.watched }} of ${base.size} watched" + (if (kind == BrowseKind.Shows) " · $going in progress" else ""),
            // More holds titles you can't watch in India, so service chips there only confused (premiere platform).
            platformTabs = if (more) emptyList() else tabs,
            typeSegments = if (more) listOf("all" to "Everything", "show" to "Shows", "movie" to "Movies").map { (k, l) -> FilterOption(k, l, selected = type == k) } else emptyList(),
            selects = listOfNotNull(tagSelect("franchise", "Any franchise"), tagSelect("genre", "Any genre"), tagSelect("studio", "Any studio")),
            resultCount = "${sorted.size} ${if (sorted.size == 1) "title" else "titles"}",
            sort = FilterSelect(GROUP_SORT, sorts.first { it.first == sort }.second, active = sort != "default",
                options = sorts.map { (k, l) -> FilterOption(k, l, selected = k == sort) }),
            unwatchedOnly = unw,
            items = sorted.map { wallCard(it) },
        )
    }

    // ================================================================ Horror (horror-stream.js)

    override fun horror(): Flow<HorrorUiState> = combine(snap, filters) { s, f -> s.horrorState(f) }.flowOn(Dispatchers.Default)

    private fun Snap.horrorTitles() = titles.filter { it.horror && !it.r.placeholder && it.r.avail }
    private fun Snap.yearLists() = lib.lists.filter { it.kind == "year" && it.genre == "Horror" }.sortedByDescending { it.year ?: 0 }
    private fun Snap.listTitles(listId: Int) = (lib.listItems[listId] ?: emptyList()).mapNotNull { byId[it] }.filter { it.r.avail }
    private fun Snap.svcOrCode(c: String) = svc(c) ?: Service(c, c, "#888888", free = false, sort = 99)
    private fun rentOf(t: T) = t.r.indiaRent

    private class Where(val text: String, val colour: Long?)

    private fun Snap.where(t: T): Where {
        val all = codes(t)
        return when {
            all.isNotEmpty() -> svcOrCode(all[0]).let { s -> Where(if (s.free) "${s.name}, free" else s.name, argb(s.colour)) }
            rentOf(t).isNotEmpty() -> Where("Rent only", null)
            else -> Where(if (t.r.indiaStatus == "not_yet") "Not in India yet" else "", null)
        }
    }

    private fun Snap.horrorState(f: Filters): HorrorUiState {
        if (titles.isEmpty()) return HorrorUiState()
        val years = yearLists()
        val tab = f.sel(HORROR, GROUP_TAB, "browse").let { t -> if (t == "browse" || t == "all" || years.any { it.year.toString() == t }) t else "browse" }
        val tabs = listOf(FilterOption("browse", "Browse", selected = tab == "browse")) +
            years.map { FilterOption(it.year.toString(), it.year.toString(), selected = tab == it.year.toString()) } +
            FilterOption("all", "All horror", selected = tab == "all")
        return HorrorUiState(
            picks = round("horror").mapIndexed { n, t -> heroPick(t, "horror", n == 0) },
            tabs = tabs,
            shelves = if (tab == "browse") horrorRows() else emptyList(),
            wall = if (tab == "browse") null else horrorWall(tab, f),
        )
    }

    private fun Snap.horrorRows(): List<Shelf> {
        val c = horrorTitles().filter { !it.watched && released(it) }
        val out = ArrayList<Shelf>()
        fun rating(t: T) = scoreLabel(t)
        fun yearRating(t: T) = listOfNotNull(t.year?.toString(), rating(t).ifEmpty { null }).joinToString(" · ")
        fun ratingWhere(t: T) = listOf(rating(t), where(t).text).filter { it.isNotEmpty() }.joinToString(" · ")
        val fav = c.filter { it.interested }.sortedWith(byHorrorScore)
        if (fav.isNotEmpty()) out.add(Shelf("fav", "You’re interested in", items = fav.take(30).map { shelfCard(it, ratingWhere(it)) }))
        // One row per streaming service, biggest first.
        lib.services
            .map { s -> s to c.filter { s.code in codes(it) }.sortedWith(byHorrorScore) }
            .filter { it.second.size >= 3 }
            .sortedByDescending { it.second.size }
            .forEach { (s, list) ->
                out.add(Shelf("svc:${s.code}", if (s.free) "Free on ${s.name}" else "Streaming on ${s.name}", dotArgb = argb(s.colour),
                    seeAllLabel = if (list.size > 30) "See all ${list.size}" else null, items = list.take(30).map { shelfCard(it, yearRating(it)) }))
            }
        // Next film in each horror franchise, the ones you've started first.
        class Next(val t: T, val sub: String, val seen: Int)
        val nexts = ArrayList<Next>()
        for (g in lib.tags.filter { it.kind == "franchise" }) {
            val films = titles.filter { g.name in it.r.franchises && !it.r.placeholder && it.r.avail }.sortedBy { it.year ?: 9999 }
            if (films.isEmpty() || films.count { it.horror }.toFloat() / films.size < 0.5f) continue
            val seen = films.count { it.watched }
            val next = films.firstOrNull { !it.watched && released(it) } ?: continue
            nexts.add(Next(next, "${g.name} · $seen of ${films.size} seen", seen))
        }
        nexts.sortWith(compareByDescending<Next> { if (it.seen > 0) 1 else 0 }.thenByDescending { it.seen })
        if (nexts.size >= 3) out.add(Shelf("next", "Next in your franchises", items = nexts.map { shelfCard(it.t, it.sub) }))
        // The year lists, in the order you curated them.
        for (l in yearLists()) {
            val all = listTitles(l.id)
            val left = all.filter { !it.watched }
            if (left.isNotEmpty()) out.add(Shelf("year:${l.year}", "${l.year} horror", count = left.size, seeAllLabel = "See all ${all.size}",
                items = left.take(30).map { shelfCard(it, ratingWhere(it)) }))
        }
        val rentOnly = c.filter { codes(it).isEmpty() && rentOf(it).isNotEmpty() }.sortedWith(byHorrorScore)
        if (rentOnly.size >= 3) out.add(Shelf("rent", "Rent or buy only", items = rentOnly.take(30).map { shelfCard(it, yearRating(it)) }))
        return out
    }

    private fun Snap.horrorWall(tab: String, f: Filters): HorrorWall {
        val list = yearLists().firstOrNull { it.year.toString() == tab }
        val all = if (list != null) listTitles(list.id) else horrorTitles()
        val status = f.sel(HORROR, GROUP_SEGMENT, "notseen")
        val where = f.sel(HORROR, GROUP_WHERE, "anywhere")
        val sortDefault = if (list != null) "list" else "score"
        val sort = f.sel(HORROR, FilterKeys.select(GROUP_SORT), sortDefault).let { if (it == "list" && list == null) "score" else it }
        var items = all.filter { t ->
            (status == "all" || (status == "seen") == t.watched) &&
                (where == "anywhere" || (where == "free" && free(t).isNotEmpty()) ||
                    (where == "rent" && codes(t).isEmpty() && rentOf(t).isNotEmpty()) || (where.startsWith("svc:") && where.removePrefix("svc:") in codes(t)))
        }
        items = when (sort) {
            "score" -> items.sortedWith(byHorrorScore)
            "title" -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.r.title })
            "year" -> items.sortedWith(compareByDescending<T> { it.year ?: 0 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.r.title })
            else -> items
        }
        val used = lib.services.filter { s -> all.any { s.code in codes(it) } }.sortedByDescending { s -> all.count { s.code in codes(it) } }
        val sorts = listOf("score" to "Best reviewed") + (if (list != null) listOf("list" to "Your list order") else emptyList()) +
            listOf("year" to "Newest first", "title" to "Title A–Z")
        val chips = listOf(FilterOption("anywhere", "Anywhere"), FilterOption("free", "Free with ads")) +
            used.map { FilterOption("svc:${it.code}", it.name, dotArgb = argb(it.colour)) } + FilterOption("rent", "Rent only")
        return HorrorWall(
            title = if (list != null) "${list.year} horror" else "All horror",
            summary = "${all.count { it.watched }} of ${all.size} seen · ${all.count { !it.watched && watchable(it) }} streaming in India",
            seenSegments = listOf("notseen" to "Not seen", "seen" to "Seen", "all" to "Everything").map { (k, l) -> FilterOption(k, l, selected = k == status) },
            sort = FilterSelect(GROUP_SORT, sorts.first { it.first == sort }.second, active = sort != sortDefault, options = sorts.map { (k, l) -> FilterOption(k, l, selected = k == sort) }),
            whereChips = chips.map { it.copy(selected = it.id == where) },
            items = items.map { t ->
                val w = where(t)
                TitleCard(t.sid, t.r.title, listOf(w.text, scoreLabel(t)).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { t.year?.toString() ?: "" },
                    t.interested, t.watched, dotArgb = w.colour, posterUrl = t.r.poster)
            },
        )
    }

    // ================================================================ Universes (universes.js)

    private val palette = listOf(0xFFE5484D, 0xFFF76B15, 0xFFFFC53D, 0xFF46A758, 0xFF12A594, 0xFF0090FF, 0xFF6E56CF, 0xFFD6409F, 0xFFAD7F58, 0xFF8E8C99)

    private fun slugOf(name: String) = name.lowercase().replace(Regex("[’']"), "").replace(Regex("[^a-z0-9]+"), " ").trim().replace(' ', '-')

    private inner class Universe(s: Snap, val name: String) {
        val films = s.titles.filter { name in it.r.franchises && !it.r.placeholder }
            .sortedWith(compareBy<T> { it.year ?: 9999 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.r.title })
        val out = films.filter { with(s) { released(it) } }
        val seen = out.count { it.watched }
        val soon = films.filter { !with(s) { released(it) } }
        val next = out.firstOrNull { !it.watched }
        val left = out.filter { !it.watched && it.isMovie }.sumOf { it.r.runtime ?: 0 }
        val frac = if (out.isEmpty()) 0f else seen.toFloat() / out.size
        val colour: Long = s.lib.tags.firstOrNull { it.kind == "franchise" && it.name == name }?.colour?.let { argb(it) }
            ?: palette[(name.fold(0L) { h, ch -> (h * 31 + ch.code) and 0xFFFFFFFFL } % palette.size).toInt()]
    }

    override fun universes(): Flow<UniversesUiState> = snap.map { s -> s.universesState() }.flowOn(Dispatchers.Default)

    private fun Snap.universesState(): UniversesUiState {
        if (titles.isEmpty()) return UniversesUiState()
        val all = lib.tags.filter { it.kind == "franchise" }.map { Universe(this, it.name) }
        val by = compareByDescending<Universe> { it.frac }.thenByDescending { it.films.size }.thenBy { it.name }
        fun tile(u: Universe): UniverseTile {
            val pick = (listOfNotNull(u.next?.takeIf { it.r.poster != null }) + u.films.filter { it.r.poster != null && it !== u.next }).take(5)
            return UniverseTile(
                id = slugOf(u.name),
                name = u.name,
                seenLabel = if (u.films.isEmpty()) "No films listed yet" else "${u.seen} of ${u.out.size} seen" + (if (u.soon.isNotEmpty()) " · ${u.soon.size} coming" else ""),
                progress = u.frac,
                upNext = when { u.next != null -> "Up next: ${u.next.r.title}"; u.soon.isNotEmpty() -> "Caught up until ${u.soon[0].year ?: "TBA"}"; else -> "All seen" },
                tintArgb = u.colour,
                posterUrls = pick.mapNotNull { it.r.poster },
            )
        }
        val groups = listOf(
            Triple("Keep going", "You’ve started these. The next film is waiting.", all.filter { it.seen > 0 && it.seen < it.out.size }.sortedWith(by)),
            Triple("Start something new", "Not a single one seen yet.", all.filter { it.films.isNotEmpty() && it.seen == 0 }.sortedByDescending { it.films.size }),
            Triple("Finished", "Every released film seen.", all.filter { it.out.isNotEmpty() && it.seen == it.out.size }.sortedWith(by)),
        ).filter { it.third.isNotEmpty() }
        val withFilms = all.filter { it.films.isNotEmpty() }
        return UniversesUiState(
            eyebrow = "Universes",
            headline = "${withFilms.size} universes, ${all.sumOf { it.seen }} of ${all.sumOf { it.out.size }} seen",
            intro = "Pick one to see every film in order, with what’s next and where it streams.",
            groups = groups.map { (title, note, list) -> UniverseGroup(title, list.size, note, list.map(::tile)) },
        )
    }

    override fun universe(id: String): Flow<UniverseUiState> =
        combine(snap, filters) { s, f -> s.universeState(id, f) }.flowOn(Dispatchers.Default)

    private fun Snap.universeState(id: String, f: Filters): UniverseUiState {
        val name = lib.tags.firstOrNull { it.kind == "franchise" && slugOf(it.name) == id }?.name ?: return UniverseUiState(id = id)
        val u = Universe(this, name)
        val screen = FilterKeys.universe(id)
        val kind = f.sel(screen, GROUP_SEGMENT, "all")
        val hide = f.flag(screen, FLAG_HIDE_WATCHED)
        val n = u.next
        val rows = u.films.filter { t -> (kind == "all" || t.r.type == kind) && (!hide || !t.watched || t.id == n?.id) }
        return UniverseUiState(
            id = id,
            name = u.name,
            stat = "${u.seen} of ${u.out.size} seen" + (if (u.left > 0) " · about ${hm(u.left)} of films to go" else "") + (if (u.soon.isNotEmpty()) " · ${u.soon.size} announced" else ""),
            progress = u.frac,
            tintArgb = u.colour,
            upNext = n?.let { UpNext(it.sid, it.r.title, listOfNotNull(it.year?.toString(), if (it.isMovie) it.r.runtime?.let(::hm) else null).joinToString(" · ")) },
            segments = listOf("all" to "Everything", "movie" to "Movies", "show" to "Shows").map { (k, l) -> FilterOption(k, l, selected = k == kind) },
            hideWatched = hide,
            entries = rows.map { t ->
                val soon = !released(t)
                UniverseEntry(
                    id = t.sid,
                    title = t.r.title,
                    meta = listOf(t.year?.toString() ?: "", if (t.isShow) seasonsLabel(t) else t.r.runtime?.let(::hm) ?: "Film", scoreLabel(t))
                        .filter { it.isNotEmpty() }.joinToString(" · ") + if (soon) " · coming" else "",
                    seen = t.watched,
                    upNext = t.id == n?.id,
                    services = codes(t).take(3).map { c -> val sv = svcOrCode(c); ServiceTag(sv.name, argb(sv.colour)) },
                    posterUrl = t.r.poster,
                )
            },
            backdropUrl = n?.r?.backdrop ?: u.films.firstOrNull { it.r.backdrop != null }?.r?.backdrop,
        )
    }

    // ================================================================ details panel (app.js renderDetail)

    override fun detail(id: String): Flow<TitleDetail> = snap.map { s -> s.detailState(id) }.flowOn(Dispatchers.Default)

    private fun Snap.detailState(id: String): TitleDetail {
        val t = id.toIntOrNull()?.let { byId[it] } ?: return TitleDetail(id, "", "", null, "", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), false, false, false)
        val r = t.r
        val facts = ArrayList<Fact>()
        if (t.isShow && r.episodes.isNotEmpty()) {
            val total = r.totalEpisodes
            facts.add(Fact("Your progress", if (t.watched) "Finished" else if (t.progSeason != null) "${epsDone(t)} of $total episodes · up to S${t.progSeason} E${t.progEpisode ?: 0}" else "Not started"))
        }
        airing(t)?.let { a ->
            facts.add(Fact(if (a.live) "Airing now" else "Coming up", "${a.what} on ${day(a.date)} (${if (a.days == 0L) "today" else "in ${a.days} day${if (a.days == 1L) "" else "s"}"})"))
        }
        r.tmdbRating?.let { facts.add(Fact("TMDB rating", "★ %.1f".format(Locale.US, it))) }
        r.traktRating?.let { facts.add(Fact("Trakt rating", "★ %.1f".format(Locale.US, it) + (r.traktVotes?.let { v -> " (${"%,d".format(Locale.US, v)} votes)" } ?: ""))) }
        if (r.tmdbGenres.isNotEmpty()) facts.add(Fact("TMDB genres", r.tmdbGenres.joinToString(", ")))
        if (r.networks.isNotEmpty()) facts.add(Fact("Original network", r.networks.joinToString(", ")))
        r.rtScore?.let { facts.add(Fact("Tomatometer", "$it%")) }
        val lists = lib.lists.filter { l -> lib.listItems[l.id]?.contains(t.id) == true }
        if (lists.isNotEmpty()) facts.add(Fact("On your lists", lists.joinToString(", ") { it.name }))
        r.tmdbStatus?.let { facts.add(Fact("Status", it)) }

        val c = codes(t)
        val streaming = when {
            c.isNotEmpty() -> c.map { code ->
                val sv = svcOrCode(code)
                ServiceTag(sv.name + if (sv.free) " · free" else "", argb(sv.colour))
            }
            r.providerNames.isNotEmpty() -> r.providerNames.map { ServiceTag(it, 0xFF8B8A96) }
            r.indiaStatus == "not_yet" -> listOf(ServiceTag("Not streaming in India yet", 0xFF6E6C7A))
            else -> emptyList()
        }
        val rent = r.indiaRent.map { entry ->
            // "Apple TV Store rent ₹149.00" -> Apple TV Store rent · ₹149
            val i = entry.indexOf('₹')
            if (i > 0) Fact(entry.substring(0, i).trim(), entry.substring(i).removeSuffix(".00")) else Fact(entry, "Rent or buy")
        } + listOfNotNull(r.indiaNote?.let { Fact("Note", it) })
        val links = listOfNotNull(
            r.tmdbId?.let { Link("TMDB", "https://www.themoviedb.org/${r.tmdbType}/$it") },
            r.traktSlug?.let { Link("Trakt", "https://trakt.tv/${if (r.tmdbType == "tv") "shows" else "movies"}/$it") },
            r.tvmazeId?.let { Link("TVmaze", "https://www.tvmaze.com/shows/$it") },
            r.imdbId?.let { Link("IMDb", "https://www.imdb.com/title/$it/") },
            r.providersLink?.let { Link("Where to watch (IN)", it) },
        )
        return TitleDetail(
            id = t.sid,
            kindLine = listOfNotNull(if (t.isShow) "Show" else if (t.isMovie) "Movie" else null, r.years, r.runtime?.let { "$it min" }).joinToString(" · "),
            title = r.title,
            tagline = r.tagline,
            overview = r.overview ?: "",
            ratings = ratings(t),
            facts = facts,
            streaming = streaming,
            rentPrices = rent,
            links = links,
            watched = t.watched,
            interested = t.interested,
            hasTrailer = r.trailerKey != null,
            trailerUrl = r.trailerKey?.let { "https://www.youtube.com/watch?v=$it" },
            backdropUrl = r.backdrop,
            posterUrl = r.poster,
        )
    }

    // ================================================================ search (app.js filtered, q)

    override fun search(query: String): Flow<SearchUiState> = snap.map { s ->
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@map SearchUiState(query = query, resultLabel = "Type to search your collection")
        val hits = s.titles.filter { t ->
            listOfNotNull(t.r.title, t.r.notes, t.r.years, t.r.tmdbTitle).any { q in it.lowercase() } ||
                (t.r.franchises + t.r.genres + t.r.studios).any { q in it.lowercase() }
        }.sortedWith(compareBy<T> { if (it.r.title.lowercase().startsWith(q)) 0 else 1 }.thenByDescending { it.r.score ?: -1.0 })
        SearchUiState(query = query, resultLabel = "${hits.size} result${if (hits.size == 1) "" else "s"} for “${query.trim()}”", results = hits.map { s.wallCard(it) })
    }.flowOn(Dispatchers.Default)

    // ================================================================ actions

    private fun now() = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private fun save() {
        val who = profilesFlow.value.current.id
        io.launch {
            profileMutex.withLock {
                // A switch saves the outgoing profile under this same lock. Do not let an
                // older queued write overwrite it after the profile has changed.
                if (profilesFlow.value.current.id == who) store.saveChanges(who, changes.value)
            }
        }
    }

    // ================================================================ profile sync (data/library/ProfileSync.kt)

    /** Records one change for the central database: stamped locally, queued, and sent when the TV is online. */
    private fun record(profile: String, kind: String, titleId: Int?, value: JSONObject) {
        if (profile == Profiles.GUEST_ID) return
        val e = ProfileEvent(profile, kind, titleId, value, ProfileEvent.now())
        if (titleId != null && profile == profilesFlow.value.current.id) changes.update { it.copy(stamps = it.stamps + (titleId to e.createdAt)) }
        io.launch {
            synchronized(outboxLock) { store.saveOutbox(store.loadOutbox() + e) }
            flush()
        }
    }

    private fun flush() {
        synchronized(outboxLock) {
            val pending = store.loadOutbox()
            if (pending.isNotEmpty()) {
                val remaining = OutboxDispatch.dispatch(pending, { it.profile }, googleAccessToken) { events, token ->
                    googleCloudProfile(events.first().profile)?.let { cloud -> ProfileSync.send(events, token, cloud) } ?: false
                }
                if (remaining.size != pending.size) store.saveOutbox(remaining)
            }
        }
    }

    /**
     * Sends what is waiting, downloads the profiles from Supabase, and merges them into the TV's cache: the Master's
     * rows, with this TV's newer changes on top. Profiles made on another device appear; ones deleted elsewhere go.
     */
    suspend fun syncProfiles() = kotlinx.coroutines.withContext(Dispatchers.IO) { profileMutex.withLock {
        flush()
        val ps = profilesFlow.value
        val tokens = ps.list.mapNotNull { p ->
            val token = googleAccessToken(p.id)
            val cloud = googleCloudProfile(p.id)
            if (token == null || cloud == null) null else p.id to (cloud to token)
        }.toMap()
        val text = ProfileSync.fetch(tokens)
        val server = ServerProfiles.parse(text)
        store.saveServerProfiles(text)
        val pending = synchronized(outboxLock) { store.loadOutbox() }
        val waiting = pending.filter { it.kind == "profile" }.map { it.profile }.toSet()
        val gone = pending.filter { it.kind == "delete_profile" }.map { it.profile }.toSet()
        // The Master's list, plus profiles made here that it hasn't seen yet; never one deleted here.
        // Only accounts signed in on this device appear here; cloud profiles must not reappear after logout.
        val list = ps.refreshVisible(server.profiles, waiting, gone)
        if (list.isEmpty()) return@withLock
        val active = if (list.any { it.id == ps.active }) ps.active else list.first().id
        // Save what is on screen before merging into the files.
        store.saveChanges(ps.current.id, changes.value)
        for (p in list) if (p.id != Profiles.GUEST_ID) store.saveChanges(p.id, ProfileSync.merge(store.loadChanges(p.id), server.rows[p.id] ?: emptyList()))
        val next = Profiles(list, active)
        store.saveProfiles(next)
        if (active != ps.current.id) { synchronized(rounds) { rounds.clear() }; keep.clear() }
        changes.value = store.loadChanges(active)
        profilesFlow.value = next
    } }

    // ================================================================ profiles

    private fun tile(p: Profile, ps: Profiles) = ProfileTile(p.id, p.name, p.avatar, p.id == ps.current.id, p.photoUrl)

    /** Set by MainActivity: whether a profile has a Google session. */
    var googleSignedIn: (String) -> Boolean = { false }
    var googleAccessToken: (String) -> String? = { null }
    var googleCloudProfile: (String) -> String? = { null }
    private val googleSessionRevision = MutableStateFlow(0)

    suspend fun useGuestProfile() = kotlinx.coroutines.withContext(Dispatchers.IO) {
        profileMutex.withLock {
            val ps = profilesFlow.value
            if (ps.list.isNotEmpty()) store.saveChanges(ps.current.id, changes.value)
            val next = ps.withGuest()
            store.saveProfiles(next)
            synchronized(rounds) { rounds.clear() }
            keep.clear()
            changes.value = store.loadChanges(Profiles.GUEST_ID)
            profilesFlow.value = next
        }
    }

    /** Add or restore an account locally without changing or deleting another profile's history. */
    suspend fun useGoogleProfile(id: String, name: String, photoUrl: String?) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        profileMutex.withLock {
            val ps = profilesFlow.value
            if (ps.list.isNotEmpty()) store.saveChanges(ps.current.id, changes.value)
            val old = ps.list.firstOrNull { it.id == id }
            val p = Profile(id, name.trim().ifEmpty { old?.name ?: "Profile" }.take(20), old?.avatar ?: 0, photoUrl)
            val next = ps.withSelected(p)
            store.saveProfiles(next)
            synchronized(rounds) { rounds.clear() }
            keep.clear()
            changes.value = store.loadChanges(id)
            profilesFlow.value = next
            googleSessionRevision.update { it + 1 }
            record(id, "profile", null, JSONObject().put("name", p.name).put("avatar", p.avatar).put("photo_url", p.photoUrl ?: JSONObject.NULL))
        }
        // Restore the server copy, including on a device that has never seen this account.
        runCatching { syncProfiles() }.isSuccess
    }

    /** Remove a signed-out profile only from this device; keep its changes and the server records. */
    suspend fun removeLocalProfile(id: String) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        profileMutex.withLock {
            val ps = profilesFlow.value
            if (ps.list.none { it.id == id }) return@withLock
            if (ps.current.id == id) store.saveChanges(id, changes.value)
            val next = ps.withoutLocal(id)
            store.saveProfiles(next)
            if (ps.current.id == id) {
                synchronized(rounds) { rounds.clear() }
                keep.clear()
                changes.value = store.loadChanges(next.current.id)
            }
            profilesFlow.value = next
            googleSessionRevision.update { it + 1 }
        }
    }

    override fun profiles(): Flow<ProfilesUiState> = combine(profilesFlow, profilesReady) { ps, ready ->
        ProfilesUiState(ps.list.map { tile(it, ps) }, ps.list.firstOrNull { it.id == ps.current.id }?.let { tile(it, ps) }, ready)
    }

    override fun profilePage(id: String): Flow<ProfilePageState> = combine(library, profilesFlow, changes, googleSessionRevision) { lib, ps, ch, _ ->
        val p = ps.list.firstOrNull { it.id == id } ?: return@combine ProfilePageState(id)
        // Another profile's lists come from its own file; the one in use from what is on screen now.
        val mine = if (p.id == ps.current.id) ch else store.loadChanges(p.id)
        val s = Snap(lib, mine, p.id == "p1")
        val interested = s.titles.filter { it.interested }.sortedWith(byScoreDesc)
        val watched = s.titles.filter { it.watched }.sortedByDescending { mine.watchedAt[it.id] ?: "" }
        val going = s.titles.count { it.isShow && !it.watched && it.progSeason != null }
        ProfilePageState(
            id = p.id, name = p.name, avatar = p.avatar, photoUrl = p.photoUrl,
            google = if (googleSignedIn(p.id)) "Signed in with Google" else null, active = p.id == ps.current.id,
            stat = "${watched.size} watched · ${interested.size} interested" + if (going > 0) " · $going show${if (going > 1) "s" else ""} in progress" else "",
            interested = interested.map { shelfCard(it) },
            watched = watched.map { shelfCard(it) },
        )
    }.flowOn(Dispatchers.IO)

    override fun switchProfile(id: String) {
        io.launch {
            profileMutex.withLock {
                val ps = profilesFlow.value
                if (ps.current.id == id || ps.list.none { it.id == id }) return@withLock
                store.saveChanges(ps.current.id, changes.value)
                val next = ps.copy(active = id)
                store.saveProfiles(next)
                val ch = store.loadChanges(id)
                synchronized(rounds) { rounds.clear() }
                keep.clear()
                changes.value = ch
                profilesFlow.value = next
            }
        }
    }

    override fun setInterested(id: String, on: Boolean) {
        val n = id.toIntOrNull() ?: return
        changes.update { it.copy(interested = it.interested + (n to on)) }
        record(profilesFlow.value.current.id, "interested", n, JSONObject().put("on", on))
        save()
    }

    override fun setWatched(id: String, on: Boolean) {
        val n = id.toIntOrNull() ?: return
        if (on) keep.add(n)
        val at = ProfileEvent.now()
        changes.update { it.copy(watched = it.watched + (n to on), watchedAt = if (on) it.watchedAt + (n to at) else it.watchedAt - n) }
        record(profilesFlow.value.current.id, "watched", n, JSONObject().put("on", on).put("at", at))
        save()
    }

    /** One more episode (the Mac's progress "next"); finishing the last episode of the last season marks the show watched. */
    override fun markEpisodeWatched(id: String) {
        val n = id.toIntOrNull() ?: return
        val r = library.value.byId[n] ?: return
        val t = T(r, changes.value, profilesFlow.value.current.id == "p1")
        val eps = r.episodes
        var s = t.progSeason ?: 0
        var e = t.progEpisode ?: 0
        if (s == 0) { s = 1; e = 1 } else {
            e += 1
            if (eps.isNotEmpty() && s <= eps.size && e > eps[s - 1]) { if (s < eps.size) { s += 1; e = 1 } else e -= 1 }
        }
        val done = eps.isNotEmpty() && s == eps.size && e > 0 && eps.last() > 0 && e >= eps.last()
        changes.update {
            var c = it.copy(progress = it.progress + (n to Changes.Progress(s, e, now())))
            if (done) c = c.copy(watched = c.watched + (n to true), watchedAt = c.watchedAt + (n to now()))
            c
        }
        record(profilesFlow.value.current.id, "progress", n, JSONObject().put("season", s).put("episode", e).put("at", ProfileEvent.now()))
        if (done) record(profilesFlow.value.current.id, "watched", n, JSONObject().put("on", true).put("at", ProfileEvent.now()))
        save()
    }

    override fun selectFilter(screen: String, group: String, optionId: String) {
        if (group == GROUP_SEE_ALL) { seeAll(screen, optionId); return }
        filters.update { it.copy(selected = it.selected + ("$screen|$group" to optionId)) }
    }

    /** "See all" on a shelf: Home's new horror opens this year's horror wall; Horror's rows open their wall with that filter. */
    private fun seeAll(screen: String, shelfId: String) {
        val sel = HashMap<String, String>()
        when {
            shelfId == "newh" -> sel["$HORROR|$GROUP_TAB"] = LocalDate.now().year.toString()
            shelfId.startsWith("year:") -> sel["$HORROR|$GROUP_TAB"] = shelfId.removePrefix("year:")
            shelfId.startsWith("svc:") -> { sel["$HORROR|$GROUP_TAB"] = "all"; sel["$HORROR|$GROUP_WHERE"] = shelfId }
            else -> sel["$HORROR|$GROUP_TAB"] = "all"
        }
        filters.update { f ->
            // a new wall starts unfiltered (horror-stream.js render): not seen, its own default sort
            val cleared = f.selected.filterKeys { it != "$HORROR|$GROUP_WHERE" && it != "$HORROR|$GROUP_SEGMENT" && it != "$HORROR|${FilterKeys.select(GROUP_SORT)}" }
            f.copy(selected = cleared + sel)
        }
    }

    override fun toggleFilter(screen: String, flag: String) = filters.update {
        val k = "$screen|$flag"
        it.copy(flags = if (k in it.flags) it.flags - k else it.flags + k)
    }

    // ================================================================ shuffle (tonight.js shuffled: a seeded mulberry32)

    private fun <E> shuffled(list: List<E>, seed: Long, salt: Int): List<E> {
        var x = ((seed xor (salt.toLong() * 2654435761L)) and 0xFFFFFFFFL).toInt()
        fun rnd(): Double {
            x += 0x6D2B79F5
            var t = x
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
        val a = ArrayList(list)
        for (i in a.size - 1 downTo 1) {
            val j = (rnd() * (i + 1)).toInt()
            val tmp = a[i]; a[i] = a[j]; a[j] = tmp
        }
        return a
    }
}
