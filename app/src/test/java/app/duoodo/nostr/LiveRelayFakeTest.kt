package app.duoodo.nostr

import app.duoodo.crypto.Crypto
import app.duoodo.crypto.Schnorr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Exercises RelayPool and RelaySubscriber against local fake relays (no internet). */
class LiveRelayFakeTest {
    private val servers = mutableListOf<MockWebServer>()
    private val client = OkHttpClient()
    private val key = Schnorr.privateKeyFrom(Crypto.randomBytes(32))

    @After
    fun tearDown() = servers.forEach { runCatching { it.shutdown() } }

    /** A relay that answers OK to EVENT after [okDelayMs], and pushes [push] events to any REQ. */
    private fun relay(okDelayMs: Long = 0, accept: Boolean = true, push: List<NostrEvent> = emptyList(),
                      reqs: MutableList<String>? = null): String {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = Json.parseToJsonElement(text).jsonArray
                when (msg[0].jsonPrimitive.content) {
                    "EVENT" -> Thread {
                        Thread.sleep(okDelayMs)
                        val id = (msg[1] as kotlinx.serialization.json.JsonObject)["id"]!!.jsonPrimitive.content
                        webSocket.send("""["OK","$id",$accept,""]""")
                    }.start()
                    "REQ" -> {
                        reqs?.add(text)
                        val sub = msg[1].jsonPrimitive.content
                        push.forEach { webSocket.send(JsonArray(listOf(JsonPrimitive("EVENT"), JsonPrimitive(sub), it.toJson())).toString()) }
                        webSocket.send("""["EOSE","$sub"]""")
                    }
                }
            }
        }))
        server.start()
        servers += server
        return server.url("/").toString().replace("http", "ws")
    }

    private fun event(content: String) = NostrEvent.sign(key, 1_700_000_000, 30078, listOf(listOf("d", "x")), content)

    @Test
    fun publishReturnsAtQuorumWithoutWaitingForSlowRelay() = runBlocking {
        val pool = RelayPool(listOf(relay(), relay(), relay(okDelayMs = 6_000)), timeoutMillis = 8_000, client = client, quorum = 2)
        val t0 = System.currentTimeMillis()
        assertEquals(2, pool.publish(event("hi")))
        val elapsed = System.currentTimeMillis() - t0
        assertTrue("returned after ${elapsed}ms", elapsed < 3_000)
    }

    @Test
    fun publishStillCompletesWhenOnlyOneRelayAccepts() = runBlocking {
        val pool = RelayPool(listOf(relay(), relay(accept = false), relay(accept = false)), client = client, quorum = 2)
        assertEquals(1, pool.publish(event("hi")))
    }

    @Test
    fun subscriberDeliversPushedEventsAndDropsForgeries() = runBlocking {
        val good = event("real")
        val forged = NostrEvent(good.id, good.pubkey, good.createdAt, good.kind, good.tags, "tampered", good.sig)
        val reqs = CopyOnWriteArrayList<String>()
        val url = relay(push = listOf(forged, good), reqs = reqs)
        val got = Channel<NostrEvent>(Channel.UNLIMITED)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sub = RelaySubscriber(
            urls = listOf(url),
            filter = { RelayPool.filter(listOf(30078), dTags = listOf("x")) },
            onEvent = { got.send(it) },
            client = client,
        )
        sub.start(scope)
        val first = withTimeout(5_000) { got.receive() }
        assertEquals("real", first.content)
        assertTrue("forged event was not delivered", got.tryReceive().isFailure)
        assertTrue(reqs.single().contains("\"#d\":[\"x\"]"))
        scope.cancel()
    }

    @Test
    fun hostileRelayFloodingEventsIsCappedInQuery() = runBlocking {
        val events = (1..60).map { event("msg$it") }
        val url = relay(push = events)
        val pool = RelayPool(listOf(url), client = client)
        val results = pool.query(RelayPool.filter(listOf(30078), dTags = listOf("x")))
        assertTrue("query caps results at MAX_QUERY_EVENTS (50)", results.size <= RelayPool.MAX_QUERY_EVENTS)
        assertEquals(RelayPool.MAX_QUERY_EVENTS, results.size)
    }

    @Test
    fun subscriberIgnoresEventsNotMatchingFilterWithoutSigCheck() = runBlocking {
        // Event has wrong tag "y" (filter expects "x")
        val otherTagEvent = NostrEvent.sign(key, 1_700_000_000, 30078, listOf(listOf("d", "y")), "wrong_tag")
        val good = event("right_tag")
        val url = relay(push = listOf(otherTagEvent, good))
        val got = Channel<NostrEvent>(Channel.UNLIMITED)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sub = RelaySubscriber(
            urls = listOf(url),
            filter = { RelayPool.filter(listOf(30078), dTags = listOf("x")) },
            onEvent = { got.send(it) },
            client = client,
        )
        sub.start(scope)
        val received = withTimeout(5_000) { got.receive() }
        assertEquals("right_tag", received.content)
        assertTrue("unsolicited tag event was discarded", got.tryReceive().isFailure)
        scope.cancel()
    }
}
