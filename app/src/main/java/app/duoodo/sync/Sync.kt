package app.duoodo.sync

import android.content.Context
import app.duoodo.data.PairingRepository
import app.duoodo.model.Doodle
import app.duoodo.nostr.RelayPool
import app.duoodo.pairing.Handshake

/** App-level sync operations shared by the UI and the background worker. */
object Sync {
    val relays by lazy { RelayPool() }

    private fun transport(repo: PairingRepository): NostrTransport? =
        repo.pairing?.let { NostrTransport(it, relays, repo.transportState) }

    /** Fetches and stores the partner's newest doodle, after advancing any pairing in progress. */
    suspend fun check(context: Context) {
        advancePairing(PairingRepository(context))
        transport(PairingRepository(context))?.fetchLatest()?.let { Inbox.deliver(context, it) }
    }

    suspend fun advancePairing(repo: PairingRepository) = Handshake(relays).advance(repo)

    suspend fun send(context: Context, doodle: Doodle) {
        val transport = transport(PairingRepository(context)) ?: throw IllegalStateException("not paired")
        transport.send(doodle)
    }
}
