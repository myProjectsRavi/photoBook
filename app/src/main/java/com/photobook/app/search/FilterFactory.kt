package com.photobook.app.search

import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.notes.PhotoNoteStore
import com.photobook.app.ml.LabelMapping
import com.photobook.app.util.Constants
import com.photobook.app.util.DateUtils
import java.time.Duration
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

typealias PhotoFilter = (PhotoRecord) -> Boolean

data class SearchContext(
    val nowMillis: Long = System.currentTimeMillis(),
    val nearMeLatitude: Double? = null,
    val nearMeLongitude: Double? = null,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val officeLatitude: Double? = null,
    val officeLongitude: Double? = null,
    val radiusKm: Double = 1.0,
    val homeCountry: String? = null,
)

class FilterFactory private constructor(
    private val noteContains: (Long, String) -> Boolean,
) {

    @Inject
    constructor(photoNoteStore: PhotoNoteStore) : this(photoNoteStore::noteContains)

    constructor() : this({ _, _ -> false })

    fun create(token: QueryToken, context: SearchContext): PhotoFilter? {
        return when (token) {
            is TemporalToken -> temporalFilter(token, context.nowMillis)
            is MonthToken -> { photo -> photo.month == token.month }
            is DayOfWeekToken -> { photo -> photo.dayOfWeek == token.day }
            is TimeOfDayToken -> timeOfDayFilter(token)
            is YearToken -> { photo -> photo.year == token.year }
            is RelativeDateToken -> relativeDateFilter(token, context.nowMillis)
            is FolderToken -> folderFilter(token.keyword)
            is PropertyToken -> propertyFilter(token.keyword)
            is LocationToken -> locationFilter(token.keyword, context)
            is MLTagToken -> mlFilter(token.keyword)
            is SourceToken -> sourceFilter(token.source)
            is TextToken -> textFilter(token.keyword)
            is UnknownToken -> null
        }
    }

    private fun temporalFilter(token: TemporalToken, nowMillis: Long): PhotoFilter {
        return when (token.keyword) {
            "today" -> { photo -> DateUtils.isSameDay(photo.dateAdded, nowMillis) }
            "yesterday" -> { photo -> DateUtils.isSameDay(photo.dateAdded, nowMillis - Duration.ofDays(1).toMillis()) }
            "this_week" -> { photo -> DateUtils.isInCurrentWeek(photo.dateAdded, nowMillis) }
            "last_week" -> { photo -> DateUtils.isInLastWeek(photo.dateAdded, nowMillis) }
            "this_month" -> { photo -> DateUtils.isInCurrentMonth(photo.dateAdded, nowMillis) }
            "last_month" -> { photo -> DateUtils.isInLastMonth(photo.dateAdded, nowMillis) }
            "this_year" -> { photo -> DateUtils.isInCurrentYear(photo.dateAdded, nowMillis) }
            "last_year" -> { photo -> DateUtils.isInLastYear(photo.dateAdded, nowMillis) }
            else -> { _ -> true }
        }
    }

    private fun timeOfDayFilter(token: TimeOfDayToken): PhotoFilter {
        return when (token.bucket) {
            "morning" -> { photo -> photo.hourOfDay in 6..11 }
            "afternoon" -> { photo -> photo.hourOfDay in 12..16 }
            "evening" -> { photo -> photo.hourOfDay in 17..20 }
            else -> { photo -> photo.hourOfDay >= 21 || photo.hourOfDay <= 5 }
        }
    }

    private fun relativeDateFilter(token: RelativeDateToken, nowMillis: Long): PhotoFilter {
        val duration = when (token.unit) {
            RelativeUnit.DAY -> Duration.ofDays(token.amount.toLong())
            RelativeUnit.WEEK -> Duration.ofDays(token.amount * 7L)
            RelativeUnit.MONTH -> Duration.ofDays(token.amount * 30L)
            RelativeUnit.YEAR -> Duration.ofDays(token.amount * 365L)
        }
        val minimumTimestamp = nowMillis - duration.toMillis()
        return { photo -> photo.dateAdded >= minimumTimestamp }
    }

    private fun folderFilter(keyword: String): PhotoFilter {
        return when (keyword) {
            "camera", "dcim" -> { photo ->
                photo.folderPath.contains("dcim") || photo.folderPath.contains("camera")
            }

            "screenshots", "screenshot" -> { photo ->
                photo.folderPath.contains("screenshot") || photo.fileName.contains("screenshot", ignoreCase = true)
            }

            else -> { photo ->
                photo.folderPath.contains(keyword) || photo.folderName.contains(keyword)
            }
        }
    }

    private fun propertyFilter(keyword: String): PhotoFilter {
        return when (keyword) {
            "large" -> { photo -> photo.fileSize > Constants.LARGE_FILE_SIZE_BYTES }
            "small" -> { photo -> photo.fileSize < Constants.SMALL_FILE_SIZE_BYTES }
            "wide", "panorama" -> { photo -> photo.aspectRatio > 2f }
            "portrait" -> { photo -> photo.height > photo.width }
            "landscape" -> { photo -> photo.width > photo.height }
            "square" -> { photo -> photo.aspectRatio in 0.9f..1.1f }
            "hdr" -> { photo -> photo.isHdr }
            "gif" -> { photo -> photo.mimeType.contains("gif") }
            "png" -> { photo -> photo.mimeType.contains("png") }
            "jpg", "jpeg" -> { photo ->
                photo.mimeType.contains("jpeg") || photo.mimeType.contains("jpg")
            }

            "raw" -> { photo ->
                photo.mimeType.contains("raw") || photo.fileName.endsWith(".dng", ignoreCase = true)
            }

            "favorite", "favorites", "starred" -> { photo -> photo.isFavorite }
            "blurry", "blurred" -> { photo -> (photo.blurScore ?: Double.MAX_VALUE) <= BLUR_VARIANCE_THRESHOLD }
            "text", "with_text" -> { photo -> photo.ocrText.isNotBlank() }
            "with_location" -> { photo -> photo.latitude != null && photo.longitude != null }
            "without_location" -> { photo -> photo.latitude == null || photo.longitude == null }
            "receipt", "receipts" -> { photo ->
                photo.ocrText.contains("receipt", ignoreCase = true) ||
                    photo.fileName.contains("receipt", ignoreCase = true) ||
                    photo.folderName.contains("receipt", ignoreCase = true) ||
                    photo.hasMlTag("document", 0.60f)
            }
            "payment", "payments" -> { photo ->
                val text = buildString {
                    append(photo.ocrText)
                    append(' ')
                    append(photo.fileName)
                    append(' ')
                    append(photo.folderName)
                    append(' ')
                    append(photo.folderPath)
                }.lowercase()
                photo.matchesSource(PhotoSource.Screenshots) &&
                    PAYMENT_CUES.any { cue -> text.contains(cue) }
            }

            else -> { _ -> true }
        }
    }

    private fun locationFilter(keyword: String, context: SearchContext): PhotoFilter {
        return when (keyword) {
            "near_me", "here" -> radiusFilter(context.nearMeLatitude, context.nearMeLongitude, context.radiusKm)
            "home" -> radiusFilter(context.homeLatitude, context.homeLongitude, context.radiusKm)
            "office" -> radiusFilter(context.officeLatitude, context.officeLongitude, context.radiusKm)
            "abroad" -> { photo ->
                val home = context.homeCountry?.lowercase()
                val country = photo.country?.lowercase()
                home != null && country != null && country != home
            }

            else -> { photo ->
                val lower = keyword.lowercase()
                (photo.city?.contains(lower, ignoreCase = true) == true) ||
                    (photo.state?.contains(lower, ignoreCase = true) == true) ||
                    (photo.country?.contains(lower, ignoreCase = true) == true)
            }
        }
    }

    private fun mlFilter(keyword: String): PhotoFilter {
        val canonical = LabelMapping.canonicalKeyword(keyword)
        val threshold = LabelMapping.threshold(canonical)
        return { photo ->
            photo.hasMlTag(canonical, threshold)
        }
    }

    private fun sourceFilter(source: PhotoSource): PhotoFilter {
        return { photo ->
            photo.matchesSource(source)
        }
    }

    private fun textFilter(keyword: String): PhotoFilter {
        val normalized = keyword.lowercase().trim()
        if (normalized.isBlank()) return { true }

        return { photo ->
            photo.hasOcrToken(normalized) ||
                photo.fileName.contains(normalized, ignoreCase = true) ||
                photo.folderName.contains(normalized, ignoreCase = true) ||
                photo.folderPath.contains(normalized, ignoreCase = true) ||
                noteContains(photo.id, normalized)
        }
    }

    private fun radiusFilter(latitude: Double?, longitude: Double?, radiusKm: Double): PhotoFilter {
        if (latitude == null || longitude == null) {
            return { false }
        }

        // Bounding box for fast initial filtering
        val latDelta = radiusKm / 111.0
        val lonDelta = radiusKm / (111.0 * cos(Math.toRadians(latitude)))
        val latRange = (latitude - latDelta)..(latitude + latDelta)
        val lonRange = (longitude - lonDelta)..(longitude + lonDelta)

        return { photo ->
            val lat = photo.latitude
            val lon = photo.longitude
            lat != null && lon != null &&
                lat in latRange && lon in lonRange &&
                haversine(latitude, longitude, lat, lon) <= radiusKm
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
        return 2 * 6371.0 * atan2(sqrt(a), sqrt(1 - a))
    }

    private companion object {
        private const val BLUR_VARIANCE_THRESHOLD = 95.0
        private val PAYMENT_CUES = listOf(
            "upi",
            "phonepe",
            "gpay",
            "google pay",
            "paytm",
            "bhim",
            "transaction",
            "paid",
            "payment",
        )
    }
}
