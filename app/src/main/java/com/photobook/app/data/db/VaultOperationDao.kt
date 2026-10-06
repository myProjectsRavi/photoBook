package com.photobook.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VaultOperationDao {
    @Query("SELECT * FROM vault_operations ORDER BY createdAtMs ASC")
    suspend fun getAll(): List<VaultOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operation: VaultOperationEntity)

    @Query("DELETE FROM vault_operations WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM vault_operations WHERE vaultItemId = :vaultItemId")
    suspend fun deleteByVaultItemId(vaultItemId: String): Int
}
