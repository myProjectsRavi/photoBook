package com.photobook.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PhotoEntity::class,
        PhotoFtsEntity::class,
        VaultEntity::class,
        VaultOperationEntity::class,
        ArchiveDecisionEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class PhotoBookDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun vaultDao(): VaultDao
    abstract fun vaultOperationDao(): VaultOperationDao
    abstract fun archiveDao(): ArchiveDao
}
