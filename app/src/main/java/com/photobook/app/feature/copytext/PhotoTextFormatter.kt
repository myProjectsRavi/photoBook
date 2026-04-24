package com.photobook.app.feature.copytext

class PhotoTextFormatter(
    private val maxChars: Int = DEFAULT_MAX_CHARS,
) {

    fun format(rawText: String): String {
        if (rawText.isBlank()) return ""

        val normalized = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val cleaned = normalized
            .lines()
            .map { line ->
                line.replace('\t', ' ')
                    .replace(MULTI_SPACE_REGEX, " ")
                    .trimEnd()
            }
            .joinToString(separator = "\n")
            .replace(MULTI_NEWLINE_REGEX, "\n\n")
            .trim()

        if (cleaned.length <= maxChars) {
            return cleaned
        }

        return cleaned.take(maxChars).trimEnd()
    }

    companion object {
        private val MULTI_SPACE_REGEX = Regex(" {2,}")
        private val MULTI_NEWLINE_REGEX = Regex("\n{3,}")
        const val DEFAULT_MAX_CHARS = 4000
    }
}
