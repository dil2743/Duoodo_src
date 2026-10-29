package app.duoodo.sync

import app.duoodo.model.Doodle
import app.duoodo.model.DoodleCodec
import app.duoodo.model.Stroke
import app.duoodo.nostr.FakeRelays
import app.duoodo.nostr.NostrEvent
import app.duoodo.pairing.InviteAcceptor
import app.duoodo.pairing.Pairing
import app.duoodo.pairing.PairingRelay
import app.duoodo.pairing.PendingInvite
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NostrTransportTest {
    private val relays = FakeRelays()
    private var now = 1_761_700_000_000L
    private val clock = { now }

    private class MemState : NostrTransport.State {
        override var lastSentDay: Long? = null
    }

    private fun pair(): Pair<Pairing, Pairing> = runBlocking {
        val pending = PendingInvite.create(now)
        val accepted = InviteAcceptor.accept(pending.invite, now)
        PairingRelay(relays, clock).publishReply(pending.invite, accepted.sealedReply)
        val initiator = PairingRelay(relays, clock).findReply(pending)
        assertNotNull(initiator)
        initiator!! to accepted.pairing
    }

    private fun doodle(t: Long, color: Int = 1) = Doodle(listOf(Stroke(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), color, 0.01f)), t)

    @Test
    fun doodleTravelsBothWaysAndOnlyLatestIsKept() = runBlocking {
        val (a, b) = pair()
        val ta = NostrTransport(a, relays, MemState(), clock)
        val tb = NostrTransport(b, relays, MemState(), clock)

        assertNull(tb.fetchLatest())
        ta.send(doodle(now, color = 1))
        assertEquals(1, tb.fetchLatest()!!.strokes[0].color)
        assertNull(ta.fetchLatest()) // A doesn't receive its own doodle

        now += 60_000
        ta.send(doodle(now, color = 2))
        assertEquals(2, tb.fetchLatest()!!.strokes[0].color)
        assertEquals(1, relays.stored.count { it.kind == NostrEvent.KIND_APP_DATA && it.tag("d") == a.slotTag })

        tb.send(doodle(now, color = 3))
        assertEquals(3, ta.fetchLatest()!!.strokes[0].color)
    }

    @Test
    fun relaySeesOnlyOpaquePaddedData() = runBlocking {
        val (a, _) = pair()
        val small = doodle(now)
        NostrTransport(a, relays, MemState(), clock).send(small)
        val event = relays.stored.last { it.tag("d") == a.slotTag }
        val raw = java.util.Base64.getDecoder().decode(event.content)
        assertTrue("padded well beyond the ${DoodleCodec.encode(small).size}-byte doodle", raw.size > 1000)
        assertTrue(event.tag("expiration")!!.toLong() > now / 1000)
    }

    @Test
    fun dayRolloverDeletesYesterdaysEventAndStillDelivers() = runBlocking {
        val (a, b) = pair()
        val state = MemState()
        val ta = NostrTransport(a, relays, state, clock)
        ta.send(doodle(now, color = 1))
        now += NostrTransport.DAY_SECONDS * 1000
        ta.send(doodle(now, color = 2))
        assertEquals(1, relays.stored.count { it.kind == NostrEvent.KIND_APP_DATA && it.tag("d") == a.slotTag })
        assertEquals(2, NostrTransport(b, relays, MemState(), clock).fetchLatest()!!.strokes[0].color)
    }

    @Test
    fun strangerPostingUnderTheSlotTagIsIgnored() = runBlocking {
        val (a, b) = pair()
        val impostor = Pairing(app.duoodo.crypto.Crypto.randomBytes(32), true)
        // Even knowing the slot tag, an outsider can't sign as A's derived key or encrypt with A's key.
        relays.publish(
            NostrEvent.sign(
                impostor.nostrPrivateKey(impostor.outgoing, now / 1000 / NostrTransport.DAY_SECONDS),
                now / 1000, NostrEvent.KIND_APP_DATA, listOf(listOf("d", a.slotTag)), "AAAA",
            )
        )
        assertNull(NostrTransport(b, relays, MemState(), clock).fetchLatest())
    }

    @Test
    fun sendFailsLoudlyWhenAllRelaysAreDown() {
        val (a, _) = pair()
        relays.online = false
        assertThrows(IOException::class.java) {
            runBlocking { NostrTransport(a, relays, MemState(), clock).send(doodle(now)) }
        }
    }
}
