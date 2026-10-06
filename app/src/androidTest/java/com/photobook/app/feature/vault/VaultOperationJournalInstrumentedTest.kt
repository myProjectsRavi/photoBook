package com.photobook.app.feature.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photobook.app.data.db.VaultEntity
import com.photobook.app.data.db.VaultOperationEntity
import com.photobook.app.data.db.VaultOperationStates
import com.photobook.app.data.db.VaultOperationTypes
import com.photobook.app.di.AppModule
import java.io.File
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
