package com.photobook.app.data.geo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CityDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class CityEntry(
        val latitude: Double,
        val longitude: Double,
        val city: String,
        val state: String,
        val country: String,
    )

    fun loadCities(): List<CityEntry> {
        return runCatching {
            context.assets.open("cities_min.csv").bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split(',')
                    if (parts.size < 5) return@mapNotNull null
                    CityEntry(
                        latitude = parts[0].toDoubleOrNull() ?: return@mapNotNull null,
                        longitude = parts[1].toDoubleOrNull() ?: return@mapNotNull null,
                        city = parts[2].trim(),
                        state = parts[3].trim(),
                        country = parts[4].trim(),
                    )
                }.toList()
            }
        }.getOrDefault(emptyList())
    }
}
