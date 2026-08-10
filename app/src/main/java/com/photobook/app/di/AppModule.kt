package com.photobook.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.photobook.app.data.db.ArchiveDao
import com.photobook.app.data.db.PhotoBookDatabase
import com.photobook.app.data.db.PhotoDao
import com.photobook.app.data.db.VaultDao
import com.photobook.app.util.Constants
import com.photobook.app.util.PerformanceProfiler
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
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(WAL_OPEN_CALLBACK)
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
            )
            .build()
    }

    @Provides
    @Singleton
    fun providePhotoDao(database: PhotoBookDatabase): PhotoDao {
        return database.photoDao()
    }

    @Provides
    @Singleton
    fun provideVaultDao(database: PhotoBookDatabase): VaultDao {
        return database.vaultDao()
    }

    @Provides
    @Singleton
    fun provideArchiveDao(database: PhotoBookDatabase): ArchiveDao {
        return database.archiveDao()
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
        val cachePercent = PerformanceProfiler.from(context).imageCacheMemoryPercent
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(cachePercent)
                    .build()
            }
            .allowHardware(true)
            .crossfade(false)
            .build()
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_dateAdded ON photos(dateAdded)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_photos_isFavorite_dateAdded " +
                    "ON photos(isFavorite, dateAdded)",
            )
        }
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

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE photos ADD COLUMN perceptualHash INTEGER")
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS video_frame_fts
                USING fts4(
                    searchableText,
                    tokenize=unicode61,
                    prefix='2,3,4'
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO video_frame_fts(rowid, searchableText)
                SELECT id, searchableText FROM video_frames
                """.trimIndent(),
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE photos ADD COLUMN blurScore REAL")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Video search feature removed — drop legacy video tables.
            db.execSQL("DROP TABLE IF EXISTS video_frame_fts")
            db.execSQL("DROP TABLE IF EXISTS video_frames")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_photos_fileSize_width_height " +
                    "ON photos(fileSize, width, height)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS vault_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    sourcePhotoId INTEGER NOT NULL,
                    originalFileName TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    encryptedFileName TEXT NOT NULL,
                    addedAtMs INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_vault_items_sourcePhotoId " +
                    "ON vault_items(sourcePhotoId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_vault_items_addedAtMs " +
                    "ON vault_items(addedAtMs)",
            )
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS archive_decisions (
                    photoId INTEGER NOT NULL PRIMARY KEY,
                    uriString TEXT NOT NULL,
                    state TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    reasons TEXT NOT NULL,
                    firstDetectedAtMs INTEGER NOT NULL,
                    lastDetectedAtMs INTEGER NOT NULL,
                    trashedAtMs INTEGER,
                    retentionDays INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_archive_decisions_state " +
                    "ON archive_decisions(state)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_archive_decisions_lastDetectedAtMs " +
                    "ON archive_decisions(lastDetectedAtMs)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_archive_decisions_trashedAtMs " +
                    "ON archive_decisions(trashedAtMs)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_archive_decisions_retentionDays " +
                    "ON archive_decisions(retentionDays)",
            )
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE photos ADD COLUMN mlStatus TEXT NOT NULL DEFAULT 'PENDING'")
            db.execSQL("ALTER TABLE photos ADD COLUMN ocrStatus TEXT NOT NULL DEFAULT 'PENDING'")
            db.execSQL("UPDATE photos SET mlStatus = 'PROCESSED' WHERE isMlProcessed = 1")
            db.execSQL("UPDATE photos SET ocrStatus = 'PROCESSED' WHERE isOcrProcessed = 1")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE photos ADD COLUMN isArchiveScreenshotCandidate INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE photos ADD COLUMN isArchiveFoodCandidate INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_photos_isArchiveScreenshotCandidate_dateAdded " +
                    "ON photos(isArchiveScreenshotCandidate, dateAdded)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_photos_isArchiveFoodCandidate_dateAdded " +
                    "ON photos(isArchiveFoodCandidate, dateAdded)",
            )
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // The old Food flag was based on a color-only heuristic. Re-evaluate every
            // existing photo once so semantic Food can discover photos the old heuristic
            // missed as well as remove its false positives. Persisted tags and OCR state
            // remain intact; only the ML completion bit is reopened.
            db.execSQL(
                """
                UPDATE photos
                SET isMlProcessed = 0,
                    mlStatus = 'PENDING',
                    isArchiveFoodCandidate = 0
                """.trimIndent(),
            )
            db.execSQL(
                """
                UPDATE archive_decisions
                SET state = 'stale'
                WHERE state = 'candidate'
                    AND reasons = 'Food photo'
                """.trimIndent(),
            )
        }
    }

    private val WAL_OPEN_CALLBACK = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA synchronous = NORMAL")
        }
    }
}
