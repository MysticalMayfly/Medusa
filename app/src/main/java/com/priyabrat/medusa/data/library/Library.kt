package com.priyabrat.medusa.data.library

import org.json.JSONArray
import org.json.JSONObject

/*
 * The library the TV imports from the Mac app (scripts/export_library.py writes it from medusa.db).
 * These are plain records, read once. They mirror the Mac's /api/bootstrap so the Mac's page logic ports 1:1.
 * Nothing here is shown directly: LibraryRepository turns it into the ready-to-show screen state.
 */

class Platform(val slug: String, val name: String, val colour: String?, val sort: Int)

class Service(val code: String, val name: String, val colour: String?, val mine: Boolean, val free: Boolean, val sort: Int)

class Tag(val kind: String, val name: String, val colour: String?, val count: Int)

class YearList(val id: Int, val name: String, val kind: String, val year: Int?, val genre: String?)

/** One offer from JustWatch India (titles.watch): a service, its kind and the link that opens it. */
class Offer(val code: String?, val pkg: String?, val name: String, val type: String, val url: String)

class Episode(val season: Int, val number: Int, val airdate: String?)

class Title(
    val id: Int,
    val title: String,
    val type: String?,            // "movie" or "show"
    val platform: String?,        // where it premiered (platform slug)
    val years: String?,
    val episodes: List<Int>,      // episodes per season
    val notes: String?,
    val watched: Boolean,
    val interested: Boolean,
    val placeholder: Boolean,
    val createdAt: String,
    val airingDate: String?,
    val airingNote: String?,
    val tmdbId: Int?,
    val tmdbType: String?,
    val imdbId: String?,
    val tvmazeId: Int?,
    val traktSlug: String?,
    val tmdbTitle: String?,
    val overview: String?,
    val tagline: String?,
    val runtime: Int?,
    val tmdbRating: Double?,
    val traktRating: Double?,
    val traktVotes: Int?,
    val imdbRating: Double?,
    val imdbVotes: Int?,
    val rtScore: Int?,
    val tmdbStatus: String?,
    val progSeason: Int?,
    val progEpisode: Int?,
    val progAt: String?,
    val trailerKey: String?,
    val trailerOk: Int?,
    val poster: String?,
    val backdrop: String?,
    val franchises: List<String>,
    val genres: List<String>,
    val studios: List<String>,
    val ott: List<String>,
    val score: Double?,           // the Mac's hidden sort score, never shown
    val avail: Boolean,           // false: nowhere to watch in India, so it lives on More
    val tmdbGenres: List<String>,
    val networks: List<String>,
    val providerNames: List<String>,   // TMDB flatrate + free + ads, for the details panel
    val providersLink: String?,
    val nextEp: Episode?,
    val prevEp: Episode?,
    val indiaStatus: String?,     // "streaming", "not_yet"
    val indiaSubs: List<String>,
    val indiaRent: List<String>,
    val indiaNote: String?,
    val watch: List<Offer>,
) {
    val seasons: Int get() = episodes.size
    val totalEpisodes: Int get() = episodes.sum()
}

class Library(
    val exportedAt: String,
    val platforms: List<Platform>,
    val services: List<Service>,
    val tags: List<Tag>,
    val lists: List<YearList>,
    val listItems: Map<Int, List<Int>>,   // list id -> title ids in the curated order
    val titles: List<Title>,
) {
    val byId: Map<Int, Title> = titles.associateBy { it.id }
    val serviceBy: Map<String, Service> = services.associateBy { it.code }
    val platformBy: Map<String, Platform> = platforms.associateBy { it.slug }

    companion object {
        val EMPTY = Library("", emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyList())

        fun parse(text: String): Library {
            val o = JSONObject(text)
            val items = HashMap<Int, MutableList<Pair<Int, Int>>>()
            o.optJSONArray("list_items")?.forEachArray { a -> items.getOrPut(a.getInt(0)) { ArrayList() }.add(a.getInt(1) to a.optInt(2)) }
            return Library(
                exportedAt = o.optString("exported_at"),
                platforms = o.optJSONArray("platforms").objects { Platform(it.getString("slug"), it.getString("name"), it.str("colour"), it.optInt("sort")) },
                services = o.optJSONArray("services").objects {
                    Service(it.getString("code"), it.getString("name"), it.str("colour"), it.optInt("mine") == 1, it.optInt("free") == 1, it.optInt("sort"))
                },
                tags = o.optJSONArray("tags").objects { Tag(it.getString("kind"), it.getString("name"), it.str("colour"), it.optInt("n")) },
                lists = o.optJSONArray("lists").objects { YearList(it.getInt("id"), it.getString("name"), it.optString("kind"), it.int("year"), it.str("genre")) },
                listItems = items.mapValues { (_, v) -> v.sortedBy { it.second }.map { it.first } },
                titles = o.optJSONArray("titles").objects(::title),
            )
        }

        /**
         * The central catalogue as the TV caches it: {"fetched_at", "services", "platforms", "titles"} where titles are
         * rows of the Supabase view app_titles (scripts/catalogue/schema.sql). Watched and Interested are not in it:
         * they live on the TV (changes.json). Horror year walls are built here from the horror films of each year.
         */
        fun fromCatalogue(text: String): Library {
            val o = JSONObject(text)
            val titles = o.optJSONArray("titles").objects(::catalogueTitle)
            val thisYear = java.time.LocalDate.now().year
            val lists = ArrayList<YearList>()
            val items = HashMap<Int, List<Int>>()
            for (y in thisYear downTo thisYear - 5) {
                val ids = titles.filter { it.years == y.toString() && "Horror" in it.genres }.sortedByDescending { it.score ?: -1.0 }.map { it.id }
                if (ids.isNotEmpty()) { lists.add(YearList(y, "Horror $y", "year", y, "Horror")); items[y] = ids }
            }
            val franchiseCounts = HashMap<String, Int>()
            titles.forEach { t -> t.franchises.forEach { franchiseCounts[it] = (franchiseCounts[it] ?: 0) + 1 } }
            val genreCounts = HashMap<String, Int>()
            titles.forEach { t -> t.genres.forEach { genreCounts[it] = (genreCounts[it] ?: 0) + 1 } }
            val tags = franchiseCounts.map { (n, c) -> Tag("franchise", n, null, c) }.sortedBy { it.name } +
                genreCounts.map { (n, c) -> Tag("genre", n, null, c) }.sortedBy { it.name }
            return Library(
                exportedAt = o.optString("fetched_at"),
                platforms = o.optJSONArray("platforms").objects { Platform(it.getString("slug"), it.getString("name"), it.str("colour"), it.optInt("sort")) }.sortedBy { it.sort },
                services = o.optJSONArray("services").objects {
                    Service(it.getString("code"), it.getString("name"), it.str("colour"), it.optBoolean("mine"), it.optBoolean("free"), it.optInt("sort"))
                }.sortedBy { it.sort },
                tags = tags,
                lists = lists,
                listItems = items,
                titles = titles,
            )
        }

        private fun catalogueTitle(j: JSONObject): Title {
            val horror = j.optBoolean("horror")
            val genres = j.optJSONArray("genres").strings().let { g -> if (horror && "Horror" !in g) listOf("Horror") + g else g }
            val status = j.str("india_status")
            val year = j.int("year")
            return Title(
                id = (j.opt("id") as Number).toInt(),
                title = j.optString("title"),
                type = j.str("kind"),
                platform = j.str("platform"),
                years = year?.toString(),
                episodes = j.optJSONArray("seasons").ints(),
                notes = null,
                watched = false,
                interested = false,
                placeholder = false,
                createdAt = j.optString("published_at"),
                airingDate = null,
                airingNote = null,
                tmdbId = j.int("tmdb_id"),
                tmdbType = if (j.str("kind") == "show") "tv" else "movie",
                imdbId = j.str("imdb_id"),
                tvmazeId = null,
                traktSlug = null,
                tmdbTitle = j.str("original_title"),
                overview = j.str("overview"),
                tagline = j.str("tagline"),
                runtime = j.int("runtime"),
                tmdbRating = j.dbl("tmdb_rating"),
                traktRating = null,
                traktVotes = null,
                imdbRating = j.dbl("imdb_rating"),
                imdbVotes = j.int("imdb_votes"),
                rtScore = j.int("rt_score"),
                tmdbStatus = j.str("tmdb_status"),
                progSeason = null,
                progEpisode = null,
                progAt = null,
                trailerKey = j.str("trailer_key"),
                trailerOk = null,
                poster = j.str("poster"),
                backdrop = j.str("backdrop"),
                franchises = listOfNotNull(j.str("franchise")),
                genres = genres,
                studios = emptyList(),
                ott = j.optJSONArray("services").strings(),
                score = j.dbl("score"),
                avail = j.optBoolean("avail"),
                tmdbGenres = j.optJSONArray("genres").strings(),
                networks = j.optJSONArray("networks").strings(),
                providerNames = j.optJSONArray("service_names").strings(),
                providersLink = j.str("watch_page"),
                nextEp = j.optJSONObject("next_ep")?.let(::episode),
                prevEp = j.optJSONObject("prev_ep")?.let(::episode),
                indiaStatus = when (status) { "streaming" -> "streaming"; "not_yet" -> "not_yet"; else -> status },
                indiaSubs = j.optJSONArray("services").strings(),
                indiaRent = j.optJSONArray("rent_buy").strings(),
                indiaNote = null,
                watch = j.optJSONArray("watch").objects {
                    Offer(it.str("code"), it.str("pkg"), it.optString("name"), it.optString("type"), it.optString("url"))
                },
            )
        }

        private fun title(j: JSONObject): Title {
            val tags = j.optJSONObject("tags")
            val providers = j.optJSONObject("providers")
            val india = j.optJSONObject("india")
            return Title(
                id = j.getInt("id"),
                title = j.optString("title"),
                type = j.str("type"),
                platform = j.str("platform"),
                years = j.str("years"),
                episodes = j.optJSONArray("episodes").ints(),
                notes = j.str("notes"),
                watched = j.optBoolean("watched"),
                interested = j.optBoolean("interested"),
                placeholder = j.optBoolean("placeholder"),
                createdAt = j.optString("created_at"),
                airingDate = j.str("airing_date"),
                airingNote = j.str("airing_note"),
                tmdbId = j.int("tmdb_id"),
                tmdbType = j.str("tmdb_type"),
                imdbId = j.str("imdb_id"),
                tvmazeId = j.int("tvmaze_id"),
                traktSlug = j.str("trakt_slug"),
                tmdbTitle = j.str("tmdb_title"),
                overview = j.str("overview"),
                tagline = j.str("tagline"),
                runtime = j.int("runtime"),
                tmdbRating = j.dbl("tmdb_rating"),
                traktRating = j.dbl("trakt_rating"),
                traktVotes = j.int("trakt_votes"),
                imdbRating = j.dbl("imdb_rating"),
                imdbVotes = j.int("imdb_votes"),
                rtScore = j.int("rt_score"),
                tmdbStatus = j.str("tmdb_status"),
                progSeason = j.int("prog_season"),
                progEpisode = j.int("prog_episode"),
                progAt = j.str("prog_at"),
                trailerKey = j.str("trailer_key"),
                trailerOk = j.int("trailer_ok"),
                poster = j.str("poster"),
                backdrop = j.str("backdrop"),
                franchises = tags?.optJSONArray("franchise").strings(),
                genres = tags?.optJSONArray("genre").strings(),
                studios = tags?.optJSONArray("studio").strings(),
                ott = j.optJSONArray("ott").strings(),
                score = j.dbl("score"),
                avail = j.optBoolean("avail"),
                tmdbGenres = j.optJSONArray("tmdb_genres").strings(),
                networks = j.optJSONArray("networks").strings(),
                providerNames = listOf("flatrate", "free", "ads").flatMap { providers?.optJSONArray(it).strings() }.distinct(),
                providersLink = providers?.str("link"),
                nextEp = j.optJSONObject("next_ep")?.let(::episode),
                prevEp = j.optJSONObject("prev_ep")?.let(::episode),
                indiaStatus = india?.str("status"),
                indiaSubs = india?.optJSONArray("subs").strings(),
                indiaRent = india?.optJSONArray("rent").strings(),
                indiaNote = india?.str("note"),
                watch = j.optJSONArray("watch").objects {
                    Offer(it.str("code"), it.str("pkg"), it.optString("name"), it.optString("type"), it.optString("url"))
                },
            )
        }

        private fun episode(j: JSONObject) = Episode(j.optInt("season"), j.optInt("number"), j.str("airdate"))
    }
}

// ---- small org.json helpers (null-safe, "null" strings treated as missing)

private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }
private fun JSONObject.int(k: String): Int? = if (isNull(k) || !has(k)) null else (opt(k) as? Number)?.toInt()
private fun JSONObject.dbl(k: String): Double? = if (isNull(k) || !has(k)) null else (opt(k) as? Number)?.toDouble()

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) if (!isNull(i)) out.add(getString(i))
    return out
}

private fun JSONArray?.ints(): List<Int> {
    if (this == null) return emptyList()
    val out = ArrayList<Int>(length())
    for (i in 0 until length()) out.add(optInt(i))
    return out
}

private fun <T> JSONArray?.objects(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(f(it)) }
    return out
}

private fun JSONArray.forEachArray(f: (JSONArray) -> Unit) {
    for (i in 0 until length()) optJSONArray(i)?.let(f)
}
