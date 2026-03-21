package com.photobook.app.search

import com.photobook.app.data.index.PhotoIndex
import javax.inject.Inject

data class SuggestionItem(
    val text: String,
    val isHistory: Boolean,
)

class SuggestionEngine @Inject constructor(
    private val index: PhotoIndex,
) {

    private val staticKeywords = listOf(
        "today", "yesterday", "this week", "last week", "this month", "last month",
        "camera", "screenshots", "whatsapp", "download",
        "large", "small", "portrait", "landscape", "hdr",
        "near me", "home", "office", "abroad",
        "selfie", "food", "sunset", "document", "pet", "car", "people", "nature",
    )

    fun getSuggestions(prefix: String, history: List<String> = emptyList()): List<SuggestionItem> {
        val normalized = prefix.trim().lowercase()
        val distinctHistory = history
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)

        if (normalized.isBlank()) {
            return distinctHistory.map { SuggestionItem(text = it, isHistory = true) }
        }

        val indexTerms = buildList {
            addAll(index.folderKeywords())
            addAll(index.cityKeywords())
            addAll(index.mlKeywords())
        }

        val historyMatches = distinctHistory
            .filter { it.startsWith(normalized, ignoreCase = true) }
            .map { SuggestionItem(text = it, isHistory = true) }

        val otherMatches = (staticKeywords + indexTerms)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { it.startsWith(normalized) }
            .filter { candidate -> distinctHistory.none { it.equals(candidate, ignoreCase = true) } }
            .take(5)
            .map { SuggestionItem(text = it, isHistory = false) }
            .toList()

        return historyMatches + otherMatches
    }
}
