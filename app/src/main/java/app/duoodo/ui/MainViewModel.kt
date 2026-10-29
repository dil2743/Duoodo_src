package app.duoodo.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.duoodo.data.LatestDoodleStore
import app.duoodo.data.PairingRepository
import app.duoodo.model.Doodle
import app.duoodo.pairing.Invite
import app.duoodo.pairing.InviteAcceptor
import app.duoodo.pairing.PairingRelay
import app.duoodo.pairing.PendingInvite
import app.duoodo.sync.Sync
import app.duoodo.widget.DuoodoWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LatestDoodleStore(app)
    private val repo = PairingRepository(app)

    sealed interface PairState {
        data object Unpaired : PairState
        data class Inviting(val code: String) : PairState
        data object Paired : PairState
    }

    val drawing = DrawingState()
    var pairState by mutableStateOf<PairState>(PairState.Unpaired)
        private set
    var received by mutableStateOf<Doodle?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    private var inviteWatcher: Job? = null

    init {
        refreshPairState()
    }

    private fun refreshPairState() {
        val pairing = repo.pairing
        val pending = repo.pendingInvite
        pairState = when {
            pairing != null -> PairState.Paired
            pending != null -> PairState.Inviting(pending.invite.toCode())
            else -> PairState.Unpaired
        }
        if (pairState is PairState.Inviting) watchForReply() else inviteWatcher?.cancel()
    }

    // --- Pairing ---

    fun createInvite() {
        repo.pendingInvite = PendingInvite.create()
        refreshPairState()
    }

    /** While the invite is on screen, poll briefly so pairing feels immediate. */
    private fun watchForReply() {
        if (inviteWatcher?.isActive == true) return
        inviteWatcher = viewModelScope.launch {
            while (isActive) {
                val pending = repo.pendingInvite ?: break
                val pairing = withContext(Dispatchers.IO) { runCatching { PairingRelay(Sync.relays).findReply(pending) }.getOrNull() }
                if (pairing != null) {
                    repo.pairing = pairing
                    repo.pendingInvite = null
                    refreshPairState()
                    break
                }
                delay(INVITE_POLL_MILLIS)
            }
        }
    }

    fun acceptInvite(text: String) {
        val invite = try {
            Invite.parse(text)
        } catch (e: IOException) {
            status = "That isn't a Duoodo invite code."
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                val result = InviteAcceptor.accept(invite)
                withContext(Dispatchers.IO) { PairingRelay(Sync.relays).publishReply(invite, result.sealedReply) }
                repo.pendingInvite = null
                repo.pairing = result.pairing
                refreshPairState()
                checkNow()
            } catch (e: Exception) {
                status = "Couldn't reach the relays. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    /** Optional check: both phones show the same emojis only if nobody tampered with the invite. */
    val safetyEmojis: List<String>? get() = repo.pairing?.safetyEmojis

    /** Also used for cancelling an invite. */
    fun unpair() {
        repo.unpair()
        drawing.clear()
        received = null
        refreshPairState()
        viewModelScope.launch { DuoodoWidget.refreshAll(getApplication()) }
    }

    // --- Doodles ---

    /** Called when the app comes to the foreground: check right away instead of waiting. */
    fun checkNow() {
        if (pairState is PairState.Unpaired) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Sync.check(getApplication()) } }
            if (pairState !is PairState.Paired) refreshPairState()
            received = withContext(Dispatchers.IO) { store.read() }
        }
    }

    fun send() {
        if (drawing.strokes.isEmpty() || busy) return
        val doodle = drawing.snapshot(System.currentTimeMillis())
        viewModelScope.launch {
            busy = true
            status = try {
                withContext(Dispatchers.IO) { Sync.send(getApplication(), doodle) }
                drawing.clear()
                "Sent ✓"
            } catch (e: Exception) {
                "Couldn't send. Check your connection."
            } finally {
                busy = false
            }
        }
    }

    fun dismissStatus() {
        status = null
    }

    private companion object {
        const val INVITE_POLL_MILLIS = 5_000L
    }
}
