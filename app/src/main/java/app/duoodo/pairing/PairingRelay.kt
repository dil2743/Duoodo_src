package app.duoodo.pairing

import app.duoodo.crypto.Crypto
import app.duoodo.crypto.fromBase64
import app.duoodo.crypto.toBase64
import app.duoodo.nostr.NostrEvent
import app.duoodo.nostr.RelayPool
import app.duoodo.nostr.Relays
import app.duoodo.crypto.Schnorr
import java.io.IOException

/**
 * The pairing handshake over the relays:
 *  1. Responder publishes its public key, encrypted with the invite secret ([publishReply]).
 *  2. Initiator, only while its invite is pending and unexpired, takes the first valid reply
 *     ([findReply]) and publishes a confirmation encrypted with the new pairing key ([publishConfirm]).
 *  3. Responder treats itself as paired only once that confirmation decrypts ([isConfirmed]).
 * A second person who obtained the same code never gets a confirmation that opens for them.
 */
class PairingRelay(private val relays: Relays = RelayPool(), private val clock: () -> Long = System::currentTimeMillis) {

    /** Responder: publish the sealed reply under the invite's secret tag, signed by a throwaway key. */
    suspend fun publishReply(invite: Invite, sealedReply: ByteArray) {
        val nowSec = clock() / 1000
        val event = NostrEvent.sign(
            Schnorr.privateKeyFrom(Crypto.randomBytes(32)),
            nowSec,
            NostrEvent.KIND_APP_DATA,
            listOf(listOf("d", invite.replyTag), listOf("expiration", (nowSec + REPLY_EXPIRY_SECONDS).toString())),
            sealedReply.toBase64(),
        )
        if (relays.publish(event) == 0) throw IOException("no relay accepted the pairing reply")
    }

    /** Initiator: look for a reply that decrypts with the invite secret. Null if none yet. */
    suspend fun findReply(pending: PendingInvite): Pairing? {
        val events = relays.query(
            RelayPool.filter(listOf(NostrEvent.KIND_APP_DATA), dTags = listOf(pending.invite.replyTag))
        )
        // Anyone can post under the tag, but only someone holding the invite secret can produce a reply that opens.
        return events.sortedBy { it.createdAt }.firstNotNullOfOrNull { event ->
            runCatching { pending.complete(event.content.fromBase64()) }.getOrNull()
        }
    }

    /** Initiator: tell the responder its reply was accepted. */
    suspend fun publishConfirm(pairing: Pairing) {
        val nowSec = clock() / 1000
        val sealed = Crypto.seal(pairing.confirmKey, CONFIRM_PLAINTEXT, CONFIRM_AAD)
        val event = NostrEvent.sign(
            Schnorr.privateKeyFrom(Crypto.randomBytes(32)),
            nowSec,
            NostrEvent.KIND_APP_DATA,
            listOf(listOf("d", pairing.confirmTag), listOf("expiration", (nowSec + REPLY_EXPIRY_SECONDS).toString())),
            sealed.toBase64(),
        )
        if (relays.publish(event) == 0) throw IOException("no relay accepted the confirmation")
    }

    /** Responder: has the initiator accepted our reply? */
    suspend fun isConfirmed(pairing: Pairing): Boolean {
        val events = relays.query(RelayPool.filter(listOf(NostrEvent.KIND_APP_DATA), dTags = listOf(pairing.confirmTag)))
        return events.any { event ->
            runCatching { Crypto.open(pairing.confirmKey, event.content.fromBase64(), CONFIRM_AAD) }
                .getOrNull()?.contentEquals(CONFIRM_PLAINTEXT) == true
        }
    }

    companion object {
        private val CONFIRM_PLAINTEXT = "confirmed".toByteArray()
        private val CONFIRM_AAD = "duoodo/v1/confirm".toByteArray()
        const val REPLY_EXPIRY_SECONDS = 2 * 86_400L
    }
}
