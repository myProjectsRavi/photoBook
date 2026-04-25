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
        ).addMigrations(MIGRATION_2_3)
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
}
