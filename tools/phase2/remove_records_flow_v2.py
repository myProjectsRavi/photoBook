#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/photobook/app/data/index/PhotoIndex.kt")
text = path.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected one match, found {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)


replace_once(
'''    fun records(): StateFlow<List<PhotoRecord>> = backend.records()

    fun changes(): StateFlow<Long> = backend.changes()
''',
'''    fun changes(): StateFlow<Long> = backend.changes()
''',
)

replace_once(
'''internal interface PhotoIndexBackend {
    fun records(): StateFlow<List<PhotoRecord>>
    fun changes(): StateFlow<Long>
''',
'''internal interface PhotoIndexBackend {
    fun changes(): StateFlow<Long>
''',
)

replace_once(
'''    override fun records(): StateFlow<List<PhotoRecord>> = recordsFlow.asStateFlow()
    override fun changes(): StateFlow<Long> = changeFlow.asStateFlow()
''',
'''    override fun changes(): StateFlow<Long> = changeFlow.asStateFlow()
''',
)

replace_once(
'''    @Volatile private var snapshot = OverlayPhotoList.empty()
    private val recordsFlow = MutableStateFlow<List<PhotoRecord>>(snapshot)
    private val changeFlow = MutableStateFlow(0L)
''',
'''    @Volatile private var snapshot = OverlayPhotoList.empty()
    private val changeFlow = MutableStateFlow(0L)
''',
)

# The remaining records() override is the v2 backend.
replace_once(
'''    override fun records(): StateFlow<List<PhotoRecord>> = recordsFlow.asStateFlow()
    override fun changes(): StateFlow<Long> = changeFlow.asStateFlow()
''',
'''    override fun changes(): StateFlow<Long> = changeFlow.asStateFlow()
''',
)

replace_once(
'''        snapshot = nextSnapshot
        recordsFlow.value = nextSnapshot
        changeFlow.value = publicationVersion
''',
'''        snapshot = nextSnapshot
        changeFlow.value = publicationVersion
''',
)

replace_once(
'''/**
 * Immutable List view with O(1) ID-to-position lookup and a bounded point-update overlay.
 * Identity equality is intentional: MutableStateFlow otherwise performs structural List equality,
 * turning an otherwise O(1) favorite update back into an O(n) scan before emission.
 */
''',
'''/** Immutable List view with O(1) ID-to-position lookup and a bounded point-update overlay. */
''',
)

replace_once(
'''    // StateFlow conflation must be identity-based for this immutable snapshot wrapper.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

''',
'',
)

path.write_text(text)
print(f"removed obsolete records flow and restored normal List equality in {path}")
