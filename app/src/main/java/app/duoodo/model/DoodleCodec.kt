package app.duoodo.model

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.math.roundToInt

/**
 * Compact binary format for a [Doodle]. This is what gets encrypted and sent to the partner,
 * so [decode] treats its input as hostile: every count is bounded before anything is allocated.
 *
 * Layout (big-endian):
 *   u8 version | i64 createdAt | u16 markCount | markCount * mark
 *   stroke: u8 0 | i32 argb | u16 width | u16 pointCount | pointCount * (u16 x, u16 y)
 *   text:   u8 1 | i32 argb | u16 size | u16 x | u16 y | u16 byteLength | UTF-8 bytes
 * Fractions (coordinates, width, size) are quantized to u16 as value * 65535.
 */
object DoodleCodec {
    // Limits keep the largest doodle (~41 KB) inside the top padding bucket of [app.duoodo.sync.Envelope].
    private const val VERSION = 3
    const val MAX_MARKS = 500
    const val MAX_TOTAL_POINTS = 8_000
    const val MAX_TEXT_BYTES = 400
    const val MAX_TOTAL_TEXT_BYTES = 2_000
    const val MAX_IMAGE_BYTES = 40_000
    const val MAX_TOTAL_IMAGE_BYTES = 45_000
    private const val TYPE_STROKE = 0
    private const val TYPE_TEXT = 1
    private const val TYPE_IMAGE = 2
    private const val Q = 65535f

    fun encode(doodle: Doodle): ByteArray {
        require(doodle.marks.size <= MAX_MARKS) { "too many marks" }
        require(doodle.strokes.sumOf { it.pointCount } <= MAX_TOTAL_POINTS) { "too many points" }
        val texts = doodle.texts.map { it.text.toByteArray(Charsets.UTF_8) }
        require(texts.all { it.size <= MAX_TEXT_BYTES } && texts.sumOf { it.size } <= MAX_TOTAL_TEXT_BYTES) { "too much text" }
        require(doodle.images.all { it.bytes.size <= MAX_IMAGE_BYTES } && doodle.images.sumOf { it.bytes.size } <= MAX_TOTAL_IMAGE_BYTES) { "too much image data" }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeLong(doodle.createdAtMillis)
            out.writeShort(doodle.marks.size)
            for (m in doodle.marks) when (m) {
                is Stroke -> {
                    out.writeByte(TYPE_STROKE)
                    out.writeInt(m.color)
                    out.writeShort(quantize(m.width))
                    out.writeShort(m.pointCount)
                    for (v in m.points) out.writeShort(quantize(v))
                }
                is TextMark -> {
                    val utf8 = m.text.toByteArray(Charsets.UTF_8)
                    out.writeByte(TYPE_TEXT)
                    out.writeInt(m.color)
                    out.writeShort(quantize(m.size))
                    out.writeShort(quantize(m.x))
                    out.writeShort(quantize(m.y))
                    out.writeShort(quantizeRotation(m.rotation))
                    out.writeShort(utf8.size)
                    out.write(utf8)
                }
                is ImageMark -> {
                    out.writeByte(TYPE_IMAGE)
                    out.writeShort(quantize(m.x))
                    out.writeShort(quantize(m.y))
                    out.writeShort(quantize(m.scale / 10f))
                    out.writeShort(quantizeRotation(m.rotation))
                    out.writeInt(m.bytes.size)
                    out.write(m.bytes)
                }
            }
        }
        return bytes.toByteArray()
    }

    /** @throws IOException if the data is malformed or exceeds limits. */
    fun decode(data: ByteArray): Doodle {
        val input = DataInputStream(data.inputStream())
        try {
            val version = input.readUnsignedByte()
            if (version !in 2..VERSION) throw IOException("unsupported version $version")
            val createdAt = input.readLong()
            val markCount = input.readUnsignedShort()
            if (markCount > MAX_MARKS) throw IOException("too many marks")
            var totalPoints = 0
            var totalTextBytes = 0
            var totalImageBytes = 0
            val marks = ArrayList<Mark>(markCount)
            repeat(markCount) {
                when (input.readUnsignedByte()) {
                    TYPE_STROKE -> {
                        val color = input.readInt()
                        val width = input.readUnsignedShort() / Q
                        val pointCount = input.readUnsignedShort()
                        totalPoints += pointCount
                        if (totalPoints > MAX_TOTAL_POINTS) throw IOException("too many points")
                        val points = FloatArray(pointCount * 2) { input.readUnsignedShort() / Q }
                        marks += Stroke(points, color, width)
                    }
                    TYPE_TEXT -> {
                        val color = input.readInt()
                        val size = input.readUnsignedShort() / Q
                        val x = input.readUnsignedShort() / Q
                        val y = input.readUnsignedShort() / Q
                        val rotation = if (version >= 3) dequantizeRotation(input.readUnsignedShort()) else 0f
                        val length = input.readUnsignedShort()
                        totalTextBytes += length
                        if (length > MAX_TEXT_BYTES || totalTextBytes > MAX_TOTAL_TEXT_BYTES) throw IOException("too much text")
                        val utf8 = ByteArray(length).also(input::readFully)
                        marks += TextMark(decodeUtf8(utf8), x, y, color, size, rotation)
                    }
                    TYPE_IMAGE -> {
                        if (version < 3) throw IOException("image not supported in version $version")
                        val x = input.readUnsignedShort() / Q
                        val y = input.readUnsignedShort() / Q
                        val scale = (input.readUnsignedShort() / Q) * 10f
                        val rotation = dequantizeRotation(input.readUnsignedShort())
                        val length = input.readInt()
                        totalImageBytes += length
                        if (length > MAX_IMAGE_BYTES || totalImageBytes > MAX_TOTAL_IMAGE_BYTES) throw IOException("too much image data")
                        val imageBytes = ByteArray(length).also(input::readFully)
                        marks += ImageMark(imageBytes, x, y, scale, rotation)
                    }
                    else -> throw IOException("unknown mark type")
                }
            }
            if (input.read() != -1) throw IOException("trailing data")
            return Doodle(marks, createdAt)
        } catch (e: java.io.EOFException) {
            throw IOException("truncated doodle", e)
        }
    }

    /** Strict decoding: malformed UTF-8 is rejected rather than silently replaced. */
    private fun decodeUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        throw IOException("invalid text", e)
    }

    private fun quantize(v: Float): Int = (v.coerceIn(0f, 1f) * Q).roundToInt()
    private fun quantizeRotation(deg: Float): Int = ((((deg % 360f) + 360f) % 360f) / 360f * Q).roundToInt()
    private fun dequantizeRotation(v: Int): Float = (v / Q) * 360f
}
