package app.duoodo.sync

import android.content.Context
import app.duoodo.data.LatestDoodleStore
import app.duoodo.data.PairingRepository
import app.duoodo.model.Doodle
import app.duoodo.widget.DuoodoWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single entry point for "a doodle from the partner arrived": overwrite, then refresh the widget. */
object Inbox {
    private val mutex = Mutex()
    private val _updates = MutableStateFlow(0L)

    /** Bumps whenever a newer doodle is stored, so an open app can redraw immediately. */
    val updates: StateFlow<Long> = _updates

    suspend fun deliver(context: Context, doodle: Doodle) {
        // Several relays push the same doodle at once; serialize so it's stored and drawn once.
        mutex.withLock {
            val store = LatestDoodleStore(context)
            val newest = maxOf(store.read()?.createdAtMillis ?: 0L, store.clearedUpTo())
            // Ignore stale, replayed, or already-cleared doodles; the newest one always wins.
            if (doodle.createdAtMillis <= newest) return
            store.overwrite(doodle)
            _updates.value = doodle.createdAtMillis
        }
        DuoodoWidget.refreshAll(context)
    }

    /**
     * Removes the received doodle from this phone and the widget. The newest timestamp is kept so
     * the same doodle doesn't reappear from the relays on the next check.
     */
    suspend fun clearReceived(context: Context) {
        mutex.withLock {
            val store = LatestDoodleStore(context)
            store.read()?.let { store.markCleared(it.createdAtMillis) }
            store.clear()
            _updates.value = -System.nanoTime()
        }
        DuoodoWidget.refreshAll(context)
    }

    /** The partner unpaired: remember it, drop their doodle, and blank the widget. */
    suspend fun partnerLeft(context: Context) {
        mutex.withLock {
            val repo = PairingRepository(context)
            if (repo.pairing == null || repo.partnerLeft) return
            repo.partnerLeft = true
            LatestDoodleStore(context).clear()
            _updates.value = -System.currentTimeMillis()
        }
        DuoodoWidget.refreshAll(context)
        LiveService.sync(context)
    }

    /** Routes whatever the partner's slot holds. */
    suspend fun handle(context: Context, incoming: NostrTransport.Incoming) = when (incoming) {
        is NostrTransport.Incoming.Note -> deliver(context, incoming.doodle)
        NostrTransport.Incoming.Unpaired -> partnerLeft(context)
    }
}
