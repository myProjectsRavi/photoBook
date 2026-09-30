package com.photobook.app.verification

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photobook.app.data.db.PhotoBookDatabase
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
 * This test therefore reconstructs only the production v1 SQLite shape proven
 * by that commit, seeds durable user data, and opens the same database through
 * PhotoBook's real production database provider so the registered 1 -> 12
 * migration chain and Room's current schema validation execute unchanged.
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
    fun provenanceBackedV1Database_migratesToV12_preservingDurablePhotoData() {
        createHistoricalV1Fixture()

        val migrated = AppModule.providePhotoBookDatabase(context)
        migrated.openHelper.writableDatabase

        migrated.openHelper.readableDatabase.query(
            """
            SELECT
                uri,
                displayName,
                bucketName,
                isFavorite,
                tags,
                ocrText,
                ocrProcessed,
                mlProcessed
            FROM photos
            WHERE uri = ?
            """.trimIndent(),
            arrayOf(TEST_URI)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(TEST_URI, cursor.getString(0))
            assertEquals("IMG_0001.jpg", cursor.getString(1))
            assertEquals("Camera", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals("beach,sunset", cursor.getString(4))
            assertEquals("hello photobook", cursor.getString(5))
            assertEquals(1, cursor.getInt(6))
            assertEquals(0, cursor.getInt(7))
        }

        migrated.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM photo_fts WHERE uri = ? AND ocrText = ?",
            arrayOf(TEST_URI, "hello photobook")
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        migrated.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(PhotoBookDatabase.VERSION, cursor.getInt(0))
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
                uri TEXT NOT NULL PRIMARY KEY,
                displayName TEXT,
                dateTaken INTEGER NOT NULL,
                size INTEGER NOT NULL,
                bucketName TEXT,
                relativePath TEXT,
                mimeType TEXT,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                isFavorite INTEGER NOT NULL,
                tags TEXT,
                ocrText TEXT
            )
            """.trimIndent()
        )

        sqlite.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS photo_fts USING FTS4(
                uri,
                tags,
                ocrText,
                tokenize=unicode61 "prefix=2,3,4"
            )
            """.trimIndent()
        )

        sqlite.execSQL(
            """
            INSERT INTO photos(
                uri, displayName, dateTaken, size, bucketName, relativePath,
                mimeType, width, height, isFavorite, tags, ocrText
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                TEST_URI,
                "IMG_0001.jpg",
                1_700_000_000_000L,
                4_096L,
                "Camera",
                "DCIM/Camera/",
                "image/jpeg",
                4_000,
                3_000,
                1,
                "beach,sunset",
                "hello photobook"
            )
        )

        sqlite.execSQL(
            "INSERT INTO photo_fts(uri, tags, ocrText) VALUES(?, ?, ?)",
            arrayOf(TEST_URI, "beach,sunset", "hello photobook")
        )

        sqlite.version = 1
        sqlite.close()
    }

    private fun deleteDatabaseArtifacts() {
        listOf(
            dbFile,
            File(dbFile.path + "-shm"),
            File(dbFile.path + "-wal"),
            File(dbFile.path + "-journal")
        ).forEach { it.delete() }
    }

    private companion object {
        const val DATABASE_NAME = "photobook.db"
        const val TEST_URI = "content://media/external/images/media/424242"
    }
}

internal object HistoricalRoomMigrationProvenance {
    const val VERSION_ONE_SOURCE_COMMIT =
        "09c35afc15f3bfa087b0b78ab826f04598555c9f"
}
