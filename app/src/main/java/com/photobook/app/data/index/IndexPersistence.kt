package com.photobook.app.data.index

import android.content.Context
import com.photobook.app.data.model.MLTag
import com.photobook.app.data.model.PhotoRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class IndexPersistence @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val indexFile = File(context.filesDir, "photo_index.json")

    suspend fun save(records: List<PhotoRecord>) {
        withContext(Dispatchers.IO) {
            if (records.isEmpty()) {
                if (indexFile.exists()) indexFile.delete()
                return@withContext
            }

            val root = JSONObject()
            val photos = JSONArray()
            records.forEach { record ->
                photos.put(record.toJson())
            }
            root.put("photos", photos)
            indexFile.writeText(root.toString())
        }
    }

    suspend fun load(): List<PhotoRecord> {
        return withContext(Dispatchers.IO) {
            if (!indexFile.exists()) {
                return@withContext emptyList()
            }
            runCatching {
                val root = JSONObject(indexFile.readText())
                val photos = root.optJSONArray("photos") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until photos.length()) {
                        val obj = photos.optJSONObject(i) ?: continue
                        add(obj.toPhotoRecord())
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun PhotoRecord.toJson(): JSONObject {
        val mlTagArray = JSONArray()
        mlTags.forEach { tag ->
            mlTagArray.put(
                JSONObject()
                    .put("label", tag.label)
                    .put("confidence", tag.confidence)
            )
        }

        return JSONObject()
            .put("id", id)
            .put("uriString", uriString)
            .put("filePath", filePath)
            .put("fileName", fileName)
            .put("dateAdded", dateAdded)
            .put("year", year)
            .put("month", month)
            .put("dayOfMonth", dayOfMonth)
            .put("dayOfWeek", dayOfWeek)
            .put("hourOfDay", hourOfDay)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("city", city)
            .put("state", state)
            .put("country", country)
            .put("fileSize", fileSize)
            .put("width", width)
            .put("height", height)
            .put("mimeType", mimeType)
            .put("folderName", folderName)
            .put("folderPath", folderPath)
            .put("cameraModel", cameraModel)
            .put("isFrontCamera", isFrontCamera)
            .put("isHdr", isHdr)
            .put("isMlProcessed", isMlProcessed)
            .put("ocrText", ocrText)
            .put("isOcrProcessed", isOcrProcessed)
            .put("mlTags", mlTagArray)
    }

    private fun JSONObject.toPhotoRecord(): PhotoRecord {
        val mlTagArray = optJSONArray("mlTags") ?: JSONArray()
        val tags = buildList {
            for (i in 0 until mlTagArray.length()) {
                val obj = mlTagArray.optJSONObject(i) ?: continue
                add(
                    MLTag(
                        label = obj.optString("label"),
                        confidence = obj.optDouble("confidence").toFloat(),
                    )
                )
            }
        }

        return PhotoRecord(
            id = optLong("id"),
            uriString = optString("uriString"),
            filePath = optString("filePath"),
            fileName = optString("fileName"),
            dateAdded = optLong("dateAdded"),
            year = optInt("year"),
            month = optInt("month"),
            dayOfMonth = optInt("dayOfMonth"),
            dayOfWeek = optInt("dayOfWeek"),
            hourOfDay = optInt("hourOfDay"),
            latitude = optDoubleOrNull("latitude"),
            longitude = optDoubleOrNull("longitude"),
            city = optStringOrNull("city"),
            state = optStringOrNull("state"),
            country = optStringOrNull("country"),
            fileSize = optLong("fileSize"),
            width = optInt("width"),
            height = optInt("height"),
            mimeType = optString("mimeType"),
            folderName = optString("folderName"),
            folderPath = optString("folderPath"),
            cameraModel = optStringOrNull("cameraModel"),
            isFrontCamera = optBoolean("isFrontCamera"),
            isHdr = optBoolean("isHdr"),
            mlTags = tags,
            isMlProcessed = optBoolean("isMlProcessed", tags.isNotEmpty()),
            ocrText = optString("ocrText", ""),
            isOcrProcessed = optBoolean("isOcrProcessed", false),
        )
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        return if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        return if (isNull(name)) null else optDouble(name)
    }
}
