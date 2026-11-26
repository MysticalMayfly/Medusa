package com.priyabrat.medusa.data.library

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.priyabrat.medusa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Sign in with Google for a profile: the TV's Google account picker (Credential Manager) gives an ID token, which
 * Supabase Auth exchanges for a session. The profile then takes the account's name and picture.
 * Google Cloud project "Medusa TV"; Supabase checks the token against the web client id.
 */
class GoogleAccount(private val context: Context) {

    class Result(val name: String, val photoUrl: String?, val email: String?, val userId: String, val accessToken: String, val refreshToken: String)

    val configured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotEmpty() && CatalogueSource.configured

    /** Shows the account picker; throws with a short reason when it is cancelled or fails. */
    suspend fun signIn(activity: android.app.Activity): Result {
        check(configured) { "Google sign-in is not set up in this build" }
        val rawNonce = UUID.randomUUID().toString()
        val hashed = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray()).joinToString("") { "%02x".format(it) }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setNonce(hashed)
            .build()
        val response = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val cred = response.credential
        check(cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "no Google account was picked" }
        val google = GoogleIdTokenCredential.createFrom(cred.data)
        val session = withContext(Dispatchers.IO) { exchange(google.idToken, rawNonce) }
        val user = session.getJSONObject("user")
        return Result(
            name = google.displayName ?: google.givenName ?: user.optString("email").substringBefore('@'),
            photoUrl = google.profilePictureUri?.toString(),
            email = user.optString("email").ifEmpty { null },
            userId = user.getString("id"),
            accessToken = session.getString("access_token"),
            refreshToken = session.getString("refresh_token"),
        )
    }

    /** Exchange the Google device-flow ID token without changing nonce settings for other clients. */
    suspend fun fromDeviceToken(idToken: String, accessToken: String?): Result = withContext(Dispatchers.IO) {
        val session = exchange(idToken, null, accessToken)
        val user = session.getJSONObject("user")
        val metadata = user.optJSONObject("user_metadata") ?: JSONObject()
        Result(
            name = metadata.optString("full_name").ifEmpty { metadata.optString("name").ifEmpty { user.optString("email").substringBefore('@') } },
            photoUrl = metadata.optString("avatar_url").ifEmpty { metadata.optString("picture") }.ifEmpty { null },
            email = user.optString("email").ifEmpty { null }, userId = user.getString("id"),
            accessToken = session.getString("access_token"), refreshToken = session.getString("refresh_token"),
        )
    }

    /** Resolve this authenticated user to the same profile on every device. */
    suspend fun resolveProfile(r: Result, existingProfile: String? = null): String = withContext(Dispatchers.IO) {
        fun request(method: String, path: String, body: String? = null): Pair<Int, String> {
            val c = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$path").openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = 10_000
                c.readTimeout = 20_000
                c.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                c.setRequestProperty("Authorization", "Bearer ${r.accessToken}")
                c.setRequestProperty("Content-Type", "application/json")
                if (body != null) {
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toByteArray()) }
                }
                val status = c.responseCode
                val stream = if (status in 200..299) c.inputStream else c.errorStream
                return status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally { c.disconnect() }
        }
        fun lookup(): String? {
            val (status, body) = request("GET", "account_profile_links?select=profile_id&user_id=eq.${r.userId}")
            check(status in 200..299) { "Could not load the account's profile ($status)" }
            val rows = JSONArray(body)
            return if (rows.length() == 0) null else rows.getJSONObject(0).getString("profile_id")
        }
        lookup()?.let {
            check(existingProfile == null || existingProfile == it) { "This Google account is linked to a different profile" }
            return@withContext it
        }
        check(existingProfile == null) { "This existing profile needs to be linked before it can use this Google account" }
        val id = "g" + r.userId.replace("-", "")
        val (status, _) = request("POST", "account_profile_links", JSONObject().put("user_id", r.userId).put("profile_id", id).toString())
        if (status == 409) return@withContext checkNotNull(lookup()) { "Could not recover the account's profile" }
        check(status in 200..299) { "Could not create the account's profile ($status)" }
        id
    }

    /** Supabase Auth: Google ID token -> session (POST /auth/v1/token?grant_type=id_token). */
    private fun exchange(idToken: String, nonce: String?, googleAccessToken: String? = null): JSONObject {
        val c = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=id_token").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            c.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject().put("provider", "google").put("id_token", idToken)
            if (nonce != null) payload.put("nonce", nonce)
            if (googleAccessToken != null) payload.put("access_token", googleAccessToken)
            c.outputStream.use { it.write(payload.toString().toByteArray()) }
            val ok = c.responseCode in 200..299
            val body = (if (ok) c.inputStream else c.errorStream).bufferedReader().readText()
            check(ok) { "Supabase refused the sign-in (${c.responseCode})" }
            return JSONObject(body)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /** The session is kept per profile in the app's private preferences (never in the catalogue cache). */
        fun save(context: Context, profile: String, r: Result) {
            context.getSharedPreferences("google", Context.MODE_PRIVATE).edit()
                .putString("$profile.user", r.userId).putString("$profile.email", r.email)
                .putString("$profile.access", r.accessToken).putString("$profile.refresh", r.refreshToken)
                .apply()
        }

        fun email(context: Context, profile: String): String? = context.getSharedPreferences("google", Context.MODE_PRIVATE).getString("$profile.email", null)

        fun isSignedIn(context: Context, profile: String): Boolean = context.getSharedPreferences("google", Context.MODE_PRIVATE).contains("$profile.user")

        fun signOut(context: Context, profile: String) {
            val p = context.getSharedPreferences("google", Context.MODE_PRIVATE)
            p.edit().apply { listOf("user", "email", "access", "refresh").forEach { remove("$profile.$it") } }.apply()
        }
    }
}
