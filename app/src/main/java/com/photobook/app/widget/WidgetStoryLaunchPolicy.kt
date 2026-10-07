package com.photobook.app.widget

internal object WidgetStoryLaunchPolicy {
    internal const val MAX_STORY_IDS = 100
    private const val MAX_CSV_LENGTH = 4096
    internal const val MAX_TITLE_LENGTH = 80

    fun parseStoryIds(csv: String): List<Long> {
        if (csv.isBlank() || csv.length > MAX_CSV_LENGTH) return emptyList()
        val result = ArrayList<Long>(minOf(MAX_STORY_IDS, 16))
        val seen = HashSet<Long>()
        csv.split(',').forEach { token ->
            if (result.size >= MAX_STORY_IDS) return@forEach
            val id = token.trim().toLongOrNull() ?: return@forEach
            if (id <= 0L || !seen.add(id)) return@forEach
            result += id
        }
        return result
    }

    fun sanitizeTitle(value: String): String =
        value.trim().take(MAX_TITLE_LENGTH)
}
