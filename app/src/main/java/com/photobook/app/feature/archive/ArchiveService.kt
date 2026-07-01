package com.photobook.app.feature.archive

import android.content.SharedPreferences
import com.photobook.app.data.db.ArchiveDao
import com.photobook.app.data.db.ArchiveDecisionEntity
import com.photobook.app.data.db.ArchiveDecisionStates
import com.photobook.app.data.db.PhotoDao
import com.photobook.app.data.db.VaultDao
import com.photobook.app.data.db.toPhotoRecord
import com.photobook.app.data.model.PhotoRecord
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArchiveCandidate(
    val photo: PhotoRecord,
    val confidence: Double,
    val reasons: List<String>,
)

data class ArchiveDueDeleteItem(
    val photoId: Long,
    val uriString: String,
)

data class ArchiveSummary(
    val candidates: List<ArchiveCandidate>,
    val dueDeleteCount: Int,
    val retentionDays: Int,
    val enabled: Boolean,
) {
    val candidateCount: Int get() = candidates.size
    val estimatedBytes: Long get() = candidates.sumOf { candidate -> candidate.photo.fileSize.coerceAtLeast(0L) }
}

class ArchiveService @Inject constructor(
    private val photoDao: PhotoDao,
    private val archiveDao: ArchiveDao,
    private val vaultDao: VaultDao,
    private val classifier: ArchiveClassifier,
    private val sharedPreferences: SharedPreferences,
) {

    suspend fun refreshCandidates(scanLimit: Int = DEFAULT_SCAN_LIMIT): ArchiveSummary = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext disabledSummary()

        val nowMs = System.currentTimeMillis()
        val screenshotEntities = photoDao.getArchiveScreenshotCandidates(scanLimit)
        val photoIds = screenshotEntities.map { entity -> entity.id }
        val existingById = if (photoIds.isEmpty()) {
            emptyMap()
        } else {
            archiveDao.getByPhotoIds(photoIds).associateBy { decision -> decision.photoId }
        }
        val protectedIds = getProtectedIds(photoIds)
        val retentionDays = retentionDays()

        val nextDecisions = screenshotEntities.mapNotNull { entity ->
            if (entity.id in protectedIds) return@mapNotNull null
            val existing = existingById[entity.id]
            if (existing?.state in SUPPRESSED_STATES) return@mapNotNull null

            val photo = entity.toPhotoRecord()
            val classification = classifier.classify(photo, nowMs) ?: return@mapNotNull null
            ArchiveDecisionEntity(
                photoId = photo.id,
                uriString = photo.uriString,
                state = ArchiveDecisionStates.CANDIDATE,
                confidence = classification.confidence,
                reasons = encodeReasons(classification.reasons),
                firstDetectedAtMs = existing?.firstDetectedAtMs ?: nowMs,
                lastDetectedAtMs = nowMs,
                trashedAtMs = existing?.trashedAtMs,
                retentionDays = existing?.retentionDays ?: retentionDays,
            )
        }

        if (nextDecisions.isNotEmpty()) {
            archiveDao.upsertDecisions(nextDecisions)
        }
        archiveDao.markDueDeleteItems(nowMs)
        loadSummaryInternal(nowMs = nowMs)
    }

    suspend fun loadSummary(): ArchiveSummary = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext disabledSummary()
        archiveDao.markDueDeleteItems(System.currentTimeMillis())
        loadSummaryInternal()
    }

    suspend fun setEnabled(enabled: Boolean): ArchiveSummary = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (enabled) {
            refreshCandidates()
        } else {
            disabledSummary()
        }
    }

    fun isEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
    }

    suspend fun setRetentionDays(days: Int): Int = withContext(Dispatchers.IO) {
        val normalized = normalizeRetentionDays(days)
        sharedPreferences.edit()
            .putInt(KEY_RETENTION_DAYS, normalized)
            .apply()
        normalized
    }

    fun retentionDays(): Int {
        return normalizeRetentionDays(
            sharedPreferences.getInt(KEY_RETENTION_DAYS, ArchiveClassifier.DEFAULT_RETENTION_DAYS),
        )
    }

    suspend fun markKept(photoIds: Set<Long>) = withContext(Dispatchers.IO) {
        if (photoIds.isEmpty()) return@withContext
        archiveDao.markKept(photoIds.toList(), System.currentTimeMillis())
    }

    suspend fun markTrashed(photoIds: Set<Long>, retentionDays: Int) = withContext(Dispatchers.IO) {
        if (photoIds.isEmpty()) return@withContext
        archiveDao.markTrashed(
            photoIds = photoIds.toList(),
            trashedAtMs = System.currentTimeMillis(),
            retentionDays = normalizeRetentionDays(retentionDays),
        )
    }

    suspend fun markDueDeleted(photoIds: Set<Long>) = withContext(Dispatchers.IO) {
        if (photoIds.isEmpty()) return@withContext
        archiveDao.markStale(photoIds.toList(), System.currentTimeMillis())
    }

    suspend fun dueDeleteItems(limit: Int = MAX_DUE_DELETE_ITEMS): List<ArchiveDueDeleteItem> =
        withContext(Dispatchers.IO) {
            if (!isEnabled()) return@withContext emptyList()
            archiveDao.markDueDeleteItems(System.currentTimeMillis())
            archiveDao.getDueDeleteItems(System.currentTimeMillis(), limit)
                .map { decision ->
                    ArchiveDueDeleteItem(
                        photoId = decision.photoId,
                        uriString = decision.uriString,
                    )
                }
        }

    suspend fun refreshDueDeleteState() = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext
        archiveDao.markDueDeleteItems(System.currentTimeMillis())
    }

    private suspend fun loadSummaryInternal(nowMs: Long = System.currentTimeMillis()): ArchiveSummary {
        val candidates = loadCandidatesInternal()
        val dueCount = archiveDao.getDueDeleteCount(nowMs)
        return ArchiveSummary(
            candidates = candidates,
            dueDeleteCount = dueCount,
            retentionDays = retentionDays(),
            enabled = isEnabled(),
        )
    }

    private suspend fun loadCandidatesInternal(): List<ArchiveCandidate> {
        val decisions = archiveDao.getCandidates(MAX_CANDIDATES)
        if (decisions.isEmpty()) return emptyList()

        val photoIds = decisions.map { decision -> decision.photoId }
        val photosById = photoDao.getByIds(photoIds)
            .associate { entity -> entity.id to entity.toPhotoRecord() }
        val missingIds = photoIds.filterNot { id -> id in photosById }
        if (missingIds.isNotEmpty()) {
            archiveDao.markStale(missingIds, System.currentTimeMillis())
        }

        val protectedIds = getProtectedIds(photoIds)
        return decisions.mapNotNull { decision ->
            val photo = photosById[decision.photoId] ?: return@mapNotNull null
            if (photo.isFavorite || photo.id in protectedIds) return@mapNotNull null
            ArchiveCandidate(
                photo = photo,
                confidence = decision.confidence,
                reasons = decodeReasons(decision.reasons),
            )
        }
    }

    private suspend fun getProtectedIds(photoIds: List<Long>): Set<Long> {
        if (photoIds.isEmpty()) return emptySet()
        return vaultDao.getProtectedPhotoIds(photoIds).toSet()
    }

    private fun disabledSummary(): ArchiveSummary {
        return ArchiveSummary(
            candidates = emptyList(),
            dueDeleteCount = 0,
            retentionDays = retentionDays(),
            enabled = false,
        )
    }

    private fun normalizeRetentionDays(days: Int): Int {
        return when (days) {
            7, 14, 30 -> days
            else -> ArchiveClassifier.DEFAULT_RETENTION_DAYS
        }
    }

    private fun encodeReasons(reasons: List<String>): String {
        return reasons.joinToString(REASON_SEPARATOR)
    }

    private fun decodeReasons(encoded: String): List<String> {
        return encoded.split(REASON_SEPARATOR)
            .map { reason -> reason.trim() }
            .filter { reason -> reason.isNotBlank() }
    }

    companion object {
        private const val KEY_ENABLED = "archives_enabled_v1"
        private const val KEY_RETENTION_DAYS = "archives_retention_days_v1"
        private const val DEFAULT_ENABLED = false
        private const val REASON_SEPARATOR = "|"
        private const val DEFAULT_SCAN_LIMIT = 600
        private const val MAX_CANDIDATES = 120
        private const val MAX_DUE_DELETE_ITEMS = 200

        private val SUPPRESSED_STATES = setOf(
            ArchiveDecisionStates.KEPT,
            ArchiveDecisionStates.TRASHED,
            ArchiveDecisionStates.DELETE_DUE,
            ArchiveDecisionStates.STALE,
        )
    }
}
