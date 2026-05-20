package com.photobook.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PhotoEntity::class,
        PhotoFtsEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class PhotoBookDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
}
