package app.duoodo.pairing

import app.duoodo.model.Doodle
import app.duoodo.model.Stroke
import app.duoodo.nostr.FakeRelays
import app.duoodo.sync.NostrTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HandshakeTest {
    private val relays = FakeRelays()
    private var now = 1_761_700_000_000L
    private val handshake = Handshake(relays) { now }

    private class Phone : Handshake.State {
        override var pairing: Pairing? = null
        override var pendingInvite: PendingInvite? = null
        override var joining: Pairing? = null
        override var joiningSinceMillis: Long? = null
    }

    private fun invite(phone: Phone): Invite =
        PendingInvite.create(now).also { phone.pendingInvite = it }.invite.let { Invite.parse(it.toCode()) }

    @Test
    fun pairOnceThenKeepWorkingForWeeks() = runBlocking {
        val a = Phone()
        val b = Phone()
        handshake.join(b, invite(a))
        assertNull("B is not paired until A confirms", b.pairing)

        handshake.advance(a)
        assertNotNull(a.pairing)
        assertNull("invite closed after first use", a.pendingInvite)

        handshake.advance(b)
        assertNotNull(b.pairing)
        assertNull(b.joining)
        assertArrayEquals(a.pairing!!.root, b.pairing!!.root)

        // Weeks later, with no further handshake, doodles still flow both ways.
        val state = object : NostrTransport.State { override var lastSentDay: Long? = null }
        for (week in 1..4) {
            now += 7 * 86_400_000L
            val d = Doodle(listOf(Stroke(floatArrayOf(0.5f, 0.5f), week, 0.01f)), now)
            NostrTransport(a.pairing!!, relays, state) { now }.send(d)
            assertEquals(week, NostrTransport(b.pairing!!, relays, state) { now }.fetchLatest()!!.strokes[0].color)
        }
    }

    @Test
    fun secondPersonWithTheSameCodeIsShutOut() = runBlocking {
        val a = Phone()
        val b = Phone()
        val eve = Phone()
        val code = invite(a)
        handshake.join(b, code)
        now += 1000
        handshake.join(eve, code)

        handshake.advance(a)
        handshake.advance(b)
        handshake.advance(eve)
        assertNotNull(b.pairing)
        assertArrayEquals(a.pairing!!.root, b.pairing!!.root)
        assertNull("Eve never gets a confirmation that opens for her", eve.pairing)
    }

    @Test
    fun initiatorThatIsNotWaitingAcceptsNothing() = runBlocking {
        val a = Phone()
        val code = invite(a)
        a.pendingInvite = null // user cancelled the invite
        handshake.join(Phone(), code)
        handshake.advance(a)
        assertNull(a.pairing)
    }

    @Test
    fun expiredInviteIsRefusedOnBothSides() = runBlocking {
        val a = Phone()
        val code = invite(a)
        now += (Invite.TTL_SECONDS + 1) * 1000

        assertThrows(InviteExpiredException::class.java) { runBlocking { handshake.join(Phone(), code) } }

        // Even a reply that slipped in before expiry is ignored once the initiator's invite is stale.
        val late = InviteAcceptor.accept(code, code.createdAtSeconds * 1000)
        PairingRelay(relays) { now }.publishReply(code, late.sealedReply)
        handshake.advance(a)
        assertNull(a.pairing)
        assertNull("stale invite is discarded", a.pendingInvite)
    }

    @Test
    fun joinerGivesUpIfNeverConfirmed() = runBlocking {
        val b = Phone()
        handshake.join(b, invite(Phone()))
        now += (Invite.TTL_SECONDS + 1) * 1000
        handshake.advance(b)
        assertNull(b.pairing)
        assertNull(b.joining)
    }

    @Test
    fun inviteCodeIsLongAndRandom() {
        val codes = (1..200).map { PendingInvite.create(now).invite.toCode() }
        assertEquals("all distinct", codes.size, codes.toSet().size)
        codes.forEach { assertEquals(7 + 98, it.length) } // "duoodo-" + 73 bytes base64url
    }
}
