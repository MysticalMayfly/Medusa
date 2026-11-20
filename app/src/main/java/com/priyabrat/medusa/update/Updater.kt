package com.priyabrat.medusa.update

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Self-updater. Reads latest.json from GitHub, downloads the APK
 * and installs it with PackageInstaller. The APK must be signed with the same key.
 */
class Updater(private val activity: Activity, private val listener: Listener) {

    companion object {
        private const val TAG = "MedusaUpdater"

        /** latest.json on the repo's "updates" branch (each publish overwrites it) */
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/latest.json"

        fun installedVersionCode(ctx: Context): Long =
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

        fun installedVersionName(ctx: Context): String =
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    }

    interface Listener { fun onState(state: State) }

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val percent: Int) : State
        data object Installing : State
        data object NeedsPermission : State
        data object NoPermissionScreen : State
        data class Failed(val reason: String) : State
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var available: UpdateInfo? = null
    private var downloadedApk: File? = null
    private var waitingForPermission = false

    fun onButtonPressed() {
        val apk = downloadedApk
        val info = available
        when {
            apk != null && apk.exists() -> install(apk)
            info != null -> download(info)
            else -> check()
        }
    }

    fun onResume() {
        if (waitingForPermission && activity.packageManager.canRequestPackageInstalls()) {
            waitingForPermission = false
            downloadedApk?.let { install(it) }
        }
    }

    fun release() = io.shutdownNow()

    fun check() {
        post(State.Checking)
        io.execute {
            try {
                val info = UpdateInfo.parse(httpGet("$MANIFEST_URL?t=${System.currentTimeMillis()}"))
                val installed = installedVersionCode(activity)
                Log.i(TAG, "installed=$installed latest=${info.versionCode}")
                if (info.versionCode > installed) {
                    available = info
                    post(State.Available(info))
                } else {
                    post(State.UpToDate)
                }
            } catch (e: Exception) {
                Log.e(TAG, "check failed", e)
                post(State.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun download(info: UpdateInfo) {
        post(State.Downloading(info, 0))
        io.execute {
            try {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "medusa-${info.versionCode}.apk")
                val conn = open(info.apkUrl)
                val total = conn.contentLengthLong
                val digest = MessageDigest.getInstance("SHA-256")
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; post(State.Downloading(info, pct)) }
                            }
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (info.sha256 != null && !info.sha256.equals(hash, ignoreCase = true)) {
                    out.delete()
                    throw IllegalStateException("checksum mismatch")
                }
                downloadedApk = out
                main.post { install(out) }
            } catch (e: Exception) {
                Log.e(TAG, "download failed", e)
                post(State.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun install(apk: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            waitingForPermission = true
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            try {
                activity.startActivity(intent)
                post(State.NeedsPermission)
            } catch (e: ActivityNotFoundException) {
                post(State.NoPermissionScreen) // some TVs hide this screen
            }
            return
        }
        post(State.Installing)
        io.execute {
            try {
                val installer = activity.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(activity.packageName)
                    // Skips the confirm prompt when Medusa installed itself last time
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("medusa.apk", 0, apk.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    val result = Intent(activity, InstallResultReceiver::class.java)
                    val pending = PendingIntent.getBroadcast(
                        activity, id, result,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    session.commit(pending.intentSender)
                }
            } catch (e: Exception) {
                Log.e(TAG, "install failed", e)
                post(State.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Medusa-Updater")
            setRequestProperty("Cache-Control", "no-cache")
            if (responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode")
        }

    private fun httpGet(url: String): String = open(url).inputStream.bufferedReader().use { it.readText() }

    private fun post(state: State) = main.post { listener.onState(state) }
}
