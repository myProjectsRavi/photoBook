package com.photobook.app.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSearchRevealRequestGateTest {

    @Test
    fun currentRequestIsAlreadyConsumedWhenSessionStarts() {
        val gate = PhotoSearchRevealRequestGate(initialConsumedRequest = 7L)

        assertFalse(gate.consume(7L))
        assertFalse(gate.consume(6L))
    }

    @Test
    fun explicitRequestIsConsumedExactlyOnce() {
        val gate = PhotoSearchRevealRequestGate(initialConsumedRequest = 7L)

        assertTrue(gate.consume(8L))
        assertFalse(gate.consume(8L))
    }

    @Test
    fun laterLayoutOrQueryWorkCannotReplayOldRequest() {
        val gate = PhotoSearchRevealRequestGate(initialConsumedRequest = 12L)

        assertTrue(gate.consume(13L))

        // Simulates recomposition/effect restarts caused by query publication or viewport resize.
        repeat(5) {
            assertFalse(gate.consume(13L))
        }

        assertTrue(gate.consume(14L))
        assertFalse(gate.consume(14L))
    }

    @Test
    fun newSessionCanStartAtLatestRequestWithoutReplayingIt() {
        val priorSession = PhotoSearchRevealRequestGate(initialConsumedRequest = 20L)
        assertTrue(priorSession.consume(21L))

        val reopenedSession = PhotoSearchRevealRequestGate(initialConsumedRequest = 21L)
        assertFalse(reopenedSession.consume(21L))
        assertTrue(reopenedSession.consume(22L))
    }
}
