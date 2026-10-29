package app.duoodo.pairing

import app.duoodo.nostr.Relays

/**
 * Moves the pairing handshake forward. Only a phone that is actively waiting acts on relay data:
 * the initiator only while its invite is pending and unexpired, the responder only while joining.
 * Once paired, the pairing is permanent until the user unpairs.
 */
class Handshake(relays: Relays, private val clock: () -> Long = System::currentTimeMillis) {
    private val pairingRelay = PairingRelay(relays, clock)

    interface State {
        var pairing: Pairing?
        var pendingInvite: PendingInvite?
        var joining: Pairing?
        var joiningSinceMillis: Long?
    }

    suspend fun advance(state: State) {
        val now = clock()
        state.pendingInvite?.let { pending ->
            if (pending.invite.isExpired(now)) {
                state.pendingInvite = null
                return
            }
            pairingRelay.findReply(pending)?.let { pairing ->
                // Close the invite first so no later reply can ever be accepted.
                state.pendingInvite = null
                state.pairing = pairing
                pairingRelay.publishConfirm(pairing)
            }
        }
        state.joining?.let { joining ->
            if (pairingRelay.isConfirmed(joining)) {
                state.pairing = joining
                state.joining = null
                state.joiningSinceMillis = null
            } else if (now - (state.joiningSinceMillis ?: now) > Invite.TTL_SECONDS * 1000) {
                state.joining = null
                state.joiningSinceMillis = null
            }
        }
    }

    /** Responder: answer an invite and start waiting for the initiator's confirmation. */
    suspend fun join(state: State, invite: Invite) {
        val result = InviteAcceptor.accept(invite, clock())
        pairingRelay.publishReply(invite, result.sealedReply)
        state.pendingInvite = null
        state.joining = result.pairing
        state.joiningSinceMillis = clock()
    }
}
