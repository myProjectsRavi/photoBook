package com.photobook.app.feature.vault

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Android Keystore operation that protects PhotoBook Vault v2 key material.
 *
 * The wrapping key is authentication-per-use. Callers must pass [cryptoObject] to
 * [BiometricPrompt.authenticate] and complete the matching request only with the CryptoObject
 * returned by a successful authentication callback. Clear Vault key material is never persisted
 * by this class.
 *
 * This boundary deliberately does not know about Vault media files or Tink. It only protects the
 * small in-memory data-encryption keyset that a later Vault session uses for streaming file crypto.
 */
@Singleton
class VaultAuthKeyEnvelope @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    sealed interface AuthenticationRequest {
        val cryptoObject: BiometricPrompt.CryptoObject
        val promptAuthenticators: Int

        data class Create internal constructor(
            override val cryptoObject: BiometricPrompt.CryptoObject,
            override val promptAuthenticators: Int,
            internal val iv: ByteArray,
        ) : AuthenticationRequest

        data class Open internal constructor(
            override val cryptoObject: BiometricPrompt.CryptoObject,
            override val promptAuthenticators: Int,
            internal val iv: ByteArray,
            internal val ciphertext: ByteArray,
        ) : AuthenticationRequest
    }

    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun prepareAuthentication(): AuthenticationRequest {
        val envelopeExists = envelopeFile.exists()
        val key = getWrappingKey(createIfMissing = !envelopeExists)
        return if (envelopeExists) {
            val envelope = readEnvelope()
            val cipher = newCipher().apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, envelope.iv))
                updateAAD(KEYSET_AAD)
            }
            AuthenticationRequest.Open(
                cryptoObject = BiometricPrompt.CryptoObject(cipher),
                promptAuthenticators = promptAuthenticators(),
                iv = envelope.iv,
                ciphertext = envelope.ciphertext,
            )
        } else {
            val cipher = newCipher().apply {
                init(Cipher.ENCRYPT_MODE, key)
                updateAAD(KEYSET_AAD)
            }
            val iv = cipher.iv?.copyOf()
                ?: throw GeneralSecurityException("Android Keystore did not provide a GCM IV")
            validateIv(iv)
            AuthenticationRequest.Create(
                cryptoObject = BiometricPrompt.CryptoObject(cipher),
                promptAuthenticators = promptAuthenticators(),
                iv = iv,
            )
        }
    }

    /**
     * Completes first-time keyset creation after successful biometric/device authentication.
     * The caller remains responsible for zeroing [secret] after this method returns.
     */
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun completeCreate(
        request: AuthenticationRequest.Create,
        authenticatedCryptoObject: BiometricPrompt.CryptoObject,
        secret: ByteArray,
    ) {
        require(secret.isNotEmpty()) { "Vault key material must not be empty" }
        require(!envelopeFile.exists()) { "Vault key envelope already exists" }

        val cipher = authenticatedCipher(request.iv, authenticatedCryptoObject)
        val ciphertext = try {
            cipher.doFinal(secret)
        } catch (error: KeyPermanentlyInvalidatedException) {
            throw VaultWrappingKeyInvalidatedException(error)
        }
        require(ciphertext.isNotEmpty()) { "Vault key envelope ciphertext must not be empty" }
        writeEnvelopeAtomically(Envelope(request.iv, ciphertext))
    }

    /** Completes an existing keyset unwrap after successful user authentication. */
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun completeOpen(
        request: AuthenticationRequest.Open,
        authenticatedCryptoObject: BiometricPrompt.CryptoObject,
    ): ByteArray {
        val cipher = authenticatedCipher(request.iv, authenticatedCryptoObject)
        return try {
            cipher.doFinal(request.ciphertext)
        } catch (error: KeyPermanentlyInvalidatedException) {
            throw VaultWrappingKeyInvalidatedException(error)
        }
    }

    fun hasEnvelope(): Boolean = envelopeFile.exists()

    /**
     * The prompt must request the same authentication classes permitted by the wrapping key.
     * Device-credential CryptoObject authentication is supported only on API 30+.
     */
    fun promptAuthenticators(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }

    private fun authenticatedCipher(
        expectedIv: ByteArray,
        cryptoObject: BiometricPrompt.CryptoObject,
    ): Cipher {
        val cipher = cryptoObject.cipher
            ?: throw GeneralSecurityException("Biometric result did not return the Vault cipher")
        val actualIv = cipher.iv
            ?: throw GeneralSecurityException("Authenticated Vault cipher has no IV")
        if (!actualIv.contentEquals(expectedIv)) {
            throw GeneralSecurityException("Biometric result returned a different Vault operation")
        }
        return cipher
    }

    private fun getWrappingKey(createIfMissing: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing != null) {
            return existing as? SecretKey
                ?: throw GeneralSecurityException("Vault wrapping key has unexpected type")
        }
        if (!createIfMissing) {
            throw VaultWrappingKeyUnavailableException()
        }

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            // Vault data must survive normal biometric enrollment changes. The key still requires
            // a currently enrolled strong biometric on API 26-29, and strong biometric or device
            // credential on API 30+ for every unwrap operation.
            .setInvalidatedByBiometricEnrollment(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(builder.build())
            generateKey()
        }
    }

    private fun newCipher(): Cipher = Cipher.getInstance(TRANSFORMATION)

    private fun readEnvelope(): Envelope {
        val length = envelopeFile.length()
        if (length !in MIN_ENVELOPE_BYTES..MAX_ENVELOPE_BYTES) {
            throw VaultKeyEnvelopeCorruptException("Vault key envelope has invalid size")
        }

        val bytes = envelopeFile.readBytes()
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val magic = ByteArray(MAGIC.size)
                input.readFully(magic)
                if (!magic.contentEquals(MAGIC)) {
                    throw VaultKeyEnvelopeCorruptException("Vault key envelope magic mismatch")
                }
                val version = input.readUnsignedByte()
                if (version != FORMAT_VERSION) {
                    throw VaultKeyEnvelopeCorruptException("Unsupported Vault key envelope version")
                }
                val ivLength = input.readUnsignedByte()
                if (ivLength !in MIN_IV_BYTES..MAX_IV_BYTES) {
                    throw VaultKeyEnvelopeCorruptException("Vault key envelope IV is invalid")
                }
                val iv = ByteArray(ivLength)
                input.readFully(iv)
                validateIv(iv)

                val ciphertextLength = input.readInt()
                if (ciphertextLength !in 1..MAX_CIPHERTEXT_BYTES) {
                    throw VaultKeyEnvelopeCorruptException("Vault key envelope ciphertext is invalid")
                }
                if (ciphertextLength != input.available()) {
                    throw VaultKeyEnvelopeCorruptException("Vault key envelope length mismatch")
                }
                val ciphertext = ByteArray(ciphertextLength)
                input.readFully(ciphertext)
                if (input.available() != 0) {
                    throw VaultKeyEnvelopeCorruptException("Vault key envelope has trailing data")
                }
                return Envelope(iv, ciphertext)
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun writeEnvelopeAtomically(envelope: Envelope) {
        validateIv(envelope.iv)
        if (envelope.ciphertext.size !in 1..MAX_CIPHERTEXT_BYTES) {
            throw VaultKeyEnvelopeCorruptException("Vault key envelope ciphertext is invalid")
        }

        val encoded = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(MAGIC)
                output.writeByte(FORMAT_VERSION)
                output.writeByte(envelope.iv.size)
                output.write(envelope.iv)
                output.writeInt(envelope.ciphertext.size)
                output.write(envelope.ciphertext)
            }
            buffer.toByteArray()
        }
        if (encoded.size > MAX_ENVELOPE_BYTES) {
            encoded.fill(0)
            throw VaultKeyEnvelopeCorruptException("Vault key envelope exceeds maximum size")
        }

        envelopeFile.parentFile?.mkdirs()
        val atomicFile = AtomicFile(envelopeFile)
        val output = atomicFile.startWrite()
        try {
            output.write(encoded)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        } finally {
            encoded.fill(0)
        }
    }

    private fun validateIv(iv: ByteArray) {
        if (iv.size !in MIN_IV_BYTES..MAX_IV_BYTES) {
            throw VaultKeyEnvelopeCorruptException("Vault key envelope IV is invalid")
        }
    }

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.PRIVATE)
    internal fun clearForTesting() {
        AtomicFile(envelopeFile).delete()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private data class Envelope(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "photobook_vault_auth_wrap_v2"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val FORMAT_VERSION = 1
        private const val MIN_IV_BYTES = 12
        private const val MAX_IV_BYTES = 32
        private const val MAX_CIPHERTEXT_BYTES = 64 * 1024
        private const val MIN_ENVELOPE_BYTES = 4L + 1L + 1L + MIN_IV_BYTES + 4L + 1L
        private const val MAX_ENVELOPE_BYTES = 4L + 1L + 1L + MAX_IV_BYTES + 4L + MAX_CIPHERTEXT_BYTES
        private val MAGIC = byteArrayOf('P'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '2'.code.toByte())
        private val KEYSET_AAD = "photobook-vault:keyset:v2".encodeToByteArray()

        internal fun envelopePath(context: Context): File =
            File(File(context.filesDir, "vault_store"), "vault_keyset_v2.pbk")
    }

    private val envelopeFile: File
        get() = envelopePath(context)
}

class VaultWrappingKeyInvalidatedException(cause: Throwable) :
    GeneralSecurityException("Vault authentication key is no longer valid", cause)

class VaultWrappingKeyUnavailableException :
    GeneralSecurityException("Vault key envelope exists but its Android Keystore key is unavailable")

class VaultKeyEnvelopeCorruptException(message: String) : GeneralSecurityException(message)
