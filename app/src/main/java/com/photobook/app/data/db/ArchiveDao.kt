package com.photobook.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArchiveDao {
    @Query("SELECT * FROM archive_decisions WHERE photoId IN (:photoIds)")
    suspend fun getByPhotoIds(photoIds: List<Long>): List<ArchiveDecisionEntity>

    @Query(
        """
        SELECT * FROM archive_decisions
        WHERE state = 'candidate'
        ORDER BY lastDetectedAtMs DESC
        LIMIT :limit
        """,
    )
    suspend fun getCandidates(limit: Int): List<ArchiveDecisionEntity>

    @Query("SELECT COUNT(*) FROM archive_decisions WHERE state = 'candidate'")
    suspend fun getCandidateCount(): Int

    @Query(
        """
        SELECT COUNT(*) FROM archive_decisions
        WHERE state IN ('trashed', 'delete_due')
            AND trashedAtMs IS NOT NULL
            AND (trashedAtMs + (retentionDays * 86400000)) <= :nowMs
        """,
    )
    suspend fun getDueDeleteCount(nowMs: Long): Int

    @Query(
        """
        SELECT * FROM archive_decisions
        WHERE state IN ('trashed', 'delete_due')
            AND trashedAtMs IS NOT NULL
            AND (trashedAtMs + (retentionDays * 86400000)) <= :nowMs
        ORDER BY trashedAtMs ASC
        LIMIT :limit
        """,
    )
    suspend fun getDueDeleteItems(nowMs: Long, limit: Int): List<ArchiveDecisionEntity>

    @Query(
        """
        SELECT photoId FROM archive_decisions
        WHERE state IN ('trashed', 'delete_due')
        """,
    )
    suspend fun getArchivedTrashPhotoIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDecisions(decisions: List<ArchiveDecisionEntity>)

    @Query(
        """
        UPDATE archive_decisions
        SET state = 'kept',
            lastDetectedAtMs = :nowMs
        WHERE photoId IN (:photoIds)
        """,
    )
    suspend fun markKept(photoIds: List<Long>, nowMs: Long)

    @Query(
        """
        UPDATE archive_decisions
        SET state = 'trashed',
            trashedAtMs = :trashedAtMs,
            retentionDays = :retentionDays,
            lastDetectedAtMs = :trashedAtMs
        WHERE photoId IN (:photoIds)
        """,
    )
    suspend fun markTrashed(photoIds: List<Long>, trashedAtMs: Long, retentionDays: Int)

    @Query(
        """
        UPDATE archive_decisions
        SET state = 'delete_due',
            lastDetectedAtMs = :nowMs
        WHERE state = 'trashed'
            AND trashedAtMs IS NOT NULL
            AND (trashedAtMs + (retentionDays * 86400000)) <= :nowMs
        """,
    )
    suspend fun markDueDeleteItems(nowMs: Long)

    @Query(
        """
        UPDATE archive_decisions
        SET state = 'stale',
            lastDetectedAtMs = :nowMs
        WHERE photoId IN (:photoIds)
        """,
    )
    suspend fun markStale(photoIds: List<Long>, nowMs: Long)

    @Query(
        """
        UPDATE archive_decisions
        SET state = 'stale',
            lastDetectedAtMs = :nowMs
        WHERE state = 'candidate'
            AND lastDetectedAtMs < :scanStartedAtMs
        """,
    )
    suspend fun markCandidatesStaleBefore(scanStartedAtMs: Long, nowMs: Long)
}
