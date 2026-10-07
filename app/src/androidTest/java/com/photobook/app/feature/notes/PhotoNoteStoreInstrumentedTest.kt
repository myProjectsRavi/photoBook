package com.photobook.app.feature.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.model.PhotoRecord
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoNoteStoreInstrumentedTest {

    @Test
    fun stableIdentity_keepsPlaintextEncryptedAndRejectsReusedMediaId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PhotoNoteStore(context)
        val original = photo(id = 91_001L, dateAdded = 1_786_000_000_000L)
        val reusedId = original.copy(
            uriString = "content://media/external/images/media/91001",
            fileName = "replacement.jpg",
            filePath = "/storage/emulated/0/DCIM/Camera/replacement.jpg",
            dateAdded = original.dateAdded + 86_400_000L,
        )
        val marker = "private-note-plaintext-must-never-appear-91a7"

        store.deleteNote(original)
        store.deleteNote(reusedId)
        store.deleteNote(original.id)

        try {
            assertTrue(store.saveNote(original, marker))
            assertEquals(marker, store.getNote(original))
            assertEquals("", store.getNote(reusedId))
            assertTrue(store.noteContains(original, "plaintext-must-never"))
            assertFalse(store.noteContains(reusedId, "plaintext-must-never"))

            val prefsFile = File(
                context.applicationInfo.dataDir,
                "shared_prefs/photobook_private_notes.xml",
            )
            assertTrue("Encrypted preference file should exist after committed note", prefsFile.isFile)
            val rawXml = prefsFile.readText()
            assertFalse("Private-note plaintext leaked to preferences XML", rawXml.contains(marker))
            assertFalse(
                "Stable media identity key leaked in plaintext",
                rawXml.contains("photo_note_v2_${original.id}_${original.dateAdded}"),
            )
        } finally {
            store.deleteNote(original)
            store.deleteNote(reusedId)
            store.deleteNote(original.id)
        }
    }


    @Test
    fun stableIdentity_survivesUriPathAndFileNameChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PhotoNoteStore(context)
        val original = photo(id = 91_002L, dateAdded = 1_786_100_000_000L)
        val movedRepresentation = original.copy(
            uriString = "content://media/external_primary/images/media/91002",
            filePath = "/storage/emulated/0/Pictures/Relocated/renamed.jpg",
            fileName = "renamed.jpg",
            folderName = "Relocated",
            folderPath = "Pictures/Relocated",
        )
        val marker = "stable-private-note-after-path-change"

        store.deleteNote(original)
        store.deleteNote(movedRepresentation)
        try {
            assertTrue(store.saveNote(original, marker))
            assertEquals(marker, store.getNote(movedRepresentation))
            assertTrue(store.noteContains(movedRepresentation, "path-change"))
        } finally {
            store.deleteNote(original)
            store.deleteNote(movedRepresentation)
        }
    }

    private fun photo(id: Long, dateAdded: Long) = PhotoRecord(
        id = id,
        uriString = "content://media/external/images/media/$id",
        filePath = "/storage/emulated/0/DCIM/Camera/$id.jpg",
        fileName = "$id.jpg",
        dateAdded = dateAdded,
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
        fileSize = 1_024_000L,
        width = 1920,
        height = 1080,
        mimeType = "image/jpeg",
        folderName = "Camera",
        folderPath = "DCIM/Camera",
        cameraModel = null,
        isFrontCamera = false,
        isHdr = false,
    )
}
