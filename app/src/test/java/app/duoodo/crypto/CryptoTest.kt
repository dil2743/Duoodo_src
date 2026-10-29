package app.duoodo.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

class CryptoTest {
    @Test
    fun aeadRoundTripAndTamperDetection() {
        val key = Crypto.randomBytes(32)
        val aad = "ctx".toByteArray()
        val sealed = Crypto.seal(key, "hello".toByteArray(), aad)
        assertArrayEquals("hello".toByteArray(), Crypto.open(key, sealed, aad))

        val flipped = sealed.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }
        assertThrows(GeneralSecurityException::class.java) { Crypto.open(key, flipped, aad) }
        assertThrows(GeneralSecurityException::class.java) { Crypto.open(key, sealed, "other".toByteArray()) }
        assertThrows(GeneralSecurityException::class.java) { Crypto.open(Crypto.randomBytes(32), sealed, aad) }
        assertThrows(GeneralSecurityException::class.java) { Crypto.open(key, ByteArray(5), aad) }
    }

    @Test
    fun sealUsesFreshNonces() {
        val key = Crypto.randomBytes(32)
        assertFalse(Crypto.seal(key, ByteArray(8), ByteArray(0)).contentEquals(Crypto.seal(key, ByteArray(8), ByteArray(0))))
    }

    @Test
    fun x25519RfcVector() {
        // RFC 7748 section 6.1
        val alicePriv = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a".hexToBytes()
        val bobPub = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f".hexToBytes()
        assertEquals(
            "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742",
            Crypto.x25519(alicePriv, bobPub).toHex(),
        )
    }

    @Test
    fun x25519RejectsLowOrderPoint() {
        assertThrows(GeneralSecurityException::class.java) { Crypto.x25519(Crypto.x25519KeyPair().privateKey, ByteArray(32)) }
    }

    @Test
    fun schnorrBip340Vectors() {
        // BIP-340 test vectors 0 and 1.
        val sig0 = Schnorr.sign(
            "0000000000000000000000000000000000000000000000000000000000000003".hexToBytes(),
            ByteArray(32),
            ByteArray(32),
        )
        assertEquals(
            "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0",
            sig0.toHex(),
        )
        val priv1 = "b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef".hexToBytes()
        assertEquals("dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", Schnorr.publicKey(priv1).toHex())
        val msg1 = "243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89".hexToBytes()
        val aux1 = "0000000000000000000000000000000000000000000000000000000000000001".hexToBytes()
        val sig1 = Schnorr.sign(priv1, msg1, aux1)
        assertEquals(
            "6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de33418906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a",
            sig1.toHex(),
        )
        assertTrue(Schnorr.verify(Schnorr.publicKey(priv1), msg1, sig1))
    }

    @Test
    fun schnorrRejectsForgery() {
        val priv = Schnorr.privateKeyFrom(Crypto.randomBytes(32))
        val msg = Crypto.randomBytes(32)
        val sig = Schnorr.sign(priv, msg)
        assertTrue(Schnorr.verify(Schnorr.publicKey(priv), msg, sig))
        assertFalse(Schnorr.verify(Schnorr.publicKey(priv), Crypto.randomBytes(32), sig))
        assertFalse(Schnorr.verify(Schnorr.publicKey(Schnorr.privateKeyFrom(Crypto.randomBytes(32))), msg, sig))
        val bad = sig.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }
        assertFalse(Schnorr.verify(Schnorr.publicKey(priv), msg, bad))
    }
}
