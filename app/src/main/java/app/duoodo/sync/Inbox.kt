package app.duoodo.sync

import android.content.Context
import app.duoodo.data.LatestDoodleStore
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
            val existing = store.read()
            // Ignore stale or replayed doodles; the newest one always wins.
            if (existing != null && doodle.createdAtMillis <= existing.createdAtMillis) return
            store.overwrite(doodle)
            _updates.value = doodle.createdAtMillis
        }
        DuoodoWidget.refreshAll(context)
    }
}
