package app.duoodo.nostr

import app.duoodo.crypto.Crypto
import app.duoodo.crypto.Schnorr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrEventTest {
    private val key = Schnorr.privateKeyFrom(Crypto.randomBytes(32))

    @Test
    fun signedEventValidatesAndSurvivesJson() {
        val e = NostrEvent.sign(key, 1_700_000_000, 30078, listOf(listOf("d", "x")), "content \"quoted\" \n ✏️")
        assertTrue(e.isValid())
        val parsed = NostrEvent.fromJson(e.toJson())
        assertNotNull(parsed)
        assertTrue(parsed!!.isValid())
        assertEquals("x", parsed.tag("d"))
    }

    @Test
    fun tamperedEventIsInvalid() {
        val e = NostrEvent.sign(key, 1_700_000_000, 30078, emptyList(), "a")
        assertFalse(NostrEvent(e.id, e.pubkey, e.createdAt, e.kind, e.tags, "b", e.sig).isValid())
        assertFalse(NostrEvent(e.id, e.pubkey, e.createdAt + 1, e.kind, e.tags, e.content, e.sig).isValid())
        assertFalse(NostrEvent("zz", e.pubkey, e.createdAt, e.kind, e.tags, e.content, e.sig).isValid())
    }

    @Test
    fun idMatchesKnownNip01Serialization() {
        // Escaping rules matter: the id must be computed over exactly ["0",pubkey,...] with standard JSON escaping.
        val id = NostrEvent.computeId("aa", 1, 1, listOf(listOf("t", "x")), "hi\n")
        assertEquals(Crypto.sha256("""[0,"aa",1,1,[["t","x"]],"hi\n"]""".toByteArray()).toList(), id.toList())
    }
}
