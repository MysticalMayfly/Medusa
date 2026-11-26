package com.priyabrat.medusa

import android.os.Bundle
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.priyabrat.medusa.data.LibraryRepository
import com.priyabrat.medusa.data.library.CatalogueSource
import com.priyabrat.medusa.data.library.LibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.priyabrat.medusa.ui.boot.BootScreen
import com.priyabrat.medusa.ui.model.SettingsUiState
import com.priyabrat.medusa.ui.shell.MedusaApp
import com.priyabrat.medusa.ui.theme.MedusaTheme
import com.priyabrat.medusa.update.Updater

/**
 * Hosts the Compose UI and owns the self-updater wiring (docs/RULES.md rule 11).
 * The "Check for updates" button now sits on the Settings screen; it calls [Updater.onButtonPressed] here.
 */
class MainActivity : AppCompatActivity(), Updater.Listener {

    private companion object { const val REFRESH_HOURS = 6L }

    private lateinit var updater: Updater

    /** Your library, imported once from the Mac (Settings > Import library from Mac); screens stay the same. */
    private val libraryStore by lazy { LibraryStore(applicationContext) }
    private val repository by lazy { LibraryRepository(libraryStore) }

    private var settings by mutableStateOf(
        SettingsUiState(version = "", status = "", buttonLabel = "", buttonEnabled = true, progressPercent = null, updateWaiting = false),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updater = Updater(this, this)
        settings = settings.copy(
            version = getString(R.string.version_fmt, Updater.installedVersionName(this), Updater.installedVersionCode(this)),
            status = getString(R.string.status_idle),
            buttonLabel = getString(R.string.btn_check),
            importLabel = getString(R.string.btn_import),
        )
        lifecycleScope.launch {
            val lib = withContext(Dispatchers.IO) { libraryStore.loadCatalogue() }
            settings = settings.copy(libraryStatus = libraryLine(lib.titles.size, lib.exportedAt))
            // The TV keeps its own cache; it refreshes from Supabase in the background when it is a few hours old.
            val age = runCatching { java.time.Duration.between(java.time.Instant.parse(lib.exportedAt), java.time.Instant.now()) }.getOrNull()
            if (savedInstanceState == null && (age == null || age.toHours() >= REFRESH_HOURS)) refreshCatalogue()
        }

        setContent {
            MedusaTheme {
                // The loading screen plays once per launch (not again after a configuration change).
                var booting by rememberSaveable { mutableStateOf(true) }
                var appShown by rememberSaveable { mutableStateOf(false) }
                androidx.compose.runtime.SideEffect { bootActive = booting }
                Box {
                    if (appShown) MedusaApp(repository, settings, onUpdateButton = { updater.onButtonPressed() }, onImportButton = ::refreshCatalogue)
                    if (booting) BootScreen(showApp = { appShown = true }, onDone = { booting = false })
                }
            }
        }

        // Quiet check on launch; installing still needs a button press
        if (savedInstanceState == null) updater.check()
    }

    private fun libraryLine(count: Int, fetchedAt: String): String {
        if (count == 0) return getString(R.string.import_none)
        val time = runCatching {
            java.time.Instant.parse(fetchedAt).atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK))
        }.getOrDefault(fetchedAt)
        return getString(R.string.import_done, count, time)
    }

    /** Downloads the central catalogue from Supabase into the TV's cache and swaps it in (Settings button, or on launch). */
    private fun refreshCatalogue() {
        if (!CatalogueSource.configured) return
        val before = settings.libraryStatus
        settings = settings.copy(importEnabled = false, libraryStatus = getString(R.string.import_looking))
        lifecycleScope.launch {
            val line = runCatching {
                val text = CatalogueSource.fetch()
                val lib = withContext(Dispatchers.IO) { libraryStore.saveCatalogue(text) }
                repository.replaceLibrary(lib)
                // Profiles too: send this TV's changes and fetch the Master's (a failure here leaves the cache as it is).
                runCatching { repository.syncProfiles() }
                libraryLine(lib.titles.size, lib.exportedAt)
            }.getOrElse { getString(R.string.import_failed, it.message ?: "no connection", before) }
            settings = settings.copy(importEnabled = true, libraryStatus = line)
        }
    }

    /** While the loading screen plays, the remote does nothing except Back (which leaves the app). */
    private var bootActive = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (bootActive && event.keyCode != KeyEvent.KEYCODE_BACK) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        updater.onResume() // continues an install after the user grants "unknown sources"
    }

    override fun onDestroy() {
        updater.release()
        super.onDestroy()
    }

    // ---- Updater.Listener ----

    override fun onState(state: Updater.State) {
        var label = getString(R.string.btn_check)
        var enabled = true
        var percent: Int? = null
        val status = when (state) {
            Updater.State.Idle -> getString(R.string.status_idle)
            Updater.State.Checking -> { enabled = false; getString(R.string.status_checking) }
            Updater.State.UpToDate -> getString(R.string.status_latest)
            is Updater.State.Available -> {
                label = getString(R.string.btn_install, state.info.versionName)
                getString(R.string.status_available, state.info.versionName)
            }
            is Updater.State.Downloading -> {
                enabled = false
                percent = state.percent
                getString(R.string.status_downloading, state.info.versionName, state.percent)
            }
            Updater.State.Installing -> { enabled = false; getString(R.string.status_installing) }
            Updater.State.NeedsPermission -> getString(R.string.status_need_permission)
            Updater.State.NoPermissionScreen -> getString(R.string.status_no_permission_screen)
            is Updater.State.Failed -> getString(R.string.status_error, state.reason)
        }
        settings = settings.copy(
            status = status,
            buttonLabel = label,
            buttonEnabled = enabled,
            progressPercent = percent,
            updateWaiting = state is Updater.State.Available || state is Updater.State.NeedsPermission,
        )
    }
}
