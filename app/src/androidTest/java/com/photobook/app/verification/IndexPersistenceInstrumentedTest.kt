package com.photobook.app.verification

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.db.PhotoBookDatabase
import com.photobook.app.data.index.IndexCommitCoordinator
import com.photobook.app.data.index.IndexPersistence
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ui.viewmodel.AccessGenerationGate
import com.photobook.app.ui.viewmodel.commitLimitedAccessReconciliation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndexPersistenceInstrumentedTest {

    @Test
    fun replaceAll_crossesBatchBoundariesAndRemovesStaleRowsAndFts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            PhotoBookDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val photoDao = database.photoDao()
            val persistence = IndexPersistence(
                context = context,
                database = database,
                photoDao = photoDao,
            )

            val initial = (1L..802L).map { id ->
                fixtureRecord(
                    id = id,
                    fileName = if (id == 802L) "staleonlytoken.jpg" else "initial_$id.jpg",
                )
            }
            persistence.save(initial)

            assertEquals(802, photoDao.getPhotoCount())
            assertEquals(listOf(802L), photoDao.searchIdsByText("staleonlytoken", 10))

            val replacement = (1L..401L).map { id ->
                fixtureRecord(
                    id = id,
                    fileName = if (id == 401L) "freshonlytoken.jpg" else "replacement_$id.jpg",
                )
            }
            persistence.save(replacement)

            assertEquals(401, photoDao.getPhotoCount())
            assertEquals((1L..401L).toSet(), photoDao.getAllIds().toSet())
            assertEquals("freshonlytoken.jpg", photoDao.getById(401L)?.fileName)
            assertEquals(null, photoDao.getById(402L))
            assertTrue(photoDao.searchIdsByText("staleonlytoken", 10).isEmpty())
            assertEquals(
                listOf(401L),
                persistence.searchIdsByQueryText("freshonlytoken", limit = 10),
            )

            database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun getByIdsOrdered_aboveAndroidSqliteBindLimit_batchesAndPreservesOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            PhotoBookDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val photoDao = database.photoDao()
            val persistence = IndexPersistence(
                context = context,
                database = database,
                photoDao = photoDao,
            )
            val records = (1L..1_205L).map { id ->
                fixtureRecord(id = id, fileName = "selected_$id.jpg")
            }
            persistence.upsertAll(records)

            val requestedIds = (1_205L downTo 1L).toList()
            val resolved = persistence.getByIdsOrdered(requestedIds)

            assertEquals(requestedIds, resolved.map { record -> record.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun limitedReconcile_interveningFavoriteAndOcrCommitCannotBeRevertedInMemory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            PhotoBookDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val persistence = IndexPersistence(
                context = context,
                database = database,
                photoDao = database.photoDao(),
            )
            val coordinator = IndexCommitCoordinator()
            val photoIndex = PhotoIndex()
            val accessGate = AccessGenerationGate()
            val initial = fixtureRecord(91L, "before.jpg").copy(
                isFavorite = false,
                ocrText = "",
                isOcrProcessed = false,
                ocrStatus = IntelligenceStatus.PENDING,
            )
            persistence.upsertAll(listOf(initial))
            photoIndex.setRecords(listOf(initial))
            assertTrue(accessGate.markPublished(accessGate.current()))

            // Reconciliation has already captured/rebuilt this stale record outside the commit
            // boundary. Before it publishes, a newer favorite + OCR commit lands.
            val staleRebuilt = initial.copy(fileName = "after-structural-scan.jpg")
            coordinator.withCommit {
                persistence.setFavorite(initial.id, true)
                val intelligenceUpdate = PhotoIndex.PhotoIntelligenceUpdate(
                    id = initial.id,
                    ocrText = "recognized invoice 8675309",
                    isOcrProcessed = true,
                    ocrStatus = IntelligenceStatus.PROCESSED,
                )
                persistence.applyIntelligenceUpdates(listOf(intelligenceUpdate))
                photoIndex.setFavorite(initial.id, true)
                photoIndex.updatePhotosIntelligence(listOf(intelligenceUpdate))
            }

            // Production reconciliation must reload Room *inside the same commit lock* after its
            // structural upsert instead of publishing staleRebuilt directly.
            commitLimitedAccessReconciliation(
                indexCommitCoordinator = coordinator,
                indexPersistence = persistence,
                photoIndex = photoIndex,
                accessGenerationGate = accessGate,
                accessGeneration = accessGate.current(),
                visibleIds = listOf(initial.id),
                rebuiltRecords = listOf(staleRebuilt),
            )

            val inMemory = photoIndex.getById(initial.id)
            val durable = persistence.getByIdsOrdered(listOf(initial.id)).single()
            assertEquals(true, inMemory?.isFavorite)
            assertEquals("recognized invoice 8675309", inMemory?.ocrText)
            assertEquals(IntelligenceStatus.PROCESSED, inMemory?.ocrStatus)
            assertEquals(true, durable.isFavorite)
            assertEquals("recognized invoice 8675309", durable.ocrText)
            assertEquals(IntelligenceStatus.PROCESSED, durable.ocrStatus)
        } finally {
            database.close()
        }
    }

    private fun fixtureRecord(id: Long, fileName: String): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://media/external/images/media/$id",
            filePath = "/storage/emulated/0/Pictures/Phase3/$fileName",
            fileName = fileName,
            dateAdded = 1_783_000_000_000L + id,
            year = 2026,
            month = 8,
            dayOfMonth = 17,
            dayOfWeek = 1,
            hourOfDay = 10,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = 1_024L + id,
            width = 1_024,
            height = 768,
            mimeType = "image/jpeg",
            folderName = "Phase3",
            folderPath = "Pictures/Phase3",
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
        )
    }
}
