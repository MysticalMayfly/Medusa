package com.priyabrat.medusa.data.library

import org.json.JSONArray
import org.json.JSONObject

/**
 * Who's watching. Each profile has a name, a picture (one of the built-in avatars) and its own Watched, Interested
 * and episode progress (a Changes file per profile). Kept on the TV for now; syncing through the Master and Supabase
 * plugs in here once Prince picks how the TV signs in.
 */
data class Profile(val id: String, val name: String, val avatar: Int, val photoUrl: String? = null)

data class Profiles(val list: List<Profile>, val active: String) {
    val current: Profile get() = list.firstOrNull { it.id == active } ?: list.first()

    fun toJson(): JSONObject = JSONObject()
        .put("active", active)
        .put("profiles", JSONArray().also { a ->
            list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar).put("photo_url", it.photoUrl ?: JSONObject.NULL)) }
        })

    companion object {
        val DEFAULT = Profiles(listOf(Profile("p1", "Me", 0)), "p1")

        fun fromJson(o: JSONObject): Profiles {
            val a = o.optJSONArray("profiles") ?: return DEFAULT
            val list = (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { Profile(it.optString("id"), it.optString("name"), it.optInt("avatar"), if (it.isNull("photo_url")) null else it.optString("photo_url").ifEmpty { null }) }
            }.filter { it.id.isNotEmpty() }
            return if (list.isEmpty()) DEFAULT else Profiles(list, o.optString("active").ifEmpty { list.first().id })
        }
    }
}
