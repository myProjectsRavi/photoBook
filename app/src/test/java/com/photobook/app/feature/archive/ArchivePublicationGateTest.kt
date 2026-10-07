package com.photobook.app.feature.archive

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchivePublicationGateTest {
    @Test
    fun newerRequestInvalidatesOlderPublicationRevision() {
        val gate = ArchivePublicationGate()

        val older = gate.begin()
        val newer = gate.begin()

        assertThat(gate.isCurrent(older)).isFalse()
        assertThat(gate.isCurrent(newer)).isTrue()
    }

    @Test
    fun dismissInvalidatesInFlightPublication() {
        val gate = ArchivePublicationGate()

        val inFlight = gate.begin()
        gate.invalidate()

        assertThat(gate.isCurrent(inFlight)).isFalse()
    }

    @Test
    fun latestRevisionRemainsPublishableUntilNextMutation() {
        val gate = ArchivePublicationGate()

        val revision = gate.begin()

        assertThat(gate.isCurrent(revision)).isTrue()
    }
}
