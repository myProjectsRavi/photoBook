package com.photobook.app.feature.duplicates

import com.photobook.app.data.model.PhotoRecord
import kotlin.math.abs

class BurstBestShotPicker {
    fun pick(photos: List<PhotoRecord>, anchorIndex: Int): BestShotRecommendation? {
        if (photos.size < MIN_BURST_COUNT || anchorIndex !in photos.indices) return null
        val anchor = photos[anchorIndex]
        if (anchor.dateAdded <= 0L) return null

        val members = ArrayList<Pair<Int, PhotoRecord>>(8)
        members += anchorIndex to anchor

        var left = anchorIndex - 1
        while (left >= 0) {
            val candidate = photos[left]
            if (!withinBurstTimeWindow(anchor, candidate)) break
            if (belongsToBurst(anchor, candidate)) {
                members += left to candidate
            }
            left -= 1
        }

        var right = anchorIndex + 1
        while (right < photos.size) {
            val candidate = photos[right]
            if (!withinBurstTimeWindow(anchor, candidate)) break
            if (belongsToBurst(anchor, candidate)) {
                members += right to candidate
            }
            right += 1
        }

        members.sortBy { (_, photo) -> photo.dateAdded }

        if (members.size < MIN_BURST_COUNT) return null

        val best = members.maxByOrNull { (_, photo) -> score(photo) } ?: return null
        return BestShotRecommendation(
            bestIndex = best.first,
            memberIndices = members.map { it.first }.toSet(),
        )
    }

    private fun score(photo: PhotoRecord): Double {
        val sharpness = (photo.blurScore ?: 0.0).coerceAtLeast(0.0)
        val megapixels = (photo.width.toDouble() * photo.height.toDouble()) / 1_000_000.0
        val favoriteBonus = if (photo.isFavorite) 0.75 else 0.0
        val sizeBonus = (photo.fileSize / 1_000_000.0).coerceIn(0.0, 4.0) * 0.08
        return (sharpness * 0.68) + (megapixels * 0.24) + favoriteBonus + sizeBonus
    }

    private fun withinBurstTimeWindow(anchor: PhotoRecord, candidate: PhotoRecord): Boolean {
        if (candidate.dateAdded <= 0L) return false
        return abs(anchor.dateAdded - candidate.dateAdded) <= BURST_WINDOW_MS
    }

    private fun belongsToBurst(anchor: PhotoRecord, candidate: PhotoRecord): Boolean {
        if (anchor.id == candidate.id) return true
        if (anchor.dateAdded <= 0L || candidate.dateAdded <= 0L) return false
        val delta = abs(anchor.dateAdded - candidate.dateAdded)
        if (delta > BURST_WINDOW_MS) return false
        if (!anchor.folderPath.equals(candidate.folderPath, ignoreCase = true)) return false
        val widthDelta = abs(anchor.width - candidate.width).toFloat() / maxOf(anchor.width, candidate.width).toFloat()
        val heightDelta = abs(anchor.height - candidate.height).toFloat() / maxOf(anchor.height, candidate.height).toFloat()
        if (widthDelta > BURST_DIMENSION_DELTA || heightDelta > BURST_DIMENSION_DELTA) return false
        return abs(anchor.aspectRatio - candidate.aspectRatio) <= BURST_ASPECT_RATIO_DELTA
    }

    companion object {
        private const val MIN_BURST_COUNT = 3
        private const val BURST_WINDOW_MS = 6_000L
        private const val BURST_DIMENSION_DELTA = 0.08f
        private const val BURST_ASPECT_RATIO_DELTA = 0.12f
    }
}

data class BestShotRecommendation(
    val bestIndex: Int,
    val memberIndices: Set<Int>,
)
