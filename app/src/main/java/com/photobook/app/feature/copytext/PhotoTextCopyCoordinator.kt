package com.photobook.app.feature.copytext

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface PreviewSeed {
    data class Cached(val text: String) : PreviewSeed
    data class Fallback(val text: String) : PreviewSeed
    data object None : PreviewSeed
}

class PhotoTextCopyCoordinator(
    private val extractor: PhotoTextExtractor,
    private val formatter: PhotoTextFormatter = PhotoTextFormatter(),
    private val maxCacheEntries: Int = DEFAULT_CACHE_ENTRIES,
) {

    private val cacheMutex = Mutex()
    private val cache = object : LinkedHashMap<Long, String>(maxCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean {
            return size > maxCacheEntries
        }
    }

    private var activeJob: Job? = null
    private var activePhotoId: Long? = null

    suspend fun previewSeed(photoId: Long, fallbackRawText: String): PreviewSeed {
        val cached = cacheMutex.withLock { cache[photoId] }
        if (!cached.isNullOrBlank()) {
            return PreviewSeed.Cached(cached)
        }

        val fallback = formatter.format(fallbackRawText)
        return if (fallback.isNotBlank()) {
            PreviewSeed.Fallback(fallback)
        } else {
            PreviewSeed.None
        }
    }

    fun extractForPhoto(
        scope: CoroutineScope,
        photoId: Long,
        photoUri: String,
        onResult: (ExtractedTextResult) -> Unit,
    ) {
        cancelActiveRequest()
        activePhotoId = photoId
        activeJob = scope.launch {
            val result = extractor.extract(photoUri)
            if (!isActive || activePhotoId != photoId) {
                return@launch
            }
            if (result is ExtractedTextResult.Success) {
                cacheMutex.withLock {
                    cache[photoId] = result.text
                }
            }
            onResult(result)
        }
    }

    fun extractRegionForPhoto(
        scope: CoroutineScope,
        photoId: Long,
        photoUri: String,
        region: NormalizedTextRegion,
        onResult: (ExtractedTextResult) -> Unit,
    ) {
        cancelActiveRequest()
        activePhotoId = photoId
        activeJob = scope.launch {
            val result = extractor.extractRegion(photoUri, region)
            if (!isActive || activePhotoId != photoId) {
                return@launch
            }
            onResult(result)
        }
    }

    fun cancelActiveRequest() {
        activeJob?.cancel()
        activeJob = null
        activePhotoId = null
    }

    companion object {
        private const val DEFAULT_CACHE_ENTRIES = 32
    }
}
