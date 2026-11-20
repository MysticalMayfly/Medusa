package com.priyabrat.medusa.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast

/** Gets the result of a PackageInstaller session started by [Updater]. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i("MedusaUpdater", "install status=$status message=$message")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android wants the user to confirm: show its install dialog
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // app is replaced and this process ends
            else -> Toast.makeText(context, "Update failed: ${message ?: status}", Toast.LENGTH_LONG).show()
        }
    }
}
