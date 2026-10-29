package app.duoodo.pairing

import app.duoodo.crypto.Crypto
import app.duoodo.crypto.Schnorr
import app.duoodo.crypto.fromBase64Url
import app.duoodo.crypto.toBase64Url
import app.duoodo.crypto.toHex
import java.io.IOException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException

/**
 * The secret shared by exactly two partners. Every key used afterwards is derived from [root]:
 * a message key and a daily-rotating Nostr identity for each direction, so the two sides never
 * share a signing key and relays can't link one day's notes to the next.
 *
 * @param isInitiator true on the phone that created the invite; decides which direction is "mine".
 */
class Pairing(val root: ByteArray, val isInitiator: Boolean) {
    init {
        require(root.size == Crypto.KEY_SIZE)
    }

    enum class Direction(val label: String) { INITIATOR_TO_RESPONDER("i2r"), RESPONDER_TO_INITIATOR("r2i") }

    val outgoing get() = if (isInitiator) Direction.INITIATOR_TO_RESPONDER else Direction.RESPONDER_TO_INITIATOR
    val incoming get() = if (isInitiator) Direction.RESPONDER_TO_INITIATOR else Direction.INITIATOR_TO_RESPONDER

    fun messageKey(dir: Direction): ByteArray = Crypto.hkdf(root, EMPTY, "duoodo/v1/msg/${dir.label}")

    /** Per-direction, per-day Nostr private key. [day] is days since the Unix epoch (UTC). */
    fun nostrPrivateKey(dir: Direction, day: Long): ByteArray =
        Schnorr.privateKeyFrom(Crypto.hkdf(root, EMPTY, "duoodo/v1/nostr/${dir.label}/$day"))

    fun nostrPublicKey(dir: Direction, day: Long): ByteArray = Schnorr.publicKey(nostrPrivateKey(dir, day))

    /** The "d" tag of our replaceable relay event; looks random to relays. */
    val slotTag: String get() = Crypto.hkdf(root, EMPTY, "duoodo/v1/slot", 16).toHex()

    /** The inviting phone confirms the pairing under this tag, encrypted with [confirmKey]. */
    val confirmTag: String get() = Crypto.hkdf(root, EMPTY, "duoodo/v1/confirm/tag", 16).toHex()
    val confirmKey: ByteArray get() = Crypto.hkdf(root, EMPTY, "duoodo/v1/confirm/key")

    /** Short code both partners compare to detect a man-in-the-middle during pairing. */
    val safetyEmojis: List<String>
        get() {
            val bits = Crypto.hkdf(root, EMPTY, "duoodo/v1/safety", 6)
            return bits.map { EMOJIS[(it.toInt() and 0xff) % EMOJIS.size] }
        }

    fun serialize(): ByteArray = byteArrayOf(if (isInitiator) 1 else 0) + root

    companion object {
        private val EMPTY = ByteArray(0)

        // 64 visually distinct emoji, so each byte mod 64 is uniform.
        val EMOJIS = listOf(
            "🐶", "🐱", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🦉", "🦄",
            "🐝", "🦋", "🐢", "🐙", "🦀", "🐳", "🐬", "🦈", "🌵", "🌲", "🍀", "🍁", "🌻", "🌹", "🍄", "🌙",
            "⭐", "🔥", "🌈", "❄️", "⚡", "☂️", "🍎", "🍌", "🍇", "🍓", "🍒", "🍍", "🥕", "🌽", "🍕", "🍩",
            "🎈", "🎁", "🎸", "🎲", "⚽", "🏀", "🚀", "🚲", "⛵", "🏠", "🔑", "🔔", "📚", "✏️", "⏰", "💎",
        )

        fun deserialize(bytes: ByteArray): Pairing {
            if (bytes.size != 1 + Crypto.KEY_SIZE) throw IOException("bad pairing data")
            return Pairing(bytes.copyOfRange(1, bytes.size), bytes[0].toInt() == 1)
        }

        fun deriveRoot(shared: ByteArray, inviteSecret: ByteArray, initiatorPub: ByteArray, responderPub: ByteArray) =
            Crypto.hkdf(shared, Crypto.sha256(inviteSecret, initiatorPub, responderPub), "duoodo/v1/root")
    }
}

/**
 * A one-time invite code, created by the initiator and shared by copy/paste over any messenger.
 * Holds a fresh X25519 public key and 256 random bits that both authenticate and encrypt the
 * responder's reply, plus the creation time so a stale code is refused on both sides.
 * It is only good for setting up the pairing; the pairing itself lasts until unpaired.
 */
class Invite(val initiatorPublicKey: ByteArray, val secret: ByteArray, val createdAtSeconds: Long) {
    init {
        require(initiatorPublicKey.size == 32 && secret.size == 32)
    }

    fun toCode(): String = CODE_PREFIX + bytes().toBase64Url()

    fun isExpired(nowMillis: Long): Boolean {
        val age = nowMillis / 1000 - createdAtSeconds
        // Small negative ages are tolerated for clock skew between the phones.
        return age > TTL_SECONDS || age < -CLOCK_SKEW_SECONDS
    }

    internal fun bytes(): ByteArray =
        byteArrayOf(VERSION) + initiatorPublicKey + secret + ByteBuffer.allocate(8).putLong(createdAtSeconds).array()

    /** Relays look up the reply by this tag; derived from the secret so only the two phones know it. */
    val replyTag: String get() = Crypto.hkdf(secret, ByteArray(0), "duoodo/v1/invite/tag", 16).toHex()
    val replyKey: ByteArray get() = Crypto.hkdf(secret, ByteArray(0), "duoodo/v1/invite/key")

    companion object {
        const val CODE_PREFIX = "duoodo-"
        private const val VERSION: Byte = 2
        private const val SIZE = 1 + 32 + 32 + 8
        const val TTL_SECONDS = 24 * 3600L
        private const val CLOCK_SKEW_SECONDS = 3600L
        val REPLY_AAD = "duoodo/v1/invite/reply".toByteArray()

        /**
         * Accepts the code alone or embedded in a longer pasted message.
         * @throws IOException if no valid invite is found.
         */
        fun parse(text: String): Invite {
            val encoded = Regex("$CODE_PREFIX([A-Za-z0-9_-]+)").find(text)?.groupValues?.get(1)
                ?: throw IOException("not an invite")
            val bytes = try {
                encoded.fromBase64Url()
            } catch (e: IllegalArgumentException) {
                throw IOException("not an invite", e)
            }
            return fromBytes(bytes)
        }

        internal fun fromBytes(bytes: ByteArray): Invite {
            if (bytes.size != SIZE || bytes[0] != VERSION) throw IOException("not an invite")
            return Invite(bytes.copyOfRange(1, 33), bytes.copyOfRange(33, 65), ByteBuffer.wrap(bytes, 65, 8).long)
        }
    }
}

/** Initiator state while waiting for the partner to answer an invite. */
class PendingInvite(val keyPair: Crypto.KeyPair, val invite: Invite) {
    /** Completes pairing from the responder's encrypted reply. */
    fun complete(sealedReply: ByteArray): Pairing {
        val responderPub = Crypto.open(invite.replyKey, sealedReply, Invite.REPLY_AAD)
        if (responderPub.size != 32) throw GeneralSecurityException("bad reply")
        val shared = Crypto.x25519(keyPair.privateKey, responderPub)
        return Pairing(Pairing.deriveRoot(shared, invite.secret, keyPair.publicKey, responderPub), isInitiator = true)
    }

    fun serialize(): ByteArray = keyPair.privateKey + invite.bytes()

    companion object {
        fun create(nowMillis: Long = System.currentTimeMillis()): PendingInvite {
            val kp = Crypto.x25519KeyPair()
            return PendingInvite(kp, Invite(kp.publicKey, Crypto.randomBytes(32), nowMillis / 1000))
        }

        fun deserialize(bytes: ByteArray): PendingInvite {
            if (bytes.size <= 32) throw IOException("bad pending invite")
            val invite = Invite.fromBytes(bytes.copyOfRange(32, bytes.size))
            return PendingInvite(Crypto.KeyPair(bytes.copyOf(32), invite.initiatorPublicKey), invite)
        }
    }
}

/** Responder side: accept an invite, producing the pairing plus the encrypted reply to publish. */
object InviteAcceptor {
    class Result(val pairing: Pairing, val sealedReply: ByteArray)

    /** @throws InviteExpiredException if the invite is too old. */
    fun accept(invite: Invite, nowMillis: Long = System.currentTimeMillis()): Result {
        if (invite.isExpired(nowMillis)) throw InviteExpiredException()
        val kp = Crypto.x25519KeyPair()
        val shared = Crypto.x25519(kp.privateKey, invite.initiatorPublicKey)
        val root = Pairing.deriveRoot(shared, invite.secret, invite.initiatorPublicKey, kp.publicKey)
        val reply = Crypto.seal(invite.replyKey, kp.publicKey, Invite.REPLY_AAD)
        return Result(Pairing(root, isInitiator = false), reply)
    }
}

class InviteExpiredException : IOException("invite expired")
