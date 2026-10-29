package app.duoodo.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.duoodo.data.LatestDoodleStore
import app.duoodo.model.Doodle
import app.duoodo.sync.CheckWorker
import app.duoodo.sync.Inbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LatestDoodleStore(app)

    val drawing = DrawingState()
    var received by mutableStateOf<Doodle?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    /** Called when the app comes to the foreground: check right away instead of waiting. */
    fun checkNow() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { CheckWorker.transport.fetchLatest() }.getOrNull()?.let { Inbox.deliver(getApplication(), it) }
            }
            received = withContext(Dispatchers.IO) { store.read() }
        }
    }

    fun send() {
        if (drawing.strokes.isEmpty()) return
        val doodle = drawing.snapshot(System.currentTimeMillis())
        viewModelScope.launch {
            status = try {
                withContext(Dispatchers.IO) { CheckWorker.transport.send(doodle) }
                drawing.clear()
                "Sent ✓"
            } catch (e: Exception) {
                "Couldn't send. Check your connection."
            }
        }
    }

    fun dismissStatus() {
        status = null
    }
}
