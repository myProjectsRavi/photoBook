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
    fun everyDeclaredLivestockSubject_isRejected() {
        val livestock = listOf(
            "cow",
            "buffalo",
            "goat",
            "sheep",
            "lamb",
            "cattle",
            "bull",
            "horse",
            "hen",
            "rooster",
            "chicken",
            "livestock",
        )

        livestock.forEach { subject ->
            assertThat(
                ArchiveFoodSignals.isEligible(
                    listOf(
                        MLTag("food", 0.95f),
                        MLTag("prepared_food", 0.95f),
                        MLTag(subject, 0.90f),
                    ),
                ),
            ).named(subject).isFalse()
        }
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
        val aliases = mapOf(
            "Bird" to "bird",
            "Person" to "people",
            "Animal" to "animal",
            "Hen" to "bird",
            "Cattle" to "animal",
            "Bull" to "animal",
            "Horse" to "animal",
            "Cow" to "animal",
            "Buffalo" to "animal",
            "Goat" to "animal",
            "Sheep" to "animal",
            "Lamb" to "animal",
            "Livestock" to "animal",
            "Chicken" to "bird",
        )

        aliases.forEach { (raw, expected) ->
            assertThat(LabelMapping.map(raw)).named(raw).isEqualTo(expected)
        }
    }

    @Test
    fun genericObjects_doNotCountAsPreparedFood() {
        listOf("product", "plate", "bowl", "steaming").forEach { label ->
            assertThat(LabelMapping.isPreparedFoodLabel(label)).named(label).isFalse()
            assertThat(LabelMapping.map(label)).named(label).isNull()
        }
    }
}
