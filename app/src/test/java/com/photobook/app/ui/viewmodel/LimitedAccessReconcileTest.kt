package com.photobook.app.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.data.model.RawPhotoData
import com.photobook.app.util.PermissionUtils
import org.junit.Test

class LimitedAccessReconcileTest {

    @Test
    fun limitedColdStart_excludesPersistedButUngrantedRecordsBeforePublication() {
        val granted = photo(1L)
        val retainedButRevoked = photo(2L)

        val visible = visiblePersistedRecordsForAccess(
            persisted = listOf(granted, retainedButRevoked),
            accessMode = PermissionUtils.PhotoAccessMode.Limited,
            accessiblePhotoIds = setOf(granted.id),
        )

        assertThat(visible.map { it.id }).containsExactly(granted.id)
    }

    @Test
    fun unchangedSelectedAccess_needsNoRecordRebuildOrRepublish() {
        val current = listOf(photo(1L), photo(2L))
        val raw = current.map(::rawFrom)

        assertThat(changedRawPhotosForReconcile(raw, current)).isEmpty()
        assertThat(publishedRecordsEquivalent(current, current.toList())).isTrue()
    }

    @Test
    fun regrantedPersistedRecord_reusesDurableMetadataWithoutExifRebuild() {
        val retained = photo(7L)
        val raw = listOf(rawFrom(retained))

        val changed = changedRawPhotosForReconcile(
            allRaw = raw,
            reusableRecords = listOf(retained),
        )

        assertThat(changed).isEmpty()
        assertThat(publishedRecordsEquivalent(emptyList(), listOf(retained))).isFalse()
    }

    @Test
    fun changedMetadata_rebuildsOnlyChangedOrNewRecords() {
        val unchanged = photo(1L)
        val changed = photo(2L)
        val changedRaw = rawFrom(changed).copy(fileSize = changed.fileSize + 1L)
        val newRaw = rawFrom(photo(3L))

        val result = changedRawPhotosForReconcile(
            allRaw = listOf(rawFrom(unchanged), changedRaw, newRaw),
            reusableRecords = listOf(unchanged, changed),
        )

        assertThat(result.map { it.id }).containsExactly(2L, 3L).inOrder()
    }

    @Test
    fun accessGeneration_rejectsObsoleteMemoryPublication() {
        val gate = AccessGenerationGate()
        val first = gate.current()

        assertThat(gate.isCurrent(first)).isTrue()

        val second = gate.advance()

        assertThat(gate.isCurrent(first)).isFalse()
        assertThat(gate.isCurrent(second)).isTrue()
    }

    private fun rawFrom(photo: PhotoRecord): RawPhotoData {
        return RawPhotoData(
            id = photo.id,
            uriString = photo.uriString,
            filePath = photo.filePath,
            fileName = photo.fileName,
            dateAdded = photo.dateAdded,
            fileSize = photo.fileSize,
            width = photo.width,
            height = photo.height,
            mimeType = photo.mimeType,
            folderName = "Camera",
            folderPath = "DCIM/Camera/",
            generationModified = 42L,
        )
    }

    private fun photo(id: Long): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://media/external/images/media/$id",
            filePath = "/storage/emulated/0/DCIM/Camera/IMG_$id.jpg",
            fileName = "IMG_$id.jpg",
            dateAdded = 1_786_900_000_000L - id,
            year = 2026,
            month = 9,
            dayOfMonth = 1,
            dayOfWeek = 2,
            hourOfDay = 12,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = 100_000L + id,
            width = 1200,
            height = 900,
            mimeType = "image/jpeg",
            folderName = "camera",
            folderPath = "dcim/camera/",
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
            ocrText = "",
            isOcrProcessed = false,
            ocrStatus = IntelligenceStatus.PENDING,
        )
    }
}
