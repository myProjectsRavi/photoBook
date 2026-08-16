#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/photobook/app/data/index/PhotoIndex.kt")
text = path.read_text()
start = text.index("internal class OverlayPhotoIndexBackend : PhotoIndexBackend {")
end = text.index("/**\n * Immutable List view with O(1) ID-to-position lookup", start)
region = text[start:end]


def replace_once(old: str, new: str) -> None:
    global region
    count = region.count(old)
    if count != 1:
        raise SystemExit(f"expected one v2 match, found {count}: {old[:120]!r}")
    region = region.replace(old, new, 1)


replace_once(
'''            val sorted = records.sortedByDescending { it.dateAdded }
            rebuildAuxiliarySets(sorted)
            publishStructural(sorted)
''',
'''            val sorted = records.sortedByDescending { it.dateAdded }
            val publicationVersion = beginPublication()
            rebuildAuxiliarySets(sorted)
            publishStructural(sorted, publicationVersion)
''',
)

replace_once(
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
'''            if (results.isNotEmpty()) {
                val publicationVersion = beginPublication()
                val newMlKeywords = results.asSequence()
                    .flatMap { it.mlTags.asSequence().map { tag -> tag.label.lowercase() } }
                    .toSet()
                if (!mlKeywords.containsAll(newMlKeywords)) {
                    mlKeywords = mlKeywords + newMlKeywords
                }
                publishPointUpdates(results, publicationVersion)
            }
''',
)

old = '''            recordsMap[id] = updated
            publishPointUpdates(listOf(updated))
'''
new = '''            recordsMap[id] = updated
            val publicationVersion = beginPublication()
            publishPointUpdates(listOf(updated), publicationVersion)
'''
if region.count(old) != 2:
    raise SystemExit(f"expected two favorite/toggle publication matches, found {region.count(old)}")
region = region.replace(old, new, 2)

replace_once(
'''            // Keep keyword semantics exactly aligned with the rollback implementation.
            rebuildAuxiliarySets(nextSnapshot)
            publishSnapshot(nextSnapshot)
''',
'''            val publicationVersion = beginPublication()
            // Keep keyword semantics exactly aligned with the rollback implementation.
            rebuildAuxiliarySets(nextSnapshot)
            publishSnapshot(nextSnapshot, publicationVersion)
''',
)

replace_once(
'''            if (anyRemoved) {
                val nextSnapshot = OverlayPhotoList.from(recordsMap.values.sortedByDescending { it.dateAdded })
                rebuildAuxiliarySets(nextSnapshot)
                publishSnapshot(nextSnapshot)
            }
''',
'''            if (anyRemoved) {
                val nextSnapshot = OverlayPhotoList.from(recordsMap.values.sortedByDescending { it.dateAdded })
                val publicationVersion = beginPublication()
                rebuildAuxiliarySets(nextSnapshot)
                publishSnapshot(nextSnapshot, publicationVersion)
            }
''',
)

replace_once(
'''    private fun publishStructural(records: List<PhotoRecord>) {
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
'''    private fun beginPublication(): Long {
        val nextVersion = version + 1L
        version = nextVersion
        return nextVersion
    }

    private fun publishStructural(records: List<PhotoRecord>, publicationVersion: Long) {
        publishSnapshot(OverlayPhotoList.from(records), publicationVersion)
    }

    private fun publishPointUpdates(records: List<PhotoRecord>, publicationVersion: Long) {
        publishSnapshot(snapshot.replaceAll(records), publicationVersion)
    }

    /**
     * `version` is advanced by beginPublication() before any exposed keyword/snapshot state changes.
     * `changeFlow` advances only after the immutable snapshot is fully visible. Therefore
     * version != changeFlow.value means a publication is in progress and Search v2 must fail closed.
     */
    private fun publishSnapshot(nextSnapshot: OverlayPhotoList, publicationVersion: Long) {
        check(version == publicationVersion) { "PhotoIndex publication generation changed unexpectedly" }
        snapshot = nextSnapshot
        recordsFlow.value = nextSnapshot
        changeFlow.value = publicationVersion
    }
''',
)

text = text[:start] + region + text[end:]
path.write_text(text)
print(f"finalized publication protocol in {path}")
