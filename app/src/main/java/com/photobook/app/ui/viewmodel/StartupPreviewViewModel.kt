package com.photobook.app.ui.viewmodel

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photobook.app.data.db.PhotoDao
import com.photobook.app.data.db.toPhotoRecord
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.data.source.MediaStoreScanner
import com.photobook.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Supplies a small, read-only cold-start preview from authoritative Room state while the normal
 * MainViewModel hydrates and reconciles the complete in-memory index.
 *
 * This ViewModel never publishes into PhotoIndex and never enables user actions. It fails closed
 * unless MediaStore version + generation exactly match the persisted reconciliation token both
 * before and after the bounded Room read.
 */
@HiltViewModel
class StartupPreviewViewModel @Inject constructor(
    private val mediaStoreScanner: MediaStoreScanner,
    private val photoDao: PhotoDao,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {

    private val _photos = MutableStateFlow<List<PhotoRecord>>(emptyList())
    val photos: StateFlow<List<PhotoRecord>> = _photos.asStateFlow()

    init {
        viewModelScope.launch {
            _photos.value = withContext(Dispatchers.IO) {
                loadTrustedPreview()
            }
        }
    }

    private suspend fun loadTrustedPreview(): List<PhotoRecord> {
        val persistedVersion = sharedPreferences.getString(Constants.MEDIA_STORE_VERSION_KEY, null)
        val persistedGeneration = sharedPreferences
            .getLong(Constants.MEDIA_STORE_GENERATION_KEY, -1L)
            .takeIf { value -> value >= 0L }

        val beforeVersion = mediaStoreScanner.currentMediaStoreVersion()
        val beforeGeneration = mediaStoreScanner.currentGenerationOrNull()
        if (
            !canUseStartupPreview(
                currentVersion = beforeVersion,
                currentGeneration = beforeGeneration,
                persistedVersion = persistedVersion,
                persistedGeneration = persistedGeneration,
            )
        ) {
            Log.i(PHASE6_TAG, "stage=startup_preview trusted=false reason=sync_token_mismatch")
            return emptyList()
        }

        val loadStartMs = SystemClock.elapsedRealtime()
        val recent = photoDao.getRecent(STARTUP_PREVIEW_COUNT).map { entity -> entity.toPhotoRecord() }

        // Close the race where MediaStore changes while the bounded Room query is in flight.
        val afterVersion = mediaStoreScanner.currentMediaStoreVersion()
        val afterGeneration = mediaStoreScanner.currentGenerationOrNull()
        val remainedStable = beforeVersion == afterVersion && beforeGeneration == afterGeneration
        val stillTrusted = remainedStable && canUseStartupPreview(
            currentVersion = afterVersion,
            currentGeneration = afterGeneration,
            persistedVersion = persistedVersion,
            persistedGeneration = persistedGeneration,
        )
        if (!stillTrusted) {
            Log.i(PHASE6_TAG, "stage=startup_preview trusted=false reason=media_changed_during_load")
            return emptyList()
        }

        Log.i(
            PHASE6_TAG,
            "stage=startup_preview trusted=true elapsedMs=${SystemClock.elapsedRealtime() - loadStartMs} " +
                "count=${recent.size}",
        )
        return recent
    }

    companion object {
        internal const val STARTUP_PREVIEW_COUNT = 60
        private const val PHASE6_TAG = "PhotoBookPhase6"
    }
}
