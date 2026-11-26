package com.priyabrat.medusa.data

import com.priyabrat.medusa.ui.model.BrowseKind
import com.priyabrat.medusa.ui.model.BrowseUiState
import com.priyabrat.medusa.ui.model.HomeUiState
import com.priyabrat.medusa.ui.model.HorrorUiState
import com.priyabrat.medusa.ui.model.ProfilePageState
import com.priyabrat.medusa.ui.model.ProfilesUiState
import com.priyabrat.medusa.ui.model.SearchUiState
import com.priyabrat.medusa.ui.model.TitleDetail
import com.priyabrat.medusa.ui.model.UniverseUiState
import com.priyabrat.medusa.ui.model.UniversesUiState
import kotlinx.coroutines.flow.Flow

/**
 * The only door between the screens and the data.
 *
 * Stage 1 ships [FakeMedusaRepository] (placeholder values, no database, no network).
 * Stage 2 adds the real one behind this same interface; no screen code changes.
 * Every value a screen shows comes ready to display: sorting, counting and shuffling happen here.
 */
interface MedusaRepository {
    /** Home. Shelf order is shuffled each time Home is opened. */
    fun home(): Flow<HomeUiState>
    fun browse(kind: BrowseKind): Flow<BrowseUiState>
    fun horror(): Flow<HorrorUiState>
    fun universes(): Flow<UniversesUiState>
    fun universe(id: String): Flow<UniverseUiState>
    fun detail(id: String): Flow<TitleDetail>
    fun search(query: String): Flow<SearchUiState>

    fun setInterested(id: String, on: Boolean)
    fun setWatched(id: String, on: Boolean)
    /** Continue watching: tick the next episode. */
    fun markEpisodeWatched(id: String)

    fun profiles(): Flow<ProfilesUiState>
    fun profilePage(id: String): Flow<ProfilePageState>
    /** Watched, Interested and progress follow the profile in use. */
    fun switchProfile(id: String)
    /** Adds a profile (id null) or changes one; returns its id. */
    fun saveProfile(id: String?, name: String, avatar: Int): String
    fun deleteProfile(id: String)

    /** Pick one option in a filter group, e.g. screen "movies", group "platform", option "p2". */
    fun selectFilter(screen: String, group: String, optionId: String)
    /** Flip an on/off filter, e.g. screen "movies", flag "unwatched". */
    fun toggleFilter(screen: String, flag: String)
}

/** Screen keys used with [MedusaRepository.selectFilter] and [MedusaRepository.toggleFilter]. */
object FilterKeys {
    const val HORROR = "horror"
    const val GROUP_PLATFORM = "platform"
    const val GROUP_SEGMENT = "segment"
    const val GROUP_SORT = "sort"
    const val GROUP_TAB = "tab"
    const val GROUP_WHERE = "where"
    /** "See all" on a shelf: the option id is the shelf id, the repository opens the matching wall. */
    const val GROUP_SEE_ALL = "seeAll"
    const val FLAG_UNWATCHED = "unwatched"
    const val FLAG_HIDE_WATCHED = "hideWatched"

    fun browse(kind: BrowseKind) = kind.name.lowercase()
    fun universe(id: String) = "universe:$id"
    fun select(id: String) = "select:$id"
}
