package com.photobook.app.feature.phototextsearch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhotoTextCoordinateMapperTest {

    @Test
    fun fitLetterbox_mapsAuthoritativeFixture() {
        val mapped = PhotoTextCoordinateMapper.mapToFitViewport(
            point = UnitPoint(0.25f, 0.5f),
            uprightWidth = 1200,
            uprightHeight = 800,
            viewportWidth = 1080f,
            viewportHeight = 1600f,
        )

        assertThat(mapped.x).isWithin(0.01f).of(270f)
        assertThat(mapped.y).isWithin(0.01f).of(800f)
    }

    @Test
    fun fitMapping_roundTripsViewportCenterForResizePreservation() {
        val unit = UnitPoint(0.62f, 0.41f)
        val mapped = PhotoTextCoordinateMapper.mapToFitViewport(
            point = unit,
            uprightWidth = 1600,
            uprightHeight = 900,
            viewportWidth = 1080f,
            viewportHeight = 1200f,
        )

        val roundTrip = PhotoTextCoordinateMapper.mapViewportPointToUnit(
            point = mapped,
            uprightWidth = 1600,
            uprightHeight = 900,
            viewportWidth = 1080f,
            viewportHeight = 1200f,
        )

        assertThat(roundTrip).isNotNull()
        assertThat(roundTrip!!.x).isWithin(0.0001f).of(unit.x)
        assertThat(roundTrip.y).isWithin(0.0001f).of(unit.y)
    }

    @Test
    fun nonFiniteUnitPoint_withValidViewport_isSanitized() {
        val mapped = PhotoTextCoordinateMapper.mapToFitViewport(
            point = UnitPoint(Float.NaN, Float.POSITIVE_INFINITY),
            uprightWidth = 100,
            uprightHeight = 100,
            viewportWidth = 500f,
            viewportHeight = 500f,
        )

        assertThat(mapped.x.isFinite()).isTrue()
        assertThat(mapped.y.isFinite()).isTrue()
    }

    @Test
    fun invalidViewport_neverProducesNan() {
        val mapped = PhotoTextCoordinateMapper.mapToFitViewport(
            point = UnitPoint(Float.NaN, Float.POSITIVE_INFINITY),
            uprightWidth = 0,
            uprightHeight = 0,
            viewportWidth = 0f,
            viewportHeight = 0f,
        )

        assertThat(mapped.x.isFinite()).isTrue()
        assertThat(mapped.y.isFinite()).isTrue()
    }
}
