package com.photobook.app.widget

internal object WidgetStoryLaunchPolicy {
    internal const val MAX_STORY_IDS = 100
    private const val MAX_CSV_LENGTH = 4096
    internal const val MAX_TITLE_LENGTH = 80

    fun parseStoryIds(csv: String): List<Long> {
        if (csv.isBlank() || csv.length > MAX_CSV_LENGTH) return emptyList()
        val tokens = csv.split(',')
        if (tokens.isEmpty() || tokens.size > MAX_STORY_IDS) return emptyList()

        val result = ArrayList<Long>(tokens.size)
        val seen = HashSet<Long>(tokens.size)
        tokens.forEach { token ->
            val normalized = token.trim()
            if (normalized.isEmpty()) return emptyList()
            val id = normalized.toLongOrNull() ?: return emptyList()
            if (id <= 0L || !seen.add(id)) return emptyList()
            result += id
        }
        return result
    }

    fun sanitizeTitle(value: String): String =
        value.trim().take(MAX_TITLE_LENGTH)
}
