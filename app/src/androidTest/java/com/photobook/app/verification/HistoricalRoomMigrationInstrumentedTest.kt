package com.photobook.app.verification

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photobook.app.di.AppModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * S04 historical migration evidence.
 *
 * Historical schema provenance: commit 09c35afc15f3bfa087b0b78ab826f04598555c9f
 * declared PhotoBookDatabase version 1 with PhotoEntity and PhotoFtsEntity and
 * exportSchema=false. Historical Room JSON therefore does not exist and must
 * not be fabricated.
 *
 * This fixture recreates only the SQLite shape proven by those v1 entity
 * definitions, seeds durable user data, then opens the file through the real
 * production provider so migrations 1 -> 14 and Room's v14 validation run.
 */
@RunWith(AndroidJUnit4::class)
class HistoricalRoomMigrationInstrumentedTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        deleteDatabaseArtifacts()
    }

    @After
    fun tearDown() {
        deleteDatabaseArtifacts()
    }

    @Test
    fun provenanceBackedV1Database_migratesToV14_preservingDurablePhotoData() {
        createHistoricalV1Fixture()

        val migrated = AppModule.providePhotoBookDatabase(context)
        migrated.openHelper.writableDatabase

        migrated.openHelper.readableDatabase.query(
            """
            SELECT
                id, uriString, filePath, fileName, folderName, folderPath,
                isFavorite, mlTagsPayload, isMlProcessed, mlStatus,
                ocrText, isOcrProcessed, ocrStatus,
                isArchiveScreenshotCandidate, isArchiveFoodCandidate, sourceRevision
            FROM photos
            WHERE id = ?
            """.trimIndent(),
            arrayOf(TEST_ID),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(TEST_ID, cursor.getLong(0))
            assertEquals(TEST_URI, cursor.getString(1))
            assertEquals("/storage/emulated/0/DCIM/Camera/IMG_0001.jpg", cursor.getString(2))
            assertEquals("IMG_0001.jpg", cursor.getString(3))
            assertEquals("Camera", cursor.getString(4))
            assertEquals("/storage/emulated/0/DCIM/Camera", cursor.getString(5))
            assertEquals(1, cursor.getInt(6))
            assertEquals("[\"beach\",\"sunset\"]", cursor.getString(7))
            assertEquals(0, cursor.getInt(8))
            assertEquals("PENDING", cursor.getString(9))
            assertEquals("hello photobook", cursor.getString(10))
            assertEquals(1, cursor.getInt(11))
            assertEquals("PROCESSED", cursor.getString(12))
            assertEquals(0, cursor.getInt(13))
            assertEquals(0, cursor.getInt(14))
            assertEquals(-1L, cursor.getLong(15))
        }

        migrated.openHelper.readableDatabase.query(
            "SELECT searchableText FROM photo_fts WHERE rowid = ?",
            arrayOf(TEST_ID),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(TEST_SEARCHABLE_TEXT, cursor.getString(0))
        }

        migrated.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(14, cursor.getInt(0))
        }

        migrated.openHelper.readableDatabase.query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }

        migrated.close()
    }

    private fun createHistoricalV1Fixture() {
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS photos (
                id INTEGER NOT NULL PRIMARY KEY,
                uriString TEXT NOT NULL,
                filePath TEXT NOT NULL,
                fileName TEXT NOT NULL,
                dateAdded INTEGER NOT NULL,
                year INTEGER NOT NULL,
                month INTEGER NOT NULL,
                dayOfMonth INTEGER NOT NULL,
                dayOfWeek INTEGER NOT NULL,
                hourOfDay INTEGER NOT NULL,
                latitude REAL,
                longitude REAL,
                city TEXT,
                state TEXT,
                country TEXT,
                fileSize INTEGER NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                mimeType TEXT NOT NULL,
                folderName TEXT NOT NULL,
                folderPath TEXT NOT NULL,
                cameraModel TEXT,
                isFrontCamera INTEGER NOT NULL,
                isHdr INTEGER NOT NULL,
                isFavorite INTEGER NOT NULL,
                mlTagsPayload TEXT NOT NULL,
                isMlProcessed INTEGER NOT NULL,
                ocrText TEXT NOT NULL,
                isOcrProcessed INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        sqlite.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS photo_fts
            USING FTS4(
                searchableText TEXT NOT NULL,
                tokenize=unicode61,
                prefix='2,3,4'
            )
            """.trimIndent(),
        )

        sqlite.execSQL(
            """
            INSERT INTO photos(
                id, uriString, filePath, fileName, dateAdded,
                year, month, dayOfMonth, dayOfWeek, hourOfDay,
                latitude, longitude, city, state, country,
                fileSize, width, height, mimeType, folderName, folderPath,
                cameraModel, isFrontCamera, isHdr, isFavorite,
                mlTagsPayload, isMlProcessed, ocrText, isOcrProcessed
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                TEST_ID,
                TEST_URI,
                "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg",
                "IMG_0001.jpg",
                1_700_000_000L,
                2023,
                11,
                14,
                3,
                10,
                17.385,
                78.4867,
                "Hyderabad",
                "Telangana",
                "India",
                4_096L,
                4_000,
                3_000,
                "image/jpeg",
                "Camera",
                "/storage/emulated/0/DCIM/Camera",
                "Pixel",
                0,
                1,
                1,
                "[\"beach\",\"sunset\"]",
                1,
                "hello photobook",
                1,
            ),
        )

        sqlite.execSQL(
            "INSERT INTO photo_fts(rowid, searchableText) VALUES(?, ?)",
            arrayOf(TEST_ID, TEST_SEARCHABLE_TEXT),
        )

        sqlite.version = 1
        sqlite.close()
    }

    private fun deleteDatabaseArtifacts() {
        listOf(
            dbFile,
            File(dbFile.path + "-shm"),
            File(dbFile.path + "-wal"),
            File(dbFile.path + "-journal"),
        ).forEach { it.delete() }
    }

    private companion object {
        const val DATABASE_NAME = "photobook.db"
        const val TEST_ID = 424242L
        const val TEST_URI = "content://media/external/images/media/424242"
        const val TEST_SEARCHABLE_TEXT =
            "img_0001.jpg camera /storage/emulated/0/dcim/camera hyderabad telangana india hello photobook beach sunset"
    }
}

internal object HistoricalRoomMigrationProvenance {
    const val VERSION_ONE_SOURCE_COMMIT =
        "09c35afc15f3bfa087b0b78ab826f04598555c9f"
}
