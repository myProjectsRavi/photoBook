package com.photobook.app.feature.phototextsearch

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.photobook.app.ml.BundledOnDeviceIntelligence
import com.photobook.app.ml.LocalOcrEngine
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

            val before = readSourceStamp(uri)
                ?: return@withContext PhotoTextLayoutLoadResult.Unavailable

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
            } ?: return@withContext PhotoTextLayoutLoadResult.Unavailable

            try {
                val result = localOcrEngine.recognizeLayout(owned.bitmap)
                currentCoroutineContext().ensureActive()

                val after = readSourceStamp(uri)
                    ?: return@withContext PhotoTextLayoutLoadResult.Unavailable
                if (before != after) {
                    return@withContext PhotoTextLayoutLoadResult.Failed
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
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                val modified = cursor.getLong(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED),
                )
                val generation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.GENERATION_MODIFIED),
                    )
                } else {
                    null
                }
                SourceStamp(
                    mediaId = id,
                    generationModified = generation,
                    dateModifiedSeconds = modified,
                    byteSize = size,
                )
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: Exception) {
            null
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

        val upright = orientBitmap(decoded, orientation)
        if (upright == null) {
            recycleSafely(decoded)
            return null
        }
        if (upright !== decoded) {
            recycleSafely(decoded)
        }
        return OwnedUprightBitmap(upright)
    }

    private fun orientBitmap(source: Bitmap, orientation: Int): Bitmap? {
        if (
            orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return source
        }

        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    preScale(-1f, 1f)
                    postRotate(270f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    preScale(-1f, 1f)
                    postRotate(90f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                else -> return null
            }
        }

        return runCatching {
            Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                matrix,
                true,
            )
        }.getOrNull()
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
        val mediaId: Long,
        val generationModified: Long?,
        val dateModifiedSeconds: Long,
        val byteSize: Long,
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

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PhotoTextSearchEntryPoint {
    fun mediaStorePhotoTextLayoutSource(): MediaStorePhotoTextLayoutSource
}

fun mediaStorePhotoTextLayoutSource(context: Context): MediaStorePhotoTextLayoutSource {
    return EntryPointAccessors.fromApplication(
        context.applicationContext,
        PhotoTextSearchEntryPoint::class.java,
    ).mediaStorePhotoTextLayoutSource()
}
