package com.priyabrat.medusa.data.library

import com.priyabrat.medusa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A device code remains on this TV; the confidential Google client secret stays on the server. */
class TvGoogleSignIn(private val google: GoogleAccount) {
    data class Challenge(val code: String, val url: String, val deviceCode: String, val interval: Long, val expiresAt: Long)

    private suspend fun call(body: JSONObject): Pair<Int, JSONObject> = withContext(Dispatchers.IO) {
        val c = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/tv-google-auth").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            c.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = c.responseCode
            val data = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
            status to runCatching { JSONObject(data ?: "{}") }.getOrDefault(JSONObject())
        } finally { c.disconnect() }
    }

    suspend fun start(): Challenge {
        val (status, body) = call(JSONObject().put("action", "start"))
        check(status in 200..299) { "Could not start sign-in. Please try again." }
        val url = body.getString("verification_url")
        check(url == "https://www.google.com/device" || url == "https://google.com/device") { "Invalid sign-in address" }
        return Challenge(body.getString("user_code"), url, body.getString("device_code"), body.optLong("interval", 5).coerceAtLeast(5),
            android.os.SystemClock.elapsedRealtime() + body.optLong("expires_in", 1800).coerceIn(1, 1800) * 1000)
    }

    suspend fun finish(challenge: Challenge): GoogleAccount.Result {
        var interval = challenge.interval
        while (android.os.SystemClock.elapsedRealtime() < challenge.expiresAt) {
            delay(interval * 1000)
            val (status, body) = call(JSONObject().put("action", "poll").put("device_code", challenge.deviceCode))
            if (status in 200..299) return google.fromDeviceToken(body.getString("id_token"), body.optString("access_token").ifEmpty { null })
            when (body.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "access_denied" -> error("Sign-in was declined.")
                "expired_token" -> error("This code expired. Please try again.")
                else -> error("Sign-in could not be completed. Please try again.")
            }
        }
        error("This code expired. Please try again.")
    }
}
