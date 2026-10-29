package app.duoodo.sync

import android.content.Context
import app.duoodo.data.PairingRepository
import app.duoodo.nostr.RelayPool
import app.duoodo.nostr.RelaySubscriber
import app.duoodo.pairing.Pairing
import kotlinx.coroutines.CoroutineScope

/** Live delivery for one pairing: relay pushes go straight to the [Inbox]. */
class LiveReceiver(context: Context, pairing: Pairing) {
    private val app = context.applicationContext
    private val transport = NostrTransport(pairing, Sync.relays, PairingRepository(app).transportState)
    private val subscriber = RelaySubscriber(
        urls = RelayPool.DEFAULT_RELAYS.take(LIVE_RELAYS),
        filter = { transport.incomingFilter() },
        onEvent = { event -> transport.open(event)?.let { Inbox.deliver(app, it) } },
    )

    fun start(scope: CoroutineScope) = subscriber.start(scope)
    fun stop() = subscriber.stop()
    fun reconnect(scope: CoroutineScope) = subscriber.reconnect(scope)

    private companion object {
        /** Three open connections is enough redundancy; sends still go to every relay. */
        const val LIVE_RELAYS = 3
    }
}
