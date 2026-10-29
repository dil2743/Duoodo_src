package app.duoodo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

class DoodleCodecTest {
    private val sample = Doodle(
        marks = listOf(
            Stroke(floatArrayOf(0f, 0f, 0.5f, 0.5f, 1f, 1f), 0xFFE53935.toInt(), 0.014f),
            Stroke(floatArrayOf(0.25f, 0.75f), 0xFF1F1F1F.toInt(), 0.03f),
        ),
        createdAtMillis = 1_761_700_000_000,
    )

    @Test
    fun roundTripPreservesDoodleWithinQuantization() {
        val decoded = DoodleCodec.decode(DoodleCodec.encode(sample))
        assertEquals(sample.createdAtMillis, decoded.createdAtMillis)
        assertEquals(sample.strokes.size, decoded.strokes.size)
        sample.strokes.zip(decoded.strokes).forEach { (a, b) ->
            assertEquals(a.color, b.color)
            assertEquals(a.width, b.width, 1e-4f)
            assertEquals(a.points.size, b.points.size)
            a.points.zip(b.points).forEach { (x, y) -> assertEquals(x, y, 1e-4f) }
        }
    }

    @Test
    fun encodingIsCompact() {
        // 1 + 8 + 2 header, 9 per stroke, 4 per point.
        assertEquals(11 + 2 * 9 + 4 * 4, DoodleCodec.encode(sample).size)
    }

    @Test
    fun textRoundTripsWithUnicodeAndKeepsOrderWithStrokes() {
        val d = Doodle(
            listOf(
                Stroke(floatArrayOf(0.1f, 0.1f, 0.2f, 0.2f), 1, 0.01f),
                TextMark("Miss you ❤️ — à bientôt! 你好", 0.5f, 0.25f, 0xFF1E88E5.toInt(), 0.07f),
                Stroke(floatArrayOf(0.3f, 0.3f), 2, 0.02f),
                TextMark("line one\nline two", 0.4f, 0.8f, 3, 0.045f),
            ),
            42,
        )
        val back = DoodleCodec.decode(DoodleCodec.encode(d))
        assertEquals(listOf(Stroke::class, TextMark::class, Stroke::class, TextMark::class), back.marks.map { it::class })
        val t = back.marks[1] as TextMark
        assertEquals("Miss you ❤️ — à bientôt! 你好", t.text)
        assertEquals(0.5f, t.x, 1e-4f)
        assertEquals(0.25f, t.y, 1e-4f)
        assertEquals(0xFF1E88E5.toInt(), t.color)
        assertEquals(0.07f, t.size, 1e-4f)
        assertEquals("line one\nline two", (back.marks[3] as TextMark).text)
    }

    @Test
    fun rejectsInvalidUtf8() {
        val bytes = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).use {
                it.writeByte(2); it.writeLong(0); it.writeShort(1)
                it.writeByte(1); it.writeInt(0); it.writeShort(0); it.writeShort(0); it.writeShort(0)
                it.writeShort(2); it.write(byteArrayOf(0xC3.toByte(), 0x28))
            }
        }.toByteArray()
        assertThrows(IOException::class.java) { DoodleCodec.decode(bytes) }
    }

    @Test
    fun rejectsTooMuchText() {
        val big = TextMark("x".repeat(DoodleCodec.MAX_TEXT_BYTES + 1), 0.5f, 0.5f, 0, 0.05f)
        assertThrows(IllegalArgumentException::class.java) { DoodleCodec.encode(Doodle(listOf(big), 0)) }
        val claimsTooMuch = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).use {
                it.writeByte(2); it.writeLong(0); it.writeShort(1)
                it.writeByte(1); it.writeInt(0); it.writeShort(0); it.writeShort(0); it.writeShort(0)
                it.writeShort(60_000)
            }
        }.toByteArray()
        assertThrows(IOException::class.java) { DoodleCodec.decode(claimsTooMuch) }
    }

    @Test
    fun rejectsUnknownMarkType() {
        val bytes = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).use { it.writeByte(2); it.writeLong(0); it.writeShort(1); it.writeByte(9) }
        }.toByteArray()
        assertThrows(IOException::class.java) { DoodleCodec.decode(bytes) }
    }

    @Test
    fun largestAllowedDoodleFitsTheTopPaddingBucket() {
        val strokes = (0 until 16).map { Stroke(FloatArray(1000) { 0.5f }, 0, 0.01f) } // 8000 points
        val texts = (0 until 5).map { TextMark("y".repeat(400), 0.5f, 0.5f, 0, 0.05f) }   // 2000 bytes
        val d = Doodle(strokes + texts, 0)
        val sealed = app.duoodo.sync.Envelope.seal(ByteArray(32), DoodleCodec.encode(d), ByteArray(0))
        assertTrue(sealed.size <= 49152 + 28)
    }

    @Test
    fun rejectsTruncatedData() {
        val bytes = DoodleCodec.encode(sample)
        for (len in 0 until bytes.size) {
            assertThrows(IOException::class.java) { DoodleCodec.decode(bytes.copyOf(len)) }
        }
    }

    @Test
    fun rejectsTrailingData() {
        assertThrows(IOException::class.java) { DoodleCodec.decode(DoodleCodec.encode(sample) + 0) }
    }

    @Test
    fun rejectsUnknownVersion() {
        val bytes = DoodleCodec.encode(sample).also { it[0] = 99 }
        assertThrows(IOException::class.java) { DoodleCodec.decode(bytes) }
    }

    @Test
    fun rejectsOversizedCountsBeforeAllocating() {
        val bytes = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).use {
                it.writeByte(2); it.writeLong(0); it.writeShort(1)
                it.writeByte(0); it.writeInt(0); it.writeShort(0); it.writeShort(65535)
            }
        }.toByteArray()
        // 65535 points claimed but none present: must fail cleanly, not hang or OOM.
        val e = assertThrows(IOException::class.java) { DoodleCodec.decode(bytes) }
        assertTrue(e.message!!.isNotEmpty())
    }

    @Test
    fun clampsOutOfRangeCoordinates() {
        val d = Doodle(listOf(Stroke(floatArrayOf(-1f, 2f), 0, 0.01f)), 0)
        val p = DoodleCodec.decode(DoodleCodec.encode(d)).strokes[0].points
        assertEquals(0f, p[0], 0f)
        assertEquals(1f, p[1], 0f)
    }
}
