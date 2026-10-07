package com.photobook.app.feature.duplicates

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.model.PhotoRecord
import org.junit.Test

class CleanupGroupPolicyTest {
    @Test
    fun confirmedTrash_removesPhotosAndDropsNonComparisonGroups() {
        val groups = listOf(
            group("exact", DuplicateMatchKind.Exact, 1L, 2L, 3L),
            group("similar", DuplicateMatchKind.Similar, 4L, 5L),
        )

        val reconciled = CleanupGroupPolicy.afterConfirmedTrash(
            groups = groups,
            removedPhotoIds = setOf(2L, 4L),
        )

        assertThat(reconciled).hasSize(1)
        assertThat(reconciled.single().id).isEqualTo("exact")
        assertThat(reconciled.single().photos.map { it.id }).containsExactly(1L, 3L).inOrder()
    }

    @Test
    fun confirmedTrash_clearsRemovedHeroInsteadOfKeepingStaleReference() {
        val groups = listOf(
            DuplicatePhotoGroup(
                id = "burst",
                kind = DuplicateMatchKind.Burst,
                photos = listOf(photo(10L), photo(11L), photo(12L)),
                heroPhotoId = 10L,
            ),
        )

        val reconciled = CleanupGroupPolicy.afterConfirmedTrash(
            groups = groups,
            removedPhotoIds = setOf(10L),
        )

        assertThat(reconciled).hasSize(1)
        assertThat(reconciled.single().photos.map { it.id }).containsExactly(11L, 12L).inOrder()
        assertThat(reconciled.single().heroPhotoId).isNull()
    }

    @Test
    fun emptyConfirmedRemoval_preservesGroups() {
        val groups = listOf(group("exact", DuplicateMatchKind.Exact, 1L, 2L))

        assertThat(CleanupGroupPolicy.afterConfirmedTrash(groups, emptySet()))
            .isSameInstanceAs(groups)
    }

    private fun group(
        id: String,
        kind: DuplicateMatchKind,
        vararg photoIds: Long,
    ) = DuplicatePhotoGroup(
        id = id,
        kind = kind,
        photos = photoIds.map(::photo),
    )

    private fun photo(id: Long) = PhotoRecord(
        id = id,
        uriString = "content://photo/$id",
        filePath = "/photo/$id.jpg",
        fileName = "$id.jpg",
        dateAdded = id,
        year = 2026,
        month = 1,
        dayOfMonth = 1,
        dayOfWeek = 1,
        hourOfDay = 1,
        latitude = null,
        longitude = null,
        city = null,
        state = null,
        country = null,
        fileSize = 100L + id,
        width = 100,
        height = 100,
        mimeType = "image/jpeg",
        folderName = "Camera",
        folderPath = "DCIM/Camera",
        cameraModel = null,
        isFrontCamera = false,
        isHdr = false,
    )
}
