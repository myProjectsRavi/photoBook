package com.photobook.app.search

import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.ml.LabelMapping
import javax.inject.Inject

class TokenClassifier @Inject constructor(
    private val index: PhotoIndex,
) {

    private val temporalKeywords = setOf(
        "today", "yesterday", "this_week", "last_week", "this_month", "last_month",
        "this_year", "last_year", "recent", "oldest",
    )

    private val monthMap = mapOf(
        "january" to 1, "jan" to 1,
        "february" to 2, "feb" to 2,
        "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4,
        "may" to 5,
        "june" to 6, "jun" to 6,
        "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9,
        "october" to 10, "oct" to 10,
        "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
    )

    private val dayMap = mapOf(
        "monday" to 1, "mon" to 1,
        "tuesday" to 2, "tue" to 2, "tues" to 2,
        "wednesday" to 3, "wed" to 3,
        "thursday" to 4, "thu" to 4, "thur" to 4, "thurs" to 4,
        "friday" to 5, "fri" to 5,
        "saturday" to 6, "sat" to 6,
        "sunday" to 7, "sun" to 7,
    )

    private val timeOfDayKeywords = setOf("morning", "afternoon", "evening", "night")

    private val folderKeywords = setOf(
        "camera", "dcim", "screenshots", "screenshot", "whatsapp", "telegram", "instagram",
        "download", "facebook", "twitter", "snapchat", "bluetooth",
    )

    private val propertyKeywords = setOf(
        "large", "small", "wide", "panorama", "portrait", "landscape", "square", "hdr",
        "gif", "png", "jpg", "jpeg", "raw", "favorite", "favorites", "starred",
        "blurry", "blurred", "text", "with_text", "with_location", "without_location",
        "receipt", "receipts", "payment", "payments",
    )

    private val sourceKeywords = setOf(
        "whatsapp", "telegram", "camera", "download", "downloads", "screenshot", "screenshots",
    )

    private val locationKeywords = setOf("home", "office", "near_me", "abroad", "here")

    private val relativeRegex = Regex("last_(\\d+)_(day|days|week|weeks|month|months|year|years)")

    fun classify(token: String): QueryToken {
        if (token in temporalKeywords) return TemporalToken(token)
        monthMap[token]?.let { return MonthToken(it) }
        dayMap[token]?.let { return DayOfWeekToken(it) }
        if (token in timeOfDayKeywords) return TimeOfDayToken(token)

        if (token.matches(Regex("(19|20)\\d{2}"))) {
            return YearToken(token.toInt())
        }

        relativeRegex.matchEntire(token)?.let { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: return@let
            val unit = when (match.groupValues[2]) {
                "day", "days" -> RelativeUnit.DAY
                "week", "weeks" -> RelativeUnit.WEEK
                "month", "months" -> RelativeUnit.MONTH
                else -> RelativeUnit.YEAR
            }
            return RelativeDateToken(amount, unit)
        }

        PhotoSource.fromToken(token)?.let { source ->
            return SourceToken(source)
        }

        if (token in sourceKeywords) {
            PhotoSource.fromToken(token)?.let { source ->
                return SourceToken(source)
            }
        }

        if (token in folderKeywords) return FolderToken(token)
        if (token in propertyKeywords) return PropertyToken(token)
        if (token in locationKeywords) return LocationToken(token)
        if (token in LabelMapping.keywords) return MLTagToken(token)

        if (fuzzyMatch(token, index.folderKeywords()) != null) return FolderToken(token)
        if (fuzzyMatch(token, index.cityKeywords()) != null) return LocationToken(token)
        if (fuzzyMatch(token, index.mlKeywords()) != null) return MLTagToken(token)

        return if (token.length >= 2) TextToken(token) else UnknownToken(token)
    }

    private fun fuzzyMatch(token: String, candidates: Set<String>): String? {
        if (token.length < 3) return null
        return candidates.firstOrNull { candidate ->
            candidate.startsWith(token)
        }
    }
}
