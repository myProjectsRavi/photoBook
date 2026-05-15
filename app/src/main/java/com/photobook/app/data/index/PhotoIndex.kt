package com.photobook.app.data.index

import com.photobook.app.data.model.MLTag
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ml.LabelMapping
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
    private val recordsMap = mutableMapOf<Long, PhotoRecord>()
    private val recordsFlow = MutableStateFlow<List<PhotoRecord>>(emptyList())
    
    private var version = 0L

    private var folderKeywords: Set<String> = emptySet()
    private var cityKeywords: Set<String> = emptySet()
    private var mlKeywords: Set<String> = emptySet()

    fun records(): StateFlow<List<PhotoRecord>> = recordsFlow.asStateFlow()

    fun snapshot(): List<PhotoRecord> = recordsFlow.value
    
    fun version(): Long = version

    fun folderKeywords(): Set<String> = folderKeywords

    fun cityKeywords(): Set<String> = cityKeywords

    fun mlKeywords(): Set<String> = mlKeywords

    suspend fun setRecords(records: List<PhotoRecord>) {
        mutex.withLock {
            recordsMap.clear()
            records.forEach { recordsMap[it.id] = it }
            val sorted = records.sortedByDescending { it.dateAdded }
            recordsFlow.value = sorted
            rebuildAuxiliarySets(sorted)
            version += 1
        }
    }

    suspend fun updatePhotoIntelligence(
        id: Long,
        tags: List<MLTag>? = null,
        isMlProcessed: Boolean? = null,
        ocrText: String? = null,
        isOcrProcessed: Boolean? = null,
        perceptualHash: Long? = null,
        blurScore: Double? = null,
    ): PhotoRecord? {
        return updatePhotosIntelligence(listOf(PhotoIntelligenceUpdate(
            id, tags, isMlProcessed, ocrText, isOcrProcessed, perceptualHash, blurScore
        ))).firstOrNull()
    }

    suspend fun updatePhotosIntelligence(updates: List<PhotoIntelligenceUpdate>): List<PhotoRecord> {
        if (updates.isEmpty()) return emptyList()
        val results = mutableListOf<PhotoRecord>()
        mutex.withLock {
            var anyChanged = false
            updates.forEach { update ->
                val record = recordsMap[update.id] ?: return@forEach
                val nextTags = update.tags?.let { incoming ->
                    mergeTags(record.mlTags, incoming)
                } ?: record.mlTags

                val nextRecord = record.copy(
                    mlTags = nextTags,
                    isMlProcessed = update.isMlProcessed ?: record.isMlProcessed,
                    ocrText = update.ocrText ?: record.ocrText,
                    isOcrProcessed = update.isOcrProcessed ?: record.isOcrProcessed,
                    perceptualHash = update.perceptualHash ?: record.perceptualHash,
                    blurScore = update.blurScore ?: record.blurScore,
                )
                if (nextRecord != record) {
                    recordsMap[update.id] = nextRecord
                    results += nextRecord
                    anyChanged = true
                }
            }
            if (anyChanged) {
                // Intelligence updates don't change dateAdded, so we can avoid sorting if we update the list in place
                val currentList = recordsFlow.value
                val updatedMap = results.associateBy { it.id }
                val nextList = currentList.map { photo -> updatedMap[photo.id] ?: photo }
                recordsFlow.value = nextList
                version += 1
                
                // For intelligence updates, we only really need to update mlKeywords
                val newMlKeywords = results.asSequence()
                    .flatMap { it.mlTags.asSequence().map { tag -> tag.label.lowercase() } }
                    .toSet()
                if (!mlKeywords.containsAll(newMlKeywords)) {
                    mlKeywords = mlKeywords + newMlKeywords
                }
            }
        }
        return results
    }

    suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        mutex.withLock {
            val record = recordsMap[id] ?: return@withLock
            if (record.isFavorite == isFavorite) return@withLock
            val updated = record.copy(isFavorite = isFavorite)
            recordsMap[id] = updated
            
            val currentList = recordsFlow.value
            val nextList = currentList.map { photo -> if (photo.id == id) updated else photo }
            recordsFlow.value = nextList
            version += 1
        }
    }

    suspend fun toggleFavorite(id: Long): Boolean {
        var nextFavorite = false
        mutex.withLock {
            val record = recordsMap[id] ?: return@withLock false
            nextFavorite = !record.isFavorite
            val updated = record.copy(isFavorite = nextFavorite)
            recordsMap[id] = updated
            
            val currentList = recordsFlow.value
            val nextList = currentList.map { photo -> if (photo.id == id) updated else photo }
            recordsFlow.value = nextList
            version += 1
        }
        return nextFavorite
    }

    suspend fun upsertRecord(record: PhotoRecord) {
        mutex.withLock {
            recordsMap[record.id] = record
            val updatedList = recordsMap.values.sortedByDescending { it.dateAdded }
            recordsFlow.value = updatedList
            rebuildAuxiliarySets(updatedList)
            version += 1
        }
    }

    suspend fun removeRecords(ids: Set<Long>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            var anyRemoved = false
            ids.forEach { id ->
                if (recordsMap.remove(id) != null) {
                    anyRemoved = true
                }
            }
            if (anyRemoved) {
                val updatedList = recordsMap.values.sortedByDescending { it.dateAdded }
                recordsFlow.value = updatedList
                rebuildAuxiliarySets(updatedList)
                version += 1
            }
        }
    }

    private fun rebuildAuxiliarySets(records: List<PhotoRecord>) {
        val folders = HashSet<String>(records.size / 10)
        val cities = HashSet<String>(records.size / 20)
        val tags = HashSet<String>(LabelMapping.keywords.size + 100)

        records.forEach { record ->
            // Folders
            splitAndAdd(record.folderName, folders)
            splitAndAdd(record.folderPath, folders)

            // Cities
            record.city?.let { if (it.isNotBlank()) cities.add(it.lowercase().trim()) }
            record.state?.let { if (it.isNotBlank()) cities.add(it.lowercase().trim()) }
            record.country?.let { if (it.isNotBlank()) cities.add(it.lowercase().trim()) }

            // ML Tags
            record.mlTags.forEach { tag ->
                tags.add(tag.label.lowercase())
            }
        }

        folderKeywords = folders
        cityKeywords = cities
        mlKeywords = tags
    }

    private fun splitAndAdd(text: String, target: MutableSet<String>) {
        if (text.isBlank()) return
        var start = 0
        val lower = text.lowercase()
        for (i in lower.indices) {
            val c = lower[i]
            if (c == '/' || c == ' ' || c == '-' || c == '_') {
                if (i - start >= 3) {
                    target.add(lower.substring(start, i))
                }
                start = i + 1
            }
        }
        if (lower.length - start >= 3) {
            target.add(lower.substring(start))
        }
    }

    private fun mergeTags(existing: List<MLTag>, incoming: List<MLTag>): List<MLTag> {
        if (incoming.isEmpty()) return existing
        val merged = existing.associateByTo(LinkedHashMap()) { it.label.lowercase() }
        incoming.forEach { tag ->
            val key = tag.label.lowercase()
            val current = merged[key]
            if (current == null || current.confidence < tag.confidence) {
                merged[key] = tag
            }
        }
        return merged.values.toList()
    }

    data class PhotoIntelligenceUpdate(
        val id: Long,
        val tags: List<MLTag>? = null,
        val isMlProcessed: Boolean? = null,
        val ocrText: String? = null,
        val isOcrProcessed: Boolean? = null,
        val perceptualHash: Long? = null,
        val blurScore: Double? = null,
    )
}
