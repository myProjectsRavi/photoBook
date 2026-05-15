package com.photobook.app.feature.memories

import com.photobook.app.data.model.PhotoRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class MemoryCurator @Inject constructor() {

    fun curate(records: List<PhotoRecord>, maxStories: Int = DEFAULT_MAX_STORIES): List<MemoryStory> {
        if (records.size < MIN_STORY_PHOTOS) return emptyList()

        val grouped = records
            .asSequence()
            .filter { record -> record.dateAdded > 0L && record.uriString.isNotBlank() }
            .groupBy(::eventKey)

        if (grouped.isEmpty()) return emptyList()

        return grouped.values
            .asSequence()
            .filter { cluster -> cluster.size >= MIN_STORY_PHOTOS }
            .mapNotNull(::toCandidate)
            .sortedWith(
                compareByDescending<MemoryStoryCandidate> { it.score }
                    .thenByDescending { it.coverDateMillis }
            )
            .take(maxStories)
            .map { candidate ->
                MemoryStory(
                    id = candidate.id,
                    title = candidate.title,
                    subtitle = candidate.subtitle,
                    coverUriString = candidate.coverUriString,
                    photoCount = candidate.photoCount,
                    suggestedQuery = candidate.suggestedQuery,
                    photoIds = candidate.photoIds,
                )
            }
            .toList()
    }

    fun curateOnThisDay(records: List<PhotoRecord>, nowMillis: Long = System.currentTimeMillis()): MemoryStory? {
        if (records.size < MIN_STORY_PHOTOS) return null
        val today = Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val sameDay = records
            .asSequence()
            .filter { record ->
                record.month == today.monthValue &&
                    record.dayOfMonth == today.dayOfMonth &&
                    record.year < today.year &&
                    record.uriString.isNotBlank()
            }
            .groupBy { record -> record.year }

        if (sameDay.isEmpty()) return null

        val selected = sameDay.entries
            .sortedWith(
                compareByDescending<Map.Entry<Int, List<PhotoRecord>>> { it.value.size }
                    .thenByDescending { it.key },
            )
            .firstOrNull()
            ?: return null
        if (selected.value.size < MIN_STORY_PHOTOS) return null

        val photos = selected.value.sortedByDescending { it.dateAdded }
        val cover = photos.firstOrNull() ?: return null
        val prettyDate = formatMonthDay(today)

        return MemoryStory(
            id = "on-this-day-${today.monthValue}-${today.dayOfMonth}-${selected.key}",
            title = "On This Day · ${selected.key}",
            subtitle = "${photos.size} photos · $prettyDate",
            coverUriString = cover.uriString,
            photoCount = photos.size,
            suggestedQuery = "today",
            photoIds = photos.map { it.id },
            isOnThisDay = true,
        )
    }

    private fun eventKey(photo: PhotoRecord): String {
        val day = "${photo.year}-${photo.month}-${photo.dayOfMonth}"
        val location = normalizedLocationKey(photo)
        return "$day|$location"
    }

    private fun normalizedLocationKey(photo: PhotoRecord): String {
        val city = photo.city?.trim()?.lowercase().orEmpty()
        if (city.isNotBlank()) return "city:$city"

        val state = photo.state?.trim()?.lowercase().orEmpty()
        if (state.isNotBlank()) return "state:$state"

        val country = photo.country?.trim()?.lowercase().orEmpty()
        if (country.isNotBlank()) return "country:$country"

        val folder = photo.folderName.trim().lowercase()
        if (folder.isNotBlank()) return "folder:$folder"

        return "unknown"
    }

    private fun toCandidate(cluster: List<PhotoRecord>): MemoryStoryCandidate? {
        val sorted = cluster.sortedByDescending { photo -> photo.dateAdded }
        val cover = sorted.firstOrNull() ?: return null

        val locationLabel = prettyLocationLabel(cover)
        val dateLabel = formatDateLabel(cover.dateAdded)
        val photoCount = sorted.size

        val title = when {
            locationLabel.isNotBlank() -> locationLabel
            cover.folderName.isNotBlank() -> cover.folderName
            else -> "Memory"
        }

        val subtitleParts = buildList {
            add("$photoCount photos")
            if (dateLabel.isNotBlank()) add(dateLabel)
            if (locationLabel.isNotBlank()) add(locationLabel)
        }

        val suggestedQuery = buildSuggestedQuery(cover)

        return MemoryStoryCandidate(
            id = "memory-${cover.id}",
            title = title,
            subtitle = subtitleParts.joinToString(" · "),
            coverUriString = cover.uriString,
            photoCount = photoCount,
            coverDateMillis = cover.dateAdded,
            suggestedQuery = suggestedQuery,
            photoIds = sorted.map { photo -> photo.id },
            score = photoCount * 100 + recencyScore(cover.dateAdded),
        )
    }

    private fun recencyScore(dateMillis: Long): Int {
        return (dateMillis / RECENCY_DIVISOR_MS).toInt().coerceAtLeast(0)
    }

    private fun buildSuggestedQuery(photo: PhotoRecord): String {
        val preferred = listOfNotNull(
            photo.city,
            photo.state,
            photo.country,
            photo.folderName,
        ).map { item -> item.trim() }
            .firstOrNull { item -> item.isNotBlank() }

        return preferred
            ?.replace(Regex("\\s+"), " ")
            ?.take(MAX_QUERY_LENGTH)
            ?.ifBlank { "today" }
            ?: "today"
    }

    private fun prettyLocationLabel(photo: PhotoRecord): String {
        return listOfNotNull(photo.city, photo.state, photo.country)
            .map { part -> part.trim() }
            .firstOrNull { part -> part.isNotBlank() }
            ?: photo.folderName.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun formatDateLabel(dateMillis: Long): String {
        if (dateMillis <= 0L) return ""
        return runCatching {
            val instant = Instant.ofEpochMilli(dateMillis)
            val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            DATE_FORMATTER.format(localDate)
        }.getOrDefault("")
    }

    private fun formatMonthDay(localDate: LocalDate): String {
        return MONTH_DAY_FORMATTER.format(localDate)
    }

    private data class MemoryStoryCandidate(
        val id: String,
        val title: String,
        val subtitle: String,
        val coverUriString: String,
        val photoCount: Int,
        val coverDateMillis: Long,
        val suggestedQuery: String,
        val photoIds: List<Long>,
        val score: Int,
    )

    companion object {
        private const val MIN_STORY_PHOTOS = 3
        private const val DEFAULT_MAX_STORIES = 8
        private const val MAX_QUERY_LENGTH = 36
        private const val RECENCY_DIVISOR_MS = 86_400_000L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    }
}
