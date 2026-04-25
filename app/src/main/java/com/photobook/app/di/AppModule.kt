package com.photobook.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.photobook.app.data.db.PhotoBookDatabase
import com.photobook.app.data.db.PhotoDao
import com.photobook.app.util.Constants
import coil.ImageLoader
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePhotoBookDatabase(
        @ApplicationContext context: Context,
    ): PhotoBookDatabase {
        return Room.databaseBuilder(
            context,
            PhotoBookDatabase::class.java,
            "photobook.db",
        ).addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun providePhotoDao(database: PhotoBookDatabase): PhotoDao {
        return database.photoDao()
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15)
                    .build()
            }
            .allowHardware(true)
            .crossfade(false)
            .build()
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_folderName ON photos(folderName)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_photos_city_state_country ON photos(city, state, country)",
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS video_frames (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoUriString TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    timestampMs INTEGER NOT NULL,
                    durationMs INTEGER NOT NULL,
                    videoDateModifiedMs INTEGER NOT NULL,
                    mimeType TEXT NOT NULL,
                    searchableText TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_video_frames_videoUriString_timestampMs " +
                    "ON video_frames(videoUriString, timestampMs)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_video_frames_videoDateModifiedMs " +
                    "ON video_frames(videoDateModifiedMs)",
            )
        }
    }
}
