package com.photobook.app.search

import com.photobook.app.data.model.PhotoRecord

enum class PhotoSource(
    val token: String,
    val label: String,
) {
    WhatsApp(token = "whatsapp", label = "WhatsApp"),
    Telegram(token = "telegram", label = "Telegram"),
    Camera(token = "camera", label = "Camera"),
    Downloads(token = "downloads", label = "Downloads"),
    Screenshots(token = "screenshots", label = "Screenshots");

    companion object {
        val all: List<PhotoSource> = entries.toList()

        fun fromToken(token: String): PhotoSource? {
            val normalized = token.trim().lowercase()
                .removePrefix("source:")
            return when (normalized) {
                "whatsapp" -> WhatsApp
                "telegram" -> Telegram
                "camera", "dcim" -> Camera
                "download", "downloads" -> Downloads
                "screenshot", "screenshots" -> Screenshots
                else -> null
            }
        }
    }
}

fun PhotoRecord.matchesSource(source: PhotoSource): Boolean {
    val path = buildString {
        append(folderPath)
        append(' ')
        append(folderName)
        append(' ')
        append(filePath)
        append(' ')
        append(fileName)
    }.lowercase()

    return when (source) {
        PhotoSource.WhatsApp -> path.contains("whatsapp")
        PhotoSource.Telegram -> path.contains("telegram") || path.contains("org.telegram")
        PhotoSource.Camera -> path.contains("dcim/camera") || path.contains("/camera")
        PhotoSource.Downloads -> path.contains("/download") || path.contains("downloads")
        PhotoSource.Screenshots -> path.contains("screenshot")
    }
}

enum class UtilityKind {
    Screenshot,
    Download,
    Social,
    Document,
    Meme,
}

fun PhotoRecord.utilityKind(): UtilityKind? {
    val path = buildString {
        append(folderPath)
        append(' ')
        append(folderName)
        append(' ')
        append(filePath)
        append(' ')
        append(fileName)
    }.lowercase()

    if (matchesSource(PhotoSource.Screenshots)) return UtilityKind.Screenshot
    if (matchesSource(PhotoSource.Downloads)) return UtilityKind.Download
    if (path.contains("whatsapp") || path.contains("telegram")) return UtilityKind.Social
    if (path.contains("document") || path.contains("scan") || path.contains("receipt")) return UtilityKind.Document
    if (path.contains("meme")) return UtilityKind.Meme
    return null
}

fun PhotoRecord.isUtilityPhoto(): Boolean = utilityKind() != null
