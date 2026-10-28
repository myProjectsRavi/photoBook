package com.photobook.app.feature.editor

/**
 * Shared, pure editor transform model used by both preview and export paths.
 *
 * Geometry is represented in normalized coordinates so preview and bitmap rendering
 * consume the same rotation/crop intent without touching source media.
 */
internal data class EditTransform(
    val quarterTurns: Int,
    val cropRegion: NormalizedCropRegion?,
    val cropPreset: CropPreset,
    val toneMatrix: FloatArray,
) {
    fun previewAspectRatio(sourceAspectRatio: Float): Float {
        val safeSource = sourceAspectRatio.coerceAtLeast(0.01f)
        val rotatedAspect = if (quarterTurns % 2 == 0) safeSource else 1f / safeSource
        cropRegion?.let { region ->
            val width = (region.right - region.left).coerceAtLeast(0.0001f)
            val height = (region.bottom - region.top).coerceAtLeast(0.0001f)
            return (rotatedAspect * width / height).coerceIn(0.45f, 2.2f)
        }
        return if (cropPreset == CropPreset.Original) {
            rotatedAspect.coerceIn(0.45f, 2.2f)
        } else {
            cropPreset.ratio.coerceIn(0.45f, 2.2f)
        }
    }

    companion object {
        fun from(state: PhotoEditState): EditTransform {
            return EditTransform(
                quarterTurns = ((state.rotationQuarterTurns % 4) + 4) % 4,
                cropRegion = state.customCrop?.normalized()?.takeIf { it.isUsable() },
                cropPreset = state.cropPreset,
                toneMatrix = toneMatrix(
                    exposure = state.exposure,
                    contrast = state.contrast,
                    filter = state.filter,
                ),
            )
        }

        fun toneMatrix(
            exposure: Float,
            contrast: Float,
            filter: QuickFilter,
        ): FloatArray {
            val c = contrast.coerceIn(PhotoEditService.CONTRAST_MIN, PhotoEditService.CONTRAST_MAX)
            val translate = 128f * (1f - c)
            val contrastValues = floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )

            val offset = exposure.coerceIn(PhotoEditService.EXPOSURE_MIN, PhotoEditService.EXPOSURE_MAX) * 62f
            val exposureValues = floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            )

            val filterValues = when (filter) {
                QuickFilter.Original -> identityMatrix()
                QuickFilter.Mono -> floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
                QuickFilter.Vivid -> floatArrayOf(
                    1.343f, -0.168f, -0.033f, 0f, 6f,
                    -0.078f, 1.434f, -0.114f, 0f, 6f,
                    -0.078f, -0.168f, 1.388f, 0f, 6f,
                    0f, 0f, 0f, 1f, 0f,
                )
                QuickFilter.Warm -> floatArrayOf(
                    1.08f, 0f, 0f, 0f, 8f,
                    0f, 1.0f, 0f, 0f, 2f,
                    0f, 0f, 0.92f, 0f, -6f,
                    0f, 0f, 0f, 1f, 0f,
                )
                QuickFilter.Cool -> floatArrayOf(
                    0.94f, 0f, 0f, 0f, -4f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.08f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f,
                )
            }

            return multiply(multiply(contrastValues, exposureValues), filterValues)
        }

        private fun identityMatrix(): FloatArray = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

        private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
            require(a.size == 20 && b.size == 20)
            val out = FloatArray(20)
            for (row in 0..3) {
                for (col in 0..3) {
                    var sum = 0f
                    for (k in 0..3) {
                        sum += a[row * 5 + k] * b[k * 5 + col]
                    }
                    out[row * 5 + col] = sum
                }
                var translation = a[row * 5 + 4]
                for (k in 0..3) {
                    translation += a[row * 5 + k] * b[k * 5 + 4]
                }
                out[row * 5 + 4] = translation
            }
            return out
        }
    }
}
