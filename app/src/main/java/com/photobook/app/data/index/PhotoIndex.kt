package com.photobook.app.data.index

import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.MLTag
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ml.LabelMapping
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Internal migration switch only. This is deliberately not user-facing and can be flipped back to
 * LEGACY in one line while Phase 2 is being certified.
 */
internal enum class PhotoIndexStrategy {
    LEGACY,
    V2,
}

@Singleton
class PhotoIndex internal constructor(
    private val backend: PhotoIndexBackend,
) {
    @Inject
    constructor() : this(createBackend(ACTIVE_STRATEGY))

    internal constructor(strategy: PhotoIndexStrategy) : this(createBackend(strategy))

    fun records(): StateFlow<List<PhotoRecord>> = backend.records()

    fun changes(): StateFlow<Long> = backend.changes()

    fun snapshot(): List<PhotoRecord> = backend.snapshot()

    /** Resolve against one immutable snapshot so a search generation never mixes index states. */
    internal fun getByIdFromSnapshot(records: List<PhotoRecord>, id: Long): PhotoRecord? {
        return when (records) {
            is OverlayPhotoList -> records.getById(id)
            else -> records.firstOrNull { record -> record.id == id }
        }
    }

    fun getById(id: Long): PhotoRecord? = backend.getById(id)

    fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord> = backend.getByIdsOrdered(ids)

    fun size(): Int = backend.size()

    fun version(): Long = backend.version()

    fun folderKeywords(): Set<String> = backend.folderKeywords()

    fun cityKeywords(): Set<String> = backend.cityKeywords()

    fun mlKeywords(): Set<String> = backend.mlKeywords()

    suspend fun setRecords(records: List<PhotoRecord>) {
        backend.setRecords(records)
    }

    suspend fun updatePhotoIntelligence(
        id: Long,
        tags: List<MLTag>? = null,
        isMlProcessed: Boolean? = null,
        mlStatus: IntelligenceStatus? = null,
        ocrText: String? = null,
        isOcrProcessed: Boolean? = null,
        ocrStatus: IntelligenceStatus? = null,
        perceptualHash: Long? = null,
        blurScore: Double? = null,
    ): PhotoRecord? {
        return updatePhotosIntelligence(
            listOf(
                PhotoIntelligenceUpdate(
                    id = id,
                    tags = tags,
                    isMlProcessed = isMlProcessed,
                    mlStatus = mlStatus,
                    ocrText = ocrText,
                    isOcrProcessed = isOcrProcessed,
                    ocrStatus = ocrStatus,
                    perceptualHash = perceptualHash,
                    blurScore = blurScore,
                ),
            ),
        ).firstOrNull()
    }

    suspend fun updatePhotosIntelligence(updates: List<PhotoIntelligenceUpdate>): List<PhotoRecord> {
        return backend.updatePhotosIntelligence(updates)
    }

    suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        backend.setFavorite(id, isFavorite)
    }

    suspend fun toggleFavorite(id: Long): Boolean = backend.toggleFavorite(id)

    suspend fun upsertRecord(record: PhotoRecord) {
        backend.upsertRecord(record)
    }

    suspend fun removeRecords(ids: Set<Long>) {
        backend.removeRecords(ids)
    }

    data class PhotoIntelligenceUpdate(
        val id: Long,
        val tags: List<MLTag>? = null,
        val archiveFoodCandidate: Boolean? = null,
        val isMlProcessed: Boolean? = null,
        val mlStatus: IntelligenceStatus? = null,
        val ocrText: String? = null,
        val isOcrProcessed: Boolean? = null,
        val ocrStatus: IntelligenceStatus? = null,
        val perceptualHash: Long? = null,
        val blurScore: Double? = null,
    )

    companion object {
        // Keep legacy code compiled until parity/scale certification is complete.
        private val ACTIVE_STRATEGY = PhotoIndexStrategy.V2

        private fun createBackend(strategy: PhotoIndexStrategy): PhotoIndexBackend {
            return when (strategy) {
                PhotoIndexStrategy.LEGACY -> LegacyPhotoIndexBackend()
                PhotoIndexStrategy.V2 -> OverlayPhotoIndexBackend()
            }
        }
    }
}

internal interface PhotoIndexBackend {
    fun records(): StateFlow<List<PhotoRecord>>
    fun changes(): StateFlow<Long>
    fun snapshot(): List<PhotoRecord>
    fun getById(id: Long): PhotoRecord?
    fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord>
    fun size(): Int
    fun version(): Long
    fun folderKeywords(): Set<String>
    fun cityKeywords(): Set<String>
    fun mlKeywords(): Set<String>
    suspend fun setRecords(records: List<PhotoRecord>)
    suspend fun updatePhotosIntelligence(updates: List<PhotoIndex.PhotoIntelligenceUpdate>): List<PhotoRecord>
    suspend fun setFavorite(id: Long, isFavorite: Boolean)
    suspend fun toggleFavorite(id: Long): Boolean
    suspend fun upsertRecord(record: PhotoRecord)
    suspend fun removeRecords(ids: Set<Long>)
}

/** Exact Phase-1 behavior retained as the temporary rollback implementation. */
internal class LegacyPhotoIndexBackend : PhotoIndexBackend {
    private val mutex = Mutex()
    private val recordsMap = mutableMapOf<Long, PhotoRecord>()
    private val recordsFlow = MutableStateFlow<List<PhotoRecord>>(emptyList())
    private val changeFlow = MutableStateFlow(0L)

    @Volatile private var version = 0L
    @Volatile private var folderKeywords: Set<String> = emptySet()
    @Volatile private var cityKeywords: Set<String> = emptySet()
    @Volatile private var mlKeywords: Set<String> = emptySet()

    override fun records(): StateFlow<List<PhotoRecord>> = recordsFlow.asStateFlow()
    override fun changes(): StateFlow<Long> = changeFlow.asStateFlow()
    override fun snapshot(): List<PhotoRecord> = recordsFlow.value
    override fun getById(id: Long): PhotoRecord? = recordsFlow.value.firstOrNull { record -> record.id == id }
    override fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord> {
        if (ids.isEmpty()) return emptyList()
        val byId = recordsFlow.value.associateBy { record -> record.id }
        return ids.mapNotNull(byId::get)
    }
    override fun size(): Int = recordsFlow.value.size
    override fun version(): Long = version
    override fun folderKeywords(): Set<String> = folderKeywords
    override fun cityKeywords(): Set<String> = cityKeywords
    override fun mlKeywords(): Set<String> = mlKeywords

    override suspend fun setRecords(records: List<PhotoRecord>) {
        mutex.withLock {
            recordsMap.clear()
            records.forEach { recordsMap[it.id] = it }
            val sorted = records.sortedByDescending { it.dateAdded }
            recordsFlow.value = sorted
            rebuildAuxiliarySets(sorted)
            bumpVersion()
        }
    }

    override suspend fun updatePhotosIntelligence(
        updates: List<PhotoIndex.PhotoIntelligenceUpdate>,
    ): List<PhotoRecord> {
        if (updates.isEmpty()) return emptyList()
        val results = mutableListOf<PhotoRecord>()
        mutex.withLock {
            var anyChanged = false
            updates.forEach { update ->
                val record = recordsMap[update.id] ?: return@forEach
                val nextTags = update.tags?.let { incoming ->
                    mergeTags(record.mlTags, incoming)
                } ?: record.mlTags
                val nextRecord = applyIntelligenceUpdate(record, update, nextTags)
                if (nextRecord != record) {
                    recordsMap[update.id] = nextRecord
                    results += nextRecord
                    anyChanged = true
                }
            }
            if (anyChanged) {
                val updatedMap = results.associateBy { it.id }
                recordsFlow.value = recordsFlow.value.map { photo -> updatedMap[photo.id] ?: photo }
                bumpVersion()
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

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        mutex.withLock {
            val record = recordsMap[id] ?: return@withLock
            if (record.isFavorite == isFavorite) return@withLock
            val updated = record.copy(isFavorite = isFavorite)
            recordsMap[id] = updated
            recordsFlow.value = recordsFlow.value.map { photo -> if (photo.id == id) updated else photo }
            bumpVersion()
        }
    }

    override suspend fun toggleFavorite(id: Long): Boolean {
        var nextFavorite = false
        mutex.withLock {
            val record = recordsMap[id] ?: return@withLock
            nextFavorite = !record.isFavorite
            val updated = record.copy(isFavorite = nextFavorite)
            recordsMap[id] = updated
            recordsFlow.value = recordsFlow.value.map { photo -> if (photo.id == id) updated else photo }
            bumpVersion()
        }
        return nextFavorite
    }

    override suspend fun upsertRecord(record: PhotoRecord) {
        mutex.withLock {
            recordsMap[record.id] = record
            val updatedList = recordsMap.values.sortedByDescending { it.dateAdded }
            recordsFlow.value = updatedList
            rebuildAuxiliarySets(updatedList)
            bumpVersion()
        }
    }

    override suspend fun removeRecords(ids: Set<Long>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            var anyRemoved = false
            ids.forEach { id ->
                if (recordsMap.remove(id) != null) anyRemoved = true
            }
            if (anyRemoved) {
                val updatedList = recordsMap.values.sortedByDescending { it.dateAdded }
                recordsFlow.value = updatedList
                rebuildAuxiliarySets(updatedList)
                bumpVersion()
            }
        }
    }

    private fun rebuildAuxiliarySets(records: List<PhotoRecord>) {
        val sets = buildAuxiliarySets(records)
        folderKeywords = sets.folders
        cityKeywords = sets.cities
        mlKeywords = sets.tags
    }

    private fun bumpVersion() {
        version += 1
        changeFlow.value = version
    }
}

/**
 * Phase-2 index backend.
 *
 * Full-library structural changes still rebuild intentionally. The high-frequency favorite and
 * intelligence paths use immutable overlay snapshots, so a point mutation does not map/copy every
 * PhotoRecord. The overlay is compacted periodically to keep read overhead bounded.
 */
internal class OverlayPhotoIndexBackend : PhotoIndexBackend {
    private val mutex = Mutex()
    // recordsMap is writer-only under mutex. Readers use immutable snapshots exclusively.
    private val recordsMap = LinkedHashMap<Long, PhotoRecord>()
    @Volatile private var snapshot = OverlayPhotoList.empty()
    private val recordsFlow = MutableStateFlow<List<PhotoRecord>>(snapshot)
    private val changeFlow = MutableStateFlow(0L)

    @Volatile private var version = 0L
    @Volatile private var folderKeywords: Set<String> = emptySet()
    @Volatile private var cityKeywords: Set<String> = emptySet()
    @Volatile private var mlKeywords: Set<String> = emptySet()

    override fun records(): StateFlow<List<PhotoRecord>> = recordsFlow.asStateFlow()
    override fun changes(): StateFlow<Long> = changeFlow.asStateFlow()
    override fun snapshot(): List<PhotoRecord> = snapshot
    override fun getById(id: Long): PhotoRecord? = snapshot.getById(id)
    override fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord> = snapshot.getByIdsOrdered(ids)
    override fun size(): Int = snapshot.size
    override fun version(): Long = version
    override fun folderKeywords(): Set<String> = folderKeywords
    override fun cityKeywords(): Set<String> = cityKeywords
    override fun mlKeywords(): Set<String> = mlKeywords

    override suspend fun setRecords(records: List<PhotoRecord>) {
        mutex.withLock {
            recordsMap.clear()
            records.forEach { recordsMap[it.id] = it }
            val sorted = records.sortedByDescending { it.dateAdded }
            rebuildAuxiliarySets(sorted)
            publishStructural(sorted)
        }
    }

    override suspend fun updatePhotosIntelligence(
        updates: List<PhotoIndex.PhotoIntelligenceUpdate>,
    ): List<PhotoRecord> {
        if (updates.isEmpty()) return emptyList()
        val results = mutableListOf<PhotoRecord>()
        mutex.withLock {
            updates.forEach { update ->
                val record = recordsMap[update.id] ?: return@forEach
                val nextTags = update.tags?.let { incoming ->
                    mergeTags(record.mlTags, incoming)
                } ?: record.mlTags
                val nextRecord = applyIntelligenceUpdate(record, update, nextTags)
                if (nextRecord != record) {
                    recordsMap[update.id] = nextRecord
                    results += nextRecord
                }
            }
            if (results.isNotEmpty()) {
                val newMlKeywords = results.asSequence()
                    .flatMap { it.mlTags.asSequence().map { tag -> tag.label.lowercase() } }
                    .toSet()
                if (!mlKeywords.containsAll(newMlKeywords)) {
                    mlKeywords = mlKeywords + newMlKeywords
                }
                publishPointUpdates(results)
            }
        }
        return results
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        mutex.withLock {
            val record = recordsMap[id] ?: return@withLock
            if (record.isFavorite == isFavorite) return@withLock
            val updated = record.copy(isFavorite = isFavorite)
            recordsMap[id] = updated
            publishPointUpdates(listOf(updated))
        }
    }

    override suspend fun toggleFavorite(id: Long): Boolean {
        var nextFavorite = false
        mutex.withLock {
            val record = recordsMap[id] ?: return@withLock
            nextFavorite = !record.isFavorite
            val updated = record.copy(isFavorite = nextFavorite)
            recordsMap[id] = updated
            publishPointUpdates(listOf(updated))
        }
        return nextFavorite
    }

    override suspend fun upsertRecord(record: PhotoRecord) {
        mutex.withLock {
            val previous = recordsMap[record.id]
            recordsMap[record.id] = record
            val nextSnapshot = when {
                previous == record -> snapshot
                previous != null && previous.dateAdded == record.dateAdded -> snapshot.replaceAll(listOf(record))
                else -> OverlayPhotoList.from(recordsMap.values.sortedByDescending { it.dateAdded })
            }
            // Keep keyword semantics exactly aligned with the rollback implementation.
            rebuildAuxiliarySets(nextSnapshot)
            publishSnapshot(nextSnapshot)
        }
    }

    override suspend fun removeRecords(ids: Set<Long>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            var anyRemoved = false
            ids.forEach { id ->
                if (recordsMap.remove(id) != null) anyRemoved = true
            }
            if (anyRemoved) {
                val nextSnapshot = OverlayPhotoList.from(recordsMap.values.sortedByDescending { it.dateAdded })
                rebuildAuxiliarySets(nextSnapshot)
                publishSnapshot(nextSnapshot)
            }
        }
    }

    private fun publishStructural(records: List<PhotoRecord>) {
        publishSnapshot(OverlayPhotoList.from(records))
    }

    private fun publishPointUpdates(records: List<PhotoRecord>) {
        publishSnapshot(snapshot.replaceAll(records))
    }

    /**
     * Invalidate the old generation before publishing the new immutable view. changeFlow is emitted
     * only after publication, so production searches can use its value as a completed-generation token.
     */
    private fun publishSnapshot(nextSnapshot: OverlayPhotoList) {
        val nextVersion = version + 1L
        version = nextVersion
        snapshot = nextSnapshot
        recordsFlow.value = nextSnapshot
        changeFlow.value = nextVersion
    }

    private fun rebuildAuxiliarySets(records: List<PhotoRecord>) {
        val sets = buildAuxiliarySets(records)
        folderKeywords = sets.folders
        cityKeywords = sets.cities
        mlKeywords = sets.tags
    }
}

/**
 * Immutable List view with O(1) ID-to-position lookup and a bounded point-update overlay.
 * Identity equality is intentional: MutableStateFlow otherwise performs structural List equality,
 * turning an otherwise O(1) favorite update back into an O(n) scan before emission.
 */
internal class OverlayPhotoList private constructor(
    private val base: List<PhotoRecord>,
    private val indexById: Map<Long, Int>,
    private val overrides: Map<Int, PhotoRecord>,
) : AbstractList<PhotoRecord>() {
    override val size: Int
        get() = base.size

    override fun get(index: Int): PhotoRecord = overrides[index] ?: base[index]

    fun getById(id: Long): PhotoRecord? {
        val index = indexById[id] ?: return null
        return get(index)
    }

    fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord> {
        return ids.mapNotNull(::getById)
    }

    fun replaceAll(records: List<PhotoRecord>): OverlayPhotoList {
        if (records.isEmpty() || base.isEmpty()) return this
        val nextOverrides = HashMap(overrides)
        var changed = false
        records.forEach { record ->
            val index = indexById[record.id] ?: return@forEach
            if (get(index) != record) {
                nextOverrides[index] = record
                changed = true
            }
        }
        if (!changed) return this
        if (nextOverrides.size >= MAX_OVERLAY_ENTRIES) {
            val materialized = ArrayList<PhotoRecord>(base.size)
            for (index in base.indices) {
                materialized += nextOverrides[index] ?: base[index]
            }
            return from(materialized)
        }
        return OverlayPhotoList(base, indexById, nextOverrides)
    }

    // StateFlow conflation must be identity-based for this immutable snapshot wrapper.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

    companion object {
        private const val MAX_OVERLAY_ENTRIES = 2048

        fun empty(): OverlayPhotoList = OverlayPhotoList(emptyList(), emptyMap(), emptyMap())

        fun from(records: List<PhotoRecord>): OverlayPhotoList {
            if (records.isEmpty()) return empty()
            val immutableBase = records.toList()
            val positions = HashMap<Long, Int>(immutableBase.size * 4 / 3 + 1)
            immutableBase.forEachIndexed { index, photo -> positions[photo.id] = index }
            return OverlayPhotoList(immutableBase, positions, emptyMap())
        }
    }
}

private data class AuxiliarySets(
    val folders: Set<String>,
    val cities: Set<String>,
    val tags: Set<String>,
)

private fun buildAuxiliarySets(records: List<PhotoRecord>): AuxiliarySets {
    val folders = HashSet<String>((records.size / 10).coerceAtLeast(16))
    val cities = HashSet<String>((records.size / 20).coerceAtLeast(16))
    val tags = HashSet<String>(LabelMapping.keywords.size + 100)

    records.forEach { record ->
        splitAndAdd(record.folderName, folders)
        splitAndAdd(record.folderPath, folders)
        record.city?.let { if (it.isNotBlank()) cities.add(it.lowercase().trim()) }
        record.state?.let { if (it.isNotBlank()) cities.add(it.lowercase().trim()) }
        record.country?.let { if (it.isNotBlank()) cities.add(it.lowercase().trim()) }
        record.mlTags.forEach { tag -> tags.add(tag.label.lowercase()) }
    }
    return AuxiliarySets(folders, cities, tags)
}

private fun splitAndAdd(text: String, target: MutableSet<String>) {
    if (text.isBlank()) return
    var start = 0
    val lower = text.lowercase()
    for (i in lower.indices) {
        val c = lower[i]
        if (c == '/' || c == ' ' || c == '-' || c == '_') {
            if (i - start >= 3) target.add(lower.substring(start, i))
            start = i + 1
        }
    }
    if (lower.length - start >= 3) target.add(lower.substring(start))
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

private fun applyIntelligenceUpdate(
    record: PhotoRecord,
    update: PhotoIndex.PhotoIntelligenceUpdate,
    nextTags: List<MLTag>,
): PhotoRecord {
    return record.copy(
        mlTags = nextTags,
        isArchiveFoodCandidate = update.archiveFoodCandidate ?: record.isArchiveFoodCandidate,
        isMlProcessed = update.isMlProcessed ?: record.isMlProcessed,
        mlStatus = update.mlStatus ?: record.mlStatus,
        ocrText = update.ocrText ?: record.ocrText,
        isOcrProcessed = update.isOcrProcessed ?: record.isOcrProcessed,
        ocrStatus = update.ocrStatus ?: record.ocrStatus,
        perceptualHash = update.perceptualHash ?: record.perceptualHash,
        blurScore = update.blurScore ?: record.blurScore,
    )
}
