package app.duoodo.sync

import app.duoodo.model.Doodle

/**
 * Moves doodles between the two partners. The real implementation (encrypted Nostr relays)
 * comes in a later milestone; until then [LoopbackTransport] lets the full UI and widget
 * flow be exercised on one device.
 */
interface DoodleTransport {
    suspend fun send(doodle: Doodle)

    /** Returns the newest doodle from the partner, or null if nothing new has arrived. */
    suspend fun fetchLatest(): Doodle?
}

/** Development stand-in: a sent doodle "arrives" back on the same device. */
object LoopbackTransport : DoodleTransport {
    @Volatile private var outbox: Doodle? = null

    override suspend fun send(doodle: Doodle) {
        outbox = doodle
    }

    override suspend fun fetchLatest(): Doodle? = outbox.also { outbox = null }
}
