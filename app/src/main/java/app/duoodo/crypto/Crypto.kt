package app.duoodo.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom

/** Thin wrappers over audited BouncyCastle primitives. Pure JVM, so they are unit-testable. */
object Crypto {
    private val random = SecureRandom()

    const val KEY_SIZE = 32
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

    fun sha256(vararg parts: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run { parts.forEach(::update); digest() }

    class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

    fun x25519KeyPair(): KeyPair {
        val priv = ByteArray(X25519.SCALAR_SIZE)
        X25519.generatePrivateKey(random, priv)
        val pub = ByteArray(X25519.POINT_SIZE)
        X25519.generatePublicKey(priv, 0, pub, 0)
        return KeyPair(priv, pub)
    }

    /** @throws GeneralSecurityException if the peer key is a low-order point (all-zero shared secret). */
    fun x25519(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(peerPublicKey.size == X25519.POINT_SIZE) { "bad public key size" }
        val out = ByteArray(X25519.POINT_SIZE)
        if (!X25519.calculateAgreement(privateKey, 0, peerPublicKey, 0, out, 0)) {
            throw GeneralSecurityException("invalid peer public key")
        }
        return out
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, length: Int = KEY_SIZE): ByteArray {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(ikm, salt, info.toByteArray()))
        return ByteArray(length).also { gen.generateBytes(it, 0, length) }
    }

    fun hmacSha256(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(key))
        parts.forEach { mac.update(it, 0, it.size) }
        return ByteArray(mac.macSize).also { mac.doFinal(it, 0) }
    }

    /** ChaCha20-Poly1305 with a random nonce. Output: nonce || ciphertext || tag. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val nonce = randomBytes(NONCE_SIZE)
        val aead = ChaCha20Poly1305()
        aead.init(true, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
        val out = ByteArray(NONCE_SIZE + aead.getOutputSize(plaintext.size))
        nonce.copyInto(out)
        val n = aead.processBytes(plaintext, 0, plaintext.size, out, NONCE_SIZE)
        aead.doFinal(out, NONCE_SIZE + n)
        return out
    }

    /** @throws GeneralSecurityException if the data was tampered with or the key is wrong. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < NONCE_SIZE + TAG_BITS / 8) throw GeneralSecurityException("too short")
        val aead = ChaCha20Poly1305()
        aead.init(false, AEADParameters(KeyParameter(key), TAG_BITS, sealed.copyOf(NONCE_SIZE), aad))
        val out = ByteArray(aead.getOutputSize(sealed.size - NONCE_SIZE))
        try {
            val n = aead.processBytes(sealed, NONCE_SIZE, sealed.size - NONCE_SIZE, out, 0)
            aead.doFinal(out, n)
        } catch (e: org.bouncycastle.crypto.InvalidCipherTextException) {
            throw GeneralSecurityException("authentication failed", e)
        }
        return out
    }
}
