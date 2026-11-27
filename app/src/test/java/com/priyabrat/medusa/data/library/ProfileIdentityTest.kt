package com.priyabrat.medusa.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileIdentityTest {
    @Test fun googleUserIdProducesStableProfileId() {
        assertEquals("g123456781234123412341234567890ab", ProfileIdentity.forUser("12345678-1234-1234-1234-1234567890ab"))
    }
    @Test fun invalidUserIdsAreNotSentAsAnotherProfile() {
        assertNull(ProfileIdentity.forUser(null))
        assertNull(ProfileIdentity.forUser("p1"))
    }
    @Test fun acceptsLegacyLinkOrCanonicalLinkButNotAnotherAccountsProfile() {
        val user = "12345678-1234-1234-1234-1234567890ab"
        val canonical = "g123456781234123412341234567890ab"
        assertEquals("p1", ProfileIdentity.linked("p1", user, "p1"))
        assertEquals(canonical, ProfileIdentity.linked("p1", user, canonical))
        assertNull(ProfileIdentity.linked("p1", user, "someone-else"))
    }
}
