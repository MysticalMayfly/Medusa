package com.priyabrat.medusa.data.library

import com.priyabrat.medusa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Reads the central catalogue from Supabase: the maintained view app_titles plus the services and platforms tables
 * (scripts/catalogue/schema.sql), with the publishable key, which can only read. The result is one JSON text that
 * LibraryStore caches on the TV, so the app opens instantly and works offline; it refreshes in the background.
 * The project URL and key come from supabase.properties at build time (git-ignored).
 */
object CatalogueSource {
    val configured: Boolean get() = BuildConfig.SUPABASE_URL.isNotEmpty() && BuildConfig.SUPABASE_KEY.isNotEmpty()

    /** The whole catalogue as the cache text, or an exception. */
    suspend fun fetch(): String = withContext(Dispatchers.IO) {
        check(configured) { "no Supabase project set" }
        val titles = JSONArray()
        var offset = 0
        while (true) {
            // PostgREST returns at most 1000 rows per request, so read in pages.
            val page = JSONArray(get("app_titles?select=*&order=id&limit=$PAGE&offset=$offset"))
            for (i in 0 until page.length()) titles.put(page.get(i))
            if (page.length() < PAGE) break
            offset += PAGE
        }
        JSONObject()
            .put("fetched_at", Instant.now().toString())
            .put("services", JSONArray(get("services?select=*")))
            .put("platforms", JSONArray(get("platforms?select=*")))
            .put("titles", titles)
            .toString()
    }

    private fun get(path: String): String {
        val c = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$path").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            c.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            c.setRequestProperty("Accept", "application/json")
            check(c.responseCode == 200) { "Supabase answered ${c.responseCode}" }
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private const val PAGE = 1000
}
