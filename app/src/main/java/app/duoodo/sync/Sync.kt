package app.duoodo.sync

import android.content.Context
import app.duoodo.data.PairingRepository
import app.duoodo.model.Doodle
import app.duoodo.nostr.RelayPool
import app.duoodo.pairing.PairingRelay

/** App-level sync operations shared by the UI and the background worker. */
object Sync {
    val relays by lazy { RelayPool() }

    private fun transport(repo: PairingRepository): NostrTransport? =
        repo.pairing?.let { NostrTransport(it, relays, repo.transportState) }

    /** Fetches and stores the partner's newest doodle. Also completes a pending invite if the partner answered. */
    suspend fun check(context: Context) {
        val repo = PairingRepository(context)
        repo.pendingInvite?.let { pending ->
            PairingRelay(relays).findReply(pending)?.let {
                repo.pairing = it
                repo.pendingInvite = null
            }
        }
        transport(repo)?.fetchLatest()?.let { Inbox.deliver(context, it) }
    }

    suspend fun send(context: Context, doodle: Doodle) {
        val transport = transport(PairingRepository(context)) ?: throw IllegalStateException("not paired")
        transport.send(doodle)
    }
}
