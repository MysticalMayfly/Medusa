package com.priyabrat.medusa

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.priyabrat.medusa.data.FakeMedusaRepository
import com.priyabrat.medusa.data.MedusaRepository
import com.priyabrat.medusa.ui.model.SettingsUiState
import com.priyabrat.medusa.ui.shell.MedusaApp
import com.priyabrat.medusa.ui.theme.MedusaTheme
import com.priyabrat.medusa.update.Updater

/**
 * Hosts the Compose UI and owns the self-updater wiring (docs/RULES.md rule 11).
 * The "Check for updates" button now sits on the Settings screen; it calls [Updater.onButtonPressed] here.
 */
class MainActivity : AppCompatActivity(), Updater.Listener {

    private lateinit var updater: Updater

    /** Stage 1: placeholder data. Stage 2 swaps in the real repository here; screens stay the same. */
    private val repository: MedusaRepository = FakeMedusaRepository()

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
        )

        setContent {
            MedusaTheme {
                MedusaApp(repository, settings, onUpdateButton = { updater.onButtonPressed() })
            }
        }

        // Quiet check on launch; installing still needs a button press
        if (savedInstanceState == null) updater.check()
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
