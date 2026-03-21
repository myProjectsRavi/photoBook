package com.photobook.app.search

import com.photobook.app.data.index.PhotoIndex
import javax.inject.Inject

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

    fun getSuggestions(prefix: String, history: List<String> = emptyList()): List<String> {
        val normalized = prefix.trim().lowercase()
        if (normalized.isBlank()) {
            return history.distinct().take(8)
        }

        val indexTerms = buildList {
            addAll(index.folderKeywords())
            addAll(index.cityKeywords())
            addAll(index.mlKeywords())
        }

        return (history + staticKeywords + indexTerms)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { it.startsWith(normalized) }
            .take(8)
            .toList()
    }
}
