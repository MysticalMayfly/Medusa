package com.priyabrat.medusa.ui.model

/*
 * Screen state. Plain values, ready to show: screens never count, sum or sort.
 * Every field here has a row in docs/SLOTS.md (where stage 2 says which database value fills it).
 * Colours are ARGB longs (e.g. 0xFFFF5D73) so this package stays free of Android/Compose types.
 */

enum class Tab { Home, Movies, Shows, Horror, More, Universes }

enum class RatingKind { Imdb, RtFresh, RtRotten, Star }

data class Rating(val kind: RatingKind, val value: String)

/** One poster on a shelf or wall. */
data class TitleCard(
    val id: String,
    val title: String,
    val subtitle: String,                 // "2025 · 85%" or "S3 airing · in 4d"
    val interested: Boolean = false,
    val watched: Boolean = false,
    val badge: String? = null,            // small label top-left of the poster
    val progress: Float? = null,          // 0..1 bar along the poster's bottom edge
    val dotArgb: Long? = null,            // coloured dot before the subtitle (Horror: streaming service)
    val posterUrl: String? = null,        // TMDB poster; null shows the title on a plain card
)

/** One entry in the Watch now chooser. */
data class WatchOption(
    val id: String,
    val service: String,                  // "Netflix"
    val monogram: String,                 // "NF", shown in place of a logo
    val kind: String,                     // "Streaming", "Rent ₹79"
    val url: String? = null,              // opens the service's app (JustWatch watch link)
)

/** The carousel pick (Home, Movies, TV shows, Horror). */
data class HeroPick(
    val id: String,
    val eyebrow: String,                  // "Tonight's pick · Horror"
    val title: String,
    val year: String,
    val runtime: String,                  // "94 min"
    val ratings: List<Rating>,
    val overview: String,
    val watchLabel: String,               // "Watch on Prime Video"
    val watchOptions: List<WatchOption>,
    val interested: Boolean,
    val seen: Boolean,
    val trailerId: String? = null,        // stage 2 / trailer milestone: YouTube id, plays behind the pick
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
)

data class ContinueItem(
    val id: String,
    val title: String,
    val progress: Float,                  // 0..1
    val progressLabel: String,            // "S1 E2 done · next S1 E3"
    val nextLabel: String,                // "Watched S1 E3"
    val backdropUrl: String? = null,
)

data class Shelf(
    val id: String,
    val title: String,
    val count: Int? = null,               // shown small after the title
    val seeAllLabel: String? = null,      // "See all" or "See all 178"
    val dotArgb: Long? = null,
    val items: List<TitleCard>,
)

data class PlatformProgress(
    val id: String,
    val name: String,
    val monogram: String,
    val watched: Int,
    val total: Int,
    val percentLabel: String,             // "13%"
    val fraction: Float,                  // 0..1
    val argb: Long,
)

data class ProgressSummary(
    val eyebrow: String,                  // "Your watch progress"
    val watchedCount: Int,
    val totalLabel: String,               // "of 1635 titles"
    val breakdown: String,                // "22 of 153 shows · 137 of 1482 movies · 1 show in progress."
    val percent: Int,
    val platforms: List<PlatformProgress>,
)

data class HomeUiState(
    val picks: List<HeroPick> = emptyList(),
    val continueTitle: String = "",
    val continueWatching: List<ContinueItem> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val progress: ProgressSummary? = null,
)

/** A filter button: platform tab, segment, chip. */
data class FilterOption(
    val id: String,
    val label: String,
    val count: Int? = null,
    val selected: Boolean = false,
    val dotArgb: Long? = null,
    val monogram: String? = null,
)

/** A dropdown (franchise, genre, studio, sort). */
data class FilterSelect(
    val id: String,
    val label: String,                    // current choice, e.g. "Any franchise"
    val active: Boolean,                  // something other than the default is chosen
    val options: List<FilterOption>,
)

enum class BrowseKind { Movies, Shows, More }

/** Movies, TV shows and More share one layout. */
data class BrowseUiState(
    val kind: BrowseKind,
    val pick: HeroPick? = null,
    val picks: List<HeroPick> = emptyList(),  // the page's whole carousel round (pick is the first)
    val title: String = "",
    val summary: String = "",             // "130 of 986 watched"
    val platformTabs: List<FilterOption> = emptyList(),
    val typeSegments: List<FilterOption> = emptyList(),   // More: Everything / Shows / Movies
    val selects: List<FilterSelect> = emptyList(),
    val resultCount: String = "",         // "986 titles"
    val sort: FilterSelect? = null,
    val unwatchedOnly: Boolean = false,
    val items: List<TitleCard> = emptyList(),
)

/** Horror wall (a year, or All horror). */
data class HorrorWall(
    val title: String,                    // "2025 horror"
    val summary: String,                  // "17 of 142 seen · 106 streaming in India"
    val seenSegments: List<FilterOption>, // Not seen / Seen / Everything
    val sort: FilterSelect,
    val whereChips: List<FilterOption>,   // Anywhere / Free with ads / services / Rent only
    val items: List<TitleCard>,
)

data class HorrorUiState(
    val pick: HeroPick? = null,
    val picks: List<HeroPick> = emptyList(),
    val tabs: List<FilterOption> = emptyList(),    // Browse, 2026, 2025, 2024, All horror
    val shelves: List<Shelf> = emptyList(),        // shown on Browse
    val wall: HorrorWall? = null,                  // shown on a year or All horror
)

data class UniverseTile(
    val id: String,
    val name: String,
    val seenLabel: String,                // "2 of 3 seen · 1 coming"
    val progress: Float,                  // 0..1
    val upNext: String,                   // "Up next: Sadako DX"
    val tintArgb: Long,
    val posterUrls: List<String> = emptyList(),   // the fan of up to 5 posters on the tile
)

data class UniverseGroup(
    val title: String,                    // "Keep going"
    val count: Int,
    val note: String,                     // "You've started these. The next film is waiting."
    val tiles: List<UniverseTile>,
)

data class UniversesUiState(
    val eyebrow: String = "",
    val headline: String = "",            // "56 universes, 56 of 272 seen"
    val intro: String = "",
    val groups: List<UniverseGroup> = emptyList(),
)

data class UniverseEntry(
    val id: String,
    val title: String,
    val meta: String,                     // "2002 · 1h 55m · 72%"
    val seen: Boolean,
    val upNext: Boolean,
    val services: List<ServiceTag>,
    val posterUrl: String? = null,
)

data class UpNext(val id: String, val title: String, val meta: String)

data class UniverseUiState(
    val id: String = "",
    val name: String = "",
    val stat: String = "",                // "2 of 3 seen · about 1h 40m of films to go"
    val progress: Float = 0f,
    val tintArgb: Long = 0xFFFF5D73,
    val upNext: UpNext? = null,
    val segments: List<FilterOption> = emptyList(),   // Everything / Movies / Shows
    val hideWatched: Boolean = false,
    val entries: List<UniverseEntry> = emptyList(),
    val backdropUrl: String? = null,
)

data class ServiceTag(val name: String, val argb: Long)

data class Fact(val label: String, val value: String)

/** A link in the details panel: "IMDb" and its web address. */
data class Link(val label: String, val url: String)

/** The details panel. */
data class TitleDetail(
    val id: String,
    val kindLine: String,                 // "Movie · 2002 · 115 min"
    val title: String,
    val tagline: String?,
    val overview: String,
    val ratings: List<Rating>,
    val facts: List<Fact>,
    val streaming: List<ServiceTag>,      // where it streams in India
    val rentPrices: List<Fact>,           // service -> price
    val links: List<Link>,                // "IMDb", "Where to watch (IN)"
    val watched: Boolean,
    val interested: Boolean,
    val hasTrailer: Boolean,
    val trailerUrl: String? = null,       // YouTube page, opened in the YouTube app
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val resultLabel: String = "",
    val results: List<TitleCard> = emptyList(),
)

/** A profile: name and one of the built-in avatars (0 until AVATAR_COUNT). */
data class ProfileTile(val id: String, val name: String, val avatar: Int, val active: Boolean, val photoUrl: String? = null)

const val AVATAR_COUNT = 8

/** Who's watching: every profile, and the one in use (its avatar sits in the header). */
data class ProfilesUiState(
    val profiles: List<ProfileTile> = emptyList(),
    val current: ProfileTile? = null,
    val ready: Boolean = false,
)

/** One profile's page: its Interested and Watched lists. */
data class ProfilePageState(
    val id: String = "",
    val name: String = "",
    val avatar: Int = 0,
    val photoUrl: String? = null,         // the Google account's picture, once signed in
    val google: String? = null,           // "Signed in as name@gmail.com", or null
    val active: Boolean = false,
    val stat: String = "",                // "18 watched · 6 interested · 2 shows in progress"
    val interested: List<TitleCard> = emptyList(),
    val watched: List<TitleCard> = emptyList(),
)

/** Settings: only the updater. Filled by MainActivity from Updater.State. */
data class SettingsUiState(
    val version: String,
    val status: String,
    val buttonLabel: String,
    val buttonEnabled: Boolean,
    val progressPercent: Int?,            // null hides the bar
    val updateWaiting: Boolean,           // shows a dot on the gear
    val libraryStatus: String = "",       // "1638 titles, copied from the Mac on 26 Nov 2026"
    val importLabel: String = "",         // "Import library from Mac"
    val importEnabled: Boolean = true,
)
