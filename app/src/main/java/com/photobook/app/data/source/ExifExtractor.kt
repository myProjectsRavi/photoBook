package com.photobook.app.data.source

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ExifExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class ExifData(
        val latitude: Double?,
        val longitude: Double?,
        val cameraModel: String?,
        val isFrontCamera: Boolean,
        val isHdr: Boolean,
    )

    fun extract(uriString: String, fallbackPath: String = ""): ExifData {
        return runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { input ->
                val exif = ExifInterface(input)
                val latLong = exif.latLong
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL).orEmpty()
                val lensMake = exif.getAttribute(ExifInterface.TAG_LENS_MAKE).orEmpty()
                val lowerPath = fallbackPath.lowercase()
                val isFront =
                    lensModel.contains("front", ignoreCase = true) ||
                    lensMake.contains("front", ignoreCase = true) ||
                    lowerPath.contains("front") ||
                    lowerPath.contains("selfie")

                val sceneType = exif.getAttribute(ExifInterface.TAG_SCENE_TYPE).orEmpty()
                val hdr = sceneType.contains("hdr", ignoreCase = true) ||
                    lowerPath.contains("hdr") ||
                    lowerPath.contains("_hdr")

                ExifData(
                    latitude = latLong?.getOrNull(0),
                    longitude = latLong?.getOrNull(1),
                    cameraModel = model,
                    isFrontCamera = isFront,
                    isHdr = hdr,
                )
            } ?: ExifData(null, null, null, false, false)
        }.getOrDefault(ExifData(null, null, null, false, false))
    }
}
