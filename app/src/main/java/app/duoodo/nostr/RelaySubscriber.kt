package app.duoodo.nostr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Keeps a live subscription open on each relay and hands over matching events the moment a relay
 * pushes them. Each relay connection reconnects on its own with exponential backoff, and the
 * filter is re-evaluated periodically so rotating keys stay covered.
 *
 * @param filter current subscription filter; null means there is nothing to listen for
 * @param onEvent called with events whose id and signature check out
 */
class RelaySubscriber(
    private val urls: List<String>,
    private val filter: () -> JsonObject?,
    private val onEvent: suspend (NostrEvent) -> Unit,
    private val client: OkHttpClient = liveClient,
    private val refreshMillis: Long = 30 * 60_000L,
    private val minBackoffMillis: Long = 2_000,
    private val maxBackoffMillis: Long = 5 * 60_000L,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { urls.forEach { url -> launch { runRelay(url) } } }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Drops current connections and reconnects right away, e.g. after the network changed. */
    fun reconnect(scope: CoroutineScope) {
        stop()
        start(scope)
    }

    private suspend fun runRelay(url: String) {
        var backoff = minBackoffMillis
        while (currentCoroutineContext().isActive) {
            val started = System.currentTimeMillis()
            try {
                connectOnce(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Connection failed or dropped; retry below.
            }
            // A connection that stayed up for a while was healthy: start backoff over.
            if (System.currentTimeMillis() - started > HEALTHY_MILLIS) backoff = minBackoffMillis
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(maxBackoffMillis)
        }
    }

    /** Runs one connection until the relay or network drops it. */
    private suspend fun connectOnce(url: String) {
        val incoming = Channel<JsonArray>(Channel.UNLIMITED)
        val opened = CompletableDeferred<Unit>()
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull()?.let { incoming.trySend(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                incoming.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.completeExceptionally(t)
                incoming.close(t)
            }
        })
        try {
            withTimeout(CONNECT_TIMEOUT_MILLIS) { opened.await() }
            var subscribed: JsonObject? = null
            while (true) {
                val f = filter() ?: return
                if (f != subscribed) {
                    // Re-sending REQ with the same id replaces the previous filter on the relay.
                    ws.send(JsonArray(listOf(JsonPrimitive("REQ"), JsonPrimitive(SUB_ID), f)).toString())
                    subscribed = f
                }
                val result = withTimeoutOrNull(refreshMillis) { incoming.receiveCatching() } ?: continue
                val msg = result.getOrNull() ?: return
                when ((msg.getOrNull(0) as? JsonPrimitive)?.content) {
                    "EVENT" -> if (msg.getOrNull(1)?.jsonPrimitive?.content == SUB_ID) {
                        val event = (msg.getOrNull(2) as? JsonObject)?.let(NostrEvent::fromJson)
                        if (event != null && event.isValid()) onEvent(event)
                    }
                    // The relay ended our subscription (e.g. rate limit); reconnect after backoff.
                    "CLOSED" -> if (msg.getOrNull(1)?.jsonPrimitive?.content == SUB_ID) return
                }
            }
        } finally {
            ws.cancel()
        }
    }

    companion object {
        private const val SUB_ID = "live"
        private const val CONNECT_TIMEOUT_MILLIS = 15_000L
        private const val HEALTHY_MILLIS = 60_000L

        /**
         * No read timeout; WebSocket pings detect dead connections instead. 60 s keeps mobile-carrier
         * NATs from dropping the idle connection while costing only a few hundred bytes per ping.
         */
        val liveClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(60, TimeUnit.SECONDS)
            .build()
    }
}
