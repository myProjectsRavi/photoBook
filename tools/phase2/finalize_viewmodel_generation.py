#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/photobook/app/ui/viewmodel/MainViewModel.kt")
text = path.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected one match, found {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)


replace_once(
'''        val filteredIds = if (input.favoritesOnly) {
            searchResult.orderedIds.filter { id -> photoIndex.getById(id)?.isFavorite == true }
        } else {
            searchResult.orderedIds
        }
''',
'''        val filteredIds = if (input.favoritesOnly) {
            searchResult.orderedIds.filter { id ->
                photoIndex.getByIdFromSnapshot(records, id)?.isFavorite == true
            }
        } else {
            searchResult.orderedIds
        }
''',
)

replace_once(
'''        val timelineMarks = withContext(Dispatchers.Default) {
            buildTimelineMarks(filteredIds)
        }
''',
'''        val timelineMarks = withContext(Dispatchers.Default) {
            buildTimelineMarks(filteredIds, records)
        }
''',
)

replace_once(
'''    private fun buildTimelineMarks(
        orderedIds: List<Long>,
    ): List<TimelineMark> {
''',
'''    private fun buildTimelineMarks(
        orderedIds: List<Long>,
        records: List<PhotoRecord>,
    ): List<TimelineMark> {
''',
)

replace_once(
'''        orderedIds.forEachIndexed { index, id ->
            val record = photoIndex.getById(id) ?: return@forEachIndexed
''',
'''        orderedIds.forEachIndexed { index, id ->
            val record = photoIndex.getByIdFromSnapshot(records, id) ?: return@forEachIndexed
''',
)

path.write_text(text)
print(f"finalized one-generation ViewModel search post-processing in {path}")
