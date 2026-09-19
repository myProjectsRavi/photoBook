package com.photobook.app.verification

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.db.ArchiveDecisionEntity
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
    fun limitedAccess_appliesVisibilityBeforeBoundedCandidateLimit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            PhotoBookDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        val preferences = context.getSharedPreferences(
            "archive-limit-access-test-" + UUID.randomUUID(),
            0,
        )

        try {
            val photoDao = database.photoDao()
            val service = ArchiveService(
                photoDao = photoDao,
                archiveDao = database.archiveDao(),
                vaultDao = database.vaultDao(),
                classifier = ArchiveClassifier(),
                sharedPreferences = preferences,
            )
            val nowMs = System.currentTimeMillis()
            photoDao.upsertPhotos(
                listOf(
                    paymentScreenshot(10L)
                        .copy(dateAdded = nowMs - 60_000L)
                        .toPhotoEntity(),
                    paymentScreenshot(11L)
                        .copy(dateAdded = nowMs)
                        .toPhotoEntity(),
                ),
            )

            // Enable the feature without performing a pre-scan. The first decision creation
            // below must therefore come from the scanLimit=1 request itself.
            assertEquals(
                true,
                preferences.edit().putBoolean("archives_enabled_v1", true).commit(),
            )

            // With scanLimit=1 the newer, revoked row must not consume the only candidate slot.
            // The access boundary is applied before limiting, so the older accessible row survives.
            val summary = service.refreshCandidates(
                scanLimit = 1,
                accessiblePhotoIds = setOf(10L),
            )
            assertEquals(listOf(10L), summary.candidates.map { candidate -> candidate.photo.id })
            assertEquals(
                ArchiveDecisionStates.CANDIDATE,
                database.archiveDao().getByPhotoIds(listOf(10L)).single().state,
            )
            assertEquals(0, database.archiveDao().getByPhotoIds(listOf(11L)).size)
        } finally {
            preferences.edit().clear().commit()
            database.close()
        }
    }

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
            assertEquals(
                ArchiveDecisionStates.CANDIDATE,
                archiveDao.getByPhotoIds(listOf(1L)).single().state,
            )
            assertEquals(0, archiveDao.getByPhotoIds(listOf(2L)).size)
            assertNotNull(photoDao.getById(2L))

            val secondGrant = service.refreshCandidates(
                accessiblePhotoIds = setOf(2L),
            )
            assertEquals(listOf(2L), secondGrant.candidates.map { candidate -> candidate.photo.id })
            assertEquals(
                ArchiveDecisionStates.CANDIDATE,
                archiveDao.getByPhotoIds(listOf(1L)).single().state,
            )
            assertEquals(
                ArchiveDecisionStates.CANDIDATE,
                archiveDao.getByPhotoIds(listOf(2L)).single().state,
            )

            // Regranting the first photo must restore visibility without rebuilding user metadata.
            val regranted = service.loadSummary(accessiblePhotoIds = setOf(1L))
            assertEquals(listOf(1L), regranted.candidates.map { candidate -> candidate.photo.id })

            // Retention state must obey the same access boundary. Keep both durable
            // decisions, but a limited grant may only represent or act on the accessible one.
            val dueNowMs = System.currentTimeMillis()
            val overdueMs = dueNowMs - 8L * 24L * 60L * 60L * 1000L
            archiveDao.upsertDecisions(
                listOf(
                    overdueDecision(photoId = 1L, trashedAtMs = overdueMs),
                    overdueDecision(photoId = 2L, trashedAtMs = overdueMs),
                ),
            )

            val limitedDueSummary = service.loadSummary(accessiblePhotoIds = setOf(1L))
            assertEquals(1, limitedDueSummary.dueDeleteCount)
            assertEquals(
                listOf(1L),
                service.dueDeleteItems(accessiblePhotoIds = setOf(1L))
                    .map { item -> item.photoId },
            )

            val fullDueSummary = service.loadSummary()
            assertEquals(2, fullDueSummary.dueDeleteCount)
            assertEquals(
                setOf(1L, 2L),
                service.dueDeleteItems().map { item -> item.photoId }.toSet(),
            )

            // Revoking access must not delete durable user/intelligence rows.
            assertNotNull(photoDao.getById(1L))
            assertNotNull(photoDao.getById(2L))
        } finally {
            preferences.edit().clear().commit()
            database.close()
        }
    }

    private fun overdueDecision(
        photoId: Long,
        trashedAtMs: Long,
    ): ArchiveDecisionEntity {
        return ArchiveDecisionEntity(
            photoId = photoId,
            uriString = "content://media/external/images/media/$photoId",
            state = ArchiveDecisionStates.TRASHED,
            confidence = 1.0,
            reasons = "test",
            firstDetectedAtMs = trashedAtMs,
            lastDetectedAtMs = trashedAtMs,
            trashedAtMs = trashedAtMs,
            retentionDays = 7,
        )
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
