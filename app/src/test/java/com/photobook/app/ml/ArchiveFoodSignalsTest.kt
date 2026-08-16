package com.photobook.app.ml

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.model.MLTag
import org.junit.Test

class ArchiveFoodSignalsTest {

    @Test
    fun strongFoodWithoutLiveSubject_isEligible() {
        assertThat(
            ArchiveFoodSignals.isEligible(
                listOf(
                    MLTag("food", 0.86f),
                    MLTag("prepared_food", 0.86f),
                ),
            ),
        ).isTrue()
    }

    @Test
    fun genericFoodWithoutPreparedContext_isRejected() {
        assertThat(
            ArchiveFoodSignals.isEligible(
                listOf(MLTag("food", 0.86f)),
            ),
        ).isFalse()
    }

    @Test
    fun foodWithBird_isRejected() {
        assertThat(
            ArchiveFoodSignals.isEligible(
                listOf(
                    MLTag("food", 0.91f),
                    MLTag("prepared_food", 0.91f),
                    MLTag("bird", 0.88f),
                ),
            ),
        ).isFalse()
    }

    @Test
    fun foodWithAnimal_isRejected() {
        assertThat(
            ArchiveFoodSignals.isEligible(
                listOf(
                    MLTag("food", 0.91f),
                    MLTag("prepared_food", 0.91f),
                    MLTag("animal", 0.88f),
                ),
            ),
        ).isFalse()
    }

    @Test
    fun foodWithPerson_isRejected() {
        assertThat(
            ArchiveFoodSignals.isEligible(
                listOf(
                    MLTag("food", 0.91f),
                    MLTag("prepared_food", 0.91f),
                    MLTag("people", 0.90f),
                ),
            ),
        ).isFalse()
    }

    @Test
    fun weakFood_isRejected() {
        assertThat(
            ArchiveFoodSignals.isEligible(
                listOf(
                    MLTag("food", 0.69f),
                    MLTag("prepared_food", 0.69f),
                ),
            ),
        ).isFalse()
    }

    @Test
    fun modelLiveSubjectAliases_areCanonicalizedForTheArchiveGate() {
        assertThat(LabelMapping.map("Bird")).isEqualTo("bird")
        assertThat(LabelMapping.map("Person")).isEqualTo("people")
        assertThat(LabelMapping.map("Animal")).isEqualTo("animal")
        assertThat(LabelMapping.map("Hen")).isEqualTo("bird")
        assertThat(LabelMapping.map("Cattle")).isEqualTo("animal")
        assertThat(LabelMapping.map("Bull")).isEqualTo("animal")
        assertThat(LabelMapping.map("Horse")).isEqualTo("animal")
    }
}
