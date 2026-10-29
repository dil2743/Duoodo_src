package app.duoodo.sync

import android.content.Context
import app.duoodo.data.LatestDoodleStore
import app.duoodo.model.Doodle
import app.duoodo.widget.DuoodoWidget

/** Single entry point for "a doodle from the partner arrived": overwrite, then refresh the widget. */
object Inbox {
    suspend fun deliver(context: Context, doodle: Doodle) {
        val store = LatestDoodleStore(context)
        val existing = store.read()
        // Ignore stale or replayed doodles; the newest one always wins.
        if (existing != null && doodle.createdAtMillis <= existing.createdAtMillis) return
        store.overwrite(doodle)
        DuoodoWidget.refreshAll(context)
    }
}
