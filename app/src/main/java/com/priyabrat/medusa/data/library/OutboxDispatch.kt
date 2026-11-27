package com.priyabrat.medusa.data.library

/** Preserve failed and signed-out accounts' events; never send one user's events as another user. */
object OutboxDispatch {
    fun <T> dispatch(
        events: List<T>,
        profile: (T) -> String,
        tokenFor: (String) -> String?,
        send: (List<T>, String) -> Boolean,
    ): List<T> {
        val sent = mutableSetOf<String>()
        for ((id, group) in events.groupBy(profile)) {
            val token = tokenFor(id) ?: continue
            if (send(group, token)) sent.add(id)
        }
        return events.filter { profile(it) !in sent }
    }
}
