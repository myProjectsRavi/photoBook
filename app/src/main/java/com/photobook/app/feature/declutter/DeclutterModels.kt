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
}
