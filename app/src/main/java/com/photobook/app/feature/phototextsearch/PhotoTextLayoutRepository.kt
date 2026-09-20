package com.photobook.app.feature.phototextsearch

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.photobook.app.feature.vault.VaultCryptoSession
import com.photobook.app.feature.vault.VaultService
import com.photobook.app.ml.BundledOnDeviceIntelligence
import com.photobook.app.ml.LocalOcrEngine
import com.photobook.app.util.BitmapOrientation
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Singleton
class MediaStorePhotoTextLayoutSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localOcrEngine: LocalOcrEngine,
    private val onDeviceIntelligence: BundledOnDeviceIntelligence,
) : PhotoTextLayoutSource {

    override suspend fun load(source: PhotoTextSourceKey): PhotoTextLayoutLoadResult {
        return withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(source.uriString) }.getOrNull()
                ?: return@withContext PhotoTextLayoutLoadResult.Unavailable

            // Media metadata is advisory, not the authority for whether the photo is readable.
            // Some OEM/document providers can open the image bytes but omit one or more MediaStore
            // columns from an item query. Treating that as deletion caused valid photos to surface as
            // "no longer available" before OCR even started.
            val before = readSourceStamp(uri)
            if (before?.mediaId != null && before.mediaId != source.photoId) {
                return@withContext PhotoTextLayoutLoadResult.Unavailable
            }

            val ready = try {
                onDeviceIntelligence.ensureReady(needsMl = false, needsOcr = true).ocrReady
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            } catch (_: LinkageError) {
                false
            }
            if (!ready) {
                return@withContext PhotoTextLayoutLoadResult.Failed
            }

            val owned = try {
                decodeOwnedUprightBitmap(uri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            } catch (_: LinkageError) {
                null
            } ?: return@withContext if (isSourceReadable(uri)) {
                PhotoTextLayoutLoadResult.Failed
            } else {
                PhotoTextLayoutLoadResult.Unavailable
            }

            try {
                val result = localOcrEngine.recognizeLayout(owned.bitmap)
                currentCoroutineContext().ensureActive()

                val after = readSourceStamp(uri)
                if (after?.mediaId != null && after.mediaId != source.photoId) {
                    return@withContext PhotoTextLayoutLoadResult.Unavailable
                }
                if (before != null && after != null && before != after) {
                    return@withContext PhotoTextLayoutLoadResult.Failed
                }
                if (after == null && !isSourceReadable(uri)) {
                    return@withContext PhotoTextLayoutLoadResult.Unavailable
                }

                val layout = result.getOrNull()
                    ?: return@withContext PhotoTextLayoutLoadResult.Failed
                PhotoTextLayoutLoadResult.Success(layout)
            } finally {
                owned.close()
            }
        }
    }

    private fun readSourceStamp(uri: Uri): SourceStamp? {
        val resolver = context.contentResolver
        return try {
            val projection = buildList {
                add(MediaStore.Images.Media._ID)
                add(MediaStore.Images.Media.SIZE)
                add(MediaStore.Images.Media.DATE_MODIFIED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(MediaStore.Images.Media.GENERATION_MODIFIED)
                }
            }.toTypedArray()

            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val id = cursor.longOrNull(MediaStore.Images.Media._ID)
                val size = cursor.longOrNull(MediaStore.Images.Media.SIZE)
                val modified = cursor.longOrNull(MediaStore.Images.Media.DATE_MODIFIED)
                val generation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.longOrNull(MediaStore.Images.Media.GENERATION_MODIFIED)
                } else {
                    null
                }
                if (id == null && size == null && modified == null && generation == null) {
                    null
                } else {
                    SourceStamp(
                        mediaId = id,
                        generationModified = generation,
                        dateModifiedSeconds = modified,
                        byteSize = size,
                    )
                }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun android.database.Cursor.longOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun isSourceReadable(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeOwnedUprightBitmap(uri: Uri): OwnedUprightBitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = activityManager?.isLowRamDevice == true
        val maxEdge = if (lowRam) LOW_RAM_MAX_EDGE_PX else NORMAL_MAX_EDGE_PX
        val maxPixels = if (lowRam) LOW_RAM_MAX_PIXELS else NORMAL_MAX_PIXELS
        val sample = calculateSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxEdge = maxEdge,
            maxPixels = maxPixels,
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        if (
            decoded.isRecycled ||
            decoded.width <= 0 ||
            decoded.height <= 0 ||
            decoded.config == Bitmap.Config.HARDWARE
        ) {
            recycleSafely(decoded)
            return null
        }

        val orientation = resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED,
            )
        } ?: ExifInterface.ORIENTATION_UNDEFINED

        val upright = BitmapOrientation.upright(decoded, orientation)
        if (upright == null) {
            recycleSafely(decoded)
            return null
        }
        if (upright !== decoded) {
            recycleSafely(decoded)
        }
        return OwnedUprightBitmap(upright)
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        maxEdge: Int,
        maxPixels: Int,
    ): Int {
        var sample = 1
        while (true) {
            val sampledWidth = (width / sample).coerceAtLeast(1)
            val sampledHeight = (height / sample).coerceAtLeast(1)
            val withinEdge = sampledWidth <= maxEdge && sampledHeight <= maxEdge
            val withinPixels = sampledWidth.toLong() * sampledHeight.toLong() <= maxPixels.toLong()
            if (withinEdge && withinPixels) return sample
            if (sample >= MAX_SAMPLE_SIZE) return sample
            sample *= 2
        }
    }

    private fun recycleSafely(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            runCatching { bitmap.recycle() }
        }
    }

    private data class SourceStamp(
        val mediaId: Long?,
        val generationModified: Long?,
        val dateModifiedSeconds: Long?,
        val byteSize: Long?,
    )

    private class OwnedUprightBitmap(
        val bitmap: Bitmap,
    ) : Closeable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true) && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private companion object {
        private const val NORMAL_MAX_EDGE_PX = 1600
        private const val LOW_RAM_MAX_EDGE_PX = 1280
        private const val NORMAL_MAX_PIXELS = 2_000_000
        private const val LOW_RAM_MAX_PIXELS = 1_200_000
        private const val MAX_SAMPLE_SIZE = 128
    }
}

class VaultPhotoTextLayoutSource(
    private val vaultService: VaultService,
    private val sessionProvider: () -> VaultCryptoSession?,
    private val localOcrEngine: LocalOcrEngine,
    private val onDeviceIntelligence: BundledOnDeviceIntelligence,
) : PhotoTextLayoutSource {

    override suspend fun load(source: PhotoTextSourceKey): PhotoTextLayoutLoadResult {
        val session = sessionProvider() ?: return PhotoTextLayoutLoadResult.Unavailable
        val ready = try {
            onDeviceIntelligence.ensureReady(needsMl = false, needsOcr = true).ocrReady
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
        if (!ready) return PhotoTextLayoutLoadResult.Failed

        return try {
            val result = vaultService.withOwnedSearchBitmap(
                itemId = source.uriString,
                session = session,
            ) { bitmap ->
                localOcrEngine.recognizeLayout(bitmap)
            } ?: return PhotoTextLayoutLoadResult.Unavailable

            currentCoroutineContext().ensureActive()
            if (sessionProvider() !== session) {
                return PhotoTextLayoutLoadResult.Unavailable
            }

            result.getOrNull()?.let(PhotoTextLayoutLoadResult::Success)
                ?: PhotoTextLayoutLoadResult.Failed
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            PhotoTextLayoutLoadResult.Unavailable
        } catch (_: Exception) {
            PhotoTextLayoutLoadResult.Failed
        } catch (_: LinkageError) {
            PhotoTextLayoutLoadResult.Failed
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PhotoTextSearchEntryPoint {
    fun mediaStorePhotoTextLayoutSource(): MediaStorePhotoTextLayoutSource
    fun localOcrEngine(): LocalOcrEngine
    fun bundledOnDeviceIntelligence(): BundledOnDeviceIntelligence
}

fun mediaStorePhotoTextLayoutSource(context: Context): MediaStorePhotoTextLayoutSource {
    return EntryPointAccessors.fromApplication(
        context.applicationContext,
        PhotoTextSearchEntryPoint::class.java,
    ).mediaStorePhotoTextLayoutSource()
}


fun vaultPhotoTextLayoutSource(
    context: Context,
    vaultService: VaultService,
    sessionProvider: () -> VaultCryptoSession?,
): VaultPhotoTextLayoutSource {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        PhotoTextSearchEntryPoint::class.java,
    )
    return VaultPhotoTextLayoutSource(
        vaultService = vaultService,
        sessionProvider = sessionProvider,
        localOcrEngine = entryPoint.localOcrEngine(),
        onDeviceIntelligence = entryPoint.bundledOnDeviceIntelligence(),
    )
}
