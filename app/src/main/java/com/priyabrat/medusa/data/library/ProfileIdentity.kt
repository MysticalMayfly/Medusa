package com.priyabrat.medusa.data.library

/** Canonical cloud identifier; legacy local identifiers may remain as cache aliases. */
object ProfileIdentity {
    private val userId = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    fun forUser(id: String?): String? = id?.takeIf { userId.matches(it) }?.let { "g" + it.replace("-", "").lowercase() }
    fun linked(local: String, user: String?, remote: String?): String? {
        val canonical = forUser(user) ?: return null
        return remote?.takeIf { it == local || it == canonical }
    }
}
