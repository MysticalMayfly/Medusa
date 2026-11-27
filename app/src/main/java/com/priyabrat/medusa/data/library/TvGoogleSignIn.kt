package com.priyabrat.medusa.data.library

import com.priyabrat.medusa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The QR contains only a public session ID; the polling secret and PKCE verifier stay on this TV. */
class TvGoogleSignIn(private val google: GoogleAccount) {
    data class Challenge(val url: String, val secrets: PairingSecrets, val expiresAt: Long)
    private val endpoint get() = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/tv-pair"

    private suspend fun call(action: String, body: JSONObject): Pair<Int, JSONObject> = withContext(Dispatchers.IO) {
        val c = URL("$endpoint/$action").openConnection() as HttpURLConnection
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
        val secrets = PairingSecrets.create()
        val (status, _) = call("start", JSONObject().put("id", secrets.id).put("secret", secrets.secret)
            .put("challenge", PairingSecrets.challenge(secrets.verifier)))
        check(status == 201) { "Could not start sign-in. Please try again." }
        return Challenge("$endpoint/${secrets.id}", secrets, android.os.SystemClock.elapsedRealtime() + 300_000)
    }

    suspend fun finish(challenge: Challenge): GoogleAccount.Result {
        while (android.os.SystemClock.elapsedRealtime() < challenge.expiresAt) {
            delay(2500)
            val (status, body) = call("poll", JSONObject().put("id", challenge.secrets.id).put("secret", challenge.secrets.secret))
            when (status) {
                202 -> Unit
                200 -> return google.fromPairingCode(body.getString("code"), challenge.secrets.verifier)
                404, 410 -> error("This sign-in link expired. Please try again.")
                else -> error("Sign-in could not be completed. Please try again.")
            }
        }
        error("This sign-in link expired. Please try again.")
    }
}
