package com.priyabrat.medusa.data.library

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Separate random values for the public QR, private polling and OAuth PKCE. */
data class PairingSecrets(val id:String,val secret:String,val verifier:String) {
    companion object {
        private val random=SecureRandom()
        private fun encode(bytes:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        private fun randomValue(size:Int)=encode(ByteArray(size).also { random.nextBytes(it) })
        fun create()=PairingSecrets(randomValue(24),randomValue(32),randomValue(32))
        fun challenge(verifier:String)=encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    }
}
