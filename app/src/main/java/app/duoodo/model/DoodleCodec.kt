package app.duoodo.model

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Compact binary format for a [Doodle]. This is what gets encrypted and sent to the partner,
 * so [decode] treats its input as hostile: every count is bounded before anything is allocated.
 *
 * Layout (big-endian):
 *   u8 version | i64 createdAt | u16 strokeCount |
 *   per stroke: i32 argb | u16 width (fraction * 65535) | u16 pointCount | pointCount * (u16 x, u16 y)
 */
object DoodleCodec {
    // Limits keep the largest doodle (~36 KB) inside the top padding bucket of [app.duoodo.sync.Envelope].
    private const val VERSION = 1
    const val MAX_STROKES = 500
    const val MAX_TOTAL_POINTS = 8_000
    private const val Q = 65535f

    fun encode(doodle: Doodle): ByteArray {
        require(doodle.strokes.size <= MAX_STROKES) { "too many strokes" }
        require(doodle.strokes.sumOf { it.pointCount } <= MAX_TOTAL_POINTS) { "too many points" }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeLong(doodle.createdAtMillis)
            out.writeShort(doodle.strokes.size)
            for (s in doodle.strokes) {
                out.writeInt(s.color)
                out.writeShort(quantize(s.width))
                out.writeShort(s.pointCount)
                for (v in s.points) out.writeShort(quantize(v))
            }
        }
        return bytes.toByteArray()
    }

    /** @throws IOException if the data is malformed or exceeds limits. */
    fun decode(data: ByteArray): Doodle {
        val input = DataInputStream(data.inputStream())
        try {
            val version = input.readUnsignedByte()
            if (version != VERSION) throw IOException("unsupported version $version")
            val createdAt = input.readLong()
            val strokeCount = input.readUnsignedShort()
            if (strokeCount > MAX_STROKES) throw IOException("too many strokes")
            var totalPoints = 0
            val strokes = ArrayList<Stroke>(strokeCount)
            repeat(strokeCount) {
                val color = input.readInt()
                val width = input.readUnsignedShort() / Q
                val pointCount = input.readUnsignedShort()
                totalPoints += pointCount
                if (totalPoints > MAX_TOTAL_POINTS) throw IOException("too many points")
                val points = FloatArray(pointCount * 2) { input.readUnsignedShort() / Q }
                strokes += Stroke(points, color, width)
            }
            if (input.read() != -1) throw IOException("trailing data")
            return Doodle(strokes, createdAt)
        } catch (e: java.io.EOFException) {
            throw IOException("truncated doodle", e)
        }
    }

    private fun quantize(v: Float): Int = (v.coerceIn(0f, 1f) * Q).roundToInt()
}
