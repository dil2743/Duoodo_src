package app.duoodo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small secrets (pairing keys, sync state) encrypted at rest with a non-exportable Android
 * Keystore key. Files live in no-backup app storage, so they never leave the device.
 */
class SecretStore(context: Context) {
    private val dir = File(context.noBackupFilesDir, "secrets").apply { mkdirs() }

    fun get(name: String): ByteArray? {
        val file = AtomicFile(File(dir, name))
        val data = try {
            file.readFully()
        } catch (e: IOException) {
            return null
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_SIZE))
            cipher.updateAAD(name.toByteArray())
            cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    fun put(name: String, value: ByteArray?) {
        val file = AtomicFile(File(dir, name))
        if (value == null) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray())
        val sealed = cipher.iv + cipher.doFinal(value)
        val out = file.startWrite()
        try {
            out.write(sealed)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "duoodo-secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
