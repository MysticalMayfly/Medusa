package com.priyabrat.medusa.data.library

import java.util.Base64

/** Rotation is serialized by the caller; replace must atomically reject a changed or removed session. */
object SessionRenewal {
    data class Tokens(val userId: String, val access: String, val refresh: String)

    fun isDue(tokens: Tokens, nowSeconds: Long): Boolean {
        val expiry = runCatching {
            val encoded = tokens.access.split('.')[1]
            val payload = String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
            Regex("\\\"exp\\\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.get(1)?.toLong()
        }.getOrNull()
        return expiry == null || expiry <= nowSeconds + 300
    }

    fun renew(
        current: Tokens,
        nowSeconds: Long,
        exchange: (String) -> Tokens,
        replace: (Tokens, Tokens) -> Boolean,
    ): Boolean {
        if (!isDue(current, nowSeconds)) return false
        val updated = exchange(current.refresh)
        check(updated.userId == current.userId) { "Renewed session belongs to a different account" }
        return replace(current, updated)
    }
}
