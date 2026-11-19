package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedBlob(
    val ciphertextBase64: String,
    val ivBase64: String
)

data class PasswordDigest(
    val hashBase64: String,
    val saltBase64: String
)

object CryptoManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val LOCAL_ALIAS = "kasirpro_local_wrap_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_BITS = 256
    private const val VERIFIER_PLAINTEXT = "KASIRPRO_E2EE_VERIFIED_V1"

    private val secureRandom = SecureRandom()

    fun generateSaltBase64(bytes: Int = 16): String {
        val salt = ByteArray(bytes)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, saltBase64: String = generateSaltBase64()): PasswordDigest {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return PasswordDigest(
            hashBase64 = Base64.encodeToString(derived, Base64.NO_WRAP),
            saltBase64 = saltBase64
        )
    }

    fun verifyPassword(password: String, expectedHashBase64: String, saltBase64: String): Boolean {
        if (password.isEmpty() || expectedHashBase64.isEmpty() || saltBase64.isEmpty()) return false
        return try {
            val actual = hashPassword(password, saltBase64).hashBase64
            MessageDigest.isEqual(
                actual.toByteArray(StandardCharsets.UTF_8),
                expectedHashBase64.toByteArray(StandardCharsets.UTF_8)
            )
        } catch (_: Exception) {
            false
        }
    }

    fun deriveE2eeKey(passphrase: String, saltBase64: String): SecretKey {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encoded = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(encoded, "AES")
    }

    fun computeKeyVerifier(key: SecretKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(VERIFIER_PLAINTEXT.toByteArray(StandardCharsets.UTF_8))
        digest.update(key.encoded)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun keyFingerprint(key: SecretKey): String {
        val hash = sha256HexBytes(key.encoded)
        return hash.take(16).uppercase().chunked(4).joinToString("-")
    }

    fun encrypt(plaintext: String, key: SecretKey): EncryptedBlob {
        val iv = ByteArray(GCM_IV_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return EncryptedBlob(
            ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(ciphertextBase64: String, ivBase64: String, key: SecretKey): String? {
        if (ciphertextBase64.isBlank() || ivBase64.isBlank()) return null
        return try {
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plain = cipher.doFinal(cipherBytes)
            String(plain, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun sha256Hex(text: String): String =
        sha256HexBytes(text.toByteArray(StandardCharsets.UTF_8))

    private fun sha256HexBytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // Android Keystore wrapping for persisting unlocked vault passphrase locally on trusted device
    fun encryptWithDeviceKeystore(plaintext: String): EncryptedBlob? {
        return try {
            val key = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            EncryptedBlob(
                ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun decryptWithDeviceKeystore(ciphertextBase64: String, ivBase64: String): String? {
        return try {
            val key = getOrCreateKeystoreKey()
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(LOCAL_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                LOCAL_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
