package com.priyabrat.medusa.data.library

import org.junit.Assert.*
import org.junit.Test

class TvPairingTest {
    @Test fun pkceChallengeMatchesPublishedExample() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", PairingSecrets.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun sessionSecretsAreIndependentAndUnpredictableLength() {
        val a=PairingSecrets.create(); val b=PairingSecrets.create()
        assertEquals(32,a.id.length); assertEquals(43,a.secret.length); assertEquals(43,a.verifier.length)
        assertNotEquals(a.id,b.id); assertNotEquals(a.secret,a.verifier); assertNotEquals(a.secret,b.secret)
    }
}
