package com.photobook.app.data.model

data class RawPhotoData(
    val id: Long,
    val uriString: String,
    val filePath: String,
    val fileName: String,
    val dateAdded: Long,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val folderName: String,
    val folderPath: String,
    val generationModified: Long? = null,
)
