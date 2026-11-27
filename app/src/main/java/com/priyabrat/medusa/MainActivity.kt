package com.priyabrat.medusa

import android.os.Bundle
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.priyabrat.medusa.data.LibraryRepository
import com.priyabrat.medusa.data.library.CatalogueSource
import com.priyabrat.medusa.data.library.GoogleAccount
import com.priyabrat.medusa.data.library.TvGoogleSignIn
import com.priyabrat.medusa.ui.profiles.TvSignInDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.priyabrat.medusa.data.library.LibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    private companion object {
        const val REFRESH_HOURS = 6L
        /** The intro plays once per process: coming back to Medusa while it is still in memory skips it. */
        var introPlayed = false
    }

    private lateinit var updater: Updater
    private var signInVisible by mutableStateOf(false)
    private var signInChallenge by mutableStateOf<TvGoogleSignIn.Challenge?>(null)
    private var signInJob: Job? = null

    /** Your library, imported once from the Mac (Settings > Import library from Mac); screens stay the same. */
    private val libraryStore by lazy { LibraryStore(applicationContext) }
    private val repository by lazy { LibraryRepository(libraryStore).also { r ->
        r.googleSignedIn = { GoogleAccount.isSignedIn(applicationContext, it) }
        r.googleAccessToken = { GoogleAccount.accessToken(applicationContext, it) }
        r.googleCloudProfile = { GoogleAccount.cloudProfile(applicationContext, it) }
    } }

    private var settings by mutableStateOf(
        SettingsUiState(version = "", status = "", buttonLabel = "", buttonEnabled = true, progressPercent = null, updateWaiting = false),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Images: a smaller memory cache than Coil's default (the TV has little free memory), cached on disk instead.
        coil3.SingletonImageLoader.setSafe { ctx ->
            coil3.ImageLoader.Builder(ctx)
                .memoryCache { coil3.memory.MemoryCache.Builder().maxSizePercent(ctx, 0.12).build() }
                .build()
        }
        updater = Updater(this, this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val accounts = GoogleAccount(applicationContext)
                while (isActive) {
                    accounts.renewSessions()
                    delay(15_000)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    runCatching { repository.syncProfiles() }
                    delay(15_000)
                }
            }
        }
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
                var booting by rememberSaveable { mutableStateOf(!introPlayed) }
                var appShown by rememberSaveable { mutableStateOf(introPlayed) }
                androidx.compose.runtime.SideEffect { bootActive = booting }
                Box {
                    if (appShown) MedusaApp(
                        repository, settings,
                        onUpdateButton = { updater.onButtonPressed() },
                        onImportButton = ::refreshCatalogue,
                        onQuit = { introPlayed = false; finishAndRemoveTask() },
                        onGoogleSignIn = ::signInWithGoogle,
                        onGoogleSignOut = ::signOutOfGoogle,
                        onGuest = { done -> lifecycleScope.launch { repository.useGuestProfile(); done() } },
                    )
                    if (booting) BootScreen(showApp = { appShown = true }, onDone = { booting = false; introPlayed = true })
                    if (signInVisible) TvSignInDialog(signInChallenge) { signInJob?.cancel(); signInVisible = false }
                }
            }
        }

        // Quiet check on launch; installing still needs a button press
        if (savedInstanceState == null) updater.check()
    }

    private val google by lazy { GoogleAccount(this) }

    /** Add or restore a Google account, or link an already-migrated legacy profile. */
    private fun signInWithGoogle(profile: String?, onDone: () -> Unit) {
        if (signInJob?.isActive == true) return
        signInVisible = true
        signInChallenge = null
        signInJob = lifecycleScope.launch {
            val r = runCatching {
                val device = TvGoogleSignIn(google)
                val challenge = device.start()
                signInChallenge = challenge
                val account = device.finish(challenge)
                val linked = google.resolveProfile(account, profile)
                val id = GoogleAccount.localProfileForUser(applicationContext, account.userId) ?: linked
                GoogleAccount.save(applicationContext, id, account, linked)
                val synced = repository.useGoogleProfile(id, account.name, account.photoUrl)
                account.name to synced
            }
            r.onSuccess {
                val message = if (it.second) getString(R.string.google_signed_in, it.first)
                    else "Signed in, but profile history could not be refreshed yet."
                android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                onDone()
            }.onFailure {
                if (it !is CancellationException && it !is GetCredentialCancellationException) {
                    val reason = if (it is NoCredentialException) "Add a Google account in your TV settings and try again."
                        else it.message ?: it.javaClass.simpleName
                    android.widget.Toast.makeText(this@MainActivity, getString(R.string.google_failed, reason), android.widget.Toast.LENGTH_LONG).show()
                }
            }
            signInVisible = false
            signInChallenge = null
        }
    }

    private fun signOutOfGoogle(profile: String, onDone: () -> Unit) {
        lifecycleScope.launch {
            repository.removeLocalProfile(profile)
            GoogleAccount.signOut(applicationContext, profile)
            onDone()
        }
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
                label = getString(R.string.btn_install, state.info.versionCode.toString())
                getString(R.string.status_available, state.info.versionCode.toString())
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
