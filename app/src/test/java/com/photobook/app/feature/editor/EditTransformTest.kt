package com.photobook.app.feature.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditTransformTest {

    @Test
    fun from_normalizesRotationAndCropForBothPreviewAndExport() {
        val transform = EditTransform.from(
            PhotoEditState(
                rotationQuarterTurns = -1,
                customCrop = NormalizedCropRegion(
                    left = 0.9f,
                    top = 1.2f,
                    right = 0.1f,
                    bottom = -0.2f,
                ),
            ),
        )

        assertThat(transform.quarterTurns).isEqualTo(3)
        assertThat(transform.cropRegion).isEqualTo(
            NormalizedCropRegion(
                left = 0.1f,
                top = 0f,
                right = 0.9f,
                bottom = 1f,
            ),
        )
    }

    @Test
    fun previewAspectRatio_usesNormalizedRotationAndCropGeometry() {
        val rotated = EditTransform.from(
            PhotoEditState(
                rotationQuarterTurns = 1,
                customCrop = NormalizedCropRegion(
                    left = 0.25f,
                    top = 0.1f,
                    right = 0.75f,
                    bottom = 0.9f,
                ),
            ),
        )

        assertThat(rotated.previewAspectRatio(2f)).isWithin(0.0001f).of(0.3125f)
        assertThat(
            EditTransform.from(PhotoEditState(rotationQuarterTurns = 1))
                .previewAspectRatio(2f),
        ).isWithin(0.0001f).of(0.5f)
    }

    @Test
    fun identityToneMatrix_isExactIdentity() {
        assertThat(
            EditTransform.toneMatrix(
                exposure = 0f,
                contrast = 1f,
                filter = QuickFilter.Original,
            ).toList(),
        ).containsExactly(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ).inOrder()
    }

    @Test
    fun toneInputs_areClampedToEditorBounds() {
        val excessive = EditTransform.toneMatrix(
            exposure = 4f,
            contrast = 4f,
            filter = QuickFilter.Warm,
        )
        val bounded = EditTransform.toneMatrix(
            exposure = PhotoEditService.EXPOSURE_MAX,
            contrast = PhotoEditService.CONTRAST_MAX,
            filter = QuickFilter.Warm,
        )

        assertThat(excessive.toList()).containsExactlyElementsIn(bounded.toList()).inOrder()
    }

    @Test
    fun everyFilterProducesFiniteTwentyValueMatrix() {
        QuickFilter.entries.forEach { filter ->
            val values = EditTransform.toneMatrix(
                exposure = 0.25f,
                contrast = 1.15f,
                filter = filter,
            )
            assertThat(values.size).isEqualTo(20)
            assertThat(values.all(Float::isFinite)).isTrue()
        }
    }
}
