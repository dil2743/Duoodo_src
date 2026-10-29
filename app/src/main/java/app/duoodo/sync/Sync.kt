package app.duoodo.sync

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import app.duoodo.data.PairingRepository
import app.duoodo.model.Doodle
import app.duoodo.nostr.RelayPool
import app.duoodo.pairing.Handshake

/** App-level sync operations shared by the UI and the background worker. */
class PartnerLeftException : IllegalStateException("partner unpaired")

object Sync {
    val relays by lazy { RelayPool() }

    private fun transport(repo: PairingRepository): NostrTransport? =
        repo.pairing?.let { NostrTransport(it, relays, repo.transportState) }

    /**
     * Background backup check. Skipped while the live connection is up, since that already delivers
     * everything and a check would only spend mobile data.
     */
    suspend fun backgroundCheck(context: Context) {
        val repo = PairingRepository(context)
        if (LiveService.isRunning && repo.pendingInvite == null && repo.joining == null) return
        check(context)
    }

    /** Fetches and stores the partner's newest doodle, after advancing any pairing in progress. */
    suspend fun check(context: Context) {
        advancePairing(PairingRepository(context))
        val repo = PairingRepository(context)
        if (repo.partnerLeft) return
        transport(repo)?.fetch()?.let { Inbox.handle(context, it) }
    }

    suspend fun advancePairing(repo: PairingRepository) = Handshake(relays).advance(repo)

    /** @throws PartnerLeftException if the partner has unpaired (checked right before sending). */
    suspend fun send(context: Context, doodle: Doodle) {
        val repo = PairingRepository(context)
        val transport = transport(repo) ?: throw IllegalStateException("not paired")
        if (repo.partnerLeft) throw PartnerLeftException()
        // Check the partner is still paired while sending, rather than before, so it costs no extra time.
        // If they left, the doodle reached the relays encrypted for a dead pairing that nobody can read.
        coroutineScope {
            val stillPaired = async { transport.fetch() != NostrTransport.Incoming.Unpaired }
            transport.send(doodle)
            if (!stillPaired.await()) {
                Inbox.partnerLeft(context)
                throw PartnerLeftException()
            }
        }
    }

    /** Unpairs locally and tells the partner. Best effort: if offline, the partner learns on a later send attempt. */
    suspend fun unpair(context: Context) {
        val repo = PairingRepository(context)
        val transport = if (repo.partnerLeft) null else transport(repo)
        repo.unpair()
        LiveService.sync(context)
        transport?.let { runCatching { it.sendGoodbye() } }
    }
}
