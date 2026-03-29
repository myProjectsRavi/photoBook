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
        "large", "small", "portrait", "landscape", "hdr", "favorites",
        "near me", "home", "office", "abroad",
        "selfie", "food", "sunset", "document", "pet", "car", "people", "nature",
    )

    fun getSuggestions(prefix: String): List<SuggestionItem> {
        val normalized = prefix.trim().lowercase()

        if (normalized.isBlank()) {
            return emptyList()
        }

        val indexTerms = buildList {
            addAll(index.folderKeywords())
            addAll(index.cityKeywords())
            addAll(index.mlKeywords())
        }

        val otherMatches = (staticKeywords + indexTerms)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { it.startsWith(normalized) }
            .take(6)
            .map { SuggestionItem(text = it, isHistory = false) }
            .toList()

        return otherMatches
    }
}
