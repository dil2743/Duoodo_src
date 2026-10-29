package app.duoodo

import app.duoodo.model.Doodle
import app.duoodo.model.Stroke
import app.duoodo.model.TextMark
import app.duoodo.nostr.RelayPool
import app.duoodo.pairing.Invite
import app.duoodo.pairing.Handshake
import app.duoodo.pairing.Pairing
import app.duoodo.pairing.PendingInvite
import app.duoodo.sync.NostrTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Desktop stand-in for the partner's phone, for testing the app on a single emulator over the
 * real public relays. Not a test; it only runs when DUOODO_PEER is set:
 *
 *   DUOODO_PEER=join DUOODO_CODE=duoodo-… gradle testDebugUnitTest --tests '*PeerTool*'
 *   DUOODO_PEER=send  …   # sends a heart doodle to the app (DUOODO_TEXT=… adds a caption)
 *   DUOODO_PEER=fetch …   # prints the app's latest doodle
 *   DUOODO_WAIT=n         # join: give up after n polls (5 s apart) instead of 36
 */
class PeerTool {
    private val stateFile = File(System.getProperty("java.io.tmpdir"), "duoodo-peer.bin")
    private val pool = RelayPool()
    private val transportState = object : NostrTransport.State { override var lastSentDay: Long? = null }

    @Test
    fun run() = runBlocking {
        val mode = System.getenv("DUOODO_PEER").orEmpty()
        assumeTrue(mode.isNotEmpty())
        when (mode) {
            "join" -> {
                // Behaves exactly like the app: reply, then wait for the inviter's confirmation.
                val phone = object : Handshake.State {
                    override var pairing: Pairing? = null
                    override var pendingInvite: PendingInvite? = null
                    override var joining: Pairing? = null
                    override var joiningSinceMillis: Long? = null
                }
                val handshake = Handshake(pool)
                handshake.join(phone, Invite.parse(System.getenv("DUOODO_CODE").orEmpty()))
                println("PEER JOINING")
                val attempts = System.getenv("DUOODO_WAIT")?.toIntOrNull() ?: 36
                repeat(attempts) {
                    handshake.advance(phone)
                    phone.pairing?.let { save(it); println("PEER PAIRED ${it.safetyEmojis.joinToString(" ")}"); return@runBlocking }
                    delay(5_000)
                }
                error("inviter never confirmed")
            }
            "send" -> {
                val text = System.getenv("DUOODO_TEXT")
                val doodle = if (text.isNullOrEmpty()) heart() else heart().let {
                    Doodle(it.marks + TextMark(text, 0.5f, 0.85f, 0xFF1E88E5.toInt(), 0.08f), it.createdAtMillis)
                }
                NostrTransport(load(), pool, transportState).send(doodle)
                println("PEER SENT heart" + if (text.isNullOrEmpty()) "" else " with text")
            }
            "fetch" -> {
                val d = NostrTransport(load(), pool, transportState).fetchLatest()
                println("PEER FETCHED " + (d?.let { "${it.strokes.size} strokes, ${it.strokes.sumOf { s -> s.pointCount }} points, texts=${it.texts.map { t -> t.text }}" } ?: "nothing"))
            }
            "bench" -> {
                // Time publish + query against each relay on its own.
                val key = app.duoodo.crypto.Schnorr.privateKeyFrom(app.duoodo.crypto.Crypto.randomBytes(32))
                val content = java.util.Base64.getEncoder().encodeToString(app.duoodo.crypto.Crypto.randomBytes(1052))
                val urls = System.getenv("DUOODO_RELAYS")?.split(",")?.filter { it.isNotBlank() } ?: RelayPool.DEFAULT_RELAYS
                for (url in urls) {
                    val one = RelayPool(listOf(url))
                    val now = System.currentTimeMillis() / 1000
                    val d = app.duoodo.crypto.Crypto.randomBytes(8).joinToString("") { "%02x".format(it) }
                    val ev = app.duoodo.nostr.NostrEvent.sign(key, now, 30078,
                        listOf(listOf("d", d), listOf("expiration", (now + 600).toString())), content)
                    val t0 = System.currentTimeMillis()
                    val ok = one.publish(ev)
                    val t1 = System.currentTimeMillis()
                    val found = one.query(RelayPool.filter(listOf(30078), dTags = listOf(d))).size
                    val t2 = System.currentTimeMillis()
                    println("PEER BENCH $url publish=${if (ok > 0) "OK" else "FAIL"} ${t1 - t0}ms query=$found ${t2 - t1}ms")
                }
            }
            else -> error("unknown DUOODO_PEER=$mode")
        }
    }

    private fun save(p: Pairing) = stateFile.writeBytes(p.serialize())
    private fun load() = Pairing.deserialize(stateFile.readBytes())

    private fun heart(): Doodle {
        val pts = FloatArray(200)
        for (i in 0 until 100) {
            val t = 2 * PI * i / 99
            pts[2 * i] = (0.5 + 0.022 * 16 * sin(t).pow(3)).toFloat()
            pts[2 * i + 1] = (0.48 - 0.022 * (13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t))).toFloat()
        }
        return Doodle(listOf(Stroke(pts, 0xFFE53935.toInt(), 0.03f)), System.currentTimeMillis())
    }
}
