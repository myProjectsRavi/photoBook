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
import kotlin.math.abs

class DuplicatePhotoFinder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun findDuplicates(records: List<PhotoRecord>): List<DuplicatePhotoGroup> {
        if (records.size < 2) return emptyList()

        return withContext(Dispatchers.IO) {
            val exactGroups = findExactDuplicates(records)
            val exactIds = exactGroups.flatMap { group -> group.photos.map { it.id } }.toSet()
            val remainingForSimilar = records.filterNot { it.id in exactIds }
            val similarGroups = findNearDuplicates(remainingForSimilar)
            val similarIds = similarGroups.flatMap { group -> group.photos.map { it.id } }.toSet()

            val remainingForBurst = records.filterNot { it.id in exactIds || it.id in similarIds }
            val burstGroups = findBurstGroups(remainingForBurst)
            val burstIds = burstGroups.flatMap { group -> group.photos.map { it.id } }.toSet()

            val remainingForBlur = records.filterNot {
                it.id in exactIds || it.id in similarIds || it.id in burstIds
            }
            val blurryGroup = findBlurryGroup(remainingForBlur)

            val allGroups = buildList {
                addAll(exactGroups)
                addAll(similarGroups)
                addAll(burstGroups)
                if (blurryGroup != null) {
                    add(blurryGroup)
                }
            }

            allGroups
                .sortedWith(
                    compareByDescending<DuplicatePhotoGroup> { priorityOf(it.kind) }
                        .thenByDescending { it.photos.size }
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
            val hash = photo.perceptualHash ?: return@forEach
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

    private fun findBurstGroups(records: List<PhotoRecord>): List<DuplicatePhotoGroup> {
        if (records.size < BURST_MIN_COUNT) return emptyList()

        val sorted = records
            .asSequence()
            .filter { it.dateAdded > 0L && it.width > 0 && it.height > 0 }
            .sortedBy { it.dateAdded }
            .toList()

        if (sorted.size < BURST_MIN_COUNT) return emptyList()

        val groups = mutableListOf<DuplicatePhotoGroup>()
        var current = mutableListOf<PhotoRecord>()
        sorted.forEach { photo ->
            if (current.isEmpty()) {
                current += photo
                return@forEach
            }

            val last = current.last()
            if (belongsToSameBurst(last, photo)) {
                current += photo
            } else {
                if (current.size >= BURST_MIN_COUNT) {
                    groups += buildBurstGroup(current)
                }
                current = mutableListOf(photo)
            }
        }

        if (current.size >= BURST_MIN_COUNT) {
            groups += buildBurstGroup(current)
        }

        return groups.take(MAX_BURST_GROUPS)
    }

    private fun buildBurstGroup(photos: List<PhotoRecord>): DuplicatePhotoGroup {
        val candidates = photos.map { photo ->
            val analysis = analyzeHeroFeatures(photo.uriString)
            HeroCandidate(
                photo = photo,
                sharpness = analysis?.sharpnessVariance ?: 0.0,
                exposureBalance = analysis?.exposureBalance ?: 0.35,
                faceConfidenceHint = faceConfidenceHint(photo),
            )
        }

        val sharpnessMin = candidates.minOfOrNull { candidate -> candidate.sharpness } ?: 0.0
        val sharpnessMax = candidates.maxOfOrNull { candidate -> candidate.sharpness } ?: 0.0

        val scored = candidates.map { candidate ->
            val sharpnessNorm = normalizeMetric(candidate.sharpness, sharpnessMin, sharpnessMax)
            val score = (sharpnessNorm * HERO_SHARPNESS_WEIGHT) +
                (candidate.exposureBalance * HERO_EXPOSURE_WEIGHT) +
                (candidate.faceConfidenceHint * HERO_FACE_WEIGHT)
            candidate.copy(score = score)
        }

        val heroPhotoId = scored.maxByOrNull { candidate -> candidate.score }?.photo?.id
        val ordered = photos.sortedWith(
            compareByDescending<PhotoRecord> { photo -> photo.id == heroPhotoId }
                .thenByDescending { photo -> photo.dateAdded },
        )

        return DuplicatePhotoGroup(
            id = "burst-${photos.minOf { photo -> photo.id }}",
            kind = DuplicateMatchKind.Burst,
            photos = ordered,
            heroPhotoId = heroPhotoId,
        )
    }

    private fun belongsToSameBurst(left: PhotoRecord, right: PhotoRecord): Boolean {
        if (right.dateAdded < left.dateAdded) return false
        if (right.dateAdded - left.dateAdded > BURST_WINDOW_MS) return false
        if (!left.folderPath.equals(right.folderPath, ignoreCase = true)) return false

        val leftRatio = left.aspectRatio
        val rightRatio = right.aspectRatio
        if (abs(leftRatio - rightRatio) > BURST_ASPECT_RATIO_DELTA) return false

        val widthDelta = abs(left.width - right.width).toFloat() / maxOf(left.width, right.width).toFloat()
        val heightDelta = abs(left.height - right.height).toFloat() / maxOf(left.height, right.height).toFloat()
        return widthDelta <= BURST_DIMENSION_DELTA && heightDelta <= BURST_DIMENSION_DELTA
    }

    private fun findBlurryGroup(records: List<PhotoRecord>): DuplicatePhotoGroup? {
        if (records.size < 2) return null

        val blurryCandidates = mutableListOf<Pair<PhotoRecord, Double>>()
        records.forEach { photo ->
            val score = photo.blurScore ?: return@forEach
            if (score <= BLUR_VARIANCE_THRESHOLD) {
                blurryCandidates += photo to score
            }
        }

        if (blurryCandidates.size < MIN_BLUR_GROUP_SIZE) return null

        val rankedPhotos = blurryCandidates
            .sortedWith(
                compareBy<Pair<PhotoRecord, Double>> { it.second }
                    .thenByDescending { it.first.fileSize }
            )
            .take(MAX_BLUR_CANDIDATES)
            .map { it.first }

        if (rankedPhotos.size < MIN_BLUR_GROUP_SIZE) return null

        return DuplicatePhotoGroup(
            id = "blur-${rankedPhotos.minOf { it.id }}",
            kind = DuplicateMatchKind.Blurry,
            photos = rankedPhotos,
        )
    }

    private fun analyzeHeroFeatures(uriString: String): HeroFeatureAnalysis? {
        val bitmap = decodeSampledBitmap(Uri.parse(uriString), HERO_SAMPLE_MAX_DIMENSION) ?: return null
        if (bitmap.width < 3 || bitmap.height < 3) {
            bitmap.recycleSafely()
            return null
        }

        return try {
            val width = bitmap.width
            val height = bitmap.height
            val rowPixels = IntArray(width)
            var rowAbove = IntArray(width)
            var rowCenter = IntArray(width)
            var rowBelow = IntArray(width)

            fillLuminanceRow(bitmap, rowPixels, rowAbove, 0)
            fillLuminanceRow(bitmap, rowPixels, rowCenter, 1)
            fillLuminanceRow(bitmap, rowPixels, rowBelow, 2)

            var luminanceSum = rowAbove.sumOf { value -> value.toLong() } +
                rowCenter.sumOf { value -> value.toLong() } +
                rowBelow.sumOf { value -> value.toLong() }
            var luminanceCount = width * 3L
            var laplacianSum = 0.0
            var laplacianSquares = 0.0
            var laplacianCount = 0

            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val center = rowCenter[x]
                    val up = rowAbove[x]
                    val down = rowBelow[x]
                    val left = rowCenter[x - 1]
                    val right = rowCenter[x + 1]
                    val laplacian = (4 * center - up - down - left - right).toDouble()
                    laplacianSum += laplacian
                    laplacianSquares += laplacian * laplacian
                    laplacianCount += 1
                }

                if (y < height - 2) {
                    val reusable = rowAbove
                    rowAbove = rowCenter
                    rowCenter = rowBelow
                    rowBelow = reusable
                    fillLuminanceRow(bitmap, rowPixels, rowBelow, y + 2)
                    luminanceSum += rowBelow.sumOf { value -> value.toLong() }
                    luminanceCount += width
                }
            }

            if (laplacianCount == 0 || luminanceCount == 0L) return null
            val laplacianMean = laplacianSum / laplacianCount.toDouble()
            val sharpnessVariance = (laplacianSquares / laplacianCount.toDouble()) -
                (laplacianMean * laplacianMean)

            val meanLuminance = luminanceSum.toDouble() / luminanceCount.toDouble()
            val exposureBalance = (
                1.0 - (abs(meanLuminance - TARGET_MEAN_LUMINANCE) / TARGET_MEAN_LUMINANCE)
                ).coerceIn(0.0, 1.0)

            HeroFeatureAnalysis(
                sharpnessVariance = sharpnessVariance,
                exposureBalance = exposureBalance,
            )
        } finally {
            bitmap.recycleSafely()
        }
    }

    private fun faceConfidenceHint(photo: PhotoRecord): Double {
        val relevantTags = listOf("selfie", "face", "people", "person", "portrait", "smile")
        val maxConfidence = photo.mlTags
            .filter { tag ->
                relevantTags.any { keyword -> tag.label.contains(keyword, ignoreCase = true) }
            }
            .maxOfOrNull { tag -> tag.confidence.toDouble() }
            ?: 0.0
        return maxConfidence.coerceIn(0.0, 1.0)
    }

    private fun normalizeMetric(value: Double, min: Double, max: Double): Double {
        if (max - min <= NORMALIZE_EPSILON) return 0.5
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    private fun fillLuminanceRow(
        bitmap: Bitmap,
        rowPixels: IntArray,
        targetLuminance: IntArray,
        y: Int,
    ) {
        bitmap.getPixels(rowPixels, 0, bitmap.width, 0, y, bitmap.width, 1)
        for (x in rowPixels.indices) {
            val pixel = rowPixels[x]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            targetLuminance[x] = (r * 299 + g * 587 + b * 114) / 1000
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

    private fun Bitmap.recycleSafely() {
        runCatching {
            if (!isRecycled) {
                recycle()
            }
        }
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

    private data class HeroFeatureAnalysis(
        val sharpnessVariance: Double,
        val exposureBalance: Double,
    )

    private data class HeroCandidate(
        val photo: PhotoRecord,
        val sharpness: Double,
        val exposureBalance: Double,
        val faceConfidenceHint: Double,
        val score: Double = 0.0,
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
        private const val BAND_COUNT = 8
        private const val NEAR_DUPLICATE_DISTANCE = 8
        private const val MAX_GROUPS = 30
        private const val BURST_MIN_COUNT = 3
        private const val BURST_WINDOW_MS = 2_500L
        private const val BURST_ASPECT_RATIO_DELTA = 0.16f
        private const val BURST_DIMENSION_DELTA = 0.16f
        private const val MAX_BURST_GROUPS = 15
        private const val HERO_SAMPLE_MAX_DIMENSION = 320
        private const val BLUR_VARIANCE_THRESHOLD = 95.0
        private const val MIN_BLUR_GROUP_SIZE = 2
        private const val MAX_BLUR_CANDIDATES = 36
        private const val TARGET_MEAN_LUMINANCE = 128.0
        private const val HERO_SHARPNESS_WEIGHT = 0.55
        private const val HERO_EXPOSURE_WEIGHT = 0.25
        private const val HERO_FACE_WEIGHT = 0.20
        private const val NORMALIZE_EPSILON = 0.0001

        private fun priorityOf(kind: DuplicateMatchKind): Int {
            return when (kind) {
                DuplicateMatchKind.Exact -> 4
                DuplicateMatchKind.Similar -> 3
                DuplicateMatchKind.Burst -> 2
                DuplicateMatchKind.Blurry -> 1
            }
        }
    }
}
