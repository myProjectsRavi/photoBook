package com.photobook.app.feature.vault

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the device-bound key envelope for Vault v2.
 *
 * The Android Keystore key never encrypts photo bytes directly. Instead it gates access to a
 * small Tink StreamingAead keyset. The resulting [VaultCryptoSession] exists only in memory after
 * a successful system authentication and is intended to be scoped to one user-approved Vault
 * operation.
 *
 * This class deliberately does not touch legacy EncryptedFile data. Legacy -> v2 migration is a
 * separate transactional step so a failed upgrade can never orphan existing Vault content.
 */
internal class VaultAuthCrypto(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val envelopeFile = File(appContext.filesDir, KEYSET_ENVELOPE_FILE)

    init {
        StreamingAeadConfig.register()
    }

    fun prepareAuthentication(): VaultAuthPreparation {
        return if (envelopeFile.exists()) {
            // Never synthesize a replacement key for an existing envelope. If Android Keystore
            // lost or invalidated the device-bound key, replacing it would make the persisted
            // keyset look merely corrupt and could hide a genuine recovery boundary.
            val wrappingKey = getExistingWrappingKey()
                ?: throw VaultKeyUnavailableException("Vault v2 device key is unavailable")
            val envelope = readEnvelope()
            val cipher = Cipher.getInstance(WRAP_TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    wrappingKey,
                    GCMParameterSpec(GCM_TAG_BITS, envelope.iv),
                )
            }
            VaultAuthPreparation.Unlock(cipher, envelope)
        } else {
            val wrappingKey = getOrCreateWrappingKey()
            val cipher = Cipher.getInstance(WRAP_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, wrappingKey)
            }
            VaultAuthPreparation.Enroll(cipher)
        }
    }

    fun completeAuthentication(
        preparation: VaultAuthPreparation,
        authenticatedCipher: Cipher,
    ): VaultCryptoSession {
        require(authenticatedCipher === preparation.cipher) {
            "Vault authentication must complete with the exact prepared CryptoObject cipher"
        }

        return when (preparation) {
            is VaultAuthPreparation.Enroll -> enrollNewKeyset(authenticatedCipher)
            is VaultAuthPreparation.Unlock -> unlockExistingKeyset(
                authenticatedCipher,
                preparation.envelope,
            )
        }
    }

    fun encrypt(
        session: VaultCryptoSession,
        input: InputStream,
        output: OutputStream,
        associatedData: ByteArray,
    ) {
        session.streamingAead.newEncryptingStream(output, associatedData).use { encrypted ->
            input.copyTo(encrypted)
        }
    }

    fun openDecryptedInput(
        session: VaultCryptoSession,
        encryptedFile: File,
        associatedData: ByteArray,
    ): InputStream {
        val source = FileInputStream(encryptedFile)
        return try {
            session.streamingAead.newDecryptingStream(source, associatedData)
        } catch (t: Throwable) {
            runCatching { source.close() }
            throw t
        }
    }

    fun associatedData(itemId: String): ByteArray {
        require(itemId.isNotBlank()) { "Vault item id must not be blank" }
        return "$VAULT_FILE_AAD_PREFIX$itemId".toByteArray(StandardCharsets.UTF_8)
    }

    fun isV2FileName(fileName: String): Boolean = fileName.endsWith(V2_FILE_SUFFIX)

    fun v2FileNameFor(legacyOrOriginalName: String): String {
        val stem = legacyOrOriginalName
            .removeSuffix(LEGACY_FILE_SUFFIX)
            .removeSuffix(V2_FILE_SUFFIX)
            .ifBlank { "PhotoBook" }
        return "$stem$V2_FILE_SUFFIX"
    }

    fun temporaryV2FileName(itemId: String): String {
        val safeId = itemId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        require(safeId.isNotBlank()) { "Vault item id cannot produce an empty temporary filename" }
        return ".$safeId$V2_FILE_SUFFIX.tmp"
    }

    private fun enrollNewKeyset(authenticatedCipher: Cipher): VaultCryptoSession {
        check(!envelopeFile.exists()) { "Vault v2 keyset already exists" }

        val handle = KeysetHandle.generateNew(
            PredefinedStreamingAeadParameters.AES256_GCM_HKDF_4KB,
        )
        val serialized = TinkJsonProtoKeysetFormat.serializeKeyset(
            handle,
            InsecureSecretKeyAccess.get(),
        ).toByteArray(StandardCharsets.UTF_8)

        authenticatedCipher.updateAAD(KEYSET_AAD)
        val wrapped = authenticatedCipher.doFinal(serialized)
        val iv = authenticatedCipher.iv
        check(iv != null && iv.isNotEmpty()) { "Android Keystore did not provide a GCM IV" }
        writeEnvelopeAtomically(WrappedKeysetEnvelope(iv = iv, ciphertext = wrapped))
        serialized.fill(0)

        return sessionFrom(handle)
    }

    private fun unlockExistingKeyset(
        authenticatedCipher: Cipher,
        envelope: WrappedKeysetEnvelope,
    ): VaultCryptoSession {
        authenticatedCipher.updateAAD(KEYSET_AAD)
        val serialized = authenticatedCipher.doFinal(envelope.ciphertext)
        return try {
            val handle = TinkJsonProtoKeysetFormat.parseKeyset(
                String(serialized, StandardCharsets.UTF_8),
                InsecureSecretKeyAccess.get(),
            )
            sessionFrom(handle)
        } finally {
            serialized.fill(0)
        }
    }

    private fun sessionFrom(handle: KeysetHandle): VaultCryptoSession {
        val primitive = handle.getPrimitive(
            RegistryConfiguration.get(),
            StreamingAead::class.java,
        )
        return VaultCryptoSession(primitive)
    }

    private fun getExistingWrappingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        getExistingWrappingKey()?.let { return it }

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
            // API 26-29 auth-per-use crypto can only be unlocked by a biometric. Keeping the
            // wrapping key valid when the biometric set changes avoids needless Vault data loss;
            // every use still requires a currently enrolled strong biometric.
            builder.setInvalidatedByBiometricEnrollment(false)
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun readEnvelope(): WrappedKeysetEnvelope {
        DataInputStream(BufferedInputStream(FileInputStream(envelopeFile))).use { input ->
            val magic = input.readInt()
            if (magic != ENVELOPE_MAGIC) throw IOException("Unknown Vault keyset envelope")
            val version = input.readInt()
            if (version != ENVELOPE_VERSION) throw IOException("Unsupported Vault keyset version: $version")

            val ivLength = input.readInt()
            if (ivLength !in 1..MAX_IV_BYTES) throw IOException("Invalid Vault keyset IV length")
            val iv = ByteArray(ivLength)
            input.readFully(iv)

            val ciphertextLength = input.readInt()
            if (ciphertextLength !in 1..MAX_WRAPPED_KEYSET_BYTES) {
                throw IOException("Invalid Vault wrapped-keyset length")
            }
            val ciphertext = ByteArray(ciphertextLength)
            input.readFully(ciphertext)
            if (input.read() != -1) throw IOException("Unexpected trailing Vault keyset data")
            return WrappedKeysetEnvelope(iv = iv, ciphertext = ciphertext)
        }
    }

    private fun writeEnvelopeAtomically(envelope: WrappedKeysetEnvelope) {
        check(envelope.iv.isNotEmpty() && envelope.iv.size <= MAX_IV_BYTES)
        check(envelope.ciphertext.isNotEmpty() && envelope.ciphertext.size <= MAX_WRAPPED_KEYSET_BYTES)

        val temp = File(envelopeFile.parentFile, "${envelopeFile.name}.tmp")
        runCatching { temp.delete() }
        try {
            val fileOutput = FileOutputStream(temp)
            DataOutputStream(BufferedOutputStream(fileOutput)).use { output ->
                output.writeInt(ENVELOPE_MAGIC)
                output.writeInt(ENVELOPE_VERSION)
                output.writeInt(envelope.iv.size)
                output.write(envelope.iv)
                output.writeInt(envelope.ciphertext.size)
                output.write(envelope.ciphertext)
                output.flush()
                fileOutput.fd.sync()
            }
            Os.rename(temp.absolutePath, envelopeFile.absolutePath)
        } catch (t: Throwable) {
            runCatching { temp.delete() }
            throw t
        }
    }

    internal companion object {
        const val V2_FILE_SUFFIX = ".pbvault2"
        const val LEGACY_FILE_SUFFIX = ".pbvault"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "photobook_vault_wrap_v2"
        private const val KEYSET_ENVELOPE_FILE = "vault_keyset_v2.pbkey"
        private const val WRAP_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val ENVELOPE_MAGIC = 0x50425632 // PBV2
        private const val ENVELOPE_VERSION = 1
        private const val MAX_IV_BYTES = 32
        private const val MAX_WRAPPED_KEYSET_BYTES = 64 * 1024
        private const val VAULT_FILE_AAD_PREFIX = "photobook-vault:v2:"
        private val KEYSET_AAD = "photobook-vault:keyset:v2".toByteArray(StandardCharsets.UTF_8)
    }
}

internal sealed interface VaultAuthPreparation {
    val cipher: Cipher

    data class Enroll(
        override val cipher: Cipher,
    ) : VaultAuthPreparation

    data class Unlock(
        override val cipher: Cipher,
        internal val envelope: WrappedKeysetEnvelope,
    ) : VaultAuthPreparation
}

internal class VaultCryptoSession(
    internal val streamingAead: StreamingAead,
)

internal data class WrappedKeysetEnvelope(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

internal class VaultKeyUnavailableException(
    message: String,
) : IllegalStateException(message)
