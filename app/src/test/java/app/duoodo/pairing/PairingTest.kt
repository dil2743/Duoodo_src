package app.duoodo.pairing

import app.duoodo.crypto.Crypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException

class PairingTest {
    @Test
    fun bothSidesDeriveTheSameKeysWithMirroredDirections() {
        val pending = PendingInvite.create()
        val invite = Invite.parse("Join me on Duoodo: ${pending.invite.toCode()} 🙂")
        val responder = InviteAcceptor.accept(invite)
        val initiator = pending.complete(responder.sealedReply)

        assertArrayEquals(initiator.root, responder.pairing.root)
        assertTrue(initiator.isInitiator)
        assertFalse(responder.pairing.isInitiator)
        assertEquals(initiator.outgoing, responder.pairing.incoming)
        assertEquals(initiator.safetyEmojis, responder.pairing.safetyEmojis)
        assertEquals(6, initiator.safetyEmojis.size)
        assertEquals(initiator.slotTag, responder.pairing.slotTag)
    }

    @Test
    fun directionsAndDaysUseDistinctKeys() {
        val p = Pairing(Crypto.randomBytes(32), true)
        val i2r = Pairing.Direction.INITIATOR_TO_RESPONDER
        val r2i = Pairing.Direction.RESPONDER_TO_INITIATOR
        assertFalse(p.messageKey(i2r).contentEquals(p.messageKey(r2i)))
        assertFalse(p.nostrPublicKey(i2r, 100).contentEquals(p.nostrPublicKey(r2i, 100)))
        assertFalse(p.nostrPublicKey(i2r, 100).contentEquals(p.nostrPublicKey(i2r, 101)))
    }

    @Test
    fun replyEncryptedForAnotherInviteIsRejected() {
        val pending = PendingInvite.create()
        val stranger = InviteAcceptor.accept(PendingInvite.create().invite)
        // Encrypted with a different invite's secret, so it doesn't open.
        assertThrows(GeneralSecurityException::class.java) { pending.complete(stranger.sealedReply) }
    }

    @Test
    fun differentPairingsGetDifferentSafetyCodes() {
        assertNotEquals(Pairing(Crypto.randomBytes(32), true).safetyEmojis, Pairing(Crypto.randomBytes(32), true).safetyEmojis)
    }

    @Test
    fun serializationRoundTrips() {
        val p = Pairing(Crypto.randomBytes(32), false)
        val q = Pairing.deserialize(p.serialize())
        assertArrayEquals(p.root, q.root)
        assertEquals(p.isInitiator, q.isInitiator)

        val pending = PendingInvite.create()
        assertEquals(pending.invite.toCode(), PendingInvite.deserialize(pending.serialize()).invite.toCode())
    }

    @Test
    fun rejectsMalformedInvites() {
        listOf("", "hello", "duoodo-", "duoodo-!!!", "duoodo-AAAA").forEach {
            assertThrows(IOException::class.java) { Invite.parse(it) }
        }
    }
}
