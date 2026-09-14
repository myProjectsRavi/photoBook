package com.photobook.app.data.source

import android.provider.MediaStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaStoreScannerProjectionTest {

    @Test
    fun api26Projection_omitsColumnsUnavailableBeforeScopedStorage() {
        val projection = mediaStoreImageProjectionForSdk(26).toSet()

        assertThat(projection).doesNotContain(MediaStore.Images.Media.RELATIVE_PATH)
        assertThat(projection).doesNotContain(MediaStore.MediaColumns.GENERATION_MODIFIED)
        @Suppress("DEPRECATION")
        assertThat(projection).contains(MediaStore.Images.Media.DATA)
    }

    @Test
    fun api29Projection_includesRelativePathButNotGeneration() {
        val projection = mediaStoreImageProjectionForSdk(29).toSet()

        assertThat(projection).contains(MediaStore.Images.Media.RELATIVE_PATH)
        assertThat(projection).doesNotContain(MediaStore.MediaColumns.GENERATION_MODIFIED)
    }

    @Test
    fun api30Projection_includesRelativePathAndGeneration() {
        val projection = mediaStoreImageProjectionForSdk(30).toSet()

        assertThat(projection).contains(MediaStore.Images.Media.RELATIVE_PATH)
        assertThat(projection).contains(MediaStore.MediaColumns.GENERATION_MODIFIED)
    }
}
