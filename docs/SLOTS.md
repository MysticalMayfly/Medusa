# Medusa: screen slots (stage 1 to stage 2 handover)

Every value the TV screens show, one row per slot, and every action. Stage 1 fills them from
`data/FakeMedusaRepository.kt` (placeholders only). Stage 2 fills the **Source** column and adds the real
repository behind `data/MedusaRepository.kt`; no screen code changes.

- Slot IDs are `screen.section.field`; `[]` marks a repeated item.
- **Must match** lists other slots that show the same fact. These are the numbers that disagree in the Mac app: one source each.
- State classes are in `ui/model/UiState.kt`. Values arrive ready to show: screens never count, sum, sort or shuffle.

## Stage 2 source (from b10)

Every slot is filled by `data/LibraryRepository.kt` from one cached copy of the central catalogue, so a fact that
shows on two screens comes from the same row. The catalogue is the Supabase view `app_titles` (plus `services` and
`platforms`), defined in `scripts/catalogue/schema.sql` and built on the Mac by `scripts/catalogue/build_catalogue.py`
from `~/Desktop/Films/medusa.fixed.csv` and `tv_shows.csv` plus TMDB. The TV caches it (`catalogue.json`) and
refreshes it every 6 hours or from Settings. Watched, Interested and episode progress are the TV's own
(`changes.json`), never in Supabase. The page logic is the Mac app's, ported: `app.js` (walls, filters, details,
progress), `tonight.js` (carousel playlist, Home shelves, Watch now), `horror-stream.js`, `universes.js`.
Horror year walls are the horror films of each of the last six years, best reviewed first.

| Added field | What it shows | Source |
|---|---|---|
| `TitleCard.posterUrl`, `HeroPick.posterUrl`, `TitleDetail.posterUrl`, `UniverseEntry.posterUrl` | Poster | `app_titles.poster` (TMDB w342) |
| `HeroPick.backdropUrl`, `ContinueItem.backdropUrl`, `TitleDetail.backdropUrl`, `UniverseUiState.backdropUrl` | Backdrop | `app_titles.backdrop` (TMDB w1280) |
| `UniverseTile.posterUrls` | Poster fan on a tile | up to 5 posters of the universe, up next first |
| `WatchOption.url` | Opens the service's app | `app_titles.watch[].url` (JustWatch) |
| `TitleDetail.links[]` (`Link`) | TMDB, IMDb, Where to watch | ids in `app_titles` |
| `TitleDetail.trailerUrl`, `HeroPick.trailerId` | Trailer | `app_titles.trailer_key` |
| `ProfilesUiState`, `ProfileTile.*`, `ProfilePageState.*` | Who's watching, header avatar, a profile's page and lists | `profiles.json` on the TV, merged with Supabase `profiles` / `profile_titles` (written only by the Mac upkeeper from the Master) |
| `settings.libraryStatus`, `.importLabel`, `.importEnabled` | Catalogue size, last update, Refresh button | `MainActivity` (catalogue cache) |

## Shared pieces

| Slot ID | What it shows | Example | Type | Must match | State field | Source |
|---|---|---|---|---|---|---|
| `card[].id` | Title id (opens the details panel) | t01 | id | `detail.id` | `TitleCard.id` | |
| `card[].title` | Poster title | Weapons | text | `detail.title` | `TitleCard.title` | |
| `card[].subtitle` | Line under the poster | 2025 · 85% | text | | `TitleCard.subtitle` | |
| `card[].interested` | Heart shown and filled | true | bool | `detail.interested`, `hero.interested` | `TitleCard.interested` | |
| `card[].watched` | Poster dimmed | false | bool | `detail.watched`, `hero.seen` | `TitleCard.watched` | |
| `card[].badge` | Label on the poster, top left | New | text? | | `TitleCard.badge` | |
| `card[].progress` | Bar along the poster's bottom | 0.4 | 0..1? | | `TitleCard.progress` | |
| `card[].dot` | Coloured dot before the subtitle | Netflix red | ARGB? | | `TitleCard.dotArgb` | |
| `shelf[].id` | Shelf id | best | id | | `Shelf.id` | |
| `shelf[].title` | Shelf heading | Best reviewed on your plans | text | | `Shelf.title` | |
| `shelf[].count` | Small count after the heading | 8 | int? | | `Shelf.count` | |
| `shelf[].see_all` | See all link text | See all 178 | text? | | `Shelf.seeAllLabel` | |
| `shelf[].dot` | Dot before the heading | | ARGB? | | `Shelf.dotArgb` | |
| `shelf[].items[]` | The posters | | card[] | | `Shelf.items` | |
| `filter[].id` / `.label` / `.count` / `.selected` / `.dot` / `.monogram` | Tab, chip or segment | Netflix 67 | | browse counts must match the wall | `FilterOption.*` | |
| `select[].id` / `.label` / `.active` / `.options[]` | Dropdown (franchise, genre, studio, sort) | Any franchise | | | `FilterSelect.*` | |

## Carousel (Home, Movies, TV shows, Horror)

| Slot ID | What it shows | Example | Type | Must match | State field | Source |
|---|---|---|---|---|---|---|
| `hero.id` | Pick id | t01 | id | `detail.id` | `HeroPick.id` | |
| `hero.eyebrow` | Small line above the title | Tonight's pick · Horror | text | | `HeroPick.eyebrow` | |
| `hero.title` | Title | Passenger | text | `detail.title` | `HeroPick.title` | |
| `hero.year` | Year | 2026 | text | `detail.kind_line` | `HeroPick.year` | |
| `hero.runtime_min` | Runtime | 94 min | text | `detail.kind_line` | `HeroPick.runtime` | |
| `hero.ratings[]` | IMDb, Tomatometer | 5.5, 44% | rating[] | `detail.ratings[]` | `HeroPick.ratings` | |
| `hero.overview` | Synopsis, 3 lines | | text | `detail.overview` | `HeroPick.overview` | |
| `hero.watch_label` | Watch button text | Watch on Prime Video | text | first of `hero.watch_options[]` | `HeroPick.watchLabel` | |
| `hero.watch_options[]` | Chooser: service, monogram, kind | Netflix · Your plan | list | `detail.streaming[]`, `detail.rent[]` | `HeroPick.watchOptions` (`WatchOption.*`) | |
| `hero.interested` | Heart on | true | bool | `card.interested` | `HeroPick.interested` | |
| `hero.seen` | Tick on | false | bool | `card.watched` | `HeroPick.seen` | |
| `hero.trailer_id` | YouTube id played behind the pick (milestone 6) | | text? | | `HeroPick.trailerId` | |

## Home

| Slot ID | What it shows | Example | Type | Must match | State field | Source |
|---|---|---|---|---|---|---|
| `home.picks[]` | Carousel picks, in order | | hero[] | | `HomeUiState.picks` | |
| `home.continue.title` | Heading | Continue watching | text | | `HomeUiState.continueTitle` | |
| `home.continue[].id` / `.title` | Show | Dark Matter | | | `ContinueItem.id`, `.title` | |
| `home.continue[].progress` | Bar | 0.2 | 0..1 | | `ContinueItem.progress` | |
| `home.continue[].progress_label` | Where you are | S1 E2 done · next S1 E3 | text | | `ContinueItem.progressLabel` | |
| `home.continue[].next_label` | Button | Watched S1 E3 | text | | `ContinueItem.nextLabel` | |
| `home.shelf[]` | Shelves, already shuffled | | shelf[] | | `HomeUiState.shelves` | |
| `home.progress.eyebrow` | Heading | Your watch progress | text | | `ProgressSummary.eyebrow` | |
| `home.progress.watched` | Watched count | 18 | int | `browse.summary` (movies + shows watched) | `ProgressSummary.watchedCount` | |
| `home.progress.total_label` | Total | of 1635 titles | text | `movies.summary` + `shows.summary` totals | `ProgressSummary.totalLabel` | |
| `home.progress.breakdown` | Shows, movies, in progress | 22 of 153 shows · 137 of 1482 movies | text | `movies.summary`, `shows.summary` | `ProgressSummary.breakdown` | |
| `home.progress.percent` | Ring | 1 | int 0..100 | | `ProgressSummary.percent` | |
| `home.progress.platform[]` | Tile and bar: name, monogram, watched, total, percent, fraction, colour | Netflix 11 / 89, 12% | list | `movies.platform_tabs[].count` | `PlatformProgress.*` | |

## Movies, TV shows, More (`BrowseUiState`)

| Slot ID | What it shows | Example | Type | Must match | State field | Source |
|---|---|---|---|---|---|---|
| `browse.kind` | Which page | Movies | enum | | `BrowseUiState.kind` | |
| `browse.pick` | Carousel (not on More) | | hero? | | `BrowseUiState.pick` | |
| `browse.title` | Page title | Movies | text | | `BrowseUiState.title` | |
| `browse.summary` | Watched count line | 130 of 986 watched | text | `home.progress.breakdown` | `BrowseUiState.summary` | |
| `browse.platform_tabs[]` | All + platforms with counts | Netflix 67 | filter[] | `home.progress.platform[]` | `BrowseUiState.platformTabs` | |
| `browse.type_segments[]` | More only: Everything / Shows / Movies | | filter[] | | `BrowseUiState.typeSegments` | |
| `browse.selects[]` | Franchise, genre, studio | Any franchise | select[] | | `BrowseUiState.selects` | |
| `browse.result_count` | Count of the wall | 986 titles | text | length of `browse.items[]` | `BrowseUiState.resultCount` | |
| `browse.sort` | Sort | By platform | select | | `BrowseUiState.sort` | |
| `browse.unwatched_only` | Chip on | false | bool | | `BrowseUiState.unwatchedOnly` | |
| `browse.items[]` | The wall, sorted | | card[] | | `BrowseUiState.items` | |

## Horror, Standard look (`HorrorUiState`)

| Slot ID | What it shows | Example | Type | Must match | State field | Source |
|---|---|---|---|---|---|---|
| `horror.pick` | Carousel | | hero? | | `HorrorUiState.pick` | |
| `horror.tabs[]` | Browse, years, All horror | 2025 | filter[] | | `HorrorUiState.tabs` | |
| `horror.shelf[]` | Browse shelves (On Netflix…, Rent or buy only) | | shelf[] | `horror.wall.where[]` counts | `HorrorUiState.shelves` | |
| `horror.wall.title` | Wall title | 2025 horror | text | | `HorrorWall.title` | |
| `horror.wall.summary` | Seen and ready counts | 17 of 142 seen · 106 ready | text | | `HorrorWall.summary` | |
| `horror.wall.seen[]` | Not seen / Seen / Everything | | filter[] | | `HorrorWall.seenSegments` | |
| `horror.wall.sort` | Sort | Best reviewed | select | | `HorrorWall.sort` | |
| `horror.wall.where[]` | Anywhere, plans, free, services, rent only | | filter[] | | `HorrorWall.whereChips` | |
| `horror.wall.items[]` | The wall | | card[] | | `HorrorWall.items` | |

## Universes

| Slot ID | What it shows | Example | Type | Must match | State field | Source |
|---|---|---|---|---|---|---|
| `universes.eyebrow` | Small heading | Universes | text | | `UniversesUiState.eyebrow` | |
| `universes.headline` | Count line | 56 universes, 56 of 272 seen | text | sum of tiles | `UniversesUiState.headline` | |
| `universes.intro` | Line under it | | text | | `UniversesUiState.intro` | |
| `universes.group[].title` / `.count` / `.note` | Group heading | Keep going 8 | | | `UniverseGroup.*` | |
| `universes.group[].tile[].id` / `.name` | Tile | Ring | | `universe.name` | `UniverseTile.id`, `.name` | |
| `universes.group[].tile[].seen_label` | Seen line | 2 of 3 seen · 1 coming | text | `universe.stat` | `UniverseTile.seenLabel` | |
| `universes.group[].tile[].progress` | Bar | 0.67 | 0..1 | `universe.progress` | `UniverseTile.progress` | |
| `universes.group[].tile[].up_next` | Up next line | Up next: Sadako DX | text | `universe.up_next.title` | `UniverseTile.upNext` | |
| `universes.group[].tile[].tint` | Tile colour | | ARGB | `universe.tint` | `UniverseTile.tintArgb` | |
| `universe.id` / `.name` | Page | Ring | | tile | `UniverseUiState.id`, `.name` | |
| `universe.stat` | Seen and time to go | 2 of 3 seen · about 1h 40m to go | text | tile seen label | `UniverseUiState.stat` | |
| `universe.progress` / `.tint` | Bar and colour | | | tile | `UniverseUiState.progress`, `.tintArgb` | |
| `universe.up_next` | Up next card: id, title, meta | Sadako DX · 2022 · 1h 40m | | the `up_next` entry | `UniverseUiState.upNext` (`UpNext.*`) | |
| `universe.segments[]` | Everything / Movies / Shows | | filter[] | | `UniverseUiState.segments` | |
| `universe.hide_watched` | Chip on | false | bool | | `UniverseUiState.hideWatched` | |
| `universe.entry[]` | Timeline row: id, title, meta, seen, up next, services | The Ring · 2002 · 1h 55m · 72% | list | `card.watched` for the same title | `UniverseEntry.*` | |

## Details panel (`TitleDetail`)

| Slot ID | What it shows | Example | Type | Must match | State field | Source |
|---|---|---|---|---|---|---|
| `detail.id` | Title id | | id | | `TitleDetail.id` | |
| `detail.kind_line` | Type · year · runtime | Movie · 2002 · 115 min | text | `hero.year`, `hero.runtime_min` | `TitleDetail.kindLine` | |
| `detail.title` | Title | The Ring | text | `card.title` | `TitleDetail.title` | |
| `detail.tagline` | Tagline | Before you die, you see. | text? | | `TitleDetail.tagline` | |
| `detail.overview` | Synopsis | | text | `hero.overview` | `TitleDetail.overview` | |
| `detail.ratings[]` | IMDb, Tomatometer | | rating[] | `hero.ratings[]` | `TitleDetail.ratings` | |
| `detail.facts[]` | Label and value rows | Genres · Horror, Mystery | list | | `TitleDetail.facts` | |
| `detail.streaming[]` | Where it streams in India, yours filled | JioHotstar · your plan | list | `hero.watch_options[]` | `TitleDetail.streaming` | |
| `detail.rent[]` | Rent and buy prices | Prime Video · Rent ₹79 | list | `hero.watch_options[]` | `TitleDetail.rentPrices` | |
| `detail.links[]` | Link names | IMDb | list | | `TitleDetail.links` | |
| `detail.watched` / `.interested` / `.has_trailer` | Button states | | bool | `card.*`, `hero.*` | `TitleDetail.watched`, `.interested`, `.hasTrailer` | |

## Search and Settings

| Slot ID | What it shows | Example | Type | Must match | State field | Source |
|---|---|---|---|---|---|---|
| `search.query` | Typed text | ring | text | | `SearchUiState.query` | |
| `search.result_label` | Result line | 12 results for "ring" | text | length of results | `SearchUiState.resultLabel` | |
| `search.results[]` | Posters | | card[] | | `SearchUiState.results` | |
| `settings.*` | Version, status, button, progress, gear dot | Version 1.3 · build 6 | | | `SettingsUiState.*` | The updater (`MainActivity`), not the database |

## Actions

| Action | Changes | Repository call |
|---|---|---|
| `card.open`, `hero.info`, `universe.details` | Opens the details panel | (none, UI only) then `detail(id)` |
| `hero.interested`, `detail.interested` | Interested on/off | `setInterested(id, on)` |
| `hero.seen_it`, `detail.watched`, `universe.entry.tick`, `universe.mark_watched` | Watched on/off | `setWatched(id, on)` |
| `home.continue.watched_next` | Ticks the next episode | `markEpisodeWatched(id)` |
| `hero.watch_now.pick` | Opens the service's app (stage 2 deep link; stage 1 shows "Not wired yet") | (none yet) |
| `detail.trailer`, `detail.link` | Trailer, external link (later; stage 1 shows "Not wired yet") | (none yet) |
| `browse.platform`, `browse.segment`, `browse.select`, `browse.sort` | Filters the wall | `selectFilter(screen, group, option)` |
| `browse.unwatched_only`, `universe.hide_watched` | Filter on/off | `toggleFilter(screen, flag)` |
| `horror.tab`, `horror.seen`, `horror.where`, `horror.sort`, `horror.see_all` | Horror tab and wall filters | `selectFilter("horror", group, option)` |
| `search.type` | New results | `search(query)` |
| `settings.check_for_updates` | Updater check, download, install | `Updater.onButtonPressed()` in `MainActivity` |
| Broken-trailer report | Stage 2 hook | (none yet) |
