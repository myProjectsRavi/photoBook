package com.photobook.app.verification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.photobook.app.PhotoBookApplication
import com.photobook.app.data.db.PhotoBookDatabase
import com.photobook.app.ml.LocalOcrEngine
import com.photobook.app.worker.TrashPurgeWorker
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Disposable proof that app-owned broad keep rules are unnecessary under the real R8-minified
 * benchmark target. Each check exercises a library family whose blanket keep rule is pruned.
 */
@RunWith(AndroidJUnit4::class)
class PrunedKeepsMinifiedRuntimeInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun prunedKeepFamiliesRemainHealthyUnderMinification() = runBlocking {
        proveBundledOcr()
        proveRoomGeneratedCode()
        proveEncryptedStorageAndTink()
        proveZxingRuntime()
        proveHiltWorkManagerFactory()
    }

    private suspend fun proveBundledOcr() {
        val bitmap = Bitmap.createBitmap(1_600, 520, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 170f
            }
            canvas.drawText("PhotoBook OCR", 80f, 310f, paint)

            val result = LocalOcrEngine().recognize(bitmap)
            assertTrue("ML Kit OCR should complete after keep-rule pruning", result.isSuccess)
            val normalized = result.getOrThrow().lowercase().replace(Regex("\\s+"), " ").trim()
            assertTrue("Expected stable PhotoBook token in: $normalized", normalized.contains("photobook"))
        } finally {
            bitmap.recycle()
        }
    }

    private fun proveRoomGeneratedCode() {
        val db = Room.inMemoryDatabaseBuilder(context, PhotoBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            assertNotNull(db.photoDao())
            assertNotNull(db.vaultDao())
            assertNotNull(db.archiveDao())
            db.clearAllTables()
        } finally {
            db.close()
        }
    }

    @Suppress("DEPRECATION")
    private fun proveEncryptedStorageAndTink() {
        val prefsName = "r8_keep_smoke_${UUID.randomUUID()}"
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            prefsName,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        try {
            assertTrue(prefs.edit().putString("probe", "photobook").commit())
            assertEquals("photobook", prefs.getString("probe", null))
        } finally {
            prefs.edit().clear().commit()
            context.deleteSharedPreferences(prefsName)
        }
    }

    private fun proveZxingRuntime() {
        val width = 256
        val height = 256
        val expected = "photobook-r8-smoke"
        val matrix = QRCodeWriter().encode(expected, BarcodeFormat.QR_CODE, width, height)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val source = RGBLuminanceSource(width, height, pixels)
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(expected, decoded.text)
    }

    private fun proveHiltWorkManagerFactory() {
        val app = context.applicationContext as PhotoBookApplication
        val worker = TestListenableWorkerBuilder<TrashPurgeWorker>(context)
            .setWorkerFactory(app.workerFactory)
            .build()
        assertTrue("Hilt worker factory should instantiate the pruned worker class", worker is TrashPurgeWorker)
    }
}
