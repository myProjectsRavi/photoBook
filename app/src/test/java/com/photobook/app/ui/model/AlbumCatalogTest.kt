package com.photobook.app.ui.model

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.search.FilterEngine
import com.photobook.app.search.FilterFactory
import com.photobook.app.search.PhotoSource
import com.photobook.app.search.QueryParser
import com.photobook.app.search.TokenClassifier
import org.junit.Test

class AlbumCatalogTest {

    @Test
    fun build_exposesEachSourceOnce_andOmitsEmptySources() {
        val records = listOf(
            photo(1, "/storage/emulated/0/DCIM/Camera", "Camera"),
            photo(2, "/storage/emulated/0/DCIM/Camera", "Camera"),
            photo(3, "/storage/emulated/0/WhatsApp/Media/WhatsApp Images", "WhatsApp Images"),
        )

        val catalog = AlbumCatalogBuilder.build(records, emptyMap())
        val sources = catalog.filter { it.kind == AlbumKind.Source }

        assertThat(sources.map { it.key }).containsExactly(
            "source:whatsapp",
            "source:camera",
        )
        assertThat(sources.count { it.key == "source:camera" }).isEqualTo(1)
        assertThat(sources.none { it.key == "source:telegram" }).isTrue()
    }

    @Test
    fun build_collapsesCaseVariantPath_andDisambiguatesSameNamedFoldersWithoutPathInKey() {
        val records = listOf(
            photo(1, "/storage/emulated/0/Pictures/Trips", "Trips"),
            photo(2, "/STORAGE/EMULATED/0/PICTURES/TRIPS/", "trips"),
            photo(3, "/storage/1234-ABCD/Pictures/Trips", "Trips"),
        )

        val folders = AlbumCatalogBuilder.build(records, emptyMap())
            .filter { it.kind == AlbumKind.Folder }

        assertThat(folders).hasSize(2)
        assertThat(folders.map { it.count }).containsExactly(2, 1)
        assertThat(folders.map { it.label }.distinct()).hasSize(2)
        folders.forEach { descriptor ->
            assertThat(descriptor.key).startsWith("folder:")
            assertThat(descriptor.key).doesNotContain("/storage/")
        }
    }

    @Test
    fun sourceScope_andsWithLiteralTextSearch() {
        val records = listOf(
            photo(
                id = 1,
                path = "/storage/emulated/0/WhatsApp/Media/WhatsApp Images",
                name = "WhatsApp Images",
                ocr = "Invoice paid for lunch",
            ),
            photo(
                id = 2,
                path = "/storage/emulated/0/DCIM/Camera",
                name = "Camera",
                ocr = "Invoice photographed at desk",
            ),
        )
        val scoped = filterAlbumScopeRecords(
            records,
            AlbumScope.Source(PhotoSource.WhatsApp),
        )
        val index = PhotoIndex().apply { setRecords(records) }
        val engine = FilterEngine(index, QueryParser(), TokenClassifier(index), FilterFactory())

        val result = engine.search("invoice", scoped)

        assertThat(result.results.map { it.id }).containsExactly(1L)
    }

    @Test
    fun favoritesScope_preservesOnlyCurrentVisibleFavorites() {
        val records = listOf(
            photo(1, "/storage/emulated/0/DCIM/Camera", "Camera", favorite = true),
            photo(2, "/storage/emulated/0/DCIM/Camera", "Camera", favorite = false),
        )

        val scoped = filterAlbumScopeRecords(records, AlbumScope.Favorites)

        assertThat(scoped.map { it.id }).containsExactly(1L)
    }

    @Test
    fun unknownSourceToken_doesNotCreateDescriptor() {
        assertThat(PhotoSource.fromToken("source:unknown-app")).isNull()
    }

    private fun photo(
        id: Long,
        path: String,
        name: String,
        ocr: String = "",
        favorite: Boolean = false,
    ) = PhotoRecord(
        id = id,
        uriString = "content://album/$id",
        filePath = "$path/$id.jpg",
        fileName = "$id.jpg",
        dateAdded = 1_700_000_000_000L + id,
        year = 2026,
        month = 1,
        dayOfMonth = 1,
        dayOfWeek = 1,
        hourOfDay = 12,
        latitude = null,
        longitude = null,
        city = null,
        state = null,
        country = null,
        fileSize = 1024,
        width = 1000,
        height = 1000,
        mimeType = "image/jpeg",
        folderName = name,
        folderPath = path,
        cameraModel = null,
        isFrontCamera = false,
        isHdr = false,
        isFavorite = favorite,
        ocrText = ocr,
    )
}
