package com.photobook.app.data.index

import android.content.Context
import androidx.room.withTransaction
import com.photobook.app.data.db.PhotoBookDatabase
import com.photobook.app.data.db.PhotoDao
import com.photobook.app.data.db.toFtsEntity
import com.photobook.app.data.db.toPhotoEntity
import com.photobook.app.data.db.toPhotoRecord
import com.photobook.app.data.model.IntelligenceStatus
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
    private val database: PhotoBookDatabase,
    private val photoDao: PhotoDao,
) {

    private val legacyIndexFile = File(context.filesDir, "photo_index.json")

    suspend fun load(): List<PhotoRecord> {
        return withContext(Dispatchers.IO) {
            val existing = photoDao.getAll().map { it.toPhotoRecord() }
            if (existing.isNotEmpty()) {
                return@withContext existing
            }

            val imported = loadLegacyJson()
            if (imported.isNotEmpty()) {
                replaceAll(imported)
                runCatching { legacyIndexFile.delete() }
            }
            imported
        }
    }

    suspend fun save(records: List<PhotoRecord>) {
        withContext(Dispatchers.IO) {
            replaceAll(records)
        }
    }

    suspend fun upsert(record: PhotoRecord) {
        upsertAll(listOf(record))
    }

    suspend fun upsertAll(records: List<PhotoRecord>) {
        if (records.isEmpty()) return
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val entities = records.map { it.toPhotoEntity() }
                entities.chunked(DB_BATCH_SIZE).forEach { batch ->
                    photoDao.upsertPhotos(batch)
                }
                entities.map { it.toFtsEntity() }
                    .chunked(DB_BATCH_SIZE)
                    .forEach { batch ->
                        photoDao.upsertFtsRows(batch)
                    }
            }
        }
    }

    suspend fun removeByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            database.withTransaction {
                deleteByIdsInternal(ids.toList())
            }
        }
    }

    suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            photoDao.updateFavorite(id, isFavorite)
        }
    }

    suspend fun getByIdsOrdered(ids: List<Long>): List<PhotoRecord> {
        if (ids.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            val entities = photoDao.getByIds(ids)
            if (entities.isEmpty()) {
                return@withContext emptyList()
            }
            val byId = entities.associateBy { entity -> entity.id }
            ids.mapNotNull { id -> byId[id]?.toPhotoRecord() }
        }
    }

    suspend fun searchByQueryText(rawQuery: String, limit: Int = Int.MAX_VALUE): List<PhotoRecord> {
        val matchQuery = toFtsMatchQuery(rawQuery) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val ids = runCatching {
                photoDao.searchIdsByText(matchQuery, limit)
            }.getOrDefault(emptyList())
            if (ids.isEmpty()) {
                return@withContext emptyList()
            }

            val entities = photoDao.getByIds(ids)
            if (entities.isEmpty()) {
                return@withContext emptyList()
            }

            val byId = entities.associateBy { it.id }
            ids.mapNotNull { id -> byId[id]?.toPhotoRecord() }
        }
    }

    private suspend fun replaceAll(records: List<PhotoRecord>) {
        database.withTransaction {
            if (records.isEmpty()) {
                val existingIds = photoDao.getAllIds()
                deleteByIdsInternal(existingIds)
                return@withTransaction
            }

            val existingIds = photoDao.getAllIds().toSet()
            val entities = records.map { it.toPhotoEntity() }
            entities.chunked(DB_BATCH_SIZE).forEach { batch ->
                photoDao.upsertPhotos(batch)
            }
            entities.map { it.toFtsEntity() }
                .chunked(DB_BATCH_SIZE)
                .forEach { batch ->
                    photoDao.upsertFtsRows(batch)
                }

            val incomingIds = entities.asSequence().map { it.id }.toSet()
            val staleIds = existingIds - incomingIds
            deleteByIdsInternal(staleIds.toList())
        }
    }

    private suspend fun deleteByIdsInternal(ids: List<Long>) {
        ids.chunked(DB_BATCH_SIZE).forEach { batch ->
            if (batch.isNotEmpty()) {
                photoDao.deleteByIds(batch)
                photoDao.deleteFtsByRowIds(batch)
            }
        }
    }

    private fun toFtsMatchQuery(rawQuery: String): String? {
        val tokens = rawQuery.lowercase()
            .split(Regex("[^\\p{L}\\p{N}_]+"))
            .map { token -> token.trim() }
            .filter { token -> token.length >= 2 }
            .map(::sanitizeToken)
            .filter { token -> token.length >= 2 }
            .distinct()
            .take(8)
        if (tokens.isEmpty()) return null

        return tokens.joinToString(" AND ") { token ->
            "$token*"
        }
    }

    private fun sanitizeToken(token: String): String {
        return token.replace("\"", "")
            .replace("'", "")
            .replace("`", "")
    }

    private fun loadLegacyJson(): List<PhotoRecord> {
        if (!legacyIndexFile.exists()) {
            return emptyList()
        }
        return runCatching {
            val root = JSONObject(legacyIndexFile.readText())
            val photos = root.optJSONArray("photos") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until photos.length()) {
                    val obj = photos.optJSONObject(i) ?: continue
                    add(obj.toPhotoRecord())
                }
            }
        }.getOrDefault(emptyList())
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
        // Legacy JSON predates the semantic Food/live-subject pass. Reopen ML for every
        // imported record so the new archive flag is populated even when the old color
        // heuristic never emitted a Food tag. Existing tags and OCR fields are preserved.
        val effectiveMlProcessed = false
        val effectiveMlStatus = IntelligenceStatus.PENDING

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
            isFavorite = optBoolean("isFavorite", false),
            perceptualHash = if (isNull("perceptualHash")) null else optLong("perceptualHash"),
            blurScore = if (isNull("blurScore")) null else optDouble("blurScore"),
            mlTags = tags,
            isMlProcessed = effectiveMlProcessed,
            mlStatus = effectiveMlStatus,
            ocrText = optString("ocrText", ""),
            isOcrProcessed = optBoolean("isOcrProcessed", false),
            ocrStatus = IntelligenceStatus.fromStored(
                optNullableString("ocrStatus"),
                optBoolean("isOcrProcessed", false),
            ),
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name) else null
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        return if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        return if (isNull(name)) null else optDouble(name)
    }

    companion object {
        private const val DB_BATCH_SIZE = 200
    }
}
