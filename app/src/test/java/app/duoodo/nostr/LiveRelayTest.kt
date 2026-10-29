package app.duoodo.nostr

import app.duoodo.model.Doodle
import app.duoodo.model.Stroke
import app.duoodo.pairing.InviteAcceptor
import app.duoodo.pairing.PairingRelay
import app.duoodo.pairing.PendingInvite
import app.duoodo.sync.NostrTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** End-to-end against real public relays. Opt-in: DUOODO_LIVE=1 gradle testDebugUnitTest */
class LiveRelayTest {
    @Test
    fun pairAndDeliverOverPublicRelays() = runBlocking {
        assumeTrue(System.getenv("DUOODO_LIVE") == "1")
        val pool = RelayPool()
        val pending = PendingInvite.create()
        val accepted = InviteAcceptor.accept(pending.invite)
        val relay = PairingRelay(pool)
        relay.publishReply(pending.invite, accepted.sealedReply)
        val initiator = relay.findReply(pending)
        assertNotNull("initiator found the pairing reply", initiator)
        relay.publishConfirm(initiator!!)
        assertTrue("responder sees the confirmation", relay.isConfirmed(accepted.pairing))

        val state = object : NostrTransport.State { override var lastSentDay: Long? = null }
        val doodle = Doodle(listOf(Stroke(floatArrayOf(0.1f, 0.1f, 0.9f, 0.9f), 0xFFE53935.toInt(), 0.02f)), System.currentTimeMillis())
        NostrTransport(initiator, pool, state).send(doodle)
        val got = NostrTransport(accepted.pairing, pool, state).fetchLatest()
        assertEquals(doodle.createdAtMillis, got?.createdAtMillis)
    }
}
