package com.photobook.app.search

import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ml.LabelMapping
import javax.inject.Inject
import kotlin.math.max

class SearchRanker @Inject constructor() {

    fun rank(
        records: List<PhotoRecord>,
        tokens: List<QueryToken>,
        normalizedQuery: String,
        context: SearchContext,
    ): List<PhotoRecord> {
        if (records.size < 2 || tokens.isEmpty()) return records
        if (tokens.any { it is TemporalToken && it.keyword == "oldest" }) {
            return records.sortedBy { photo -> photo.dateAdded }
        }
        if (tokens.any { it is TemporalToken && it.keyword == "recent" }) {
            return records.sortedByDescending { photo -> photo.dateAdded }
        }

        return records.sortedWith(
            compareByDescending<PhotoRecord> { photo ->
                score(photo, tokens, normalizedQuery, context)
            }.thenByDescending { photo -> photo.dateAdded },
        )
    }

    fun score(
        photo: PhotoRecord,
        tokens: List<QueryToken>,
        normalizedQuery: String,
        context: SearchContext,
    ): Double {
        var score = 0.0
        var coveredTokens = 0
        val ocr = photo.ocrText.lowercase()
        val fileName = photo.fileName.lowercase()
        val folderName = photo.folderName.lowercase()
        val folderPath = photo.folderPath.lowercase()

        if (normalizedQuery.length >= 3 && ocr.contains(normalizedQuery)) {
            score += 100.0
        }

        tokens.forEach { token ->
            val tokenScore = tokenScore(photo, token, ocr, fileName, folderName, folderPath)
            if (tokenScore > 0.0) {
                coveredTokens += 1
                score += tokenScore
            }
        }

        if (coveredTokens > 1) {
            score += coveredTokens * 8.0
        }
        if (photo.isFavorite) {
            score += 20.0
        }
        score += recencyBoost(photo.dateAdded, context.nowMillis)
        return score
    }

    private fun tokenScore(
        photo: PhotoRecord,
        token: QueryToken,
        ocr: String,
        fileName: String,
        folderName: String,
        folderPath: String,
    ): Double {
        return when (token) {
            is TextToken -> textScore(token.keyword, ocr, fileName, folderName, folderPath)
            is FolderToken -> if (folderPath.contains(token.keyword) || folderName.contains(token.keyword)) 40.0 else 0.0
            is SourceToken -> if (photo.matchesSource(token.source)) 40.0 else 0.0
            is LocationToken -> locationScore(photo, token.keyword)
            is MLTagToken -> mlScore(photo, token.keyword)
            is PropertyToken -> propertyScore(photo, token.keyword)
            is MonthToken -> if (photo.month == token.month) 12.0 else 0.0
            is YearToken -> if (photo.year == token.year) 12.0 else 0.0
            is DayOfWeekToken -> if (photo.dayOfWeek == token.day) 8.0 else 0.0
            is TimeOfDayToken -> 8.0
            is RelativeDateToken,
            is TemporalToken,
            is UnknownToken,
            -> 0.0
        }
    }

    private fun textScore(
        keyword: String,
        ocr: String,
        fileName: String,
        folderName: String,
        folderPath: String,
    ): Double {
        var score = 0.0
        if (ocr.contains(keyword)) score += 70.0
        if (fileName.contains(keyword)) score += 45.0
        if (folderName.contains(keyword) || folderPath.contains(keyword)) score += 30.0
        return score
    }

    private fun mlScore(photo: PhotoRecord, keyword: String): Double {
        val canonical = LabelMapping.canonicalKeyword(keyword)
        val threshold = LabelMapping.threshold(canonical)
        val match = photo.mlTags
            .filter { tag -> tag.label.equals(canonical, ignoreCase = true) && tag.confidence >= threshold }
            .maxByOrNull { tag -> tag.confidence }
            ?: return 0.0
        return 30.0 * match.confidence
    }

    private fun locationScore(photo: PhotoRecord, keyword: String): Double {
        val lower = keyword.lowercase()
        return if (
            photo.city?.contains(lower, ignoreCase = true) == true ||
            photo.state?.contains(lower, ignoreCase = true) == true ||
            photo.country?.contains(lower, ignoreCase = true) == true
        ) {
            30.0
        } else {
            0.0
        }
    }

    private fun propertyScore(photo: PhotoRecord, keyword: String): Double {
        return when (keyword) {
            "favorite", "favorites", "starred" -> if (photo.isFavorite) 20.0 else 0.0
            "screenshot", "screenshots" -> if (photo.matchesSource(PhotoSource.Screenshots)) 22.0 else 0.0
            "document" -> if (photo.hasMlTag("document", 0.70f) || photo.ocrText.isNotBlank()) 18.0 else 0.0
            else -> 0.0
        }
    }

    private fun recencyBoost(dateAdded: Long, nowMillis: Long): Double {
        if (dateAdded <= 0L || nowMillis <= 0L) return 0.0
        val ageDays = max(0.0, (nowMillis - dateAdded).toDouble() / MILLIS_PER_DAY)
        return (10.0 - ageDays.coerceAtMost(30.0) / 3.0).coerceAtLeast(0.0)
    }

    private companion object {
        private const val MILLIS_PER_DAY = 24.0 * 60.0 * 60.0 * 1000.0
    }
}
