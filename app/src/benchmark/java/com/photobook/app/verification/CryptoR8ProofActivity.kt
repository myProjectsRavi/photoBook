package com.photobook.app.verification

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Disposable benchmark-only entry point that exercises the exact encrypted-storage path from
 * inside an R8-minified app process. This source set is never packaged in release artifacts.
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
                "Unexpected encrypted value before round trip"
            }
            check(prefs.edit().putString(KEY, VALUE).commit()) {
                "Encrypted preference commit failed"
            }
            check(prefs.getString(KEY, null) == VALUE) {
                "Encrypted preference round trip failed"
            }

            Log.i(TAG, "PHOTOBOOK_CRYPTO_R8_PROOF=PASS previousPresent=${previous != null}")
        } catch (error: Throwable) {
            Log.e(TAG, "PHOTOBOOK_CRYPTO_R8_PROOF=FAIL", error)
        } finally {
            finish()
        }
    }

    private companion object {
        const val TAG = "PhotoBookCryptoR8Proof"
        const val PREFS_NAME = "photobook_crypto_r8_proof"
        const val KEY = "marker"
        const val VALUE = "photobook"
    }
}
