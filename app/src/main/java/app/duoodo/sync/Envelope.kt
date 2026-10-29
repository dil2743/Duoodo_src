package app.duoodo.sync

import app.duoodo.crypto.Crypto
import java.io.IOException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException

/**
 * Encrypts a payload for one direction of a pairing. The plaintext is length-prefixed and padded
 * to a fixed bucket size so relays can't infer how detailed a doodle is from its size.
 */
object Envelope {
    private val BUCKETS = intArrayOf(1024, 4096, 16384, 49152)

    fun seal(key: ByteArray, payload: ByteArray, aad: ByteArray): ByteArray {
        val bucket = BUCKETS.firstOrNull { it >= payload.size + 4 }
            ?: throw IllegalArgumentException("payload too large: ${payload.size}")
        val padded = ByteBuffer.allocate(bucket).putInt(payload.size).put(payload).array()
        return Crypto.seal(key, padded, aad)
    }

    /** @throws GeneralSecurityException if tampered with or not for us; IOException if malformed. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        val padded = Crypto.open(key, sealed, aad)
        if (padded.size < 4) throw IOException("bad envelope")
        val len = ByteBuffer.wrap(padded).int
        if (len < 0 || len > padded.size - 4) throw IOException("bad envelope length")
        return padded.copyOfRange(4, 4 + len)
    }
}
