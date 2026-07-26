package com.photobook.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY dateAdded DESC")
    suspend fun getAll(): List<PhotoEntity>

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun getPhotoCount(): Int

    @Query("SELECT id FROM photos")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PhotoEntity>

    @Query(
        """
        SELECT * FROM photos
        WHERE isFavorite = 0
            AND mimeType LIKE 'image/%'
            AND isArchiveScreenshotCandidate = 1
        ORDER BY dateAdded DESC
        LIMIT :limit
        """,
    )
    suspend fun getArchiveScreenshotCandidates(limit: Int): List<PhotoEntity>

    @Query(
        """
        SELECT * FROM photos
        WHERE isFavorite = 0
            AND mimeType LIKE 'image/%'
            AND isArchiveScreenshotCandidate = 1
        ORDER BY dateAdded DESC
        """,
    )
    suspend fun getAllArchiveScreenshotCandidates(): List<PhotoEntity>

    @Query(
        """
        SELECT * FROM photos
        WHERE isFavorite = 0
            AND mimeType LIKE 'image/%'
            AND isArchiveScreenshotCandidate = 1
            AND (dateAdded < :beforeDateAdded OR (dateAdded = :beforeDateAdded AND id < :beforeId))
        ORDER BY dateAdded DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getArchiveScreenshotCandidatesAfter(
        beforeDateAdded: Long,
        beforeId: Long,
        limit: Int,
    ): List<PhotoEntity>

    @Query(
        """
        SELECT * FROM photos
        WHERE isFavorite = 0
            AND mimeType LIKE 'image/%'
            AND isMlProcessed = 1
            AND isArchiveFoodCandidate = 1
        ORDER BY dateAdded DESC
        LIMIT :limit
        """,
    )
    suspend fun getArchiveFoodCandidates(limit: Int): List<PhotoEntity>

    @Query(
        """
        SELECT * FROM photos
        WHERE isFavorite = 0
            AND mimeType LIKE 'image/%'
            AND isMlProcessed = 1
            AND isArchiveFoodCandidate = 1
        ORDER BY dateAdded DESC
        """,
    )
    suspend fun getAllArchiveFoodCandidates(): List<PhotoEntity>

    @Query(
        """
        SELECT * FROM photos
        WHERE isFavorite = 0
            AND mimeType LIKE 'image/%'
            AND isMlProcessed = 1
            AND isArchiveFoodCandidate = 1
            AND (dateAdded < :beforeDateAdded OR (dateAdded = :beforeDateAdded AND id < :beforeId))
        ORDER BY dateAdded DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getArchiveFoodCandidatesAfter(
        beforeDateAdded: Long,
        beforeId: Long,
        limit: Int,
    ): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE photos SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFtsRows(rows: List<PhotoFtsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFtsRow(row: PhotoFtsEntity)

    @Query("DELETE FROM photo_fts WHERE rowid IN (:ids)")
    suspend fun deleteFtsByRowIds(ids: List<Long>)

    @Query("SELECT rowid FROM photo_fts WHERE photo_fts MATCH :matchQuery LIMIT :limit")
    suspend fun searchIdsByText(matchQuery: String, limit: Int): List<Long>

    @Query(
        """
        SELECT p.id FROM photos AS p
        INNER JOIN (
            SELECT fileSize, width, height FROM photos
            WHERE fileSize > 0 AND width > 0 AND height > 0
            GROUP BY fileSize, width, height
            HAVING COUNT(*) > 1
        ) AS duplicate_key
        ON p.fileSize = duplicate_key.fileSize
            AND p.width = duplicate_key.width
            AND p.height = duplicate_key.height
        ORDER BY p.fileSize DESC
        """,
    )
    suspend fun getExactDuplicateCandidateIds(): List<Long>
}
