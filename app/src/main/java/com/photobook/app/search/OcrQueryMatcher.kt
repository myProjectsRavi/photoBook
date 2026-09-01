package com.photobook.app.search

/**
 * Literal OCR matching is intentionally independent from smart-query token classification.
 * A word visible in a photo must remain discoverable even when that word also has a structured
 * PhotoBook meaning (for example payment, food, camera, or today).
 */
object OcrQueryMatcher {

    fun matches(ocrText: String, rawQuery: String): Boolean {
        if (ocrText.isBlank() || rawQuery.isBlank()) return false

        val queryTokens = tokens(rawQuery).filter(::isUsefulQueryToken)
        if (queryTokens.isEmpty()) return false
        val textTokens = tokens(ocrText)
        if (textTokens.isEmpty()) return false

        return queryTokens.all { queryToken ->
            textTokens.any { textToken ->
                if (queryToken.length == 1) {
                    textToken == queryToken
                } else {
                    textToken.startsWith(queryToken)
                }
            }
        }
    }

    private fun tokens(value: String): List<String> {
        return value.lowercase()
            .split(NON_ALPHANUMERIC)
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
    }

    private fun isUsefulQueryToken(token: String): Boolean {
        return token.length >= 2 || (token.length == 1 && token[0].isDigit())
    }

    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
}
