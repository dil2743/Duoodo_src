package app.duoodo.nostr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

interface Relays {
    /** Publishes to every relay; returns how many accepted it. */
    suspend fun publish(event: NostrEvent): Int

    /** Collects stored events matching [filter] from all relays, de-duplicated by id, signatures checked. */
    suspend fun query(filter: JsonObject): List<NostrEvent>
}

/**
 * Talks to several public Nostr relays at once using short-lived connections. Relays only ever
 * see signed, encrypted events.
 *
 * Operations return as soon as [quorum] relays have answered, so one slow relay never holds up a
 * send or a check; the remaining relays keep going in [background] and catch up on their own.
 */
class RelayPool(
    val relays: List<String> = DEFAULT_RELAYS,
    private val timeoutMillis: Long = 8_000,
    private val client: OkHttpClient = sharedClient,
    private val quorum: Int = 2,
    private val background: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Relays {
    override suspend fun publish(event: NostrEvent): Int {
        val message = JsonArray(listOf(JsonPrimitive("EVENT"), event.toJson())).toString()
        val results = fanOut { url ->
            session(url) { send, incoming ->
                send(message)
                for (msg in incoming) {
                    if (msg.getOrNull(0)?.str() == "OK" && msg.getOrNull(1)?.str() == event.id) {
                        return@session msg.getOrNull(2)?.jsonPrimitive?.booleanOrNull == true
                    }
                }
                false
            } ?: false
        }
        return results.count { it }
    }

    override suspend fun query(filter: JsonObject): List<NostrEvent> {
        val subId = "q" + System.nanoTime().toString(36)
        val req = JsonArray(listOf(JsonPrimitive("REQ"), JsonPrimitive(subId), filter)).toString()
        val close = JsonArray(listOf(JsonPrimitive("CLOSE"), JsonPrimitive(subId))).toString()
        val expectedAuthors = filter["authors"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet()
        val expectedDTags = filter["#d"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet()
        val expectedKinds = filter["kinds"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }?.toSet()

        val results = fanOut { url ->
            session(url) { send, incoming ->
                val found = mutableListOf<NostrEvent>()
                send(req)
                for (msg in incoming) {
                    when (msg.getOrNull(0)?.str()) {
                        "EVENT" -> if (msg.getOrNull(1)?.str() == subId) {
                            val obj = msg.getOrNull(2) as? JsonObject
                            if (obj != null) {
                                val kind = (obj["kind"] as? JsonPrimitive)?.intOrNull
                                val pubkey = (obj["pubkey"] as? JsonPrimitive)?.content
                                val dTag = (obj["tags"] as? JsonArray)?.firstOrNull {
                                    (it as? JsonArray)?.getOrNull(0)?.let { p -> (p as? JsonPrimitive)?.content } == "d"
                                }?.let { (it as? JsonArray)?.getOrNull(1)?.let { p -> (p as? JsonPrimitive)?.content } }

                                if ((expectedKinds == null || kind in expectedKinds) &&
                                    (expectedAuthors == null || pubkey in expectedAuthors) &&
                                    (expectedDTags == null || dTag in expectedDTags)
                                ) {
                                    NostrEvent.fromJson(obj)?.let {
                                        found.add(it)
                                        if (found.size >= MAX_QUERY_EVENTS) return@session found
                                    }
                                }
                            }
                        }
                        "EOSE", "CLOSED" -> if (msg.getOrNull(1)?.str() == subId) break
                    }
                }
                send(close)
                found
            }
        }
        return results.filterNotNull().flatten().distinctBy { it.id }.filter { it.isValid() }
    }

    /**
     * Runs [op] against every relay and returns once [quorum] of them have produced a useful
     * result (non-null and not false/empty), or all have finished. Unfinished relays keep running
     * in [background] so a publish still reaches them.
     */
    private suspend fun <T> fanOut(op: suspend (String) -> T): List<T> {
        val done = Channel<T>(capacity = relays.size)
        relays.forEach { url -> background.launch { done.send(op(url)) } }
        val results = mutableListOf<T>()
        var useful = 0
        while (results.size < relays.size && useful < quorum) {
            val r = done.receive()
            results += r
            if (r != null && r != false && (r as? List<*>)?.isEmpty() != true) useful++
        }
        return results
    }

    /**
     * Opens one WebSocket, runs [block], and always closes it. Returns null on connection failure
     * or timeout, so one bad relay never fails the whole operation.
     */
    private suspend fun <T> session(
        url: String,
        block: suspend (send: (String) -> Unit, incoming: Channel<JsonArray>) -> T,
    ): T? {
        val incoming = Channel<JsonArray>(capacity = CHANNEL_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > MAX_FRAME_CHARS) return
                val parsed = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return
                incoming.trySend(parsed)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                incoming.close(t)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                incoming.close()
            }
        })
        return try {
            withTimeoutOrNull(timeoutMillis) { block({ ws.send(it) }, incoming) }
        } catch (e: Exception) {
            null
        } finally {
            ws.close(1000, null)
        }
    }

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        const val MAX_FRAME_CHARS = 128 * 1024
        const val MAX_QUERY_EVENTS = 50
        const val CHANNEL_CAPACITY = 64

        /**
         * Public relays that accept app-data events and answer in ~3 s (measured 2026-10-29;
         * relay.damus.io rejects these events and relay.nostr.band timed out). Using several means
         * no single relay can block or censor delivery.
         */
        val DEFAULT_RELAYS = listOf(
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://nostr.mom",
            "wss://offchain.pub",
            "wss://relay.nostr.net",
        )

        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        fun filter(
            kinds: List<Int>,
            authors: List<String>? = null,
            dTags: List<String>? = null,
            limit: Int? = null,
        ): JsonObject = JsonObject(buildMap {
            put("kinds", JsonArray(kinds.map(::JsonPrimitive)))
            authors?.let { put("authors", JsonArray(it.map(::JsonPrimitive))) }
            dTags?.let { put("#d", JsonArray(it.map(::JsonPrimitive))) }
            limit?.let { put("limit", JsonPrimitive(it)) }
        })
    }
}
