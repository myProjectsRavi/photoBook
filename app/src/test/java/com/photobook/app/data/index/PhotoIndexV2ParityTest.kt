package com.photobook.app.data.index

import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.MLTag
import com.photobook.app.data.model.PhotoRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoIndexV2ParityTest {

    @Test
    fun v2_matchesLegacyAcrossDeterministicScaleCorporaAndMutations() = runBlocking {
        listOf(10_000, 50_000, 100_000).forEach { count ->
            val records = deterministicRecords(count)
            val legacy = PhotoIndex(PhotoIndexStrategy.LEGACY)
            val v2 = PhotoIndex(PhotoIndexStrategy.V2)

            legacy.setRecords(records)
            v2.setRecords(records)
            assertParity(legacy, v2, "initial-$count")

            val ids = listOf(1L, (count / 2).toLong(), count.toLong())
            ids.forEachIndexed { step, id ->
                val previousV2Snapshot = v2.snapshot()
                legacy.setFavorite(id, true)
                v2.setFavorite(id, true)
                assertNotSame("v2 point update should publish a new immutable view", previousV2Snapshot, v2.snapshot())
                assertParity(legacy, v2, "favorite-$count-$step")
            }

            val intelligenceUpdates = ids.mapIndexed { index, id ->
                PhotoIndex.PhotoIntelligenceUpdate(
                    id = id,
                    tags = listOf(
                        MLTag("document", 0.80f + index * 0.01f),
                        MLTag("phase2_tag_$index", 0.91f),
                    ),
                    isMlProcessed = true,
                    mlStatus = IntelligenceStatus.PROCESSED,
                    ocrText = "phase2 receipt token $index",
                    isOcrProcessed = true,
                    ocrStatus = IntelligenceStatus.PROCESSED,
                    perceptualHash = 10_000L + index,
                    blurScore = index / 10.0,
                )
            }
            assertEquals(
                legacy.updatePhotosIntelligence(intelligenceUpdates),
                v2.updatePhotosIntelligence(intelligenceUpdates),
            )
            assertParity(legacy, v2, "intelligence-$count")

            val middleId = (count / 2).toLong()
            assertEquals(legacy.toggleFavorite(middleId), v2.toggleFavorite(middleId))
            assertParity(legacy, v2, "toggle-$count")

            val existing = checkNotNull(legacy.getById(middleId))
            val updatedExisting = existing.copy(
                fileName = "phase2-upsert-$count.jpg",
                folderName = "Phase2 Updated",
                folderPath = "/Pictures/Phase2 Updated",
                city = "Hyderabad",
            )
            legacy.upsertRecord(updatedExisting)
            v2.upsertRecord(updatedExisting)
            assertParity(legacy, v2, "upsert-existing-$count")

            val newRecord = fixtureRecord(
                index = count + 10,
                id = count.toLong() + 10L,
                dateAdded = existing.dateAdded,
            )
            legacy.upsertRecord(newRecord)
            v2.upsertRecord(newRecord)
            assertParity(legacy, v2, "upsert-new-$count")

            val removeIds = setOf(2L, count.toLong() - 1L, newRecord.id)
            legacy.removeRecords(removeIds)
            v2.removeRecords(removeIds)
            assertParity(legacy, v2, "remove-$count")

            assertEquals(
                ids.mapNotNull(legacy::getById),
                ids.mapNotNull(v2::getById),
            )
            assertEquals(
                ids.mapNotNull(legacy::getById),
                v2.getByIdsOrdered(ids),
            )
        }
    }

    @Test
    fun v2_compactsLargePointOverlayWithoutChangingOrderOrContent() = runBlocking {
        val count = 10_000
        val legacy = PhotoIndex(PhotoIndexStrategy.LEGACY)
        val v2 = PhotoIndex(PhotoIndexStrategy.V2)
        val records = deterministicRecords(count)
        legacy.setRecords(records)
        v2.setRecords(records)

        // Cross the v2 overlay compaction boundary using one batched intelligence mutation.
        val updates = (1L..2_100L).map { id ->
            PhotoIndex.PhotoIntelligenceUpdate(
                id = id,
                tags = listOf(MLTag("phase2_compact", 0.93f)),
                isMlProcessed = true,
                mlStatus = IntelligenceStatus.PROCESSED,
            )
        }
        assertEquals(legacy.updatePhotosIntelligence(updates), v2.updatePhotosIntelligence(updates))
        assertParity(legacy, v2, "post-compaction")
    }

    private fun assertParity(legacy: PhotoIndex, v2: PhotoIndex, label: String) {
        assertEquals("size mismatch at $label", legacy.size(), v2.size())
        assertEquals("version mismatch at $label", legacy.version(), v2.version())
        assertEquals("change version mismatch at $label", legacy.changes().value, v2.changes().value)
        assertEquals("folder keywords mismatch at $label", legacy.folderKeywords(), v2.folderKeywords())
        assertEquals("city keywords mismatch at $label", legacy.cityKeywords(), v2.cityKeywords())
        assertEquals("ML keywords mismatch at $label", legacy.mlKeywords(), v2.mlKeywords())

        val legacyRecords = legacy.snapshot().toList()
        val v2Records = v2.snapshot().toList()
        assertEquals("records mismatch at $label", legacyRecords, v2Records)
        assertEquals(
            "ID order mismatch at $label",
            legacyRecords.map { it.id },
            v2Records.map { it.id },
        )
        assertTrue("v2 IDs must remain unique at $label", v2Records.map { it.id }.toSet().size == v2Records.size)
    }

    private fun deterministicRecords(count: Int): List<PhotoRecord> {
        return List(count) { offset ->
            fixtureRecord(
                index = offset,
                id = offset.toLong() + 1L,
                // Deliberate ties exercise stable ordering semantics.
                dateAdded = BASE_TIME - (offset / 4) * 1_000L,
            )
        }
    }

    private fun fixtureRecord(index: Int, id: Long, dateAdded: Long): PhotoRecord {
        val folder = when (index % 4) {
            0 -> "Camera"
            1 -> "Screenshots"
            2 -> "Downloads"
            else -> "WhatsApp Images"
        }
        val city = when (index % 3) {
            0 -> "Hyderabad"
            1 -> "Bengaluru"
            else -> null
        }
        val tags = when (index % 5) {
            0 -> listOf(MLTag("document", 0.88f))
            1 -> listOf(MLTag("food", 0.81f))
            else -> emptyList()
        }
        return PhotoRecord(
            id = id,
            uriString = "content://phase2/$id",
            filePath = "/storage/emulated/0/Pictures/$folder/$id.jpg",
            fileName = "IMG_${id}_${index % 17}.jpg",
            dateAdded = dateAdded,
            year = 2026 - (index % 5),
            month = (index % 12) + 1,
            dayOfMonth = (index % 28) + 1,
            dayOfWeek = (index % 7) + 1,
            hourOfDay = index % 24,
            latitude = if (index % 11 == 0) 17.3850 else null,
            longitude = if (index % 11 == 0) 78.4867 else null,
            city = city,
            state = city?.let { "Telangana" },
            country = city?.let { "India" },
            fileSize = 500_000L + index * 100L,
            width = if (index % 2 == 0) 4032 else 1080,
            height = if (index % 2 == 0) 3024 else 1920,
            mimeType = "image/jpeg",
            folderName = folder,
            folderPath = "/Pictures/$folder",
            cameraModel = if (folder == "Camera") "Phase2 Camera" else null,
            isFrontCamera = index % 13 == 0,
            isHdr = index % 19 == 0,
            isFavorite = index % 23 == 0,
            mlTags = tags,
            isMlProcessed = tags.isNotEmpty(),
            mlStatus = if (tags.isNotEmpty()) IntelligenceStatus.PROCESSED else IntelligenceStatus.PENDING,
            ocrText = when (index % 7) {
                0 -> "invoice payment total 499"
                1 -> "birthday dinner menu"
                else -> ""
            },
            isOcrProcessed = index % 7 <= 1,
            ocrStatus = if (index % 7 <= 1) IntelligenceStatus.PROCESSED else IntelligenceStatus.PENDING,
        )
    }

    private companion object {
        private const val BASE_TIME = 1_786_900_000_000L
    }
}
