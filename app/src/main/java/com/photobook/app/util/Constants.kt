package com.photobook.app.util

object Constants {
    const val PREFS_NAME = "photobook_prefs"
    const val SEARCH_HISTORY_KEY = "search_history"
    const val ML_PROGRESS_KEY = "ml_progress_offset"
    const val VIDEO_INDEXING_ENABLED_KEY = "video_indexing_enabled"
    const val MEDIA_STORE_VERSION_KEY = "media_store_version"
    const val MEDIA_STORE_GENERATION_KEY = "media_store_generation"

    const val BATCH_SIZE = 50
    const val BATCH_DELAY_MS = 500L
    const val SEARCH_DEBOUNCE_MS = 300L
    const val MEDIA_OBSERVER_DEBOUNCE_MS = 1200L
    const val MEDIA_SYNC_CHUNK_SIZE = 200
    const val OCR_MAX_TEXT_CHARS = 4000

    const val LARGE_FILE_SIZE_BYTES = 5L * 1024L * 1024L
    const val SMALL_FILE_SIZE_BYTES = 500L * 1024L

    const val ML_WORKER_NAME = "photobook_ml_worker"
    const val TRASH_PURGE_WORK_NAME = "photobook_trash_purge_worker"
    const val VIDEO_INDEX_WORK_NAME = "photobook_video_index_worker"
}
