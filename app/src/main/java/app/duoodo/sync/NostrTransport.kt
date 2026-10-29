package app.duoodo.sync

import app.duoodo.crypto.fromBase64
import app.duoodo.crypto.toBase64
import app.duoodo.crypto.toHex
import app.duoodo.model.Doodle
import app.duoodo.model.DoodleCodec
import app.duoodo.nostr.NostrEvent
import app.duoodo.nostr.Relays
import app.duoodo.nostr.RelayPool
import app.duoodo.pairing.Pairing
import kotlinx.serialization.json.JsonObject
import java.io.IOException

/**
 * Sends and fetches doodles as encrypted NIP-78 events.
 *
 * - Each direction signs with its own Nostr key, re-derived every UTC day, so relays see an
 *   unlinkable stream of one-off identities.
 * - The event is parameterized-replaceable on a secret "d" tag: a new doodle replaces the
 *   previous one on the relay, matching "keep only the latest".
 * - When the signing day rolls over, the previous day's event is explicitly deleted, and every
 *   event also carries a NIP-40 expiration as a backstop.
 */
class NostrTransport(
    private val pairing: Pairing,
    private val relays: Relays = RelayPool(),
    private val state: State,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Remembers which day's key signed our last doodle, so it can be deleted after rollover. */
    interface State {
        var lastSentDay: Long?
    }

    /** @throws IOException if no relay accepted the doodle. */
    suspend fun send(doodle: Doodle) {
        val nowSec = clock() / 1000
        val day = nowSec / DAY_SECONDS
        val dir = pairing.outgoing
        val sealed = Envelope.seal(pairing.messageKey(dir), DoodleCodec.encode(doodle), aad(dir))
        val event = NostrEvent.sign(
            pairing.nostrPrivateKey(dir, day),
            nowSec,
            NostrEvent.KIND_APP_DATA,
            listOf(listOf("d", pairing.slotTag), listOf("expiration", (nowSec + EXPIRY_SECONDS).toString())),
            sealed.toBase64(),
        )
        if (relays.publish(event) == 0) throw IOException("no relay accepted the doodle")

        val previousDay = state.lastSentDay
        state.lastSentDay = day
        if (previousDay != null && previousDay != day) deleteSlot(previousDay, nowSec)
    }

    /** Returns the newest doodle from the partner on any relay, or null if none can be read. */
    suspend fun fetchLatest(): Doodle? =
        relays.query(incomingFilter())
            .mapNotNull(::open)
            // The timestamp inside the ciphertext is authenticated; the relay's created_at is not.
            .maxByOrNull { it.createdAtMillis }

    /** Relay filter matching the partner's doodle events (also used for live subscriptions). */
    fun incomingFilter(): JsonObject =
        RelayPool.filter(listOf(NostrEvent.KIND_APP_DATA), authors = incomingAuthors(), dTags = listOf(pairing.slotTag))

    /** Decrypts a doodle event from the partner; null if it isn't one. */
    fun open(event: NostrEvent): Doodle? {
        if (event.kind != NostrEvent.KIND_APP_DATA || event.tag("d") != pairing.slotTag) return null
        if (event.pubkey !in incomingAuthors()) return null
        val dir = pairing.incoming
        return runCatching {
            DoodleCodec.decode(Envelope.open(pairing.messageKey(dir), event.content.fromBase64(), aad(dir)))
        }.getOrNull()
    }

    @Volatile private var authorsCache: Pair<Long, List<String>>? = null

    /** The partner's signing keys for the past week and tomorrow; slack covers clock skew. */
    private fun incomingAuthors(): List<String> {
        val nowDay = clock() / 1000 / DAY_SECONDS
        authorsCache?.let { (day, authors) -> if (day == nowDay) return authors }
        val authors = (nowDay - LOOKBACK_DAYS..nowDay + 1).map { pairing.nostrPublicKey(pairing.incoming, it).toHex() }
        authorsCache = nowDay to authors
        return authors
    }

    private suspend fun deleteSlot(day: Long, nowSec: Long) {
        val key = pairing.nostrPrivateKey(pairing.outgoing, day)
        val pub = pairing.nostrPublicKey(pairing.outgoing, day).toHex()
        val deletion = NostrEvent.sign(
            key, nowSec, NostrEvent.KIND_DELETION,
            listOf(listOf("a", "${NostrEvent.KIND_APP_DATA}:$pub:${pairing.slotTag}")), "",
        )
        runCatching { relays.publish(deletion) }
    }

    private fun aad(dir: Pairing.Direction) = "duoodo/v1/doodle/${dir.label}".toByteArray()

    companion object {
        const val DAY_SECONDS = 86_400L
        const val EXPIRY_SECONDS = 7 * DAY_SECONDS
        private const val LOOKBACK_DAYS = 7L
    }
}
