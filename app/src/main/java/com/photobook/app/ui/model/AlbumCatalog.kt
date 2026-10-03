package com.photobook.app.ui.model

import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.search.PhotoSource
import com.photobook.app.search.matchesSource
import java.security.MessageDigest
import java.util.Locale

enum class AlbumKind {
    Favorite,
    Source,
    Folder,
    SmartQuery,
}

sealed interface AlbumScope {
    data object Favorites : AlbumScope
    data class Source(val source: PhotoSource) : AlbumScope
    data class Folder(val normalizedPath: String) : AlbumScope
    data class SmartQuery(val query: String) : AlbumScope
}

data class AlbumDescriptor(
    val key: String,
    val label: String,
    val count: Int,
    val kind: AlbumKind,
    val scope: AlbumScope,
)

data class SmartAlbumDefinition(
    val key: String,
    val label: String,
    val query: String,
)

object AlbumCatalogBuilder {
    val smartDefinitions: List<SmartAlbumDefinition> = listOf(
        SmartAlbumDefinition("receipts", "Receipts", "receipts"),
        SmartAlbumDefinition("documents", "Documents", "document"),
        SmartAlbumDefinition("food", "Food", "food"),
        SmartAlbumDefinition("selfies", "Selfies", "selfie"),
        SmartAlbumDefinition("groups", "Groups", "people"),
        SmartAlbumDefinition("blurry", "Blurry", "blurry"),
        SmartAlbumDefinition("large", "Large files", "large"),
        SmartAlbumDefinition("text", "Text", "with_text"),
        SmartAlbumDefinition("with_location", "With location", "with_location"),
        SmartAlbumDefinition("without_location", "No location", "without_location"),
    )

    fun build(
        records: List<PhotoRecord>,
        smartCounts: Map<String, Int>,
    ): List<AlbumDescriptor> {
        if (records.isEmpty()) return emptyList()

        val result = mutableListOf<AlbumDescriptor>()

        val favoriteCount = records.count(PhotoRecord::isFavorite)
        if (favoriteCount > 0) {
            result += AlbumDescriptor(
                key = "favorite",
                label = "Favorites",
                count = favoriteCount,
                kind = AlbumKind.Favorite,
                scope = AlbumScope.Favorites,
            )
        }

        PhotoSource.all.forEach { source ->
            val count = records.count { it.matchesSource(source) }
            if (count > 0) {
                result += AlbumDescriptor(
                    key = "source:${source.token}",
                    label = source.label,
                    count = count,
                    kind = AlbumKind.Source,
                    scope = AlbumScope.Source(source),
                )
            }
        }

        val folders = records
            .asSequence()
            .filter { it.folderPath.isNotBlank() }
            .groupBy { normalizePath(it.folderPath) }

        val nameFrequency = folders.entries
            .groupingBy { (_, photos) -> normalizedFolderName(photos.first()) }
            .eachCount()

        folders.entries
            .filterNot { (_, photos) ->
                PhotoSource.all.any { source ->
                    photos.all { photo -> photo.matchesSource(source) }
                }
            }
            .sortedWith(
                compareBy<Map.Entry<String, List<PhotoRecord>>> {
                    normalizedFolderName(it.value.first())
                }.thenBy { it.key },
            )
            .forEach { (normalizedPath, photos) ->
                val rawName = photos.first().folderName.trim().ifBlank { "Folder" }
                val normalizedName = normalizedFolderName(photos.first())
                val label = if ((nameFrequency[normalizedName] ?: 0) > 1) {
                    "${rawName} · ${fingerprint(normalizedPath).take(4)}"
                } else {
                    rawName
                }
                result += AlbumDescriptor(
                    key = "folder:${fingerprint(normalizedPath)}",
                    label = label,
                    count = photos.size,
                    kind = AlbumKind.Folder,
                    scope = AlbumScope.Folder(normalizedPath),
                )
            }

        smartDefinitions.forEach { definition ->
            val count = smartCounts[definition.key] ?: 0
            if (count > 0) {
                result += AlbumDescriptor(
                    key = "smart:${definition.key}",
                    label = definition.label,
                    count = count,
                    kind = AlbumKind.SmartQuery,
                    scope = AlbumScope.SmartQuery(definition.query),
                )
            }
        }

        return result
    }

    fun normalizePath(path: String): String =
        path.trim().replace('\\', '/').trimEnd('/').lowercase(Locale.ROOT)

    private fun normalizedFolderName(photo: PhotoRecord): String =
        photo.folderName.trim().lowercase(Locale.ROOT)

    private fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

fun filterAlbumScopeRecords(
    records: List<PhotoRecord>,
    scope: AlbumScope?,
    smartQueryIds: Set<Long> = emptySet(),
): List<PhotoRecord> = when (scope) {
    null -> records
    AlbumScope.Favorites -> records.filter(PhotoRecord::isFavorite)
    is AlbumScope.Source -> records.filter { it.matchesSource(scope.source) }
    is AlbumScope.Folder -> records.filter {
        AlbumCatalogBuilder.normalizePath(it.folderPath) == scope.normalizedPath
    }
    is AlbumScope.SmartQuery -> records.filter { it.id in smartQueryIds }
}


object AlbumPersonalizationPolicy {
    const val MAX_PINNED_ALBUMS = 6

    fun sanitizePinnedKeys(
        requestedKeys: List<String>,
        catalog: List<AlbumDescriptor>,
    ): List<String> {
        val validKeys = catalog.asSequence().map(AlbumDescriptor::key).toHashSet()
        return requestedKeys
            .asSequence()
            .filter(validKeys::contains)
            .distinct()
            .take(MAX_PINNED_ALBUMS)
            .toList()
    }

    fun partition(
        catalog: List<AlbumDescriptor>,
        requestedKeys: List<String>,
    ): Pair<List<AlbumDescriptor>, List<AlbumDescriptor>> {
        val pinnedKeys = sanitizePinnedKeys(requestedKeys, catalog)
        val byKey = catalog.associateBy(AlbumDescriptor::key)
        val pinned = pinnedKeys.mapNotNull(byKey::get)
        val pinnedKeySet = pinnedKeys.toHashSet()
        val unpinned = catalog.filterNot { it.key in pinnedKeySet }
        return pinned to unpinned
    }
}
