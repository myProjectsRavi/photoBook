package com.photobook.app.feature.vault

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.system.Os
import androidx.biometric.BiometricManager
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-bound key envelope for Vault v2.
 *
 * Android 11+ can bind one auth-per-use Keystore key to strong biometric OR device credential.
 * Android 8-10 cannot. On those releases PhotoBook therefore uses two independent wraps of the
 * same Tink keyset:
 *
 * 1. a normal auth-per-use biometric key which is invalidated when biometrics change; and
 * 2. a short-lived secure-lock-screen recovery key used only after Android confirms the device
 *    credential.
 *
 * The recovery wrap lets PhotoBook regenerate the biometric key after enrollment changes without
 * ever persisting the clear keyset. A recovery-only envelope is a deliberate crash-safe state: if
 * re-binding is cancelled or the process dies, the next unlock returns to device-credential
 * recovery rather than trying a mismatched primary key.
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
        if (!envelopeFile.exists()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                throw VaultCredentialSetupRequiredException()
            }
            val key = getOrCreatePrimaryKey(KeyPolicy.R_PLUS_COMBINED)
            val cipher = Cipher.getInstance(WRAP_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
            return VaultAuthPreparation.EnrollRPlus(cipher)
        }

        val envelope = readEnvelope()
        val primary = envelope.primary ?: throw VaultCredentialRecoveryRequiredException()
        val key = getExistingKey(PRIMARY_KEY_ALIAS)
            ?: if (envelope.mode == EnvelopeMode.PRE_R_DUAL) {
                throw VaultCredentialRecoveryRequiredException()
            } else {
                throw VaultKeyUnavailableException("Vault v2 device key is unavailable")
            }

        val cipher = try {
            Cipher.getInstance(WRAP_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, primary.iv))
            }
        } catch (error: KeyPermanentlyInvalidatedException) {
            if (envelope.mode == EnvelopeMode.PRE_R_DUAL) {
                throw VaultCredentialRecoveryRequiredException(error)
            }
            throw VaultKeyUnavailableException("Vault v2 device key was invalidated", error)
        } catch (error: InvalidKeyException) {
            if (envelope.mode == EnvelopeMode.PRE_R_DUAL) {
                throw VaultCredentialRecoveryRequiredException(error)
            }
            throw error
        }

        return VaultAuthPreparation.Unlock(
            cipher = cipher,
            envelope = envelope,
            promptAuthenticators = when (envelope.mode) {
                EnvelopeMode.R_PLUS_COMBINED -> R_PLUS_AUTHENTICATORS
                EnvelopeMode.PRE_R_DUAL -> BiometricManager.Authenticators.BIOMETRIC_STRONG
            },
        )
    }

    /**
     * First-time API 26-29 setup after Android has confirmed the secure lock-screen credential.
     *
     * The recovery-only envelope is committed before a biometric rebind is attempted. This makes
     * cancellation/process death recoverable and never leaves a persisted clear keyset.
     */
    fun preparePreREnrollmentAfterCredential(): VaultPreRCredentialResult {
        check(Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            "Pre-R Vault enrollment is only valid below Android 11"
        }
        check(!envelopeFile.exists()) { "Vault key envelope already exists" }

        val handle = KeysetHandle.generateNew(
            PredefinedStreamingAeadParameters.AES256_GCM_HKDF_4KB,
        )
        val serialized = serializeKeyset(handle)
        return try {
            val recovery = wrapWithRecoveryKey(
                serialized = serialized,
                key = getOrCreateRecoveryKey(),
            )
            val recoveryOnly = WrappedKeysetEnvelope(
                mode = EnvelopeMode.PRE_R_DUAL,
                primary = null,
                recovery = recovery,
            )
            writeEnvelopeAtomically(recoveryOnly)
            preparePrimaryRebindOrCredentialOnly(
                serialized = serialized,
                recovery = recovery,
                existingSessionHandle = handle,
            )
        } catch (error: Throwable) {
            serialized.fill(0)
            throw error
        }
    }

    /**
     * API 26-29 recovery after Android has confirmed the secure lock-screen credential.
     *
     * This also handles a PRE_R_DUAL envelope on a device that has since upgraded to Android 11+.
     * In that case a successful rebind converts the envelope to the modern combined-auth policy.
     */
    fun preparePreRRecoveryAfterCredential(): VaultPreRCredentialResult {
        val envelope = readEnvelope()
        check(envelope.mode == EnvelopeMode.PRE_R_DUAL) {
            "Device-credential recovery is only valid for a pre-R Vault envelope"
        }
        val recovery = envelope.recovery
            ?: throw VaultKeyUnavailableException("Vault recovery envelope is unavailable")
        val key = getExistingKey(RECOVERY_KEY_ALIAS)
            ?: throw VaultKeyUnavailableException("Vault recovery key is unavailable")

        val serialized = unwrapWithRecoveryKey(recovery, key)
        return try {
            // Recovery is authoritative from this point. Persist that state before replacing the
            // primary alias so cancellation or process death cannot strand the envelope with a
            // ciphertext that belongs to an old/invalid primary key.
            writeEnvelopeAtomically(envelope.copy(primary = null))
            deleteKey(PRIMARY_KEY_ALIAS)
            preparePrimaryRebindOrCredentialOnly(
                serialized = serialized,
                recovery = recovery,
                existingSessionHandle = null,
            )
        } catch (error: Throwable) {
            serialized.fill(0)
            throw error
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
            is VaultAuthPreparation.EnrollRPlus -> enrollRPlus(authenticatedCipher)
            is VaultAuthPreparation.Unlock -> unlockExistingKeyset(
                authenticatedCipher = authenticatedCipher,
                envelope = preparation.envelope,
            )
            is VaultAuthPreparation.RebindPreR -> completePrimaryRebind(
                preparation = preparation,
                authenticatedCipher = authenticatedCipher,
            )
        }
    }

    fun cancelAuthentication(preparation: VaultAuthPreparation) {
        if (preparation is VaultAuthPreparation.RebindPreR) {
            preparation.serialized.fill(0)
        }
    }

    fun encryptToFile(
        session: VaultCryptoSession,
        input: InputStream,
        target: File,
        associatedData: ByteArray,
    ) {
        target.parentFile?.let { parent ->
            check(parent.exists() || parent.mkdirs()) { "Unable to create Vault directory" }
        }

        val fileOutput = FileOutputStream(target)
        try {
            val nonClosingOutput = object : FilterOutputStream(fileOutput) {
                override fun close() {
                    flush()
                }
            }
            session.streamingAead.newEncryptingStream(nonClosingOutput, associatedData).use { encrypted ->
                input.copyTo(encrypted)
            }
            fileOutput.fd.sync()
        } finally {
            runCatching { fileOutput.close() }
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
        } catch (error: Throwable) {
            runCatching { source.close() }
            throw error
        }
    }

    fun associatedData(itemId: String): ByteArray {
        require(itemId.isNotBlank()) { "Vault item id must not be blank" }
        return "$VAULT_FILE_AAD_PREFIX$itemId".encodeToByteArray()
    }

    fun isV2FileName(fileName: String): Boolean = fileName.endsWith(V2_FILE_SUFFIX)

    fun v2FileNameFor(legacyOrOriginalName: String): String {
        val stem = legacyOrOriginalName
            .removeSuffix(LEGACY_FILE_SUFFIX)
            .removeSuffix(V2_FILE_SUFFIX)
            .ifBlank { "PhotoBook" }
        return "$stem$V2_FILE_SUFFIX"
    }

    fun legacyTwinNameFor(v2FileName: String): String? {
        if (!isV2FileName(v2FileName)) return null
        return v2FileName.removeSuffix(V2_FILE_SUFFIX) + LEGACY_FILE_SUFFIX
    }

    fun temporaryV2FileName(itemId: String): String {
        val safeId = itemId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        require(safeId.isNotBlank()) { "Vault item id cannot produce an empty temporary filename" }
        return ".$safeId$V2_FILE_SUFFIX.tmp"
    }

    fun renameAtomically(source: File, target: File) {
        check(source.parentFile?.canonicalFile == target.parentFile?.canonicalFile) {
            "Vault atomic rename must remain in one directory"
        }
        Os.rename(source.absolutePath, target.absolutePath)
    }

    private fun enrollRPlus(authenticatedCipher: Cipher): VaultCryptoSession {
        check(!envelopeFile.exists()) { "Vault v2 keyset already exists" }
        val handle = KeysetHandle.generateNew(
            PredefinedStreamingAeadParameters.AES256_GCM_HKDF_4KB,
        )
        val serialized = serializeKeyset(handle)
        return try {
            val primary = wrapWithAuthenticatedCipher(
                authenticatedCipher = authenticatedCipher,
                serialized = serialized,
                aad = PRIMARY_KEYSET_AAD,
            )
            writeEnvelopeAtomically(
                WrappedKeysetEnvelope(
                    mode = EnvelopeMode.R_PLUS_COMBINED,
                    primary = primary,
                    recovery = null,
                ),
            )
            sessionFrom(handle)
        } finally {
            serialized.fill(0)
        }
    }

    private fun unlockExistingKeyset(
        authenticatedCipher: Cipher,
        envelope: WrappedKeysetEnvelope,
    ): VaultCryptoSession {
        val primary = envelope.primary
            ?: throw VaultCredentialRecoveryRequiredException()
        authenticatedCipher.updateAAD(PRIMARY_KEYSET_AAD)
        val serialized = authenticatedCipher.doFinal(primary.ciphertext)
        return try {
            sessionFromSerialized(serialized)
        } finally {
            serialized.fill(0)
        }
    }

    private fun preparePrimaryRebindOrCredentialOnly(
        serialized: ByteArray,
        recovery: KeyWrap,
        existingSessionHandle: KeysetHandle?,
    ): VaultPreRCredentialResult {
        val policy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            KeyPolicy.R_PLUS_COMBINED
        } else {
            KeyPolicy.PRE_R_BIOMETRIC
        }

        if (
            policy == KeyPolicy.PRE_R_BIOMETRIC &&
            BiometricManager.from(appContext).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            val session = existingSessionHandle?.let(::sessionFrom)
                ?: sessionFromSerialized(serialized)
            serialized.fill(0)
            return VaultPreRCredentialResult.CredentialOnly(session)
        }

        val preparation = runCatching {
            val key = getOrCreatePrimaryKey(policy)
            val cipher = Cipher.getInstance(WRAP_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
            VaultAuthPreparation.RebindPreR(
                cipher = cipher,
                serialized = serialized,
                recovery = recovery,
                convertToRPlus = policy == KeyPolicy.R_PLUS_COMBINED,
                promptAuthenticators = if (policy == KeyPolicy.R_PLUS_COMBINED) {
                    R_PLUS_AUTHENTICATORS
                } else {
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                },
            )
        }.getOrNull()

        if (preparation != null) {
            return VaultPreRCredentialResult.RequiresBiometric(preparation)
        }

        // A device with no enrolled strong biometric can still recover using its confirmed secure
        // lock screen. Keep the envelope recovery-only; the next access will require credential
        // confirmation again until a biometric can be bound.
        val session = existingSessionHandle?.let(::sessionFrom) ?: sessionFromSerialized(serialized)
        serialized.fill(0)
        return VaultPreRCredentialResult.CredentialOnly(session)
    }

    private fun completePrimaryRebind(
        preparation: VaultAuthPreparation.RebindPreR,
        authenticatedCipher: Cipher,
    ): VaultCryptoSession {
        return try {
            val primary = wrapWithAuthenticatedCipher(
                authenticatedCipher = authenticatedCipher,
                serialized = preparation.serialized,
                aad = PRIMARY_KEYSET_AAD,
            )
            val envelope = if (preparation.convertToRPlus) {
                WrappedKeysetEnvelope(
                    mode = EnvelopeMode.R_PLUS_COMBINED,
                    primary = primary,
                    recovery = null,
                )
            } else {
                WrappedKeysetEnvelope(
                    mode = EnvelopeMode.PRE_R_DUAL,
                    primary = primary,
                    recovery = preparation.recovery,
                )
            }
            writeEnvelopeAtomically(envelope)
            if (preparation.convertToRPlus) {
                deleteKey(RECOVERY_KEY_ALIAS)
            }
            sessionFromSerialized(preparation.serialized)
        } finally {
            preparation.serialized.fill(0)
        }
    }

    private fun wrapWithAuthenticatedCipher(
        authenticatedCipher: Cipher,
        serialized: ByteArray,
        aad: ByteArray,
    ): KeyWrap {
        authenticatedCipher.updateAAD(aad)
        val ciphertext = authenticatedCipher.doFinal(serialized)
        val iv = authenticatedCipher.iv?.copyOf()
            ?: throw GeneralSecurityException("Android Keystore did not provide a GCM IV")
        validateWrap(KeyWrap(iv = iv, ciphertext = ciphertext))
        return KeyWrap(iv = iv, ciphertext = ciphertext)
    }

    private fun wrapWithRecoveryKey(
        serialized: ByteArray,
        key: SecretKey,
    ): KeyWrap {
        val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } catch (error: UserNotAuthenticatedException) {
            throw VaultCredentialAuthenticationExpiredException(error)
        }
        cipher.updateAAD(RECOVERY_KEYSET_AAD)
        val ciphertext = cipher.doFinal(serialized)
        val iv = cipher.iv?.copyOf()
            ?: throw GeneralSecurityException("Android Keystore did not provide a recovery GCM IV")
        return KeyWrap(iv = iv, ciphertext = ciphertext).also(::validateWrap)
    }

    private fun unwrapWithRecoveryKey(
        recovery: KeyWrap,
        key: SecretKey,
    ): ByteArray {
        val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, recovery.iv))
        } catch (error: UserNotAuthenticatedException) {
            throw VaultCredentialAuthenticationExpiredException(error)
        }
        cipher.updateAAD(RECOVERY_KEYSET_AAD)
        return cipher.doFinal(recovery.ciphertext)
    }

    private fun getOrCreatePrimaryKey(policy: KeyPolicy): SecretKey {
        getExistingKey(PRIMARY_KEY_ALIAS)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(
            PRIMARY_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)

    when (policy) {
KeyPolicy.R_PLUS_COMBINED -> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        configureRPlusPrimaryKey(builder)
    } else {
        throw IllegalStateException(
            "Android 11+ Vault key policy requested below API 30",
        )
    }
}
KeyPolicy.PRE_R_BIOMETRIC -> {
    check(Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
    @Suppress("DEPRECATION")
    builder.setUserAuthenticationValidityDurationSeconds(-1)
    builder.setInvalidatedByBiometricEnrollment(true)
}
}

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(builder.build())
            generateKey()
        }
    }

@androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
private fun configureRPlusPrimaryKey(builder: KeyGenParameterSpec.Builder) {
    builder.setUserAuthenticationParameters(
        0,
        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
    )
}

    private fun getOrCreateRecoveryKey(): SecretKey {
        getExistingKey(RECOVERY_KEY_ALIAS)?.let { return it }
        check(Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            "New recovery keys are only created on Android 8-10"
        }
        @Suppress("DEPRECATION")
        val spec = KeyGenParameterSpec.Builder(
            RECOVERY_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            // Positive validity on API 23-29 is authorized only by the secure lock screen.
            // Biometric-enrollment invalidation does not apply to this key class.
            .setUserAuthenticationValidityDurationSeconds(RECOVERY_AUTH_WINDOW_SECONDS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun getExistingKey(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun deleteKey(alias: String) {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
                deleteEntry(alias)
            }
        }
    }

    private fun serializeKeyset(handle: KeysetHandle): ByteArray =
        TinkProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get())

    private fun sessionFromSerialized(serialized: ByteArray): VaultCryptoSession {
        val handle = TinkProtoKeysetFormat.parseKeyset(
            serialized,
            InsecureSecretKeyAccess.get(),
        )
        return sessionFrom(handle)
    }

    private fun sessionFrom(handle: KeysetHandle): VaultCryptoSession {
        val primitive = handle.getPrimitive(
            RegistryConfiguration.get(),
            StreamingAead::class.java,
        )
        return VaultCryptoSession(primitive)
    }

    private fun readEnvelope(): WrappedKeysetEnvelope {
        val length = envelopeFile.length()
        if (length !in MIN_ENVELOPE_BYTES..MAX_ENVELOPE_BYTES) {
            throw IOException("Invalid Vault keyset envelope size")
        }
        DataInputStream(BufferedInputStream(FileInputStream(envelopeFile))).use { input ->
            if (input.readInt() != ENVELOPE_MAGIC) throw IOException("Unknown Vault keyset envelope")
            val version = input.readInt()
            if (version != ENVELOPE_VERSION) throw IOException("Unsupported Vault keyset version: $version")
            val mode = EnvelopeMode.fromId(input.readInt())
            val primary = readWrap(input)
            val recovery = readWrap(input)
            if (input.read() != -1) throw IOException("Unexpected trailing Vault keyset data")
            return WrappedKeysetEnvelope(mode = mode, primary = primary, recovery = recovery).also(::validateEnvelope)
        }
    }

    private fun readWrap(input: DataInputStream): KeyWrap? {
        val ivLength = input.readInt()
        if (ivLength == 0) {
            val ciphertextLength = input.readInt()
            if (ciphertextLength != 0) throw IOException("Vault wrap has ciphertext without IV")
            return null
        }
        if (ivLength !in 1..MAX_IV_BYTES) throw IOException("Invalid Vault keyset IV length")
        val iv = ByteArray(ivLength)
        input.readFully(iv)
        val ciphertextLength = input.readInt()
        if (ciphertextLength !in 1..MAX_WRAPPED_KEYSET_BYTES) {
            throw IOException("Invalid Vault wrapped-keyset length")
        }
        val ciphertext = ByteArray(ciphertextLength)
        input.readFully(ciphertext)
        return KeyWrap(iv = iv, ciphertext = ciphertext)
    }

    private fun writeEnvelopeAtomically(envelope: WrappedKeysetEnvelope) {
        validateEnvelope(envelope)
        val temp = File(envelopeFile.parentFile, "${envelopeFile.name}.tmp")
        runCatching { temp.delete() }
        try {
            val fileOutput = FileOutputStream(temp)
            DataOutputStream(BufferedOutputStream(fileOutput)).use { output ->
                output.writeInt(ENVELOPE_MAGIC)
                output.writeInt(ENVELOPE_VERSION)
                output.writeInt(envelope.mode.id)
                writeWrap(output, envelope.primary)
                writeWrap(output, envelope.recovery)
                output.flush()
                fileOutput.fd.sync()
            }
            Os.rename(temp.absolutePath, envelopeFile.absolutePath)
        } catch (error: Throwable) {
            runCatching { temp.delete() }
            throw error
        }
    }

    private fun writeWrap(output: DataOutputStream, wrap: KeyWrap?) {
        if (wrap == null) {
            output.writeInt(0)
            output.writeInt(0)
            return
        }
        output.writeInt(wrap.iv.size)
        output.write(wrap.iv)
        output.writeInt(wrap.ciphertext.size)
        output.write(wrap.ciphertext)
    }

    private fun validateEnvelope(envelope: WrappedKeysetEnvelope) {
        envelope.primary?.let(::validateWrap)
        envelope.recovery?.let(::validateWrap)
        when (envelope.mode) {
            EnvelopeMode.R_PLUS_COMBINED -> {
                if (envelope.primary == null || envelope.recovery != null) {
                    throw IOException("Invalid Android 11+ Vault envelope")
                }
            }
            EnvelopeMode.PRE_R_DUAL -> {
                if (envelope.recovery == null) {
                    throw IOException("Pre-R Vault envelope is missing recovery protection")
                }
            }
        }
    }

    private fun validateWrap(wrap: KeyWrap) {
        if (wrap.iv.isEmpty() || wrap.iv.size > MAX_IV_BYTES) {
            throw IOException("Invalid Vault keyset IV")
        }
        if (wrap.ciphertext.isEmpty() || wrap.ciphertext.size > MAX_WRAPPED_KEYSET_BYTES) {
            throw IOException("Invalid Vault wrapped keyset")
        }
    }

    private enum class KeyPolicy {
        R_PLUS_COMBINED,
        PRE_R_BIOMETRIC,
    }

    internal enum class EnvelopeMode(val id: Int) {
        R_PLUS_COMBINED(1),
        PRE_R_DUAL(2);

        companion object {
            fun fromId(id: Int): EnvelopeMode = entries.firstOrNull { it.id == id }
                ?: throw IOException("Unknown Vault envelope mode: $id")
        }
    }

    internal companion object {
        const val V2_FILE_SUFFIX = ".pbvault2"
        const val LEGACY_FILE_SUFFIX = ".pbvault"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PRIMARY_KEY_ALIAS = "photobook_vault_wrap_v2"
        private const val RECOVERY_KEY_ALIAS = "photobook_vault_recovery_v2"
        private const val KEYSET_ENVELOPE_FILE = "vault_keyset_v2.pbkey"
        private const val WRAP_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val RECOVERY_AUTH_WINDOW_SECONDS = 30
        private const val ENVELOPE_MAGIC = 0x50425632 // PBV2
        private const val ENVELOPE_VERSION = 2
        private const val MAX_IV_BYTES = 32
        private const val MAX_WRAPPED_KEYSET_BYTES = 64 * 1024
        private const val MIN_ENVELOPE_BYTES = 28L
        private const val MAX_ENVELOPE_BYTES = 2L * MAX_WRAPPED_KEYSET_BYTES + 2L * MAX_IV_BYTES + 64L
        private const val VAULT_FILE_AAD_PREFIX = "photobook-vault:v2:"
        private val PRIMARY_KEYSET_AAD = "photobook-vault:keyset:v2:primary".encodeToByteArray()
        private val RECOVERY_KEYSET_AAD = "photobook-vault:keyset:v2:recovery".encodeToByteArray()
        private const val R_PLUS_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

internal sealed interface VaultAuthPreparation {
    val cipher: Cipher
    val promptAuthenticators: Int

    data class EnrollRPlus(
        override val cipher: Cipher,
        override val promptAuthenticators: Int =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
    ) : VaultAuthPreparation

    data class Unlock(
        override val cipher: Cipher,
        internal val envelope: WrappedKeysetEnvelope,
        override val promptAuthenticators: Int,
    ) : VaultAuthPreparation

    data class RebindPreR(
        override val cipher: Cipher,
        internal val serialized: ByteArray,
        internal val recovery: KeyWrap,
        internal val convertToRPlus: Boolean,
        override val promptAuthenticators: Int,
    ) : VaultAuthPreparation
}

internal sealed interface VaultPreRCredentialResult {
    data class RequiresBiometric(
        val preparation: VaultAuthPreparation.RebindPreR,
    ) : VaultPreRCredentialResult

    data class CredentialOnly(
        val session: VaultCryptoSession,
    ) : VaultPreRCredentialResult
}

class VaultCryptoSession internal constructor(
    internal val streamingAead: StreamingAead,
)

internal data class KeyWrap(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

internal data class WrappedKeysetEnvelope(
    val mode: VaultAuthCrypto.EnvelopeMode,
    val primary: KeyWrap?,
    val recovery: KeyWrap?,
)

internal class VaultCredentialSetupRequiredException :
    GeneralSecurityException("Secure device credential confirmation is required to set up Vault recovery")

internal class VaultCredentialRecoveryRequiredException(cause: Throwable? = null) :
    GeneralSecurityException("Secure device credential confirmation is required to recover Vault", cause)

internal class VaultCredentialAuthenticationExpiredException(cause: Throwable) :
    GeneralSecurityException("Device credential authorization expired before Vault recovery completed", cause)

internal class VaultKeyUnavailableException(
    message: String,
    cause: Throwable? = null,
) : GeneralSecurityException(message, cause)
