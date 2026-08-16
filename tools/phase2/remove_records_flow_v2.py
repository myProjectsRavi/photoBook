#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/photobook/app/data/index/PhotoIndex.kt")
text = path.read_text()


def replace_exact(old: str, new: str, expected: int = 1) -> None:
    global text
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"expected {expected} match(es), found {count}: {old[:120]!r}")
    text = text.replace(old, new, expected)


replace_exact(
'''    fun records(): StateFlow<List<PhotoRecord>> = backend.records()

    fun changes(): StateFlow<Long> = backend.changes()
''',
'''    fun changes(): StateFlow<Long> = backend.changes()
''',
)

replace_exact(
'''internal interface PhotoIndexBackend {
    fun records(): StateFlow<List<PhotoRecord>>
    fun changes(): StateFlow<Long>
''',
'''internal interface PhotoIndexBackend {
    fun changes(): StateFlow<Long>
''',
)

# Both legacy and v2 implement the same obsolete exposed records flow.
replace_exact(
'''    override fun records(): StateFlow<List<PhotoRecord>> = recordsFlow.asStateFlow()
    override fun changes(): StateFlow<Long> = changeFlow.asStateFlow()
''',
'''    override fun changes(): StateFlow<Long> = changeFlow.asStateFlow()
''',
    expected=2,
)

replace_exact(
'''    @Volatile private var snapshot = OverlayPhotoList.empty()
    private val recordsFlow = MutableStateFlow<List<PhotoRecord>>(snapshot)
    private val changeFlow = MutableStateFlow(0L)
''',
'''    @Volatile private var snapshot = OverlayPhotoList.empty()
    private val changeFlow = MutableStateFlow(0L)
''',
)

replace_exact(
'''        snapshot = nextSnapshot
        recordsFlow.value = nextSnapshot
        changeFlow.value = publicationVersion
''',
'''        snapshot = nextSnapshot
        changeFlow.value = publicationVersion
''',
)

replace_exact(
'''/**
 * Immutable List view with O(1) ID-to-position lookup and a bounded point-update overlay.
 * Identity equality is intentional: MutableStateFlow otherwise performs structural List equality,
 * turning an otherwise O(1) favorite update back into an O(n) scan before emission.
 */
''',
'''/** Immutable List view with O(1) ID-to-position lookup and a bounded point-update overlay. */
''',
)

replace_exact(
'''    // StateFlow conflation must be identity-based for this immutable snapshot wrapper.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

''',
'',
)

path.write_text(text)
print(f"removed obsolete records flow and restored normal List equality in {path}")
