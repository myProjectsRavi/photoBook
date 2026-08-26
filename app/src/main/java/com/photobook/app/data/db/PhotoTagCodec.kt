package com.photobook.app.data.db

import com.photobook.app.data.model.MLTag
import org.json.JSONArray
import org.json.JSONObject

object PhotoTagCodec {
    fun encode(tags: List<MLTag>): String {
        if (tags.isEmpty()) return "[]"
        val array = JSONArray()
        tags.forEach { tag ->
            array.put(
                JSONObject()
                    .put("label", tag.label)
                    .put("confidence", tag.confidence)
            )
        }
        return array.toString()
    }

    fun decode(encoded: String): List<MLTag> {
        val normalized = encoded.trim()
        if (normalized.isEmpty() || normalized == "[]") return emptyList()
        return runCatching {
            val array = JSONArray(normalized)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    add(
                        MLTag(
                            label = obj.optString("label"),
                            confidence = obj.optDouble("confidence").toFloat(),
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun toSearchableText(tags: List<MLTag>): String {
        return tags.asSequence()
            .map { it.label.lowercase().trim() }
            .filter { it.isNotBlank() && it !in INTERNAL_EVIDENCE_LABELS }
            .distinct()
            .joinToString(" ")
    }

    private val INTERNAL_EVIDENCE_LABELS = setOf(
        "prepared_food",
    )
}
