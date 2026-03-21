package com.photobook.app.search

import javax.inject.Inject

class QueryParser @Inject constructor() {

    private val phraseReplacements = mapOf(
        "this week" to "this_week",
        "last week" to "last_week",
        "this month" to "this_month",
        "last month" to "last_month",
        "this year" to "this_year",
        "last year" to "last_year",
        "near me" to "near_me",
    )

    fun normalize(rawQuery: String): String {
        var normalized = rawQuery.trim().lowercase().replace(Regex("\\s+"), " ")
        phraseReplacements.forEach { (phrase, replacement) ->
            normalized = normalized.replace(phrase, replacement)
        }
        return normalized
    }

    fun tokenize(rawQuery: String): List<String> {
        val normalized = normalize(rawQuery)
        if (normalized.isBlank()) return emptyList()

        val tokens = normalized.split(' ')
        val output = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val current = tokens[index]
            if (current == "last" && index + 2 < tokens.size) {
                val amount = tokens[index + 1].toIntOrNull()
                val unit = tokens[index + 2]
                if (amount != null && unit in setOf(
                        "day", "days", "week", "weeks", "month", "months", "year", "years"
                    )
                ) {
                    output += "last_${amount}_${unit}"
                    index += 3
                    continue
                }
            }
            output += current
            index += 1
        }

        return output
    }
}
