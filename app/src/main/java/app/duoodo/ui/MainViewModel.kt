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
import app.duoodo.pairing.Handshake
import app.duoodo.pairing.PendingInvite
import app.duoodo.data.Settings
import app.duoodo.sync.Inbox
import app.duoodo.sync.LiveService
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
        data object Joining : PairState
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

    var instantDelivery by mutableStateOf(Settings(app).instantDelivery)
        private set

    init {
        refreshPairState()
        // Redraw as soon as the live connection (or any check) stores a newer doodle.
        viewModelScope.launch {
            Inbox.updates.collect { received = withContext(Dispatchers.IO) { store.read() } }
        }
    }

    fun changeInstantDelivery(enabled: Boolean) {
        Settings(getApplication()).instantDelivery = enabled
        instantDelivery = enabled
        LiveService.sync(getApplication())
    }

    private fun refreshPairState() {
        val pairing = repo.pairing
        val pending = repo.pendingInvite
        pairState = when {
            pairing != null -> PairState.Paired
            pending != null -> PairState.Inviting(pending.invite.toCode())
            repo.joining != null -> PairState.Joining
            else -> PairState.Unpaired
        }
        if (pairState is PairState.Inviting || pairState is PairState.Joining) watchHandshake() else inviteWatcher?.cancel()
        LiveService.sync(getApplication())
    }

    // --- Pairing ---

    fun createInvite() {
        repo.pendingInvite = PendingInvite.create()
        refreshPairState()
    }

    /** While a handshake is on screen, poll every few seconds so pairing feels immediate. */
    private fun watchHandshake() {
        if (inviteWatcher?.isActive == true) return
        inviteWatcher = viewModelScope.launch {
            while (isActive) {
                withContext(Dispatchers.IO) { runCatching { Sync.advancePairing(repo) } }
                val before = pairState
                refreshPairState()
                if (pairState != before) {
                    if (pairState is PairState.Unpaired) status = "The invite expired. Create a new one."
                    if (pairState is PairState.Paired) checkNow()
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
        if (invite.isExpired(System.currentTimeMillis())) {
            status = "This invite has expired. Ask your partner for a new one."
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { Handshake(Sync.relays).join(repo, invite) }
                refreshPairState()
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
        if (drawing.marks.isEmpty() || busy) return
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
