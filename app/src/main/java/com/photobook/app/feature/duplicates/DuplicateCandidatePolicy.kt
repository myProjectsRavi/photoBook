package com.photobook.app.feature.duplicates

internal object DuplicateCandidatePolicy {
    /**
     * Database candidate pruning is safe only when it represents the exact same photo snapshot.
     *
     * Equal row counts alone are insufficient because one removed photo and one newly inserted
     * photo can preserve the count while changing the candidate universe.
     */
    fun matchesSnapshot(
        recordIds: Collection<Long>,
        databaseIds: Collection<Long>,
    ): Boolean {
        if (recordIds.size != databaseIds.size) return false
        return recordIds.toHashSet() == databaseIds.toHashSet()
    }
}
