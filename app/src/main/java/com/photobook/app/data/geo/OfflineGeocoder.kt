package com.photobook.app.data.geo

import com.photobook.app.data.model.GeoResult
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class OfflineGeocoder @Inject constructor(
    cityDatabase: CityDatabase,
) {

    private val gridSize = 2.0
    private val cities: List<CityDatabase.CityEntry> = cityDatabase.loadCities()
    private val grid: Map<Pair<Int, Int>, List<CityDatabase.CityEntry>> = run {
        val mutable = mutableMapOf<Pair<Int, Int>, MutableList<CityDatabase.CityEntry>>()
        cities.forEach { city ->
            val key = gridKey(city.latitude, city.longitude)
            mutable.getOrPut(key) { mutableListOf() }.add(city)
        }
        mutable
    }

    fun reverseGeocode(latitude: Double, longitude: Double): GeoResult? {
        val centerKey = gridKey(latitude, longitude)
        val candidates = mutableListOf<CityDatabase.CityEntry>()
        for (latOffset in -1..1) {
            for (lonOffset in -1..1) {
                val key = centerKey.first + latOffset to centerKey.second + lonOffset
                candidates += grid[key].orEmpty()
            }
        }

        if (candidates.isEmpty()) return null

        val nearest = candidates.minByOrNull { city ->
            haversine(latitude, longitude, city.latitude, city.longitude)
        } ?: return null

        return GeoResult(
            city = nearest.city,
            state = nearest.state,
            country = nearest.country,
        )
    }

    private fun gridKey(latitude: Double, longitude: Double): Pair<Int, Int> {
        return floor(latitude / gridSize).toInt() to floor(longitude / gridSize).toInt()
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
        return 2 * 6371.0 * atan2(sqrt(a), sqrt(1 - a))
    }
}
