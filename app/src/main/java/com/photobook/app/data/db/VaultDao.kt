package com.photobook.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY addedAtMs DESC")
    suspend fun getAllVaultItems(): List<VaultEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getVaultItemById(id: String): VaultEntity?

    @Query("SELECT sourcePhotoId FROM vault_items WHERE sourcePhotoId IN (:photoIds)")
    suspend fun getProtectedPhotoIds(photoIds: List<Long>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVaultItem(item: VaultEntity): Long

    @Query("UPDATE vault_items SET encryptedFileName = :encryptedFileName WHERE id = :id")
    suspend fun updateEncryptedFileName(id: String, encryptedFileName: String): Int

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteVaultItemById(id: String): Int
}
