package com.photobook.app.feature.archive

import android.content.SharedPreferences
import com.photobook.app.data.db.ArchiveDao
import com.photobook.app.data.db.ArchiveDecisionEntity
import com.photobook.app.data.db.ArchiveDecisionStates
import com.photobook.app.data.db.PhotoDao
import com.photobook.app.data.db.PhotoEntity
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
    val paymentsEnabled: Boolean,
    val foodEnabled: Boolean,
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

    suspend fun refreshCandidates(
        scanLimit: Int = DEFAULT_SCAN_LIMIT,
        accessiblePhotoIds: Set<Long>? = null,
    ): ArchiveSummary = withContext(Dispatchers.IO) {
        refreshCandidatesInternal(
            scanLimit = scanLimit,
            accessiblePhotoIds = accessiblePhotoIds,
        )
    }

    suspend fun refreshAllCandidates(
        accessiblePhotoIds: Set<Long>? = null,
        onBatchCommitted: suspend (ArchiveSummary) -> Unit = {},
    ): ArchiveSummary = withContext(Dispatchers.IO) {
        refreshCandidatesInternal(
            scanLimit = null,
            accessiblePhotoIds = accessiblePhotoIds,
            onBatchCommitted = onBatchCommitted,
        )
    }

    private suspend fun refreshCandidatesInternal(
        scanLimit: Int?,
        accessiblePhotoIds: Set<Long>?,
        onBatchCommitted: suspend (ArchiveSummary) -> Unit = {},
    ): ArchiveSummary {
        if (!isEnabled()) return disabledSummary()

        val boundedLimit = scanLimit ?: return refreshAllCandidatesBounded(
            accessiblePhotoIds = accessiblePhotoIds,
            onBatchCommitted = onBatchCommitted,
        )

        val nowMs = System.currentTimeMillis()
        val enabledCategories = enabledCategories()
        val candidateEntities = when {
            enabledCategories.isEmpty() -> emptyList()
            else -> {
                val paymentCandidates = if (ArchiveCategory.Payments in enabledCategories) {
                    photoDao.getArchiveScreenshotCandidates(boundedLimit)
                } else {
                    emptyList()
                }
                val foodCandidates = if (ArchiveCategory.Food in enabledCategories) {
                    photoDao.getArchiveFoodCandidates(boundedLimit)
                } else {
                    emptyList()
                }
                (paymentCandidates + foodCandidates)
                    .distinctBy { entity -> entity.id }
                    .filter { entity -> accessiblePhotoIds == null || entity.id in accessiblePhotoIds }
            }
        }
        val photoIds = candidateEntities.map { entity -> entity.id }
        val existingById = getArchiveDecisionsByPhotoIds(photoIds)
        val protectedIds = getProtectedIds(photoIds)
        val retentionDays = retentionDays()

        val nextDecisions = candidateEntities.mapNotNull { entity ->
            if (entity.id in protectedIds) return@mapNotNull null
            val existing = existingById[entity.id]
            if (existing?.state in SUPPRESSED_STATES) return@mapNotNull null

            val photo = entity.toPhotoRecord()
            val classification = classifier.classify(
                photo = photo,
                nowMs = nowMs,
                enabledCategories = enabledCategories,
            ) ?: return@mapNotNull null
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
            nextDecisions.chunked(ARCHIVE_DECISION_BATCH_SIZE).forEach { batch ->
                archiveDao.upsertDecisions(batch)
            }
        }
        archiveDao.markDueDeleteItems(nowMs)
        return loadSummaryInternal(
            nowMs = nowMs,
            accessiblePhotoIds = accessiblePhotoIds,
        )
    }

    private suspend fun refreshAllCandidatesBounded(
        accessiblePhotoIds: Set<Long>?,
        onBatchCommitted: suspend (ArchiveSummary) -> Unit,
    ): ArchiveSummary {
        val scanStartedAtMs = System.currentTimeMillis()
        val enabledCategories = enabledCategories()
        val retentionDays = retentionDays()
        var paymentCursor = ArchiveKeysetCursor()
        var foodCursor = ArchiveKeysetCursor()

        while (enabledCategories.isNotEmpty()) {
            val paymentPage = if (
                ArchiveCategory.Payments in enabledCategories && !paymentCursor.exhausted
            ) {
                photoDao.getArchiveScreenshotCandidatesAfter(
                    beforeDateAdded = paymentCursor.beforeDateAdded,
                    beforeId = paymentCursor.beforeId,
                    limit = ARCHIVE_PAGE_SIZE,
                )
            } else {
                emptyList()
            }
            if (ArchiveCategory.Payments in enabledCategories && !paymentCursor.exhausted) {
                paymentCursor = paymentCursor.advance(
                    page = paymentPage.map { entity ->
                        ArchivePageKey(dateAdded = entity.dateAdded, id = entity.id)
                    },
                    pageSize = ARCHIVE_PAGE_SIZE,
                )
            }

            val foodPage = if (
                ArchiveCategory.Food in enabledCategories && !foodCursor.exhausted
            ) {
                photoDao.getArchiveFoodCandidatesAfter(
                    beforeDateAdded = foodCursor.beforeDateAdded,
                    beforeId = foodCursor.beforeId,
                    limit = ARCHIVE_PAGE_SIZE,
                )
            } else {
                emptyList()
            }
            if (ArchiveCategory.Food in enabledCategories && !foodCursor.exhausted) {
                foodCursor = foodCursor.advance(
                    page = foodPage.map { entity ->
                        ArchivePageKey(dateAdded = entity.dateAdded, id = entity.id)
                    },
                    pageSize = ARCHIVE_PAGE_SIZE,
                )
            }

            val page = (paymentPage + foodPage).distinctBy { entity -> entity.id }
            if (page.isEmpty()) break

            val accessiblePage = page.filter { entity ->
                accessiblePhotoIds == null || entity.id in accessiblePhotoIds
            }
            val pageIds = accessiblePage.map { entity -> entity.id }
            val existingById = getArchiveDecisionsByPhotoIds(pageIds)
            val protectedIds = getProtectedIds(pageIds)
            val nextDecisions = accessiblePage.mapNotNull { entity ->
                if (entity.id in protectedIds) return@mapNotNull null
                val existing = existingById[entity.id]
                if (existing?.state in SUPPRESSED_STATES) return@mapNotNull null
                val classification = classifier.classify(
                    photo = entity.toPhotoRecord(),
                    nowMs = scanStartedAtMs,
                    enabledCategories = enabledCategories,
                ) ?: return@mapNotNull null
                ArchiveDecisionEntity(
                    photoId = entity.id,
                    uriString = entity.uriString,
                    state = ArchiveDecisionStates.CANDIDATE,
                    confidence = classification.confidence,
                    reasons = encodeReasons(classification.reasons),
                    firstDetectedAtMs = existing?.firstDetectedAtMs ?: scanStartedAtMs,
                    lastDetectedAtMs = scanStartedAtMs,
                    trashedAtMs = existing?.trashedAtMs,
                    retentionDays = existing?.retentionDays ?: retentionDays,
                )
            }
            nextDecisions.chunked(ARCHIVE_DECISION_BATCH_SIZE).forEach { batch ->
                archiveDao.upsertDecisions(batch)
            }
            onBatchCommitted(
                loadSummaryInternal(
                    nowMs = scanStartedAtMs,
                    accessiblePhotoIds = accessiblePhotoIds,
                ),
            )

            val paymentsComplete = ArchiveCategory.Payments !in enabledCategories || paymentCursor.exhausted
            val foodComplete = ArchiveCategory.Food !in enabledCategories || foodCursor.exhausted
            if (paymentsComplete && foodComplete) break
        }

        // Only a full MediaStore grant proves that an unobserved candidate is structurally gone
        // or no longer qualifies. Under Android's selected-photo access, an unobserved durable row
        // may simply be outside the current grant and must remain recoverable after regrant.
        if (accessiblePhotoIds == null) {
            archiveDao.markCandidatesStaleBefore(scanStartedAtMs, System.currentTimeMillis())
        }
        archiveDao.markDueDeleteItems(System.currentTimeMillis())
        return loadSummaryInternal(accessiblePhotoIds = accessiblePhotoIds)
    }

    suspend fun loadSummary(
        accessiblePhotoIds: Set<Long>? = null,
    ): ArchiveSummary = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext disabledSummary()
        archiveDao.markDueDeleteItems(System.currentTimeMillis())
        loadSummaryInternal(accessiblePhotoIds = accessiblePhotoIds)
    }

    suspend fun setEnabled(
        enabled: Boolean,
        accessiblePhotoIds: Set<Long>? = null,
    ): ArchiveSummary = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (enabled) {
            refreshCandidates(accessiblePhotoIds = accessiblePhotoIds)
        } else {
            disabledSummary()
        }
    }

    fun isEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
    }

    suspend fun setPaymentsEnabled(
        enabled: Boolean,
        accessiblePhotoIds: Set<Long>? = null,
    ): ArchiveSummary = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putBoolean(KEY_PAYMENTS_ENABLED, enabled)
            .apply()
        if (isEnabled()) refreshCandidates(accessiblePhotoIds = accessiblePhotoIds) else disabledSummary()
    }

    fun isPaymentsEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_PAYMENTS_ENABLED, DEFAULT_PAYMENTS_ENABLED)
    }

    suspend fun setFoodEnabled(
        enabled: Boolean,
        accessiblePhotoIds: Set<Long>? = null,
    ): ArchiveSummary = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putBoolean(KEY_FOOD_ENABLED, enabled)
            .apply()
        if (isEnabled()) refreshCandidates(accessiblePhotoIds = accessiblePhotoIds) else disabledSummary()
    }

    fun isFoodEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_FOOD_ENABLED, DEFAULT_FOOD_ENABLED)
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
        val nowMs = System.currentTimeMillis()
        photoIds.toList().chunked(ARCHIVE_DB_BATCH_SIZE).forEach { batch ->
            archiveDao.markKept(batch, nowMs)
        }
    }

    suspend fun markTrashed(photoIds: Set<Long>, retentionDays: Int) = withContext(Dispatchers.IO) {
        if (photoIds.isEmpty()) return@withContext
        val trashedAtMs = System.currentTimeMillis()
        val normalizedRetentionDays = normalizeRetentionDays(retentionDays)
        photoIds.toList().chunked(ARCHIVE_DB_BATCH_SIZE).forEach { batch ->
            archiveDao.markTrashed(
                photoIds = batch,
                trashedAtMs = trashedAtMs,
                retentionDays = normalizedRetentionDays,
            )
        }
    }

    suspend fun markDueDeleted(photoIds: Set<Long>) = withContext(Dispatchers.IO) {
        if (photoIds.isEmpty()) return@withContext
        val nowMs = System.currentTimeMillis()
        photoIds.toList().chunked(ARCHIVE_DB_BATCH_SIZE).forEach { batch ->
            archiveDao.markStale(batch, nowMs)
        }
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

    suspend fun archivedTrashPhotoIds(): Set<Long> = withContext(Dispatchers.IO) {
        archiveDao.getArchivedTrashPhotoIds().toSet()
    }

    suspend fun refreshDueDeleteState() = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext
        archiveDao.markDueDeleteItems(System.currentTimeMillis())
    }

    suspend fun markDueDeleteItems(): Int = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext 0
        val nowMs = System.currentTimeMillis()
        archiveDao.markDueDeleteItems(nowMs)
        archiveDao.getDueDeleteCount(nowMs)
    }

    private suspend fun loadSummaryInternal(
        nowMs: Long = System.currentTimeMillis(),
        accessiblePhotoIds: Set<Long>? = null,
    ): ArchiveSummary {
        val candidates = loadCandidatesInternal(accessiblePhotoIds)
        val dueCount = archiveDao.getDueDeleteCount(nowMs)
        return ArchiveSummary(
            candidates = candidates,
            dueDeleteCount = dueCount,
            retentionDays = retentionDays(),
            enabled = isEnabled(),
            paymentsEnabled = isPaymentsEnabled(),
            foodEnabled = isFoodEnabled(),
        )
    }

    private suspend fun loadCandidatesInternal(
        accessiblePhotoIds: Set<Long>? = null,
    ): List<ArchiveCandidate> {
        val decisions = getVisibleCandidateDecisions(accessiblePhotoIds)
        if (decisions.isEmpty()) return emptyList()

        val photoIds = decisions.map { decision -> decision.photoId }
        val photosById = photoDao.getByIds(photoIds)
            .associate { entity -> entity.id to entity.toPhotoRecord() }
        val missingIds = photoIds.filterNot { id -> id in photosById }
        if (missingIds.isNotEmpty()) {
            val nowMs = System.currentTimeMillis()
            missingIds.chunked(ARCHIVE_DB_BATCH_SIZE).forEach { batch ->
                archiveDao.markStale(batch, nowMs)
            }
        }

        val protectedIds = getProtectedIds(photoIds)
        val enabledCategories = enabledCategories()
        return decisions.mapNotNull { decision ->
            val photo = photosById[decision.photoId] ?: return@mapNotNull null
            if (photo.isFavorite || photo.id in protectedIds) return@mapNotNull null
            val classification = classifier.classify(
                photo = photo,
                nowMs = System.currentTimeMillis(),
                enabledCategories = enabledCategories,
            ) ?: return@mapNotNull null
            ArchiveCandidate(
                photo = photo,
                confidence = classification.confidence,
                reasons = classification.reasons,
            )
        }
    }

    private suspend fun getProtectedIds(photoIds: List<Long>): Set<Long> {
        if (photoIds.isEmpty()) return emptySet()
        return photoIds.chunked(ARCHIVE_DB_BATCH_SIZE)
            .flatMap { batch -> vaultDao.getProtectedPhotoIds(batch) }
            .toSet()
    }

    private suspend fun getArchiveDecisionsByPhotoIds(
        photoIds: List<Long>,
    ): Map<Long, ArchiveDecisionEntity> {
        if (photoIds.isEmpty()) return emptyMap()
        return photoIds.chunked(ARCHIVE_DB_BATCH_SIZE)
            .flatMap { batch -> archiveDao.getByPhotoIds(batch) }
            .associateBy { decision -> decision.photoId }
    }

    private suspend fun getVisibleCandidateDecisions(
        accessiblePhotoIds: Set<Long>?,
    ): List<ArchiveDecisionEntity> {
        if (accessiblePhotoIds == null) {
            return archiveDao.getCandidates(MAX_CANDIDATES)
        }
        if (accessiblePhotoIds.isEmpty()) return emptyList()

        return accessiblePhotoIds.toList()
            .chunked(ARCHIVE_DB_BATCH_SIZE)
            .flatMap { batch ->
                archiveDao.getCandidatesForPhotoIds(
                    photoIds = batch,
                    limit = MAX_CANDIDATES,
                )
            }
            .sortedWith(
                compareByDescending<ArchiveDecisionEntity> { decision ->
                    decision.lastDetectedAtMs
                }.thenByDescending { decision -> decision.photoId },
            )
            .take(MAX_CANDIDATES)
    }

    private fun disabledSummary(): ArchiveSummary {
        return ArchiveSummary(
            candidates = emptyList(),
            dueDeleteCount = 0,
            retentionDays = retentionDays(),
            enabled = false,
            paymentsEnabled = isPaymentsEnabled(),
            foodEnabled = isFoodEnabled(),
        )
    }

    private fun enabledCategories(): Set<ArchiveCategory> {
        return buildSet {
            if (isPaymentsEnabled()) add(ArchiveCategory.Payments)
            if (isFoodEnabled()) add(ArchiveCategory.Food)
        }
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

    companion object {
        private const val KEY_ENABLED = "archives_enabled_v1"
        private const val KEY_RETENTION_DAYS = "archives_retention_days_v1"
        private const val KEY_PAYMENTS_ENABLED = "archives_payments_enabled_v1"
        private const val KEY_FOOD_ENABLED = "archives_food_enabled_v1"
        private const val DEFAULT_ENABLED = false
        private const val DEFAULT_PAYMENTS_ENABLED = true
        private const val DEFAULT_FOOD_ENABLED = false
        private const val REASON_SEPARATOR = "|"
        private const val DEFAULT_SCAN_LIMIT = 1_200
        private const val MAX_DUE_DELETE_ITEMS = 500
        private const val MAX_CANDIDATES = 120
        private const val ARCHIVE_DECISION_BATCH_SIZE = 250
        private const val ARCHIVE_PAGE_SIZE = 250
        private const val ARCHIVE_DB_BATCH_SIZE = 200

        private val SUPPRESSED_STATES = setOf(
            ArchiveDecisionStates.KEPT,
            ArchiveDecisionStates.TRASHED,
            ArchiveDecisionStates.DELETE_DUE,
            ArchiveDecisionStates.STALE,
        )
    }
}
