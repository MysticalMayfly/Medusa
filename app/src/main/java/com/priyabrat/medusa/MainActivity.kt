package com.priyabrat.medusa

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.priyabrat.medusa.update.Updater

class MainActivity : AppCompatActivity(), Updater.Listener {

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var updateButton: Button
    private lateinit var updater: Updater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        updateButton = findViewById(R.id.btn_update)
        updater = Updater(this, this)

        findViewById<TextView>(R.id.version).text =
            getString(R.string.version_fmt, Updater.installedVersionName(this), Updater.installedVersionCode(this))

        updateButton.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.06f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
        updateButton.setOnClickListener { updater.onButtonPressed() }
        updateButton.requestFocus()

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
        progress.visibility = View.GONE
        updateButton.isEnabled = true
        updateButton.text = getString(R.string.btn_check)
        status.text = when (state) {
            Updater.State.Idle -> getString(R.string.status_idle)
            Updater.State.Checking -> { updateButton.isEnabled = false; getString(R.string.status_checking) }
            Updater.State.UpToDate -> getString(R.string.status_latest)
            is Updater.State.Available -> {
                updateButton.text = getString(R.string.btn_install, state.info.versionName)
                getString(R.string.status_available, state.info.versionName)
            }
            is Updater.State.Downloading -> {
                updateButton.isEnabled = false
                progress.visibility = View.VISIBLE
                progress.progress = state.percent
                getString(R.string.status_downloading, state.info.versionName, state.percent)
            }
            Updater.State.Installing -> { updateButton.isEnabled = false; getString(R.string.status_installing) }
            Updater.State.NeedsPermission -> getString(R.string.status_need_permission)
            Updater.State.NoPermissionScreen -> getString(R.string.status_no_permission_screen)
            is Updater.State.Failed -> getString(R.string.status_error, state.reason)
        }
        if (updateButton.isEnabled && !updateButton.hasFocus()) updateButton.requestFocus()
    }
}
