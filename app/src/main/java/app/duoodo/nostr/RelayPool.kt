package app.duoodo.nostr

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Talks to several public Nostr relays at once. Each operation opens short-lived connections,
 * so nothing stays connected in the background. Relays only ever see signed, encrypted events.
 */
interface Relays {
    /** Publishes to every relay; returns how many accepted it. */
    suspend fun publish(event: NostrEvent): Int

    /** Collects stored events matching [filter] from all relays, de-duplicated by id, signatures checked. */
    suspend fun query(filter: JsonObject): List<NostrEvent>
}

class RelayPool(
    val relays: List<String> = DEFAULT_RELAYS,
    private val timeoutMillis: Long = 8_000,
    private val client: OkHttpClient = sharedClient,
) : Relays {
    override suspend fun publish(event: NostrEvent): Int = coroutineScope {
        val message = JsonArray(listOf(JsonPrimitive("EVENT"), event.toJson())).toString()
        relays.map { url ->
            async {
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
        }.awaitAll().count { it }
    }

    override suspend fun query(filter: JsonObject): List<NostrEvent> = coroutineScope {
        val subId = "q" + System.nanoTime().toString(36)
        val req = JsonArray(listOf(JsonPrimitive("REQ"), JsonPrimitive(subId), filter)).toString()
        val close = JsonArray(listOf(JsonPrimitive("CLOSE"), JsonPrimitive(subId))).toString()
        relays.map { url ->
            async {
                session(url) { send, incoming ->
                    val found = mutableListOf<NostrEvent>()
                    send(req)
                    for (msg in incoming) {
                        when (msg.getOrNull(0)?.str()) {
                            "EVENT" -> if (msg.getOrNull(1)?.str() == subId) {
                                (msg.getOrNull(2) as? JsonObject)?.let(NostrEvent::fromJson)?.let(found::add)
                            }
                            "EOSE", "CLOSED" -> if (msg.getOrNull(1)?.str() == subId) break
                        }
                    }
                    send(close)
                    found
                }.orEmpty()
            }
        }.awaitAll().flatten().distinctBy { it.id }.filter { it.isValid() }
    }

    /**
     * Opens one WebSocket, runs [block], and always closes it. Returns null on connection failure
     * or timeout, so one bad relay never fails the whole operation.
     */
    private suspend fun <T> session(
        url: String,
        block: suspend (send: (String) -> Unit, incoming: Channel<JsonArray>) -> T,
    ): T? {
        val incoming = Channel<JsonArray>(Channel.UNLIMITED)
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
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
        /** Large, long-running public relays. Using several means no single relay can block delivery. */
        val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://nostr.mom",
            "wss://relay.nostr.band",
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
