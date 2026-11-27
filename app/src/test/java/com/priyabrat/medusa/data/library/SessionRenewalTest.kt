package com.priyabrat.medusa.data.library

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SessionRenewalTest {
    private fun token(exp: Long): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"exp\":$exp}".toByteArray())
        return "header.$payload.signature"
    }

    @Test fun validSessionIsNotRefreshedEarly() {
        val session = SessionRenewal.Tokens("user", token(2000), "old")
        assertFalse(SessionRenewal.isDue(session, nowSeconds = 1000))
    }

    @Test fun expiredOrMalformedSessionIsRefreshed() {
        assertTrue(SessionRenewal.isDue(SessionRenewal.Tokens("user", token(1100), "old"), nowSeconds = 1000))
        assertTrue(SessionRenewal.isDue(SessionRenewal.Tokens("user", "invalid", "old"), nowSeconds = 1000))
    }

    @Test fun rotatedTokensAreSavedTogetherAndUserMustMatch() {
        val original = SessionRenewal.Tokens("user", token(1), "old")
        var saved: SessionRenewal.Tokens? = null
        val result = SessionRenewal.renew(original, 1000,
            exchange = { SessionRenewal.Tokens("user", "new-access", "new-refresh") },
            replace = { old, next -> assertEquals(original, old); saved = next; true })
        assertTrue(result)
        assertEquals("new-refresh", saved?.refresh)
        assertEquals("new-access", saved?.access)
        try {
            SessionRenewal.renew(original, 1000,
                exchange = { SessionRenewal.Tokens("different-user", "new", "new-refresh") },
                replace = { _, _ -> fail("Should not save tokens for another user"); true })
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
    }

    @Test fun signedOutDuringRefreshIsNotRestored() {
        val original = SessionRenewal.Tokens("user", token(1), "old")
        assertFalse(SessionRenewal.renew(original, 1000,
            exchange = { SessionRenewal.Tokens("user", "new", "new-refresh") },
            replace = { _, _ -> false }))
    }
}
