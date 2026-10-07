package com.photobook.app.feature.declutter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeclutterSessionTest {

    @Test
    fun confirmedTrash_beforeCurrentItem_keepsSameCurrentCandidate() {
        val session = DeclutterSession(
            candidates = candidates(1L, 2L, 3L, 4L),
            currentIndex = 2,
            markedTrashIds = setOf(1L),
            keptIds = setOf(2L),
        )

        val reconciled = session.afterConfirmedTrash(setOf(1L))

        assertThat(reconciled).isNotNull()
        assertThat(reconciled!!.candidates.map { it.photoId }).containsExactly(2L, 3L, 4L).inOrder()
        assertThat(reconciled.currentCandidate?.photoId).isEqualTo(3L)
        assertThat(reconciled.currentIndex).isEqualTo(1)
        assertThat(reconciled.markedTrashIds).isEmpty()
        assertThat(reconciled.keptIds).containsExactly(2L)
    }

    @Test
    fun confirmedTrash_afterCompletedReview_staysComplete() {
        val session = DeclutterSession(
            candidates = candidates(1L, 2L, 3L),
            currentIndex = 3,
            markedTrashIds = setOf(1L, 3L),
            keptIds = setOf(2L),
        )

        val reconciled = session.afterConfirmedTrash(setOf(1L, 3L))

        assertThat(reconciled).isNotNull()
        assertThat(reconciled!!.candidates.map { it.photoId }).containsExactly(2L)
        assertThat(reconciled.currentIndex).isEqualTo(1)
        assertThat(reconciled.isComplete).isTrue()
        assertThat(reconciled.currentCandidate).isNull()
    }

    @Test
    fun confirmedTrash_ofAllDraftCandidates_closesSession() {
        val session = DeclutterSession(
            candidates = candidates(1L, 2L),
            currentIndex = 2,
            markedTrashIds = setOf(1L, 2L),
        )

        assertThat(session.afterConfirmedTrash(setOf(1L, 2L))).isNull()
    }

    @Test
    fun emptyConfirmedTrash_isNoOp() {
        val session = DeclutterSession(candidates = candidates(1L, 2L), currentIndex = 1)

        assertThat(session.afterConfirmedTrash(emptySet())).isSameInstanceAs(session)
    }

    private fun candidates(vararg ids: Long): List<DeclutterCandidate> =
        ids.map { id -> DeclutterCandidate(id, DeclutterReason.Screenshot) }
}
