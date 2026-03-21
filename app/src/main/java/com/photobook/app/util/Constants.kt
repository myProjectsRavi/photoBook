package com.photobook.app.util

object Constants {
    const val PREFS_NAME = "photobook_prefs"
    const val SEARCH_HISTORY_KEY = "search_history"
    const val ML_PROGRESS_KEY = "ml_progress_offset"

    const val BATCH_SIZE = 50
    const val BATCH_DELAY_MS = 500L
    const val SEARCH_DEBOUNCE_MS = 300L

    const val LARGE_FILE_SIZE_BYTES = 5L * 1024L * 1024L
    const val SMALL_FILE_SIZE_BYTES = 500L * 1024L

    const val ML_WORKER_NAME = "photobook_ml_worker"
}
