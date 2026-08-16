#!/usr/bin/env python3
from pathlib import Path

PATH = Path("app/src/main/java/com/photobook/app/data/index/PhotoIndex.kt")
text = PATH.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected one match, found {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)


replace_once(
'''    fun snapshot(): List<PhotoRecord> = backend.snapshot()

    fun getById(id: Long): PhotoRecord? = backend.getById(id)
''',
'''    fun snapshot(): List<PhotoRecord> = backend.snapshot()

    /** Resolve against one immutable snapshot so a search generation never mixes index states. */
    internal fun getByIdFromSnapshot(records: List<PhotoRecord>, id: Long): PhotoRecord? {
        return when (records) {
            is OverlayPhotoList -> records.getById(id)
            else -> records.firstOrNull { record -> record.id == id }
        }
    }

    fun getById(id: Long): PhotoRecord? = backend.getById(id)
''',
)

replace_once(
'''    override fun snapshot(): List<PhotoRecord> = recordsFlow.value
    override fun getById(id: Long): PhotoRecord? = recordsMap[id]
    override fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord> = ids.mapNotNull(recordsMap::get)
    override fun size(): Int = recordsMap.size
''',
'''    override fun snapshot(): List<PhotoRecord> = recordsFlow.value
    override fun getById(id: Long): PhotoRecord? = recordsFlow.value.firstOrNull { record -> record.id == id }
    override fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord> {
        if (ids.isEmpty()) return emptyList()
        val byId = recordsFlow.value.associateBy { record -> record.id }
        return ids.mapNotNull(byId::get)
    }
    override fun size(): Int = recordsFlow.value.size
''',
)

# Legacy rollback reads cross coroutine/thread boundaries; volatile refs make those observations safe.
replace_once(
'''    private var version = 0L
    private var folderKeywords: Set<String> = emptySet()
    private var cityKeywords: Set<String> = emptySet()
    private var mlKeywords: Set<String> = emptySet()
''',
'''    @Volatile private var version = 0L
    @Volatile private var folderKeywords: Set<String> = emptySet()
    @Volatile private var cityKeywords: Set<String> = emptySet()
    @Volatile private var mlKeywords: Set<String> = emptySet()
''',
)

replace_once(
'''    private val recordsMap = LinkedHashMap<Long, PhotoRecord>()
    private var snapshot = OverlayPhotoList.empty()
    private val recordsFlow = MutableStateFlow<List<PhotoRecord>>(snapshot)
    private val changeFlow = MutableStateFlow(0L)

    private var version = 0L
    private var folderKeywords: Set<String> = emptySet()
    private var cityKeywords: Set<String> = emptySet()
    private var mlKeywords: Set<String> = emptySet()
''',
'''    // recordsMap is writer-only under mutex. Readers use immutable snapshots exclusively.
    private val recordsMap = LinkedHashMap<Long, PhotoRecord>()
    @Volatile private var snapshot = OverlayPhotoList.empty()
    private val recordsFlow = MutableStateFlow<List<PhotoRecord>>(snapshot)
    private val changeFlow = MutableStateFlow(0L)

    @Volatile private var version = 0L
    @Volatile private var folderKeywords: Set<String> = emptySet()
    @Volatile private var cityKeywords: Set<String> = emptySet()
    @Volatile private var mlKeywords: Set<String> = emptySet()
''',
)

replace_once(
'''    override fun snapshot(): List<PhotoRecord> = snapshot
    override fun getById(id: Long): PhotoRecord? = recordsMap[id]
    override fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord> = ids.mapNotNull(recordsMap::get)
    override fun size(): Int = recordsMap.size
''',
'''    override fun snapshot(): List<PhotoRecord> = snapshot
    override fun getById(id: Long): PhotoRecord? = snapshot.getById(id)
    override fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord> = snapshot.getByIdsOrdered(ids)
    override fun size(): Int = snapshot.size
''',
)

replace_once(
'''    override suspend fun setRecords(records: List<PhotoRecord>) {
        mutex.withLock {
            recordsMap.clear()
            records.forEach { recordsMap[it.id] = it }
            publishStructural(records.sortedByDescending { it.dateAdded })
            rebuildAuxiliarySets(snapshot)
            bumpVersion()
        }
    }
''',
'''    override suspend fun setRecords(records: List<PhotoRecord>) {
        mutex.withLock {
            recordsMap.clear()
            records.forEach { recordsMap[it.id] = it }
            val sorted = records.sortedByDescending { it.dateAdded }
            rebuildAuxiliarySets(sorted)
            publishStructural(sorted)
        }
    }
''',
)

replace_once(
'''            if (results.isNotEmpty()) {
                publishPointUpdates(results)
                bumpVersion()
                val newMlKeywords = results.asSequence()
                    .flatMap { it.mlTags.asSequence().map { tag -> tag.label.lowercase() } }
                    .toSet()
                if (!mlKeywords.containsAll(newMlKeywords)) {
                    mlKeywords = mlKeywords + newMlKeywords
                }
            }
''',
'''            if (results.isNotEmpty()) {
                val newMlKeywords = results.asSequence()
                    .flatMap { it.mlTags.asSequence().map { tag -> tag.label.lowercase() } }
                    .toSet()
                if (!mlKeywords.containsAll(newMlKeywords)) {
                    mlKeywords = mlKeywords + newMlKeywords
                }
                publishPointUpdates(results)
            }
''',
)

replace_once(
'''            recordsMap[id] = updated
            publishPointUpdates(listOf(updated))
            bumpVersion()
''',
'''            recordsMap[id] = updated
            publishPointUpdates(listOf(updated))
''',
)
replace_once(
'''            recordsMap[id] = updated
            publishPointUpdates(listOf(updated))
            bumpVersion()
''',
'''            recordsMap[id] = updated
            publishPointUpdates(listOf(updated))
''',
)

replace_once(
'''    override suspend fun upsertRecord(record: PhotoRecord) {
        mutex.withLock {
            val previous = recordsMap[record.id]
            recordsMap[record.id] = record
            when {
                previous == record -> Unit
                previous != null && previous.dateAdded == record.dateAdded -> publishPointUpdates(listOf(record))
                else -> publishStructural(recordsMap.values.sortedByDescending { it.dateAdded })
            }
            // Keep keyword semantics exactly aligned with the rollback implementation.
            rebuildAuxiliarySets(snapshot)
            bumpVersion()
        }
    }
''',
'''    override suspend fun upsertRecord(record: PhotoRecord) {
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
''',
)

replace_once(
'''    override suspend fun removeRecords(ids: Set<Long>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            var anyRemoved = false
            ids.forEach { id ->
                if (recordsMap.remove(id) != null) anyRemoved = true
            }
            if (anyRemoved) {
                publishStructural(recordsMap.values.sortedByDescending { it.dateAdded })
                rebuildAuxiliarySets(snapshot)
                bumpVersion()
            }
        }
    }

    private fun publishStructural(records: List<PhotoRecord>) {
        snapshot = OverlayPhotoList.from(records)
        recordsFlow.value = snapshot
    }

    private fun publishPointUpdates(records: List<PhotoRecord>) {
        snapshot = snapshot.replaceAll(records)
        recordsFlow.value = snapshot
    }
''',
'''    override suspend fun removeRecords(ids: Set<Long>) {
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
''',
)

# Remove only the v2 bumpVersion that remains after rebuildAuxiliarySets; legacy keeps its own.
needle = '''    private fun rebuildAuxiliarySets(records: List<PhotoRecord>) {
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
 * Immutable List view with O(1) ID-to-position lookup and a bounded point-update overlay.
'''
replacement = '''    private fun rebuildAuxiliarySets(records: List<PhotoRecord>) {
        val sets = buildAuxiliarySets(records)
        folderKeywords = sets.folders
        cityKeywords = sets.cities
        mlKeywords = sets.tags
    }
}

/**
 * Immutable List view with O(1) ID-to-position lookup and a bounded point-update overlay.
'''
replace_once(needle, replacement)

replace_once(
'''    override fun get(index: Int): PhotoRecord = overrides[index] ?: base[index]

    fun replaceAll(records: List<PhotoRecord>): OverlayPhotoList {
''',
'''    override fun get(index: Int): PhotoRecord = overrides[index] ?: base[index]

    fun getById(id: Long): PhotoRecord? {
        val index = indexById[id] ?: return null
        return get(index)
    }

    fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord> {
        return ids.mapNotNull(::getById)
    }

    fun replaceAll(records: List<PhotoRecord>): OverlayPhotoList {
''',
)

PATH.write_text(text)
print(f"hardened {PATH}")
