package com.photobook.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY dateAdded DESC")
    suspend fun getAll(): List<PhotoEntity>

    @Query("SELECT id FROM photos")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PhotoEntity>

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
}
