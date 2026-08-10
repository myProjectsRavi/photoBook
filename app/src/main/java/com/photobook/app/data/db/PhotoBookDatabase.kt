package com.photobook.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PhotoEntity::class,
        PhotoFtsEntity::class,
        VaultEntity::class,
        ArchiveDecisionEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
abstract class PhotoBookDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun vaultDao(): VaultDao
    abstract fun archiveDao(): ArchiveDao
}
