package com.photobook.app.feature.declutter

import androidx.compose.runtime.Immutable

@Immutable
enum class DeclutterReason {
    ExactDuplicate,
    SimilarDuplicate,
    BurstExtra,
    Blurry,
    Screenshot,
    Download,
    Social,
    Document,
    Meme,
}

@Immutable
data class DeclutterCandidate(
    val photoId: Long,
    val reason: DeclutterReason,
)

@Immutable
data class DeclutterSession(
    val candidates: List<DeclutterCandidate>,
    val currentIndex: Int = 0,
    val markedTrashIds: Set<Long> = emptySet(),
    val keptIds: Set<Long> = emptySet(),
) {
    val isComplete: Boolean get() = currentIndex >= candidates.size
    val currentCandidate: DeclutterCandidate? get() = candidates.getOrNull(currentIndex)
    val progressText: String get() = "${(currentIndex + 1).coerceAtMost(candidates.size)} / ${candidates.size}"

    fun markCurrentForTrash(): DeclutterSession {
        val candidate = currentCandidate ?: return this
        return copy(
            currentIndex = (currentIndex + 1).coerceAtMost(candidates.size),
            markedTrashIds = markedTrashIds + candidate.photoId,
            keptIds = keptIds - candidate.photoId,
        )
    }

    fun keepCurrent(): DeclutterSession {
        val candidate = currentCandidate ?: return this
        return copy(
            currentIndex = (currentIndex + 1).coerceAtMost(candidates.size),
            markedTrashIds = markedTrashIds - candidate.photoId,
            keptIds = keptIds + candidate.photoId,
        )
    }

    fun undoLast(): DeclutterSession {
        if (currentIndex <= 0 || candidates.isEmpty()) return this
        val previousIndex = currentIndex - 1
        val previousCandidate = candidates.getOrNull(previousIndex) ?: return this
        return copy(
            currentIndex = previousIndex,
            markedTrashIds = markedTrashIds - previousCandidate.photoId,
            keptIds = keptIds - previousCandidate.photoId,
        )
    }

    fun afterConfirmedTrash(removedPhotoIds: Set<Long>): DeclutterSession? {
        if (removedPhotoIds.isEmpty()) return this
        val processedPrefix = candidates.take(currentIndex.coerceIn(0, candidates.size))
        val removedFromProcessedPrefix = processedPrefix.count { candidate ->
            candidate.photoId in removedPhotoIds
        }
        val remainingCandidates = candidates.filterNot { candidate ->
            candidate.photoId in removedPhotoIds
        }
        if (remainingCandidates.isEmpty()) return null

        val nextIndex = (currentIndex - removedFromProcessedPrefix)
            .coerceIn(0, remainingCandidates.size)
        return copy(
            candidates = remainingCandidates,
            currentIndex = nextIndex,
            markedTrashIds = markedTrashIds - removedPhotoIds,
            keptIds = keptIds - removedPhotoIds,
        )
    }
}
