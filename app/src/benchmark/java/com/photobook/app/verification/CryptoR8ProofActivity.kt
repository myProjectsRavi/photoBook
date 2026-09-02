package com.photobook.app.verification

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Disposable benchmark-only entry point that exercises the exact encrypted-storage primitives
 * used by PhotoBook from inside an R8-minified app process. This source set is never packaged in
 * release artifacts.
 */
class CryptoR8ProofActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                this,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val previous = prefs.getString(KEY, null)
            check(previous == null || previous == VALUE) {
                "Unexpected encrypted preference value before round trip"
            }
            check(prefs.edit().putString(KEY, VALUE).commit()) {
                "Encrypted preference commit failed"
            }
            check(prefs.getString(KEY, null) == VALUE) {
                "Encrypted preference round trip failed"
            }

            val encryptedTarget = File(filesDir, ENCRYPTED_FILE_NAME)
            val filePreviouslyPresent = encryptedTarget.exists()
            if (filePreviouslyPresent) {
                check(buildEncryptedFile(encryptedTarget, masterKey).openFileInput().bufferedReader().use { it.readText() } == VALUE) {
                    "Encrypted file persisted value mismatch"
                }
                check(encryptedTarget.delete()) {
                    "Could not reset encrypted file fixture"
                }
            }

            buildEncryptedFile(encryptedTarget, masterKey).openFileOutput().bufferedWriter().use {
                it.write(VALUE)
            }
            check(buildEncryptedFile(encryptedTarget, masterKey).openFileInput().bufferedReader().use { it.readText() } == VALUE) {
                "Encrypted file round trip failed"
            }

            Log.i(
                TAG,
                "PHOTOBOOK_CRYPTO_R8_PROOF=PASS previousPresent=${previous != null} " +
                    "filePreviousPresent=$filePreviouslyPresent",
            )
        } catch (error: Throwable) {
            Log.e(TAG, "PHOTOBOOK_CRYPTO_R8_PROOF=FAIL", error)
        } finally {
            finish()
        }
    }

    private fun buildEncryptedFile(target: File, masterKey: MasterKey): EncryptedFile {
        return EncryptedFile.Builder(
            this,
            target,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }

    private companion object {
        const val TAG = "PhotoBookCryptoR8Proof"
        const val PREFS_NAME = "photobook_crypto_r8_proof"
        const val KEY = "marker"
        const val VALUE = "photobook"
        const val ENCRYPTED_FILE_NAME = "photobook_crypto_r8_proof.bin"
    }
}
