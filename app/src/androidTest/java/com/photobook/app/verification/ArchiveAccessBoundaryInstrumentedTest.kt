package com.photobook.app.verification

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.db.ArchiveDecisionStates
import com.photobook.app.data.db.PhotoBookDatabase
import com.photobook.app.data.db.toPhotoEntity
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.archive.ArchiveClassifier
import com.photobook.app.feature.archive.ArchiveService
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveAccessBoundaryInstrumentedTest {

    @Test
    fun limitedAccess_reselectionNeverReturnsRevokedArchiveCandidates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            PhotoBookDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        val preferences = context.getSharedPreferences(
            "archive-access-test-" + UUID.randomUUID(),
            0,
        )

        try {
            val photoDao = database.photoDao()
            val archiveDao = database.archiveDao()
            val service = ArchiveService(
                photoDao = photoDao,
                archiveDao = archiveDao,
                vaultDao = database.vaultDao(),
                classifier = ArchiveClassifier(),
                sharedPreferences = preferences,
            )
            photoDao.upsertPhotos(
                listOf(
                    paymentScreenshot(1L).toPhotoEntity(),
                    paymentScreenshot(2L).toPhotoEntity(),
                ),
            )

            val firstGrant = service.setEnabled(
                enabled = true,
                accessiblePhotoIds = setOf(1L),
            )
            assertEquals(listOf(1L), firstGrant.candidates.map { candidate -> candidate.photo.id })
            assertEquals(listOf(1L), archiveDao.getCandidatePhotoIds())
            assertNotNull(photoDao.getById(2L))

            val secondGrant = service.refreshCandidates(
                accessiblePhotoIds = setOf(2L),
            )
            assertEquals(listOf(2L), secondGrant.candidates.map { candidate -> candidate.photo.id })
            assertEquals(listOf(2L), archiveDao.getCandidatePhotoIds())
            assertEquals(
                ArchiveDecisionStates.STALE,
                archiveDao.getByPhotoIds(listOf(1L)).single().state,
            )

            // Revoking access must not delete durable user/intelligence rows.
            assertNotNull(photoDao.getById(1L))
            assertNotNull(photoDao.getById(2L))
        } finally {
            preferences.edit().clear().commit()
            database.close()
        }
    }

    private fun paymentScreenshot(id: Long): PhotoRecord {
        val nowMs = System.currentTimeMillis()
        return PhotoRecord(
            id = id,
            uriString = "content://media/external/images/media/$id",
            filePath = "/storage/emulated/0/Pictures/Screenshots/Screenshot_phonepe_$id.jpg",
            fileName = "Screenshot_phonepe_$id.jpg",
            dateAdded = nowMs - 3L * 24L * 60L * 60L * 1000L,
            year = 2026,
            month = 8,
            dayOfMonth = 1,
            dayOfWeek = 1,
            hourOfDay = 10,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = 10_000L,
            width = 1080,
            height = 2400,
            mimeType = "image/jpeg",
            folderName = "Screenshots",
            folderPath = "Pictures/Screenshots",
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
            ocrText = "PhonePe UPI payment successful INR 500 transaction id abc123",
            isOcrProcessed = true,
            ocrStatus = IntelligenceStatus.PROCESSED,
        )
    }
}
