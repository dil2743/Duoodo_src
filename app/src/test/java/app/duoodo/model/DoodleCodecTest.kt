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
        strokes = listOf(
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
        // 1 + 8 + 2 header, 8 per stroke, 4 per point.
        assertEquals(11 + 2 * 8 + 4 * 4, DoodleCodec.encode(sample).size)
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
                it.writeByte(1); it.writeLong(0); it.writeShort(1)
                it.writeInt(0); it.writeShort(0); it.writeShort(65535)
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
