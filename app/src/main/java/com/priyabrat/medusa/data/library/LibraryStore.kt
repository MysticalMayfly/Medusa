package com.priyabrat.medusa.data.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Where the TV keeps its library: the imported copy (library.json) and what you changed on the TV since
 * (changes.json: watched, Interested, episode progress, and which carousel picks were shown lately).
 * Both live in the app's private storage. The TV never writes back to the Mac.
 */
class LibraryStore(context: Context) {
    private val dir = context.filesDir
    private val libraryFile = File(dir, "library.json")
    private val changesFile = File(dir, "changes.json")
    private val catalogueFile = File(dir, "catalogue.json")

    /** The cached central catalogue (Supabase view), or an empty library before the first download. */
    fun loadCatalogue(): Library = runCatching { if (catalogueFile.exists()) Library.fromCatalogue(catalogueFile.readText()) else null }.getOrNull() ?: Library.EMPTY

    /** Saves a freshly downloaded catalogue (checked by parsing it first), replacing the cache in one step. */
    fun saveCatalogue(text: String): Library {
        val lib = Library.fromCatalogue(text)
        val tmp = File(dir, "catalogue.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(catalogueFile)) { catalogueFile.delete(); tmp.renameTo(catalogueFile) }
        return lib
    }

    fun loadLibrary(): Library = runCatching { if (libraryFile.exists()) Library.parse(libraryFile.readText()) else null }.getOrNull() ?: Library.EMPTY

    /** Saves a newly imported library (checked by parsing it first), replacing the old copy in one step. */
    fun saveLibrary(text: String): Library {
        val lib = Library.parse(text)
        val tmp = File(dir, "library.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(libraryFile)) { libraryFile.delete(); tmp.renameTo(libraryFile) }
        return lib
    }

    private val profilesFile = File(dir, "profiles.json")

    fun loadProfiles(): Profiles = runCatching { if (profilesFile.exists()) Profiles.fromJson(JSONObject(profilesFile.readText())) else null }.getOrNull() ?: Profiles.DEFAULT

    fun saveProfiles(p: Profiles) = writeAtomic(profilesFile, p.toJson().toString())

    /** One file per profile. The first profile also takes over the single changes.json from before profiles. */
    private fun changesFile(profile: String) = File(dir, "changes-$profile.json").let { f ->
        if (!f.exists() && profile == "p1" && changesFile.exists()) changesFile else f
    }

    fun loadChanges(profile: String): Changes = runCatching {
        val f = changesFile(profile)
        if (f.exists()) Changes.fromJson(JSONObject(f.readText())) else null
    }.getOrNull() ?: Changes()

    fun saveChanges(profile: String, c: Changes) = writeAtomic(File(dir, "changes-$profile.json"), c.toJson().toString())

    fun deleteChanges(profile: String) { File(dir, "changes-$profile.json").delete() }

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}

/** What you changed on the TV. Each map overrides the imported value for that title. */
data class Changes(
    val watched: Map<Int, Boolean> = emptyMap(),
    val watchedAt: Map<Int, String> = emptyMap(),
    val interested: Map<Int, Boolean> = emptyMap(),
    val progress: Map<Int, Progress> = emptyMap(),
    /** Carousel picks shown lately, newest first (the Mac's tn-shown), so the same few don't keep coming back. */
    val shown: List<Int> = emptyList(),
) {
    data class Progress(val season: Int?, val episode: Int?, val at: String?)

    fun toJson(): JSONObject = JSONObject()
        .put("watched", JSONObject().also { o -> watched.forEach { (k, v) -> o.put(k.toString(), v) } })
        .put("watched_at", JSONObject().also { o -> watchedAt.forEach { (k, v) -> o.put(k.toString(), v) } })
        .put("interested", JSONObject().also { o -> interested.forEach { (k, v) -> o.put(k.toString(), v) } })
        .put("progress", JSONObject().also { o ->
            progress.forEach { (k, p) -> o.put(k.toString(), JSONArray().put(p.season ?: 0).put(p.episode ?: 0).put(p.at ?: "")) }
        })
        .put("shown", JSONArray().also { a -> shown.forEach { a.put(it) } })

    companion object {
        fun fromJson(o: JSONObject): Changes {
            fun bools(k: String): Map<Int, Boolean> {
                val j = o.optJSONObject(k) ?: return emptyMap()
                val out = HashMap<Int, Boolean>()
                j.keys().forEach { key -> key.toIntOrNull()?.let { out[it] = j.optBoolean(key) } }
                return out
            }
            val at = HashMap<Int, String>()
            o.optJSONObject("watched_at")?.let { j -> j.keys().forEach { key -> key.toIntOrNull()?.let { at[it] = j.optString(key) } } }
            val prog = HashMap<Int, Progress>()
            o.optJSONObject("progress")?.let { j ->
                j.keys().forEach { key ->
                    val a = j.optJSONArray(key) ?: return@forEach
                    key.toIntOrNull()?.let { prog[it] = Progress(a.optInt(0).takeIf { s -> s > 0 }, a.optInt(1).takeIf { e -> e > 0 }, a.optString(2).ifEmpty { null }) }
                }
            }
            val shown = ArrayList<Int>()
            o.optJSONArray("shown")?.let { a -> for (i in 0 until a.length()) shown.add(a.optInt(i)) }
            return Changes(bools("watched"), at, bools("interested"), prog, shown)
        }
    }
}
