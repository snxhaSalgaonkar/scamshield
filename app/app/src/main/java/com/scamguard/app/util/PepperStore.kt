package com.scamguard.app.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the hashing pepper in no-backup storage, encrypted with an AES-GCM key that lives
 * in the Android Keystore and never leaves it. If the key is lost (e.g. after a restore to
 * a new phone) a new pepper is made; old hashes then simply stop matching.
 */
object PepperStore {
    private const val KEY_ALIAS = "scg_pepper_key"
    private const val FILE = "pepper.bin"
    private const val IV_LEN = 12

    fun loadOrCreate(context: Context): String {
        val file = File(context.noBackupFilesDir, FILE)
        return try {
            if (file.exists()) decrypt(file.readBytes()) else create(file)
        } catch (_: GeneralSecurityException) {
            create(file)
        } catch (_: java.io.IOException) {
            create(file)
        }
    }

    /** Forgets the pepper ("delete all my data"); every stored hash becomes unlinkable. */
    fun reset(context: Context): String = create(File(context.noBackupFilesDir, FILE))

    private fun create(file: File): String {
        val pepper = Hashing.randomPepper()
        try {
            file.writeBytes(encrypt(pepper))
        } catch (_: GeneralSecurityException) {
            // Keystore unavailable: the pepper still works for this run; a new one is made next start.
        }
        return pepper
    }

    private fun encrypt(pepper: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(pepper.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(data: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_LEN))
        return String(cipher.doFinal(data, IV_LEN, data.size - IV_LEN), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
