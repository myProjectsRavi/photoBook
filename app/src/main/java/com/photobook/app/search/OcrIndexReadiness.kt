package com.photobook.app.search

import com.photobook.app.data.model.PhotoRecord

/**
 * Search readiness for text already visible to PhotoBook's in-memory index.
 *
 * A library is still improving while at least one photo is in a processable OCR state. Terminal
 * failures are not treated as "still indexing" because waiting cannot make them complete; release
 * verification must instead keep terminal failures at zero for the supported acceptance corpus.
 */
object OcrIndexReadiness {
    fun hasProcessableWork(records: List<PhotoRecord>): Boolean {
        return records.any { photo -> photo.ocrStatus.shouldProcess }
    }
}
