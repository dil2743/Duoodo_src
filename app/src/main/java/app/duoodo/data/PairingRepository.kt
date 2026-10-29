package app.duoodo.data

import android.content.Context
import app.duoodo.pairing.Handshake
import app.duoodo.pairing.Pairing
import app.duoodo.pairing.PendingInvite
import app.duoodo.sync.NostrTransport
import java.nio.ByteBuffer

/** Persists pairing state in the [SecretStore]. */
class PairingRepository(context: Context) : Handshake.State {
    private val secrets = SecretStore(context)
    private val latest = LatestDoodleStore(context)

    override var pairing: Pairing?
        get() = secrets.get(PAIRING)?.let { runCatching { Pairing.deserialize(it) }.getOrNull() }
        set(value) = secrets.put(PAIRING, value?.serialize())

    /** Responder side: we answered an invite but the initiator hasn't confirmed yet. */
    override var joining: Pairing?
        get() = secrets.get(JOINING)?.let { runCatching { Pairing.deserialize(it) }.getOrNull() }
        set(value) = secrets.put(JOINING, value?.serialize())

    override var joiningSinceMillis: Long?
        get() = secrets.get(JOINING_SINCE)?.takeIf { it.size == 8 }?.let { ByteBuffer.wrap(it).long }
        set(value) = secrets.put(JOINING_SINCE, value?.let { ByteBuffer.allocate(8).putLong(it).array() })

    override var pendingInvite: PendingInvite?
        get() = secrets.get(PENDING)?.let { runCatching { PendingInvite.deserialize(it) }.getOrNull() }
        set(value) = secrets.put(PENDING, value?.serialize())

    val transportState = object : NostrTransport.State {
        override var lastSentDay: Long?
            get() = secrets.get(LAST_SENT_DAY)?.takeIf { it.size == 8 }?.let { ByteBuffer.wrap(it).long }
            set(value) = secrets.put(LAST_SENT_DAY, value?.let { ByteBuffer.allocate(8).putLong(it).array() })
    }

    /** Set when the partner unpaired; the pairing is kept only so the screen can explain what happened. */
    var partnerLeft: Boolean
        get() = secrets.get(PARTNER_LEFT)?.firstOrNull() == 1.toByte()
        set(value) = secrets.put(PARTNER_LEFT, if (value) byteArrayOf(1) else null)

    /** Forget the partner and everything received from them. */
    fun unpair() {
        listOf(PAIRING, PENDING, JOINING, JOINING_SINCE, LAST_SENT_DAY, PARTNER_LEFT).forEach { secrets.put(it, null) }
        latest.reset()
    }

    private companion object {
        const val PAIRING = "pairing"
        const val PENDING = "pending_invite"
        const val JOINING = "joining"
        const val JOINING_SINCE = "joining_since"
        const val LAST_SENT_DAY = "last_sent_day"
        const val PARTNER_LEFT = "partner_left"
    }
}
