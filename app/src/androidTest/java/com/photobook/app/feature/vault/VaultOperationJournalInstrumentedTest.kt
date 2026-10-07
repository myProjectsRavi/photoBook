package com.photobook.app.feature.vault

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photobook.app.data.db.VaultDao
import com.photobook.app.data.db.VaultEntity
import com.photobook.app.data.db.VaultOperationEntity
import com.photobook.app.data.db.VaultOperationStates
import com.photobook.app.data.db.VaultOperationTypes
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.di.AppModule
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultOperationJournalInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
        File(context.filesDir, VAULT_DIR).deleteRecursively()
    }

    @Test
    fun schema13_persistsVaultJournalAndVaultRowsTogether() = runBlocking {
        val database = AppModule.providePhotoBookDatabase(context)
        try {
            val item = VaultEntity(
                id = ITEM_ID,
                sourcePhotoId = 41L,
                originalFileName = "IMG_0041.jpg",
                mimeType = "image/jpeg",
                encryptedFileName = "IMG_0041.pbvault2",
                addedAtMs = 100L,
            )
            val inserted = database.vaultDao().insertVaultItem(item)
            assertTrue(inserted > 0L)

            val operation = VaultOperationEntity(
                id = OPERATION_ID,
                type = VaultOperationTypes.MOVE_OUT,
                state = VaultOperationStates.EXPORT_VERIFIED,
                vaultItemId = ITEM_ID,
                sourcePhotoId = 41L,
                encryptedFileName = item.encryptedFileName,
                outputUriString = "content://media/external/images/media/41",
                expectedSha256Hex = "00".repeat(32),
                createdAtMs = 200L,
                updatedAtMs = 200L,
            )
            database.vaultOperationDao().upsert(operation)

            assertNotNull(database.vaultDao().getVaultItemById(ITEM_ID))
            val journal = database.vaultOperationDao().getAll()
            assertEquals(1, journal.size)
            assertEquals(VaultOperationStates.EXPORT_VERIFIED, journal.single().state)
            assertEquals(ITEM_ID, journal.single().vaultItemId)

            assertEquals(1, database.vaultOperationDao().deleteByVaultItemId(ITEM_ID))
            assertTrue(database.vaultOperationDao().getAll().isEmpty())
            assertNotNull(database.vaultDao().getVaultItemById(ITEM_ID))
        } finally {
            database.close()
        }
    }

    @Test
    fun startupCleanup_removesPlaintextPreviewLeftByAbruptTermination() = runBlocking {
        val database = AppModule.providePhotoBookDatabase(context)
        val previewDir = File(context.cacheDir, "vault_preview").apply { mkdirs() }
        val stalePreview = File(previewDir, "abrupt-session.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val service = VaultService(context, database.vaultDao(), database.vaultOperationDao())
        try {
            assertTrue(stalePreview.isFile)
            assertTrue(service.clearStalePreviewCacheAtStartup())
            assertTrue(!stalePreview.exists())
            assertTrue(!previewDir.exists() || previewDir.listFiles().orEmpty().isEmpty())
        } finally {
            database.close()
            previewDir.deleteRecursively()
        }
    }

    @Test
    fun liveCommittedAdd_isNeverReapedByConcurrentRecovery() = runBlocking {
        val database = AppModule.providePhotoBookDatabase(context)
        val source = File(context.cacheDir, "vault-race-" + System.nanoTime() + ".jpg")
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        FileOutputStream(source).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", source)
        val photo = PhotoRecord(
            id = 77_001L,
            uriString = uri.toString(),
            filePath = source.absolutePath,
            fileName = source.name,
            dateAdded = 1_786_000_000_000L,
            year = 2026,
            month = 10,
            dayOfMonth = 7,
            dayOfWeek = 3,
            hourOfDay = 12,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = source.length(),
            width = 24,
            height = 24,
            mimeType = "image/jpeg",
            folderName = "Test",
            folderPath = source.parent.orEmpty(),
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
        )

        StreamingAeadConfig.register()
        val handle = KeysetHandle.generateNew(
            PredefinedStreamingAeadParameters.AES256_GCM_HKDF_4KB,
        )
        val session = VaultCryptoSession(
            handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java),
        )
        val enteredInsert = CompletableDeferred<Unit>()
        val releaseInsert = CompletableDeferred<Unit>()
        val realDao = database.vaultDao()
        val blockingDao = object : VaultDao by realDao {
            override suspend fun insertVaultItem(item: VaultEntity): Long {
                enteredInsert.complete(Unit)
                releaseInsert.await()
                return realDao.insertVaultItem(item)
            }
        }
        val addingService = VaultService(context, blockingDao, database.vaultOperationDao())
        val recoveringService = VaultService(context, realDao, database.vaultOperationDao())

        try {
            val add = async { addingService.addPhotos(listOf(photo), session) }
            enteredInsert.await()

            val liveJournal = database.vaultOperationDao().getAll().single()
            assertEquals(VaultOperationStates.CIPHERTEXT_COMMITTED, liveJournal.state)
            val ciphertext = File(context.filesDir, "$VAULT_DIR/${liveJournal.encryptedFileName}")
            assertTrue(ciphertext.isFile)

            // A second service instance invokes the production recovery path while A is live.
            assertTrue(recoveringService.listItems(session).isEmpty())
            assertTrue("Recovery deleted live ciphertext", ciphertext.isFile)
            assertEquals(1, database.vaultOperationDao().getAll().size)

            releaseInsert.complete(Unit)
            val result = add.await()
            assertTrue(result is VaultSaveResult.Success)
            result as VaultSaveResult.Success
            assertTrue(photo.id in result.addedPhotoIds)
            assertTrue(ciphertext.isFile)
            assertEquals(photo.id, recoveringService.listItems(session).single().sourcePhotoId)
        } finally {
            releaseInsert.complete(Unit)
            source.delete()
            database.close()
        }
    }

    @Test
    fun preparedAddJournal_survivesDatabaseReopenUntilExplicitlyReconciled() = runBlocking {
        val first = AppModule.providePhotoBookDatabase(context)
        val operation = VaultOperationEntity(
            id = OPERATION_ID,
            type = VaultOperationTypes.ADD,
            state = VaultOperationStates.PREPARED,
            vaultItemId = ITEM_ID,
            sourcePhotoId = 42L,
            encryptedFileName = "IMG_0042.pbvault2",
            outputUriString = null,
            expectedSha256Hex = null,
            createdAtMs = 300L,
            updatedAtMs = 300L,
        )
        first.vaultOperationDao().upsert(operation)
        first.close()

        val reopened = AppModule.providePhotoBookDatabase(context)
        try {
            val journal = reopened.vaultOperationDao().getAll()
            assertEquals(1, journal.size)
            assertEquals(VaultOperationStates.PREPARED, journal.single().state)
            assertEquals(42L, journal.single().sourcePhotoId)
        } finally {
            reopened.close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "photobook.db"
        const val VAULT_DIR = "vault_store"
        const val ITEM_ID = "vault-item-test"
        const val OPERATION_ID = "vault-operation-test"
    }
}
