package app.duoodo.crypto

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

/** BIP-340 Schnorr signatures over secp256k1, as required for Nostr event signing. */
object Schnorr {
    private val curve = SECNamedCurves.getByName("secp256k1")
    private val G: ECPoint = curve.g
    private val n: BigInteger = curve.n
    private val p: BigInteger = curve.curve.field.characteristic

    /** Maps arbitrary 32 bytes to a valid private key (1..n-1). */
    fun privateKeyFrom(seed: ByteArray): ByteArray {
        val d = BigInteger(1, seed).mod(n.subtract(BigInteger.ONE)).add(BigInteger.ONE)
        return d.to32()
    }

    fun publicKey(privateKey: ByteArray): ByteArray = G.multiply(BigInteger(1, privateKey)).normalize().xOnly()

    fun sign(privateKey: ByteArray, message: ByteArray, auxRand: ByteArray = Crypto.randomBytes(32)): ByteArray {
        val d0 = BigInteger(1, privateKey)
        require(d0.signum() > 0 && d0 < n) { "invalid private key" }
        val P = G.multiply(d0).normalize()
        val d = if (P.hasEvenY()) d0 else n.subtract(d0)
        val px = P.xOnly()
        val t = xor(d.to32(), taggedHash("BIP0340/aux", auxRand))
        val k0 = BigInteger(1, taggedHash("BIP0340/nonce", t, px, message)).mod(n)
        check(k0.signum() != 0)
        val R = G.multiply(k0).normalize()
        val k = if (R.hasEvenY()) k0 else n.subtract(k0)
        val rx = R.xOnly()
        val e = BigInteger(1, taggedHash("BIP0340/challenge", rx, px, message)).mod(n)
        return rx + k.add(e.multiply(d)).mod(n).to32()
    }

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val P = liftX(BigInteger(1, publicKey)) ?: return false
        val r = BigInteger(1, signature.copyOfRange(0, 32))
        val s = BigInteger(1, signature.copyOfRange(32, 64))
        if (r >= p || s >= n) return false
        val e = BigInteger(1, taggedHash("BIP0340/challenge", signature.copyOfRange(0, 32), publicKey, message)).mod(n)
        val R = G.multiply(s).add(P.multiply(n.subtract(e))).normalize()
        return !R.isInfinity && R.hasEvenY() && R.affineXCoord.toBigInteger() == r
    }

    private fun liftX(x: BigInteger): ECPoint? {
        if (x >= p) return null
        return try {
            curve.curve.decodePoint(byteArrayOf(0x02) + x.to32())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun taggedHash(tag: String, vararg data: ByteArray): ByteArray {
        val tagHash = Crypto.sha256(tag.toByteArray())
        return Crypto.sha256(tagHash, tagHash, *data)
    }

    private fun ECPoint.hasEvenY(): Boolean = !affineYCoord.toBigInteger().testBit(0)
    private fun ECPoint.xOnly(): ByteArray = affineXCoord.toBigInteger().to32()

    private fun BigInteger.to32(): ByteArray {
        val b = toByteArray()
        return when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
    }

    private fun xor(a: ByteArray, b: ByteArray) = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }
}
