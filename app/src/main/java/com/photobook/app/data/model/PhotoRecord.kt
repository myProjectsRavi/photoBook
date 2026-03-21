package com.photobook.app.data.model

data class PhotoRecord(
    val id: Long,
    val uriString: String,
    val filePath: String,
    val fileName: String,
    val dateAdded: Long,
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val latitude: Double?,
    val longitude: Double?,
    val city: String?,
    val state: String?,
    val country: String?,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val folderName: String,
    val folderPath: String,
    val cameraModel: String?,
    val isFrontCamera: Boolean,
    val isHdr: Boolean,
    val mlTags: List<MLTag> = emptyList(),
) {
    val aspectRatio: Float
        get() = if (height == 0) 1f else width.toFloat() / height.toFloat()

    fun hasMlTag(keyword: String, threshold: Float): Boolean {
        return mlTags.any {
            it.confidence >= threshold && it.label.contains(keyword, ignoreCase = true)
        }
    }
}
