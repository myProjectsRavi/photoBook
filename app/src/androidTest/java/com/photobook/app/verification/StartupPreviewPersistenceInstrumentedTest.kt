package com.photobook.app.verification

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.db.PhotoBookDatabase
import com.photobook.app.data.index.IndexPersistence
import com.photobook.app.data.model.PhotoRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupPreviewPersistenceInstrumentedTest {

    @Test
    fun recentQuery_isBoundedAndNewestFirst() = runBlocking {
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
            val records = (1L..6L).map { id -> fixtureRecord(id) }
            persistence.save(records)

            assertEquals(listOf(6L, 5L, 4L), photoDao.getRecent(3).map { entity -> entity.id })
            assertTrue(photoDao.getRecent(0).isEmpty())
        } finally {
            database.close()
        }
    }

    private fun fixtureRecord(id: Long): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://media/external/images/media/$id",
            filePath = "/storage/emulated/0/Pictures/Phase6/$id.jpg",
            fileName = "phase6_$id.jpg",
            dateAdded = 1_783_000_000_000L + id,
            year = 2026,
            month = 8,
            dayOfMonth = 26,
            dayOfWeek = 4,
            hourOfDay = 12,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = 2_048L + id,
            width = 1_024,
            height = 768,
            mimeType = "image/jpeg",
            folderName = "Phase6",
            folderPath = "Pictures/Phase6",
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
        )
    }
}
