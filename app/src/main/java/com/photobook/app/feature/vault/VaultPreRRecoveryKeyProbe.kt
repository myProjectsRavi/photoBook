package com.photobook.app.feature.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Disposable feasibility seam for API 26-29 recovery-key policy. */
internal object VaultPreRRecoveryKeyProbe {
    const val KEY_ALIAS = "photobook_vault_prer_recovery_probe"
    const val VALIDITY_SECONDS = 15

    fun getOrCreate(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        @Suppress("DEPRECATION")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            // Positive duration on API 23-29 is authorized only by the secure lock screen.
            // Biometric-enrollment invalidation does not apply to this key class.
            .setUserAuthenticationValidityDurationSeconds(VALIDITY_SECONDS)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }

    fun delete() {
        java.security.KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(KEY_ALIAS)
        }
    }
}
