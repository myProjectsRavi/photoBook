package com.photobook.app.feature.duplicates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.photobook.app.data.model.PhotoRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DuplicatePhotoFinder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun findDuplicates(records: List<PhotoRecord>): List<DuplicatePhotoGroup> {
        if (records.size < 2) return emptyList()

        return withContext(Dispatchers.IO) {
            val exactGroups = findExactDuplicates(records)
            val exactIds = exactGroups.flatMap { group -> group.photos.map { it.id } }.toSet()
            val similarGroups = findNearDuplicates(records.filterNot { it.id in exactIds })

            (exactGroups + similarGroups)
                .sortedWith(
                    compareByDescending<DuplicatePhotoGroup> { it.photos.size }
                        .thenByDescending { it.totalBytes }
                )
                .take(MAX_GROUPS)
        }
    }

    private fun findExactDuplicates(records: List<PhotoRecord>): List<DuplicatePhotoGroup> {
        return records
            .asSequence()
            .filter { it.fileSize > 0L && it.width > 0 && it.height > 0 }
            .groupBy { ExactCandidateKey(it.fileSize, it.width, it.height) }
            .values
            .filter { it.size > 1 }
            .flatMap { candidates ->
                candidates
                    .mapNotNull { photo -> sha256(photo.uriString)?.let { hash -> hash to photo } }
                    .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                    .values
                    .filter { it.size > 1 }
                    .map { photos ->
                        DuplicatePhotoGroup(
                            id = "exact-${photos.minOf { it.id }}",
                            kind = DuplicateMatchKind.Exact,
                            photos = photos.sortedByDescending { it.dateAdded },
                        )
                    }
            }
    }

    private fun findNearDuplicates(records: List<PhotoRecord>): List<DuplicatePhotoGroup> {
        val byId = records.associateBy { it.id }
        val unionFind = UnionFind<Long>()
        val buckets = mutableMapOf<String, MutableList<HashRecord>>()

        records.forEach { photo ->
            val hash = perceptualHash(photo.uriString) ?: return@forEach
            val current = HashRecord(photo.id, hash)
            unionFind.add(photo.id)

            val candidates = linkedSetOf<HashRecord>()
            repeat(BAND_COUNT) { band ->
                val key = bucketKey(band, hash)
                buckets[key].orEmpty().forEach(candidates::add)
            }

            candidates.forEach { candidate ->
                if (DuplicateHash.hammingDistance(hash, candidate.hash) <= NEAR_DUPLICATE_DISTANCE) {
                    unionFind.union(photo.id, candidate.photoId)
                }
            }

            repeat(BAND_COUNT) { band ->
                buckets.getOrPut(bucketKey(band, hash)) { mutableListOf() } += current
            }
        }

        return unionFind.groups()
            .mapNotNull { ids ->
                val photos = ids.mapNotNull(byId::get)
                if (photos.size < 2) return@mapNotNull null
                DuplicatePhotoGroup(
                    id = "similar-${photos.minOf { it.id }}",
                    kind = DuplicateMatchKind.Similar,
                    photos = photos.sortedByDescending { it.dateAdded },
                )
            }
    }

    private fun bucketKey(band: Int, hash: Long): String {
        return "$band:${DuplicateHash.bandKey(hash, band)}"
    }

    private fun sha256(uriString: String): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
        }.getOrNull()
    }

    private fun perceptualHash(uriString: String): Long? {
        val source = decodeSampledBitmap(Uri.parse(uriString), HASH_SOURCE_MAX_DIMENSION) ?: return null
        val scaled = runCatching {
            Bitmap.createScaledBitmap(source, HASH_WIDTH, HASH_HEIGHT, true)
        }.getOrNull() ?: return null

        var hash = 0L
        var bit = 0
        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH - 1) {
                val left = luminance(scaled.getPixel(x, y))
                val right = luminance(scaled.getPixel(x + 1, y))
                if (left > right) {
                    hash = hash or (1L shl bit)
                }
                bit += 1
            }
        }
        return hash
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimensionPx || bounds.outHeight / sample > maxDimensionPx) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun luminance(pixel: Int): Int {
        val red = android.graphics.Color.red(pixel)
        val green = android.graphics.Color.green(pixel)
        val blue = android.graphics.Color.blue(pixel)
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    private data class ExactCandidateKey(
        val fileSize: Long,
        val width: Int,
        val height: Int,
    )

    private data class HashRecord(
        val photoId: Long,
        val hash: Long,
    )

    private class UnionFind<T> {
        private val parent = mutableMapOf<T, T>()

        fun add(value: T) {
            parent.putIfAbsent(value, value)
        }

        fun union(left: T, right: T) {
            add(left)
            add(right)
            val leftRoot = find(left)
            val rightRoot = find(right)
            if (leftRoot != rightRoot) {
                parent[rightRoot] = leftRoot
            }
        }

        fun groups(): List<List<T>> {
            return parent.keys.groupBy(::find).values.filter { it.size > 1 }
        }

        private fun find(value: T): T {
            val current = parent[value] ?: return value
            if (current == value) return value
            val root = find(current)
            parent[value] = root
            return root
        }
    }

    companion object {
        private const val HASH_WIDTH = 9
        private const val HASH_HEIGHT = 8
        private const val HASH_SOURCE_MAX_DIMENSION = 96
        private const val BAND_COUNT = 8
        private const val NEAR_DUPLICATE_DISTANCE = 8
        private const val MAX_GROUPS = 30
    }
}
