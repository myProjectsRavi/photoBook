package com.photobook.app.verification

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.photobook.app.feature.qrshare.QrBitmapEncoder
import com.photobook.app.ml.LocalOcrEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Disposable benchmark-only proof for the three high-value keep-rule families considered for
 * removal. The benchmark source set is never packaged in release artifacts.
 */
class HighValueR8ProofActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            try {
                val persistence = proveCrypto()
                proveQr()
                proveOcr()
                Log.i(
                    TAG,
                    "PHOTOBOOK_HIGH_VALUE_R8_PROOF=PASS " +
                        "previousPresent=${persistence.first} " +
                        "filePreviousPresent=${persistence.second} qr=PASS ocr=PASS",
                )
            } catch (error: Throwable) {
                Log.e(TAG, "PHOTOBOOK_HIGH_VALUE_R8_PROOF=FAIL", error)
            } finally {
                withContext(Dispatchers.Main) { finish() }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun proveCrypto(): Pair<Boolean, Boolean> {
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
        check(previous == null || previous == VALUE) { "Unexpected encrypted preference value" }
        check(prefs.edit().putString(KEY, VALUE).commit()) { "Encrypted preference commit failed" }
        check(prefs.getString(KEY, null) == VALUE) { "Encrypted preference round trip failed" }

        val encryptedTarget = File(filesDir, ENCRYPTED_FILE_NAME)
        val filePreviouslyPresent = encryptedTarget.exists()
        if (filePreviouslyPresent) {
            val persisted = buildEncryptedFile(encryptedTarget, masterKey)
                .openFileInput().bufferedReader().use { it.readText() }
            check(persisted == VALUE) { "Encrypted file persisted value mismatch" }
            check(encryptedTarget.delete()) { "Could not reset encrypted file fixture" }
        }
        buildEncryptedFile(encryptedTarget, masterKey).openFileOutput().bufferedWriter().use {
            it.write(VALUE)
        }
        val roundTrip = buildEncryptedFile(encryptedTarget, masterKey)
            .openFileInput().bufferedReader().use { it.readText() }
        check(roundTrip == VALUE) { "Encrypted file round trip failed" }
        return (previous != null) to filePreviouslyPresent
    }

    private fun proveQr() {
        val bitmap = QrBitmapEncoder.encode(QR_PAYLOAD, QR_SIZE_PX)
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val luminance = ByteArray(pixels.size)
            for (index in pixels.indices) {
                val pixel = pixels[index]
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                luminance[index] = ((red * 299 + green * 587 + blue * 114) / 1000).toByte()
            }
            val source = PlanarYUVLuminanceSource(
                luminance,
                bitmap.width,
                bitmap.height,
                0,
                0,
                bitmap.width,
                bitmap.height,
                false,
            )
            val reader = MultiFormatReader()
            val result = try {
                reader.decode(
                    BinaryBitmap(HybridBinarizer(source)),
                    mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.TRY_HARDER to true,
                    ),
                )
            } finally {
                reader.reset()
            }
            check(result.text == QR_PAYLOAD) { "QR round trip mismatch" }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun proveOcr() {
        val bitmap = Bitmap.createBitmap(2_400, 520, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 150f
            }
            canvas.drawText(OCR_TEXT, 45f, 300f, paint)
            val recognized = LocalOcrEngine().recognize(bitmap).getOrThrow()
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()
            check(recognized.contains("photobook")) { "OCR missing photobook: $recognized" }
            check(recognized.contains("abc")) { "OCR missing abc: $recognized" }
            check(recognized.contains("xyz")) { "OCR missing xyz: $recognized" }
            check(recognized.contains("12345")) { "OCR missing 12345: $recognized" }
        } finally {
            bitmap.recycle()
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
        const val TAG = "PhotoBookHighValueR8"
        const val PREFS_NAME = "photobook_high_value_r8_proof"
        const val KEY = "marker"
        const val VALUE = "photobook"
        const val ENCRYPTED_FILE_NAME = "photobook_high_value_r8_proof.bin"
        const val QR_PAYLOAD = "PhotoBook-R8-QR-12345"
        const val QR_SIZE_PX = 512
        const val OCR_TEXT = "PhotoBook ABC xyz 12345"
    }
}
