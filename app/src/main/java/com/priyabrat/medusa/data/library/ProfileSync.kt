package com.priyabrat.medusa.data.library

import com.priyabrat.medusa.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

/*
 * Profile sync with the central database. The TV never edits Supabase's profile tables: it appends what changed to
 * profile_events (insert only, the publishable key allows nothing else). The Mac upkeeper copies events into the
 * Master database and pushes profiles and profile_titles back out; the TV downloads those and merges them with its
 * own changes, newest change wins. The TV keeps everything in its cache, so it works offline and catches up later.
 */

/** One change on the TV: kind is profile, delete_profile, watched, interested or progress. */
class ProfileEvent(val profile: String, val kind: String, val titleId: Int?, val value: JSONObject, val createdAt: String) {
    fun toJson(): JSONObject = JSONObject().put("profile_id", profile).put("kind", kind)
        .put("title_id", titleId ?: JSONObject.NULL).put("value", value).put("created_at", createdAt)

    companion object {
        fun now(): String = Instant.now().toString()
        fun fromJson(o: JSONObject) = ProfileEvent(
            o.getString("profile_id"), o.getString("kind"), if (o.isNull("title_id")) null else o.optInt("title_id"),
            o.optJSONObject("value") ?: JSONObject(), o.optString("created_at"),
        )
    }
}

/** A profile_titles row: one profile's state for one title, and when the Master last changed it. */
class ServerRow(
    val titleId: Int, val watched: Boolean, val watchedAt: String?, val interested: Boolean,
    val season: Int?, val episode: Int?, val progAt: String?, val updatedAt: String,
)

class ServerProfiles(val profiles: List<Profile>, val rows: Map<String, List<ServerRow>>) {
    companion object {
        fun parse(text: String): ServerProfiles {
            val o = JSONObject(text)
            val ps = o.getJSONArray("profiles")
            val list = (0 until ps.length()).map { ps.getJSONObject(it) }.map {
                Profile(it.getString("id"), it.optString("name"), it.optInt("avatar"), if (it.isNull("photo_url")) null else it.optString("photo_url").ifEmpty { null })
            }
            val ts = o.getJSONArray("titles")
            val rows = HashMap<String, MutableList<ServerRow>>()
            for (i in 0 until ts.length()) {
                val r = ts.getJSONObject(i)
                fun s(k: String) = if (r.isNull(k)) null else r.optString(k)
                fun n(k: String) = if (r.isNull(k)) null else r.optInt(k)
                rows.getOrPut(r.getString("profile_id")) { ArrayList() }.add(
                    ServerRow(r.optLong("title_id").toInt(), r.optBoolean("watched"), s("watched_at"), r.optBoolean("interested"),
                        n("prog_season"), n("prog_episode"), s("prog_at"), r.optString("updated_at")),
                )
            }
            return ServerProfiles(list, rows)
        }
    }
}

object ProfileSync {
    fun instant(s: String?): Instant? = s?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.recoverCatching { Instant.parse(s) }.getOrNull() }

    /** Sends events; true when Supabase took them all. */
    fun send(events: List<ProfileEvent>): Boolean {
        if (events.isEmpty()) return true
        if (!CatalogueSource.configured) return false
        val body = JSONArray().also { a -> events.forEach { e -> a.put(e.toJson().put("device", "tv")) } }.toString()
        val c = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/profile_events").openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            c.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Prefer", "return=minimal")
            c.outputStream.use { it.write(body.toByteArray()) }
            c.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            c.disconnect()
        }
    }

    /** Downloads every profile and its rows, as the cache text (ServerProfiles.parse reads it). */
    fun fetch(): String {
        val profiles = JSONArray(CatalogueSource.getPath("profiles?select=*"))
        val titles = JSONArray()
        var offset = 0
        while (true) {
            val page = JSONArray(CatalogueSource.getPath("profile_titles?select=*&order=profile_id,title_id&limit=1000&offset=$offset"))
            for (i in 0 until page.length()) titles.put(page.get(i))
            if (page.length() < 1000) break
            offset += 1000
        }
        return JSONObject().put("profiles", profiles).put("titles", titles).toString()
    }

    /** One profile's state: the Master's rows, with this TV's newer changes on top. Older TV changes are dropped. */
    fun merge(local: Changes, rows: List<ServerRow>): Changes {
        val watched = HashMap(local.watched)
        val watchedAt = HashMap(local.watchedAt)
        val interested = HashMap(local.interested)
        val progress = HashMap(local.progress)
        val stamps = HashMap(local.stamps)
        for (r in rows) {
            val mine = instant(stamps[r.titleId])
            val theirs = instant(r.updatedAt)
            if (mine != null && theirs != null && mine.isAfter(theirs)) continue
            stamps.remove(r.titleId)
            watched[r.titleId] = r.watched
            if (r.watchedAt != null) watchedAt[r.titleId] = r.watchedAt else watchedAt.remove(r.titleId)
            interested[r.titleId] = r.interested
            if (r.season != null) progress[r.titleId] = Changes.Progress(r.season, r.episode, r.progAt) else progress.remove(r.titleId)
        }
        return local.copy(watched = watched, watchedAt = watchedAt, interested = interested, progress = progress, stamps = stamps)
    }
}
