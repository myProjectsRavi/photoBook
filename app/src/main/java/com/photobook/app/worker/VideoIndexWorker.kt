package com.photobook.app.worker

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.photobook.app.data.db.VideoFrameEntity
import com.photobook.app.feature.videoindex.VideoIndexRepository
import com.photobook.app.ml.MLTagger
import com.photobook.app.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class VideoIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sharedPreferences: android.content.SharedPreferences,
    private val mlTagger: MLTagger,
    private val videoIndexRepository: VideoIndexRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!sharedPreferences.getBoolean(Constants.VIDEO_INDEXING_ENABLED_KEY, false)) {
            return Result.success()
        }

        return withContext(Dispatchers.IO) {
            val scannedUris = mutableSetOf<String>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.MIME_TYPE,
            )
            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

            applicationContext.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

                var processedVideos = 0
                while (cursor.moveToNext() && processedVideos < MAX_VIDEOS_PER_RUN) {
                    if (isStopped) return@withContext Result.retry()

                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val uriString = uri.toString()
                    scannedUris += uriString

                    val displayName = cursor.getString(nameColumn).orEmpty().ifBlank { "Video_$id" }
                    val durationMs = cursor.getLong(durationColumn).coerceAtLeast(0L)
                    val dateModifiedMs = cursor.getLong(modifiedColumn).coerceAtLeast(0L) * 1000L
                    val mimeType = cursor.getString(mimeColumn).orEmpty().ifBlank { "video/*" }

                    val frames = extractVideoFrames(
                        videoUriString = uriString,
                        displayName = displayName,
                        durationMs = durationMs,
                        dateModifiedMs = dateModifiedMs,
                        mimeType = mimeType,
                    )

                    videoIndexRepository.replaceFramesForVideo(uriString, frames)
                    processedVideos += 1
                }
            }

            videoIndexRepository.pruneMissingVideos(scannedUris)
            Result.success()
        }
    }

    private suspend fun extractVideoFrames(
        videoUriString: String,
        displayName: String,
        durationMs: Long,
        dateModifiedMs: Long,
        mimeType: String,
    ): List<VideoFrameEntity> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<VideoFrameEntity>()

        return try {
            retriever.setDataSource(applicationContext, android.net.Uri.parse(videoUriString))
            val frameCount = frameCount(durationMs)

            for (index in 0 until frameCount) {
                if (isStopped) break

                val timestampMs = frameTimestampMs(index, durationMs)
                val bitmap = runCatching {
                    retriever.getFrameAtTime(timestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }.getOrNull() ?: continue

                val analysis = try {
                    mlTagger.analyzeBitmap(bitmap, isFrontCamera = false)
                } finally {
                    bitmap.recycle()
                }

                val searchableText = buildString {
                    append(displayName.lowercase())
                    append(' ')
                    if (analysis.ocrText.isNotBlank()) {
                        append(analysis.ocrText)
                        append(' ')
                    }
                    analysis.tags.forEach { tag ->
                        append(tag.label.lowercase())
                        append(' ')
                    }
                }.replace(Regex("\\s+"), " ").trim()

                if (searchableText.length < 2) continue

                frames += VideoFrameEntity(
                    videoUriString = videoUriString,
                    displayName = displayName,
                    timestampMs = timestampMs,
                    durationMs = durationMs,
                    videoDateModifiedMs = dateModifiedMs,
                    mimeType = mimeType,
                    searchableText = searchableText,
                )
            }
            frames
        } catch (_: Throwable) {
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun frameCount(durationMs: Long): Int {
        if (durationMs <= 0L) return 1
        val computed = (durationMs / FRAME_INTERVAL_MS).toInt() + 1
        return computed.coerceAtMost(MAX_FRAMES_PER_VIDEO).coerceAtLeast(1)
    }

    private fun frameTimestampMs(index: Int, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val candidate = index.toLong() * FRAME_INTERVAL_MS
        return candidate.coerceAtMost((durationMs - SAFE_TAIL_GUARD_MS).coerceAtLeast(0L))
    }

    companion object {
        fun enqueueDaily(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            val request = PeriodicWorkRequestBuilder<VideoIndexWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.VIDEO_INDEX_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(Constants.VIDEO_INDEX_WORK_NAME)
        }
    }
}

private const val FRAME_INTERVAL_MS = 3_000L
private const val MAX_FRAMES_PER_VIDEO = 16
private const val MAX_VIDEOS_PER_RUN = 60
private const val SAFE_TAIL_GUARD_MS = 350L
