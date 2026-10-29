package app.duoodo.crypto

import java.util.Base64

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "odd hex length" }
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun ByteArray.toBase64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
fun String.fromBase64Url(): ByteArray = Base64.getUrlDecoder().decode(this)
fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
