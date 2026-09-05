package com.photobook.app.feature.duplicates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.photobook.app.data.db.PhotoDao
import com.photobook.app.data.model.PhotoRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class DuplicatePhotoFinder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoDao: PhotoDao,
    private val perceptualHashComputer: PerceptualHashComputer,
    private val blurScoreComputer: BlurScoreComputer,
) {
    suspend fun findDuplicates(records: List<PhotoRecord>): List<DuplicatePhotoGroup> {
        if (records.size < 2) return emptyList()

        return withContext(Dispatchers.IO) {
            val exactCandidates = exactDuplicateCandidates(records)
            val exactGroups = findExactDuplicates(exactCandidates)
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

    private suspend fun exactDuplicateCandidates(records: List<PhotoRecord>): List<PhotoRecord> {
        if (records.size < DB_PREFILTER_MIN_RECORDS) return records

        return runCatching {
            val dbPhotoCount = photoDao.getPhotoCount()
            if (dbPhotoCount != records.size) {
                return@runCatching records
            }

            val candidateIds = photoDao.getExactDuplicateCandidateIds()
            if (candidateIds.isEmpty()) {
                return@runCatching emptyList()
            }

            val candidateIdSet = candidateIds.toHashSet()
            records.filter { photo -> photo.id in candidateIdSet }
        }.getOrDefault(records)
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
                    .mapNotNull { photo -> partialHash(photo.uriString)?.let { hash -> hash to photo } }
                    .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                    .values
                    .filter { it.size > 1 }
                    .flatMap { partialMatchCandidates ->
                        partialMatchCandidates
                            .mapNotNull { photo -> sha256(photo.uriString)?.let { hash -> hash to photo } }
                            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                            .values
                            .filter { it.size > 1 }
                    }
                    .map { photos ->
                        DuplicatePhotoGroup(
                            id = "exact-${photos.minOf { it.id }}",
                            kind = DuplicateMatchKind.Exact,
                            photos = photos.sortedByDescending { it.dateAdded },
                        )
                    }
            }
    }

    private fun partialHash(uriString: String): String? {
        // This hash is only a bounded-I/O prefilter; exact duplicates are still verified below with
        // a full-file SHA-256. Use SHA-256 here as well so no legacy/weak digest remains in the
        // production duplicate path without changing grouping semantics for equal prefixes.
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                var totalRead = 0
                while (totalRead < PARTIAL_HASH_LIMIT) {
                    val toRead = minOf(buffer.size, PARTIAL_HASH_LIMIT - totalRead)
                    val read = input.read(buffer, 0, toRead)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    totalRead += read
                }
            } ?: return null
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }.getOrNull()
    }

    private fun findNearDuplicates(records: List<PhotoRecord>): List<DuplicatePhotoGroup> {
        val byId = records.associateBy { it.id }
        val unionFind = UnionFind<Long>()
        val buckets = mutableMapOf<String, MutableList<HashRecord>>()

        records.forEach { photo ->
            val hash = photo.perceptualHash
                ?: perceptualHashComputer.computeFromUri(photo.uriString)
                ?: return@forEach
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

    private fun findBlurryGroup(records: List<PhotoRecord>): DuplicatePhotoGroup? {
        val blurry = records
            .asSequence()
            .filter { it.width > 0 && it.height > 0 }
            .mapNotNull { photo ->
                val score = blurScoreComputer.scoreFromUri(photo.uriString) ?: return@mapNotNull null
                if (score <= BLUR_SCORE_THRESHOLD) photo to score else null
            }
            .sortedBy { (_, score) -> score }
            .take(MAX_BLUR_ITEMS)
            .map { (photo, _) -> photo }
            .toList()

        if (blurry.isEmpty()) return null
        return DuplicatePhotoGroup(
            id = "blurry-${blurry.minOf { photo -> photo.id }}",
            kind = DuplicateMatchKind.Blurry,
            photos = blurry,
        )
    }

    private fun belongsToSameBurst(previous: PhotoRecord, current: PhotoRecord): Boolean {
        val timeDelta = current.dateAdded - previous.dateAdded
        if (timeDelta !in 0..BURST_MAX_GAP_MS) return false
        if (!sameAspect(previous, current)) return false
        if (!sameCamera(previous, current)) return false
        return true
    }

    private fun sameAspect(a: PhotoRecord, b: PhotoRecord): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return false
        val ratioA = a.width.toDouble() / a.height.toDouble()
        val ratioB = b.width.toDouble() / b.height.toDouble()
        return abs(ratioA - ratioB) <= BURST_ASPECT_TOLERANCE
    }

    private fun sameCamera(a: PhotoRecord, b: PhotoRecord): Boolean {
        val modelA = a.cameraModel?.trim()?.lowercase().orEmpty()
        val modelB = b.cameraModel?.trim()?.lowercase().orEmpty()
        if (modelA.isBlank() || modelB.isBlank()) return true
        return modelA == modelB
    }

    private fun analyzeHeroFeatures(uriString: String): HeroAnalysis? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, HERO_ANALYSIS_EDGE_PX)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null

        return try {
            HeroAnalysis(
                sharpnessVariance = blurScoreComputer.score(bitmap),
                exposureBalance = exposureBalance(bitmap),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun exposureBalance(bitmap: Bitmap): Double {
        if (bitmap.width <= 0 || bitmap.height <= 0) return 0.0
        val stepX = (bitmap.width / EXPOSURE_GRID_SIZE).coerceAtLeast(1)
        val stepY = (bitmap.height / EXPOSURE_GRID_SIZE).coerceAtLeast(1)
        var total = 0.0
        var count = 0
        var y = stepY / 2
        while (y < bitmap.height) {
            var x = stepX / 2
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val luma = (
                    android.graphics.Color.red(color) * 0.2126 +
                        android.graphics.Color.green(color) * 0.7152 +
                        android.graphics.Color.blue(color) * 0.0722
                    ) / 255.0
                total += luma
                count += 1
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return 0.0
        val average = total / count
        return (1.0 - (abs(average - TARGET_EXPOSURE) / MAX_EXPOSURE_DISTANCE))
            .coerceIn(0.0, 1.0)
    }

    private fun faceConfidenceHint(photo: PhotoRecord): Double {
        val tags = photo.mlTags.map { tag -> tag.lowercase() }
        return when {
            tags.any { tag -> tag == "person" || tag == "people" || tag == "portrait" } -> 1.0
            tags.any { tag -> tag.contains("face") } -> 0.85
            else -> 0.0
        }
    }

    private fun sha256(uriString: String): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }.getOrNull()
    }

    private fun calculateSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (width / sample > maxEdge || height / sample > maxEdge) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun priorityOf(kind: DuplicateMatchKind): Int = when (kind) {
        DuplicateMatchKind.Exact -> 4
        DuplicateMatchKind.Similar -> 3
        DuplicateMatchKind.Burst -> 2
        DuplicateMatchKind.Blurry -> 1
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

    private data class HeroAnalysis(
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

    private companion object {
        private const val DB_PREFILTER_MIN_RECORDS = 2_000
        private const val PARTIAL_HASH_LIMIT = 256 * 1024
        private const val NEAR_DUPLICATE_DISTANCE = 8
        private const val BAND_COUNT = 4
        private const val MAX_GROUPS = 120
        private const val BURST_MAX_GAP_MS = 1_500L
        private const val BURST_MIN_COUNT = 3
        private const val MAX_BURST_GROUPS = 24
        private const val BURST_ASPECT_TOLERANCE = 0.035
        private const val MAX_BLUR_ITEMS = 60
        private const val BLUR_SCORE_THRESHOLD = 42.0
        private const val HERO_ANALYSIS_EDGE_PX = 720
        private const val EXPOSURE_GRID_SIZE = 16
        private const val TARGET_EXPOSURE = 0.52
        private const val MAX_EXPOSURE_DISTANCE = 0.52
        private const val HERO_SHARPNESS_WEIGHT = 0.58
        private const val HERO_EXPOSURE_WEIGHT = 0.27
        private const val HERO_FACE_WEIGHT = 0.15
    }
}
