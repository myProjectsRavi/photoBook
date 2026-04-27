package com.photobook.app.data.index

import com.photobook.app.data.model.MLTag
import com.photobook.app.data.model.PhotoRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoIndex @Inject constructor() {

    private val mutex = Mutex()
    private val recordsFlow = MutableStateFlow<List<PhotoRecord>>(emptyList())

    private var folderKeywords: Set<String> = emptySet()
    private var cityKeywords: Set<String> = emptySet()
    private var mlKeywords: Set<String> = emptySet()

    fun records(): StateFlow<List<PhotoRecord>> = recordsFlow.asStateFlow()

    fun snapshot(): List<PhotoRecord> = recordsFlow.value

    fun folderKeywords(): Set<String> = folderKeywords

    fun cityKeywords(): Set<String> = cityKeywords

    fun mlKeywords(): Set<String> = mlKeywords

    suspend fun setRecords(records: List<PhotoRecord>) {
        mutex.withLock {
            recordsFlow.value = records
            rebuildAuxiliarySets(records)
        }
    }

    suspend fun updateMlTags(id: Long, tags: List<MLTag>) {
        mutex.withLock {
            val updated = recordsFlow.value.map { record ->
                if (record.id == id) {
                    record.copy(
                        mlTags = mergeTags(record.mlTags, tags),
                        isMlProcessed = true,
                    )
                } else {
                    record
                }
            }
            recordsFlow.value = updated
            rebuildAuxiliarySets(updated)
        }
    }

    suspend fun updatePhotoIntelligence(
        id: Long,
        tags: List<MLTag>? = null,
        isMlProcessed: Boolean? = null,
        ocrText: String? = null,
        isOcrProcessed: Boolean? = null,
        perceptualHash: Long? = null,
    ): PhotoRecord? {
        var updatedRecord: PhotoRecord? = null
        mutex.withLock {
            val updated = recordsFlow.value.map { record ->
                if (record.id != id) return@map record

                val nextTags = tags?.let { incoming ->
                    mergeTags(record.mlTags, incoming)
                } ?: record.mlTags

                val nextRecord = record.copy(
                    mlTags = nextTags,
                    isMlProcessed = isMlProcessed ?: record.isMlProcessed,
                    ocrText = ocrText ?: record.ocrText,
                    isOcrProcessed = isOcrProcessed ?: record.isOcrProcessed,
                    perceptualHash = perceptualHash ?: record.perceptualHash,
                )
                updatedRecord = nextRecord
                nextRecord
            }
            recordsFlow.value = updated
            rebuildAuxiliarySets(updated)
        }
        return updatedRecord
    }

    suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        mutex.withLock {
            val updated = recordsFlow.value.map { record ->
                if (record.id == id) {
                    record.copy(isFavorite = isFavorite)
                } else {
                    record
                }
            }
            recordsFlow.value = updated
            rebuildAuxiliarySets(updated)
        }
    }

    suspend fun toggleFavorite(id: Long): Boolean {
        var nextFavorite = false
        mutex.withLock {
            val updated = recordsFlow.value.map { record ->
                if (record.id == id) {
                    nextFavorite = !record.isFavorite
                    record.copy(isFavorite = nextFavorite)
                } else {
                    record
                }
            }
            recordsFlow.value = updated
            rebuildAuxiliarySets(updated)
        }
        return nextFavorite
    }

    suspend fun upsertRecord(record: PhotoRecord) {
        mutex.withLock {
            val mutable = recordsFlow.value.toMutableList()
            val index = mutable.indexOfFirst { it.id == record.id }
            if (index >= 0) {
                mutable[index] = record
            } else {
                mutable += record
            }
            recordsFlow.value = mutable
            rebuildAuxiliarySets(mutable)
        }
    }

    suspend fun removeRecord(id: Long) {
        mutex.withLock {
            val filtered = recordsFlow.value.filterNot { it.id == id }
            recordsFlow.value = filtered
            rebuildAuxiliarySets(filtered)
        }
    }

    suspend fun removeRecords(ids: Set<Long>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            val filtered = recordsFlow.value.filterNot { record -> record.id in ids }
            recordsFlow.value = filtered
            rebuildAuxiliarySets(filtered)
        }
    }

    private fun rebuildAuxiliarySets(records: List<PhotoRecord>) {
        folderKeywords = records.flatMap { record ->
            listOf(record.folderName, record.folderPath)
        }.flatMap { text ->
            text.lowercase().split('/', ' ', '-', '_')
        }.filter { it.length >= 3 }.toSet()

        cityKeywords = records.flatMap { record ->
            listOf(record.city, record.state, record.country)
        }.mapNotNull { it?.lowercase()?.trim()?.takeIf(String::isNotBlank) }
            .toSet()

        mlKeywords = records.flatMap { record ->
            record.mlTags.map { it.label.lowercase() }
        }.toSet()
    }

    private fun mergeTags(existing: List<MLTag>, incoming: List<MLTag>): List<MLTag> {
        val merged = linkedMapOf<String, MLTag>()
        existing.forEach { tag ->
            merged[tag.label.lowercase()] = tag
        }
        incoming.forEach { tag ->
            val key = tag.label.lowercase()
            val current = merged[key]
            if (current == null || current.confidence < tag.confidence) {
                merged[key] = tag
            }
        }
        return merged.values.toList()
    }
}
