package com.photobook.app.feature.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhotoEditStateTest {

    @Test
    fun normalizedCropRegion_ordersAndClampsBounds() {
        val region = NormalizedCropRegion(
            left = 1.2f,
            top = 0.8f,
            right = -0.2f,
            bottom = 0.1f,
        ).normalized()

        assertThat(region.left).isEqualTo(0f)
        assertThat(region.top).isEqualTo(0.1f)
        assertThat(region.right).isEqualTo(1f)
        assertThat(region.bottom).isEqualTo(0.8f)
        assertThat(region.isUsable()).isTrue()
    }

    @Test
    fun identityState_requiresNoCustomCrop() {
        val edited = PhotoEditState(
            customCrop = NormalizedCropRegion(
                left = 0.1f,
                top = 0.1f,
                right = 0.9f,
                bottom = 0.9f,
            ),
        )

        assertThat(edited.isIdentity()).isFalse()
        assertThat(PhotoEditState().isIdentity()).isTrue()
    }
}
